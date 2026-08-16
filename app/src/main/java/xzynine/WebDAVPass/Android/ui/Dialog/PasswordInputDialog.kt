package xzynine.WebDAVPass.Android.ui.Dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 通用主密码输入对话框（用于合并库等需验证文件密码的场景）。
 *
 * @param show 是否显示
 * @param title 对话框标题
 * @param summary 对话框摘要（可选）
 * @param confirmButtonText 确认按钮文本
 * @param onDismiss 取消回调
 * @param onConfirm 确认回调，参数为输入的密码
 */
@Composable
fun PasswordInputDialog(
    show: Boolean,
    title: String,
    summary: String? = null,
    confirmButtonText: String = "确定",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    LaunchedEffect(show) {
        if (show) {
            password = ""
            showPassword = false
            status = ""
        }
    }

    WindowDialog(
        title = title,
        summary = summary,
        show = show,
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextField(
                value = password,
                onValueChange = {
                    password = it
                    status = ""
                },
                label = "主密码",
                visualTransformation =
                    if (showPassword) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(
                text = if (showPassword) "隐藏密码" else "显示密码",
                onClick = { showPassword = !showPassword },
            )
            if (status.isNotBlank()) {
                Text(text = status)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(
                    text = "取消",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(16.dp))
                Button(
                    onClick = {
                        if (password.isBlank()) {
                            status = "请输入主密码"
                        } else {
                            onConfirm(password)
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(confirmButtonText)
                }
            }
        }
    }
}
