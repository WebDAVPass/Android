package xzynine.WebDAVPass.Android.ui.component

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import xzylib.base.util.HapticFeedbackUtils

/**
 * 默认的字母索引列表（A-Z + #）。
 */
val DefaultIndexLetters: List<String> = ('A'..'Z').map { it.toString() } + "#"

/**
 * 右侧字母索引滚动条。
 *
 * @property letters 显示的索引字母列表。
 * @property enabledLetters 当前可跳转的字母集合。
 * @property activeLetter 当前高亮字母（通常由列表可见位置决定）。
 * @property onLetterSelected 用户选择字母后的回调。
 */
@Composable
fun AlphabetIndexScrollbar(
    context: Context,
    letters: List<String>,
    enabledLetters: Set<String>,
    activeLetter: String?,
    onLetterSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (letters.isEmpty()) return

    val density = LocalDensity.current
    var barHeightPx by remember { mutableStateOf(0) }
    var indicatorLetter by remember { mutableStateOf<String?>(null) }
    var lastVibratedLetter by remember { mutableStateOf<String?>(null) } // 上次振动的字母
    var currentY by remember { mutableStateOf(0f) } // 当前触摸的Y轴位置（像素）

    /**
     * 按触摸纵向位置计算索引，并触发跳转回调。
     */
    fun updateByTouchY(y: Float) {
        if (barHeightPx <= 0) return
        val itemHeight = barHeightPx.toFloat() / letters.size
        val index = (y / itemHeight).toInt().coerceIn(0, letters.lastIndex)
        val letter = letters[index]
        if (enabledLetters.contains(letter)) {
            // 只有当字母发生变化时才振动
            if (letter != lastVibratedLetter) {
                // 加强振动效果，增加振动时长
                HapticFeedbackUtils.performLightHaptic(context, 50L)
                lastVibratedLetter = letter
            }
            indicatorLetter = letter
            currentY = y // 更新当前触摸的Y轴位置
            onLetterSelected(letter)
        }
    }

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(30.dp)
                .fillMaxHeight(0.92f)
                .padding(vertical = 4.dp)
                .border(
                    width = 1.dp,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(999.dp)
                )
                .background(
                    color = MiuixTheme.colorScheme.surface.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(999.dp)
                )
                .onSizeChanged { barHeightPx = it.height }
                .pointerInput(letters, enabledLetters) {
                    detectTapGestures(
                        onTap = { offset ->
                            updateByTouchY(offset.y)
                            indicatorLetter = null
                            lastVibratedLetter = null // 重置上次振动的字母
                        }
                    )
                }
                .pointerInput(letters, enabledLetters) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            updateByTouchY(offset.y)
                        },
                        onVerticalDrag = { change, _ ->
                            updateByTouchY(change.position.y)
                        },
                        onDragEnd = {
                            indicatorLetter = null
                            lastVibratedLetter = null // 重置上次振动的字母
                        },
                        onDragCancel = {
                            indicatorLetter = null
                            lastVibratedLetter = null // 重置上次振动的字母
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(PaddingValues(vertical = 2.dp))
            ) {
                letters.forEach { letter ->
                    val isEnabled = enabledLetters.contains(letter)
                    val isHighlighted = letter == (indicatorLetter ?: activeLetter)

                    Text(
                        text = letter,
                        fontSize = 10.sp,
                        color = when {
                            isHighlighted -> MiuixTheme.colorScheme.primary
                            isEnabled -> MiuixTheme.colorScheme.onSurfaceSecondary
                            else -> MiuixTheme.colorScheme.onSurfaceSecondary.copy(alpha = 0.45f)
                        }
                    )
                }
            }
        }

        indicatorLetter?.let { letter ->
            val currentYDp = with(density) { currentY.toDp() }
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = currentYDp - 20.dp, end = 24.dp) // 调整气泡位置，使其对准当前触摸的字母
                    .background(
                        color = MiuixTheme.colorScheme.primary,
                        shape = RoundedCornerShape(24.dp) // 更圆润的气泡形状
                    )
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .shadow(6.dp, RoundedCornerShape(24.dp)) // 添加阴影，增强气泡效果
            ) {
                Text(
                    text = letter,
                    color = MiuixTheme.colorScheme.onPrimary,
                    fontSize = 16.sp // 增大字体
                )
            }
        }
    }
}