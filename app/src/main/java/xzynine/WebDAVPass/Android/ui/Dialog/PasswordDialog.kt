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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
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
    show: MutableState<Boolean>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    confirmButtonText: String = "确认",
    dismissButtonText: String = "取消"
) {
    val password = remember { mutableStateOf("") }
    val isPasswordVisible = remember { mutableStateOf(false) }
    
    WindowDialog(
        title = title,
        summary = summary,
        show = show,
        onDismissRequest = {
            password.value = ""
            onDismiss()
        }
    ) {
        BackHandler(enabled = true) {
            password.value = ""
            onDismiss()
        }
        
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
                    onClick = {
                        password.value = ""
                        onDismiss()
                    },
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
