package xzynine.WebDAVPass.Android.ui.Dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.extra.WindowDialog

/**
 * 通用确认对话框
 * @param title 对话框标题
 * @param summary 对话框摘要（可选）
 * @param show 是否显示对话框
 * @param onDismiss 取消回调
 * @param confirmButtonText 确认按钮文本
 * @param isDestructive 是否为破坏性操作（如删除），会使用主色调
 * @param onConfirm 确认回调
 * @param dismissButtonText 取消按钮文本
 */
@Composable
fun ConfirmationDialog(
    title: String,
    summary: String? = null,
    show: MutableState<Boolean>,
    onDismiss: () -> Unit,
    confirmButtonText: String,
    isDestructive: Boolean = false,
    onConfirm: () -> Unit,
    dismissButtonText: String = "取消"
) {
    WindowDialog(
        title = title,
        summary = summary,
        show = show,
        onDismissRequest = onDismiss
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(
                text = dismissButtonText,
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(16.dp))

            if (isDestructive) {
                TextButton(
                    text = confirmButtonText,
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            } else {
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = confirmButtonText)
                }
            }
        }
    }
}
