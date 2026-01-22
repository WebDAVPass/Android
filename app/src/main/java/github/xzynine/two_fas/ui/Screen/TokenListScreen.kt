package github.xzynine.two_fas.ui.Screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import github.xzynine.two_fas.data.OtpToken
import github.xzynine.two_fas.ui.ViewModel.TokenViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.extra.SuperDialog
import github.xzynine.two_fas.ui.Dialog.ConfirmationDialog
import github.xzynine.two_fas.ui.Dialog.TokenDialog
import org.liberty.android.freeotp.token_images.TokenImage
import org.liberty.android.freeotp.token_images.matchToken
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.ImageView
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.IntOffset
import com.amulyakhare.textdrawable.TextDrawable
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/**
 * 弹窗状态枚举
 */
enum class DialogState { NONE, EDIT, DELETE }

/**
 * 令牌列表界面
 */
@Composable
fun TokenListScreen(tokenViewModel: TokenViewModel) {
    val context = LocalContext.current
    val tokens by tokenViewModel.tokens.collectAsState(emptyList())
    val isLoading by tokenViewModel.isLoading.collectAsState(false)

    // 长按功能状态管理 - 使用枚举确保单例
    var dialogState by remember { mutableStateOf(DialogState.NONE) }
    var selectedToken by remember { mutableStateOf<OtpToken?>(null) }

    if (isLoading) {
        Box(
            modifier = Modifier.Companion.fillMaxSize(),
            contentAlignment = Alignment.Companion.Center
        ) {
            CircularProgressIndicator()
        }
    } else if (tokens.isEmpty()) {
        Box(
            modifier = Modifier.Companion.fillMaxSize(),
            contentAlignment = Alignment.Companion.Center
        ) {
            Text(text = "暂无令牌，请添加新的2FA令牌")
        }
    } else {
        LazyColumn(
            modifier = Modifier.Companion.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            items(tokens) { token ->
                TokenItem(
                    token = token,
                    tokenViewModel = tokenViewModel,
                    onLongClick = {
                        selectedToken = token
                        dialogState = DialogState.EDIT
                    }
                )
                HorizontalDivider(
                    modifier = Modifier.Companion.padding(horizontal = 16.dp),
                    thickness = 0.5.dp
                )
            }
        }

        // 根据状态显示对应的弹窗
        when (dialogState) {
            DialogState.EDIT -> selectedToken?.let { token ->
                    TokenDialog(
                        token = token,
                        show = true,
                        onDismiss = {
                            dialogState = DialogState.NONE
                            selectedToken = null
                        },
                        onDelete = {
                            dialogState = DialogState.DELETE
                        },
                        onSave = { updatedToken ->
                            tokenViewModel.updateToken(updatedToken)
                            dialogState = DialogState.NONE
                            selectedToken = null
                        }
                    )
                }

            DialogState.DELETE -> selectedToken?.let {
                ConfirmationDialog(
                    title = "确认删除",
                    summary = "确定要删除令牌 \"${it.issuer ?: it.label}\" 吗？此操作无法撤销。",
                    show = true,
                    onDismiss = {
                        dialogState = DialogState.NONE
                        selectedToken = null
                    },
                    confirmButtonText = "删除",
                    isDestructive = true,
                    onConfirm = {
                        tokenViewModel.deleteToken(it.id)
                        dialogState = DialogState.NONE
                        selectedToken = null
                    }
                )
            }

            DialogState.NONE -> { /* 不显示任何弹窗 */ }
        }
    }
}





/**
 * 单个令牌项组件
 */
