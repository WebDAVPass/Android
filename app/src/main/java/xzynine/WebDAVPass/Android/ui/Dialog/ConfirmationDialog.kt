package xzynine.WebDAVPass.Android.ui.Dialog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import top.yukonga.miuix.kmp.window.WindowDialog
import xzynine.WebDAVPass.Android.ui.component.DialogActionRow

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
    dismissButtonText: String = "取消",
) {
    WindowDialog(
        title = title,
        summary = summary,
        show = show.value,
        onDismissRequest = onDismiss,
    ) {
        DialogActionRow(
            onDismiss = onDismiss,
            onConfirm = onConfirm,
            confirmButtonText = confirmButtonText,
            dismissButtonText = dismissButtonText,
            isDestructive = isDestructive,
        )
    }
}
