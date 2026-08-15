package xzynine.WebDAVPass.Android.ui.component

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kunzisoft.keepass.icon.IconPack
import org.liberty.android.freeotp.token_images.TokenImage
import org.liberty.android.freeotp.token_images.matchToken
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VpnKey
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import xzynine.WebDAVPass.Android.data.OtpToken
import xzynine.WebDAVPass.Android.data.TokenCode

/**
 * 进程级 IconPack 单例。
 *
 * IconPack 构造会循环调用 69+2 次 resources.getIdentifier()，代价高昂；
 * 原实现每行 remember 新建一次，全平铺大库时主线程开销显著。
 */
private object IconPackHolder {
    @Volatile
    private var instance: IconPack? = null

    fun get(context: Context): IconPack {
        instance?.let { return it }
        return synchronized(this) {
            instance ?: IconPack(
                context.applicationContext.packageName,
                context.applicationContext.resources,
                com.kunzisoft.keepass.icon.material.R.string.resource_id
            ).also { instance = it }
        }
    }
}

/**
 * 图标资源 ID 的全局 LRU 缓存（容量 256，访问序淘汰）。
 *
 * 键为 (标准图标ID, 主文案, 副文案)，覆盖 KeePass 标准图标与 Token 品牌图标两条路径，
 * 避免滚动回看时每行重复构建 IconPack 与线性扫描 ~270 个 TokenImage 枚举。
 */
private const val ICON_RES_CACHE_MAX_SIZE = 256

private data class IconResCacheKey(
    val standardIconId: Int?,
    val primary: String?,
    val secondary: String?
)

private val iconResCache = object : LinkedHashMap<IconResCacheKey, Int?>(16, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<IconResCacheKey, Int?>) =
        size > ICON_RES_CACHE_MAX_SIZE
}

/**
 * 解析条目图标资源 ID：优先 KeePass 标准图标，其次 Token 品牌图标。
 */
private fun computeEntryIconRes(
    context: Context,
    standardIconId: Int?,
    primary: String?,
    secondary: String?
): Int? {
    if (standardIconId != null) {
        val keepassRes = runCatching {
            IconPackHolder.get(context).iconToResId(standardIconId)
        }.getOrNull()?.takeIf { it != com.kunzisoft.keepass.icon.R.drawable.ic_blank_32dp }
        if (keepassRes != null) {
            return keepassRes
        }
    }
    return TokenImage.values().firstOrNull { it.matchToken(primary, secondary) }?.resource
}

@Composable
fun EntryIcon(
    customIconBytes: ByteArray? = null,
    standardIconId: Int? = null,
    primary: String?,
    secondary: String?,
    modifier: Modifier = Modifier,
    contentDescription: String = "图标"
) {
    val context = LocalContext.current

    /**
     * 1) 优先渲染自定义图标（二进制，含固化的品牌图标）。
     * 在后台线程解码，避免主线程阻塞导致滚动卡顿。
     */
    val customBitmap: Bitmap? by produceState<Bitmap?>(initialValue = null, key1 = customIconBytes) {
        value = if (customIconBytes != null) {
            withContext(Dispatchers.Default) {
                runCatching {
                    BitmapFactory.decodeByteArray(customIconBytes, 0, customIconBytes.size)
                }.getOrNull()
            }
        } else {
            null
        }
    }

    if (customBitmap != null) {
        BrandIconFrame(modifier = modifier) {
            Image(
                bitmap = customBitmap!!.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize()
            )
        }
        return
    }

    /**
     * 2) 渲染 Token 品牌图标（按 issuer/label 匹配）。
     * 仅当数字标准图标未选择（默认 0 或空）时生效；
     * 结果按主副文案记忆化，避免每行重复扫描 TokenImage 枚举。
     */
    val tokenImageRes: Int? = remember(primary, secondary) {
        TokenImage.values().firstOrNull { it.matchToken(primary, secondary) }?.resource
    }
    if (standardIconId == null || standardIconId == 0) {
        tokenImageRes?.let {
            BrandIconFrame(modifier = modifier) {
                Image(
                    painter = painterResource(id = it),
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize()
                )
            }
            return
        }
    }

    /**
     * 3) 渲染 KeePass 标准图标的现代化版本（Miuix / Material 矢量图标）。
     * 显式选择的数字标准图标（ID != 0）优先于令牌品牌图标，
     * 使「有品牌图标的条目也能设置数字标准类型」。
     * 语义映射见 StandardIconIcons.kt；使用 primary 主题色着色。
     */
    if (standardIconId != null) {
        standardIconVectorMap[standardIconId]?.let { vector ->
            BrandIconFrame(modifier = modifier) {
                Icon(
                    imageVector = vector,
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize(),
                    tint = MiuixTheme.colorScheme.primary
                )
            }
            return
        }
    }

    /**
     * 4) 渲染 KeePass 标准图标（数据库标准图标ID）或 Token 品牌图标兜底。
     * 结果按 (standardIconId, primary, secondary) 全局 LRU 缓存；
     * IconPack 为进程级单例，避免每行重复 71 次 getIdentifier。
     */
    val iconRes: Int? = remember(standardIconId, primary, secondary) {
        iconResCache.getOrPut(IconResCacheKey(standardIconId, primary, secondary)) {
            computeEntryIconRes(context, standardIconId, primary, secondary)
        }
    }

    if (iconRes != null) {
        BrandIconFrame(modifier = modifier) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize()
            )
        }
        return
    }

    /**
     * 5) 无任何图标时的统一兜底：按 0 号标准图标（钥匙）渲染，
     * 与密码条目列表（standardIconId=0）的显示一致；原首字母圆形已废弃。
     */
    BrandIconFrame(modifier = modifier) {
        Icon(
            imageVector = standardIconVectorMap[0] ?: Icons.Rounded.VpnKey,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            tint = MiuixTheme.colorScheme.primary
        )
    }
}

