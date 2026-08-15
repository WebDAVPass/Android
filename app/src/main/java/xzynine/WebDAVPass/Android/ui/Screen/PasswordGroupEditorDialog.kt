package xzynine.WebDAVPass.Android.ui.Screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 分组编辑对话框（用于新增与编辑）。
 */
@Composable
fun PasswordGroupEditorDialog(
    title: String,
    show: androidx.compose.runtime.MutableState<Boolean>,
    groupTitle: String,
    groupNotes: String,
    onGroupTitleChange: (String) -> Unit,
    onGroupNotesChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    WindowDialog(
        title = title,
        show = show.value,
        onDismissRequest = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TextField(
                value = groupTitle,
                onValueChange = onGroupTitleChange,
                label = "分组标题",
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            TextField(
                value = groupNotes,
                onValueChange = onGroupNotesChange,
                label = "备注",
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = MiuixIcons.Close,
                        contentDescription = "取消"
                    )
                }
                Button(
                    onClick = onConfirm,
                    enabled = groupTitle.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = MiuixIcons.Ok,
                        contentDescription = "保存"
                    )
                }
            }
        }
    }
}
