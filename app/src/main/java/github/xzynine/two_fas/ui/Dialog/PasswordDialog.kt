package github.xzynine.two_fas.ui.Dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.extra.SuperDialog

/**
 * 密码输入对话框
 * @param title 对话框标题
 * @param summary 对话框摘要（可选）
 * @param show 是否显示对话框
 * @param onDismiss 取消回调
 * @param onConfirm 确认回调，返回输入的密码
 * @param confirmButtonText 确认按钮文本
 * @param dismissButtonText 取消按钮文本
 */
@Composable
fun PasswordDialog(
    title: String,
    summary: String? = null,
    show: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    confirmButtonText: String = "确认",
    dismissButtonText: String = "取消"
) {
    val password = remember { mutableStateOf("") }
    val isPasswordVisible = remember { mutableStateOf(false) }
    
    SuperDialog(
        title = title,
        summary = summary,
        show = remember { mutableStateOf(show) },
        onDismissRequest = onDismiss
    ) {
        Column {
            // 密码输入框
            TextField(
                value = password.value,
                onValueChange = { password.value = it },
                label = "请输入密码",
                visualTransformation = if (isPasswordVisible.value) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    TextButton(
                        text = if (isPasswordVisible.value) "隐藏" else "显示",
                        onClick = { isPasswordVisible.value = !isPasswordVisible.value }
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 按钮行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 取消按钮
                TextButton(
                    text = dismissButtonText,
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(16.dp))

                // 确认按钮
                Button(
                    onClick = {
                        onConfirm(password.value)
                        password.value = "" // 清空密码
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = confirmButtonText)
                }
            }
        }
    }
}