/**
 * 品牌图标背景容器：纯白圆角底板，
 * 深色主题下衬托苹果等深色系品牌图标；不存在纯白 logo，白色底板足够。
 * 图标内容填满容器不缩放。
 */
@Composable
private fun BrandIconFrame(
    modifier: Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
fun TokenCard(
    token: OtpToken,
    tokenCode: TokenCode?,
    currentTimeMillis: Long,
    customIconBytes: ByteArray? = null,
    standardIconId: Int? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .padding(16.dp),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.surface
        ),
        pressFeedbackType = PressFeedbackType.Sink,
        showIndication = true,
        onClick = {
            if (tokenCode != null) {
                onClick()
            }
        },
        onLongPress = { onLongClick?.invoke() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                // 占据剩余宽度并约束文本列，防止账号过长把右侧倒计时挤出/顶歪
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            ) {
                EntryIcon(
                    customIconBytes = customIconBytes,
                    standardIconId = standardIconId,
                    primary = token.issuer,
                    secondary = token.label,
                    modifier = Modifier.size(32.dp),
                    contentDescription = "令牌图标"
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (token.issuer != null) {
                        ScrollableSingleLineText(
                            text = token.issuer,
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                    }

                    ScrollableSingleLineText(
                        text = token.label,
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )

                    tokenCode?.let { code ->
                        TokenCodeDisplay(code = code, currentTimeMillis = currentTimeMillis)
                    }
                }
            }

            tokenCode?.let { code ->
                CountdownDisplay(code = code, currentTimeMillis = currentTimeMillis)
            }
        }
    }
}

/**
 * 单行文本：超长时自动横向滚动（跑马灯）循环显示完整内容，不做省略号截断；
 * 长度未超限时静态显示。禁用手动拖动，仅程序化滚动。
 */
private const val MARQUEE_SPEED_PX_PER_MS = 0.1f
private const val MARQUEE_PAUSE_MS = 1200L

@Composable
private fun ScrollableSingleLineText(
    text: String,
    fontSize: TextUnit,
    color: Color
) {
    val scrollState = rememberScrollState()
    var textWidth by remember { mutableIntStateOf(0) }
    var viewportWidth by remember { mutableIntStateOf(0) }
    val overflow = viewportWidth > 0 && textWidth > viewportWidth

    // 超长时循环滚动：滚到末尾 → 停顿 → 滚回开头 → 停顿
    LaunchedEffect(overflow, textWidth, viewportWidth) {
        if (!overflow) return@LaunchedEffect
        val range = (textWidth - viewportWidth).coerceAtLeast(0)
        val durationMs = (range / MARQUEE_SPEED_PX_PER_MS).toInt().coerceAtLeast(1500)
        while (true) {
            scrollState.animateScrollTo(range, tween(durationMs, easing = LinearEasing))
            delay(MARQUEE_PAUSE_MS)
            scrollState.animateScrollTo(0, tween(durationMs, easing = LinearEasing))
            delay(MARQUEE_PAUSE_MS)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clipToBounds()
            // enabled=false：禁用触摸拖动，仅用于无约束测量文本宽度与程序化滚动
            .horizontalScroll(scrollState, enabled = false)
            .onSizeChanged { viewportWidth = it.width }
    ) {
        Text(
            text = text,
            fontSize = fontSize,
            color = color,
            maxLines = 1,
            onTextLayout = { textWidth = it.size.width }
        )
    }
}

@Composable
private fun TokenCodeDisplay(code: TokenCode, currentTimeMillis: Long) {
    val remainingTime = code.getSecondsRemaining(currentTimeMillis)
    val isLast5Seconds = remainingTime <= 5
    val isLast1Second = remainingTime <= 1

    val nextCodeAlpha by animateFloatAsState(
        targetValue = if (isLast5Seconds && code.next != null) 1f else 0f,
        animationSpec = tween(500),
        label = "alpha"
    )

    val currentCodeAlpha by animateFloatAsState(
        targetValue = if (isLast1Second) 0.3f else 1f,
        animationSpec = tween(300),
        label = "currentAlpha"
    )

    val nextCodeOffset by animateFloatAsState(
        targetValue = if (isLast5Seconds && code.next != null) 0f else 20f,
        animationSpec = tween(500),
        label = "offset"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = code.code,
            fontSize = 24.sp,
            color = if (remainingTime <= 5) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.primary,
            modifier = Modifier.alpha(currentCodeAlpha)
        )

        code.next?.let {
            Text(
                text = it.code,
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .alpha(nextCodeAlpha * 0.9f)
                    .offset { IntOffset(nextCodeOffset.toInt(), 0) }
            )
        }
    }
}

@Composable
private fun CountdownDisplay(code: TokenCode, currentTimeMillis: Long) {
    val remainingTime = code.getSecondsRemaining(currentTimeMillis)
    val actualPeriod = code.period
    val progress = (remainingTime.toFloat() / actualPeriod.toFloat()).coerceIn(0f, 1f)

    Box(
        modifier = Modifier.size(56.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            progress = progress,
            modifier = Modifier
                .size(56.dp)
                .offset(x = 14.dp, y = 14.dp),
            strokeWidth = 4.dp,
            colors = ProgressIndicatorDefaults.progressIndicatorColors(
                foregroundColor = if (remainingTime <= 5) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.primary,
                backgroundColor = MiuixTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.1f)
            )
        )

        Text(
            text = "${remainingTime}s",
            fontSize = 13.sp,
            color = if (remainingTime <= 5) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.primary
        )
    }
}
