package xzynine.WebDAVPass.Android.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton

/**
 * 对话框底部「取消 + 确认」按钮行：两枚按钮等宽、间距 16dp。
 *
 * @param onDismiss 取消回调
 * @param onConfirm 确认回调
 * @param confirmButtonText 确认按钮文本
 * @param dismissButtonText 取消按钮文本，默认「取消」
 * @param isDestructive 是否为破坏性操作，确认按钮改用主色调文字按钮
 * @param confirmEnabled 确认按钮是否可点击，默认可用
 */
@Composable
fun DialogActionRow(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmButtonText: String,
    dismissButtonText: String = "取消",
    isDestructive: Boolean = false,
    confirmEnabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TextButton(
            text = dismissButtonText,
            onClick = onDismiss,
            modifier = Modifier.weight(1f),
        )

        Spacer(modifier = Modifier.width(16.dp))

        if (isDestructive) {
            TextButton(
                text = confirmButtonText,
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary(),
                enabled = confirmEnabled,
            )
        } else {
            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                enabled = confirmEnabled,
            ) {
                Text(text = confirmButtonText)
            }
        }
    }
}
