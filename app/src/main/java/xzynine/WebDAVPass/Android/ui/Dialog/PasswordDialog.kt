package xzynine.WebDAVPass.Android.ui.Dialog

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
import top.yukonga.miuix.kmp.extra.WindowDialog

/**
 * 密码输入对话框
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
    val showState = remember { mutableStateOf(show) }
    showState.value = show
    
    WindowDialog(
        title = title,
        summary = summary,
        show = showState,
        onDismissRequest = onDismiss
    ) {
        Column {
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

                Button(
                    onClick = {
                        onConfirm(password.value)
                        password.value = ""
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = confirmButtonText)
                }
            }
        }
    }
}
