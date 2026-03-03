package xzynine.WebDAVPass.Android.ui.component

import android.graphics.BitmapFactory
import android.widget.ImageView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.amulyakhare.textdrawable.TextDrawable
import com.kunzisoft.keepass.icon.IconPack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.liberty.android.freeotp.token_images.TokenImage
import org.liberty.android.freeotp.token_images.matchToken
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import xzynine.WebDAVPass.Android.data.OtpToken
import xzynine.WebDAVPass.Android.data.TokenCode

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
     * 1) 优先渲染 KeePass 自定义图标（二进制）。
     */
    val customBitmap = remember(customIconBytes) {
        customIconBytes?.let { bytes ->
            runCatching {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }.getOrNull()
        }
    }

    if (customBitmap != null) {
        Image(
            bitmap = customBitmap.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier
        )
        return
    }

    /**
     * 2) 渲染 KeePass 标准图标（数据库标准图标ID）。
     */
    val keepassIconRes: Int? = remember(standardIconId) {
        val iconId = standardIconId ?: return@remember null
        runCatching {
            val iconPack = IconPack(
                context.packageName,
                context.resources,
                com.kunzisoft.keepass.icon.material.R.string.resource_id
            )
            iconPack.iconToResId(iconId)
        }.getOrNull()?.takeIf { it != com.kunzisoft.keepass.icon.R.drawable.ic_blank_32dp }
    }

    if (keepassIconRes != null) {
        Image(
            painter = painterResource(id = keepassIconRes),
            contentDescription = contentDescription,
            modifier = modifier
        )
        return
    }

    val matchedRes: Int? = remember(primary, secondary) {
        TokenImage.values().firstOrNull {
            it.matchToken(primary, secondary)
        }?.resource
    }

    if (matchedRes != null) {
        Image(
            painter = painterResource(id = matchedRes),
            contentDescription = contentDescription,
            modifier = modifier
        )
        return
    }

    val letter = remember(primary, secondary) {
        (primary ?: secondary).orEmpty().firstOrNull()?.uppercase() ?: "?"
    }
    val colorInt = MiuixTheme.colorScheme.primary.toArgb()

    AndroidView(
        modifier = modifier,
        factory = { context ->
            ImageView(context).apply {
                val drawable = TextDrawable.builder().buildRound(letter, colorInt)
                setImageDrawable(drawable)
            }
        }
    )
}

@Composable
fun TokenCard(
    token: OtpToken,
    tokenCode: TokenCode?,
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
                horizontalArrangement = Arrangement.spacedBy(16.dp)
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
                        Text(
                            text = token.issuer,
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                    }

                    Text(
                        text = token.label,
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )

                    tokenCode?.let { code ->
                        TokenCodeDisplay(code = code)
                    }
                }
            }

            tokenCode?.let { code ->
                CountdownDisplay(code = code)
            }
        }
    }
}

@Composable
private fun TokenCodeDisplay(code: TokenCode) {
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    
    LaunchedEffect(code) {
        while (true) {
            try {
                delay(1000)
                currentTime = System.currentTimeMillis()
            } catch (e: CancellationException) {
                // 协程被取消，正常退出循环
                break
            } catch (e: Exception) {
                // 其他异常记录日志，但继续循环
                e.printStackTrace()
                // 避免快速循环导致CPU占用过高
                try {
                    delay(1000)
                } catch (_: CancellationException) {
                    break
                }
            }
        }
    }

    val remainingTime = code.getSecondsRemaining(currentTime)
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
private fun CountdownDisplay(code: TokenCode) {
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    
    LaunchedEffect(code) {
        while (true) {
            try {
                delay(1000)
                currentTime = System.currentTimeMillis()
            } catch (e: CancellationException) {
                // 协程被取消，正常退出循环
                break
            } catch (e: Exception) {
                // 其他异常记录日志，但继续循环
                e.printStackTrace()
                // 避免快速循环导致CPU占用过高
                try {
                    delay(1000)
                } catch (_: CancellationException) {
                    break
                }
            }
        }
    }

    val remainingTime = code.getSecondsRemaining(currentTime)
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
