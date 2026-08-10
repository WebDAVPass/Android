package xzynine.WebDAVPass.Android.ui.Dialog

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import java.io.ByteArrayOutputStream

/** 密钥文件大小上限（1 MiB）。 */
private const val MAX_KEY_FILE_BYTES = 1024 * 1024

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
 * @param onConfirm 确认回调，返回输入的密码与可选的密钥文件字节
 */
@Composable
fun CreateMasterPasswordDialog(
    mode: CreateMode,
    onDismiss: () -> Unit,
    onConfirm: (password: String, keyFileData: ByteArray?) -> Unit
) {
    val context = LocalContext.current
    val show = remember { mutableStateOf(true) }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var keyFileName by remember { mutableStateOf("") }
    var keyFileData by remember { mutableStateOf<ByteArray?>(null) }

    val keyFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArrayOutputStream(8 * 1024)
                val chunk = ByteArray(8 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(chunk)
                    if (read < 0) break
                    total += read
                    if (total > MAX_KEY_FILE_BYTES) {
                        throw IllegalStateException("密钥文件过大")
                    }
                    buffer.write(chunk, 0, read)
                }
                val bytes = buffer.toByteArray()
                if (bytes.isNotEmpty()) {
                    keyFileName = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
                        ?: "keyfile"
                    keyFileData = bytes
                }
            }
        }.onFailure {
            Toast.makeText(context, "密钥文件读取失败：${it.message ?: "未知错误"}", Toast.LENGTH_SHORT).show()
        }
    }

    WindowDialog(
        title = if (mode == CreateMode.LOCAL) "本地新建：设置主密码" else "云端新建：设置主密码",
        summary = "主密码用于解锁 .kdbx 数据库，可另选密钥文件增强安全性",
        show = show.value,
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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { keyFilePicker.launch(arrayOf("application/octet-stream", "*/*")) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = MiuixIcons.Lock,
                    contentDescription = "密钥文件",
                    tint = MiuixTheme.colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(
                        text = "密钥文件（可选）",
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                    Text(
                        text = if (keyFileName.isBlank()) "点击选择密钥文件" else keyFileName,
                        fontSize = 14.sp,
                        color = if (keyFileName.isBlank()) MiuixTheme.colorScheme.primary
                        else MiuixTheme.colorScheme.onSurface
                    )
                }
                if (keyFileName.isNotBlank()) {
                    IconButton(onClick = {
                        keyFileName = ""
                        keyFileData = null
                    }) {
                        Icon(
                            imageVector = MiuixIcons.Delete,
                            contentDescription = "清除密钥文件",
                            tint = MiuixTheme.colorScheme.onSurfaceSecondary
                        )
                    }
                }
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
                            else -> onConfirm(password, keyFileData)
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