@Composable
fun TokenItem(token: OtpToken, tokenViewModel: TokenViewModel, onLongClick: () -> Unit) {
    val tokenCode by tokenViewModel.getTokenCode(token.id).collectAsState(null)

    // 实时更新的时间状态，用于倒计时显示
    val currentTime by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            delay(1000)
            value = System.currentTimeMillis()
        }
    }

    // 上下文
    val context = LocalContext.current
    // 协程作用域，用于动画
    val coroutineScope = rememberCoroutineScope()

    // 复制到剪贴板功能
    fun copyToClipboard(code: String) {
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = ClipData.newPlainText("2FA Token", code)
        clipboardManager.setPrimaryClip(clipData)
        Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }

    Card(
        modifier = Modifier.Companion
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.surface
        ),
        pressFeedbackType = PressFeedbackType.Sink,
        showIndication = true,
        onClick = {
            tokenCode?.let { copyToClipboard(it.code) }
        },
        onLongPress = onLongClick
    ) {
        Row(
            modifier = Modifier.Companion
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Companion.CenterVertically
        ) {
            // 左侧：图标、发行者、标签和6位码区域
            Row(
                verticalAlignment = Alignment.Companion.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 图标：优先匹配品牌图标，否则显示首字母占位
                val matchedRes: Int? = remember(token.issuer, token.label) {
                    TokenImage.values().firstOrNull {
                        it.matchToken(token.issuer, token.label)
                    }?.resource
                }
                if (matchedRes != null) {
                    Image(
                        painter = painterResource(id = matchedRes),
                        contentDescription = "令牌图标",
                        modifier = Modifier.Companion.size(32.dp)
                    )
                } else {
                    // 使用 text-drawable 生成圆形首字母图标占位
                    val letter = (token.issuer ?: token.label).firstOrNull()?.uppercase() ?: "?"
                    val colorInt = MiuixTheme.colorScheme.primary.toArgb()
                    AndroidView(
                        modifier = Modifier.Companion.size(32.dp),
                        factory = { context ->
                            ImageView(context).apply {
                                val drawable = TextDrawable.builder().buildRound(letter, colorInt)
                                setImageDrawable(drawable)
                            }
                        }
                    )
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // 发行者（如果有）
                    if (token.issuer != null) {
                        Text(
                            text = token.issuer,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Companion.Medium,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                    }

                    // 标签
                    Text(
                        text = token.label,
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.outline,
                        fontWeight = FontWeight.Companion.Normal
                    )

                    // 6位码 - 添加下一个令牌显示和动画
                    tokenCode?.let { code ->
                        val remainingTime = 
                            kotlin.comparisons.maxOf(0, (code.end - currentTime) / 1000)
                        val isLast5Seconds = remainingTime <= 5
                        val isLast1Second = remainingTime <= 1
                        
                        // 动画：透明度控制
                        val nextCodeAlpha by animateFloatAsState(
                            targetValue = if (isLast5Seconds && code.next != null) 1f else 0f,
                            animationSpec = tween(500),
                            label = "alpha"
                        )
                        
                        // 当前令牌在最后1秒时淡出
                        val currentCodeAlpha by animateFloatAsState(
                            targetValue = if (isLast1Second) 0.3f else 1f,
                            animationSpec = tween(300),
                            label = "currentAlpha"
                        )
                        
                        // 下一个令牌偏移
                        val nextCodeOffset by animateFloatAsState(
                            targetValue = if (isLast5Seconds && code.next != null) 0f else 20f,
                            animationSpec = tween(500),
                            label = "offset"
                        )
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 当前令牌
                            Text(
                                text = code.code,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Companion.Bold,
                                color = if (remainingTime <= 5) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.primary,
                                modifier = Modifier.alpha(currentCodeAlpha)
                            )
                            
                            // 下一个令牌：最后5秒显示
                            code.next?.let {
                                Text(
                                    text = it.code,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Companion.Medium,
                                    color = MiuixTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .padding(start = 8.dp)
                                        .alpha(nextCodeAlpha * 0.9f)
                                        .offset { IntOffset(nextCodeOffset.toInt(), 0) }
                                )
                            }
                        }
                    }
                }
            }

            // 右侧：倒计时区域（包裹在环形进度条中）
            tokenCode?.let { code ->
                val remainingTime = kotlin.comparisons.maxOf(0, (code.end - currentTime) / 1000)
                val progress = remainingTime.toFloat() / 30f // 30秒总时间

                Box(
                    modifier = Modifier.Companion.size(56.dp),
                    contentAlignment = Alignment.Companion.Center // 容器级别设置居中对齐
                ) {
                    // 环形进度条 - 向右下角偏移使其右下角与文本中心对齐
                    CircularProgressIndicator(
                        progress = progress,
                        modifier = Modifier.Companion
                            .size(56.dp)
                            .offset(x = 14.dp, y = 14.dp), // 向右下角偏移自身尺寸的四分之一
                        strokeWidth = 4.dp,
                        colors = ProgressIndicatorDefaults.progressIndicatorColors(
                            foregroundColor = if (remainingTime <= 5) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.primary,
                            backgroundColor = MiuixTheme.colorScheme.outline.copy(alpha = 0.1f)
                        )
                    )

                    // 倒计时文本 - 继承Box的居中对齐
                    Text(
                        text = "${remainingTime}s",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Companion.SemiBold,
                        color = if (remainingTime <= 5) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}