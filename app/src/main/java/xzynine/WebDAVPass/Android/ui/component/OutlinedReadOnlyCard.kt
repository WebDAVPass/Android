package xzynine.WebDAVPass.Android.ui.component

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/**
 * 带 0.5dp 描边的只读卡片外壳：固定 surface 底色、无点击反馈与按压指示。
 *
 * @param modifier 外层修饰符
 * @param cornerRadius 圆角半径，默认 12dp
 * @param content 卡片内容
 */
@Composable
fun OutlinedReadOnlyCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
    content: @Composable () -> Unit,
) {
    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .border(
                    width = 0.5.dp,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(cornerRadius),
                ),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surface),
        cornerRadius = cornerRadius,
        pressFeedbackType = PressFeedbackType.None,
        showIndication = false,
        onClick = {},
    ) {
        content()
    }
}
