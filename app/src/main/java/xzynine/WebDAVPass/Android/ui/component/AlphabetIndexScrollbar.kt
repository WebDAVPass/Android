package xzynine.WebDAVPass.Android.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
    letters: List<String>,
    enabledLetters: Set<String>,
    activeLetter: String?,
    onLetterSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (letters.isEmpty()) return

    var barHeightPx by remember { mutableStateOf(0) }
    var indicatorLetter by remember { mutableStateOf<String?>(null) }

    /**
     * 按触摸纵向位置计算索引，并触发跳转回调。
     */
    fun updateByTouchY(y: Float) {
        if (barHeightPx <= 0) return
        val itemHeight = barHeightPx.toFloat() / letters.size
        val index = (y / itemHeight).toInt().coerceIn(0, letters.lastIndex)
        val letter = letters[index]
        if (enabledLetters.contains(letter)) {
            indicatorLetter = letter
            onLetterSelected(letter)
        }
    }

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(30.dp)
                .fillMaxHeight()
                .padding(vertical = 8.dp)
                .background(
                    color = MiuixTheme.colorScheme.surface.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(16.dp)
                )
                .onSizeChanged { barHeightPx = it.height }
                .pointerInput(letters, enabledLetters) {
                    detectTapGestures(
                        onTap = { offset ->
                            updateByTouchY(offset.y)
                            indicatorLetter = null
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
                        },
                        onDragCancel = {
                            indicatorLetter = null
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(PaddingValues(vertical = 6.dp))
            ) {
                letters.forEach { letter ->
                    val isEnabled = enabledLetters.contains(letter)
                    val isHighlighted = letter == (indicatorLetter ?: activeLetter)

                    Text(
                        text = letter,
                        color = when {
                            isHighlighted -> MiuixTheme.colorScheme.primary
                            isEnabled -> MiuixTheme.colorScheme.onSurfaceSecondary
                            else -> MiuixTheme.colorScheme.onSurfaceSecondary.copy(alpha = 0.45f)
                        },
                        modifier = Modifier.padding(vertical = 1.dp)
                    )
                }
            }
        }

        indicatorLetter?.let { letter ->
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(end = 8.dp)
                    .background(
                        color = MiuixTheme.colorScheme.primary,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = letter,
                    color = MiuixTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}