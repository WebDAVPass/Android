package xzynine.WebDAVPass.Android.ui.Dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
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
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.extra.WindowDialog

/**
 * 创建模式
 */
enum class CreateMode {
    LOCAL,
    CLOUD
}

/**
 * 创建主密码对话框
 * @param mode 创建模式：本地或云端
 * @param onDismiss 关闭回调
 * @param onConfirm 确认回调，返回输入的密码
 */
@Composable
fun CreateMasterPasswordDialog(
    mode: CreateMode,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val show = remember { mutableStateOf(true) }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    WindowDialog(
        title = if (mode == CreateMode.LOCAL) "本地新建：设置主密码" else "云端新建：设置主密码",
        summary = "主密码用于解锁 .kdbx 数据库",
        show = show,
        onDismissRequest = onDismiss,
        defaultWindowInsetsPadding = true
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextField(
                value = password,
                onValueChange = {
                    password = it
                    status = ""
                },
                label = "主密码",
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true
            )
            TextField(
                value = confirmPassword,
                onValueChange = {
                    confirmPassword = it
                    status = ""
                },
                label = "确认主密码",
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true
            )

            Button(onClick = { showPassword = !showPassword }) {
                Text(if (showPassword) "隐藏密码" else "显示密码")
            }

            if (status.isNotBlank()) {
                Text(status)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("取消")
                }
                Button(
                    onClick = {
                        when {
                            password.isBlank() -> status = "请输入主密码"
                            password != confirmPassword -> status = "两次主密码不一致"
                            else -> onConfirm(password)
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("继续")
                }
            }
        }
    }
}
