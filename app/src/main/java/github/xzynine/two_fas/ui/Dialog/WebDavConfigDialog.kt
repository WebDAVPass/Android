package github.xzynine.two_fas.ui.Dialog

import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import github.xzynine.two_fas.lib.webdav.Authorization
import github.xzynine.two_fas.lib.webdav.WebDav
import github.xzynine.two_fas.theme.getAppRoundedCorner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.icons.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.icons.basic.Check
import top.yukonga.miuix.kmp.icon.icons.useful.AddSecret
import top.yukonga.miuix.kmp.icon.icons.useful.Info
import top.yukonga.miuix.kmp.icon.icons.useful.Personal
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * WebDAV配置内容组件，用于在弹窗中显示
 * @param onDismiss 关闭弹窗的回调
 * @param onConfigSaved 配置保存成功的回调
 */
@Composable
fun WebDavConfigContent(onDismiss: () -> Unit, onConfigSaved: (serverUrl: String, username: String, password: String) -> Unit) {
    val context = LocalContext.current
    // 设置默认服务器URL为坚果云WebDAV地址
    var serverUrl by remember { mutableStateOf("https://dav.jianguoyun.com/dav/") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isTesting by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    var urlError by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope() // 使用rememberCoroutineScope代替CoroutineScope

    // URL格式验证函数
    fun validateUrl(url: String): String? {
        return if (url.isNotEmpty() && !url.matches(Regex("^https?://.*"))) {
            "请输入有效的HTTP/HTTPS URL"
        } else null
    }

    // 使用普通Column布局
    Column(
        modifier = Modifier.Companion
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 服务器地址输入框
        TextField(
            value = serverUrl,
            onValueChange = {
                serverUrl = it
                urlError = validateUrl(it)
            },
            label = "服务器地址",
            modifier = Modifier.Companion.fillMaxWidth(),
            singleLine = true,
            leadingIcon = {
                Icon(
                    imageVector = MiuixIcons.Useful.Info,
                    contentDescription = "服务器地址",
                    modifier = Modifier.Companion.padding(horizontal = 12.dp)
                )
            }
        )

        if (urlError != null) {
            // URL格式错误提示
            Text(
                text = urlError!!,
                color = MiuixTheme.colorScheme.error,
                fontSize = 12.sp,
                modifier = Modifier.Companion
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 4.dp)
            )
        }

        // 用户名输入框
        TextField(
            value = username,
            onValueChange = { username = it },
            label = "用户名",
            modifier = Modifier.Companion.fillMaxWidth(),
            singleLine = true,
            leadingIcon = {
                Icon(
                    imageVector = MiuixIcons.Useful.Personal,
                    contentDescription = "用户名",
                    modifier = Modifier.Companion.padding(horizontal = 12.dp)
                )
            }
        )

        // 密码输入框
        TextField(
            value = password,
            onValueChange = { password = it },
            label = "密码",
            modifier = Modifier.Companion.fillMaxWidth(),
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.Companion.None else PasswordVisualTransformation(),
            leadingIcon = {
                Icon(
                    imageVector = MiuixIcons.Useful.AddSecret,
                    contentDescription = "密码",
                    modifier = Modifier.Companion.padding(horizontal = 12.dp)
                )
            },
            trailingIcon = {
                IconButton(
                    onClick = { passwordVisible = !passwordVisible },
                    modifier = Modifier.Companion.padding(end = 12.dp)
                ) {
                    Icon(
                        imageVector = if (passwordVisible) MiuixIcons.Basic.Check else MiuixIcons.Basic.ArrowRight,
                        contentDescription = if (passwordVisible) "隐藏密码" else "显示密码"
                    )
                }
            }
        )

        // 测试连接按钮
        Button(
            onClick = {
                if (serverUrl.trim().isEmpty()) {
                    Toast.makeText(context, "请输入服务器地址", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                if (urlError != null) {
                    Toast.makeText(context, urlError, Toast.LENGTH_SHORT).show()
                    return@Button
                }
                isTesting = true
                Toast.makeText(context, "正在测试连接...", Toast.LENGTH_SHORT).show()

                // 在协程中测试连接
                coroutineScope.launch {
                    try {
                        val webDav = WebDav(serverUrl, Authorization(username, password))
                        val success = webDav.check()

                        withContext(Dispatchers.Main) {
                            if (success) {
                                Toast.makeText(context, "连接成功", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "连接失败", Toast.LENGTH_SHORT).show()
                            }
                            isTesting = false
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "连接错误: ${e.message}", Toast.LENGTH_SHORT)
                                .show()
                            isTesting = false
                        }
                    }
                }
            },
            modifier = Modifier.Companion.fillMaxWidth(),
            enabled = !isTesting
        ) {
            Text(text = if (isTesting) "测试中..." else "测试连接")
        }

        // 浏览文件按钮
        Button(
            onClick = {
                if (serverUrl.trim().isEmpty()) {
                    Toast.makeText(context, "请输入服务器地址", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                if (urlError != null) {
                    Toast.makeText(context, urlError, Toast.LENGTH_SHORT).show()
                    return@Button
                }

                // 创建包含2fas_xzy子目录的URL
                val webdavUrl = if (serverUrl.endsWith("/")) {
                    "${serverUrl}2fas_xzy/"
                } else {
                    "${serverUrl}/2fas_xzy/"
                }

                // 保存配置并关闭弹窗
                onConfigSaved(webdavUrl, username, password)
                onDismiss()
            },
            modifier = Modifier.Companion.fillMaxWidth(),
            enabled = !isTesting
        ) {
            Text(text = "保存配置")
        }
    }
}

/**
 * WebDAV配置弹窗组件
 * @param showDialog 是否显示弹窗
 * @param onDismissRequest 关闭弹窗的回调
 * @param onConfigSaved 配置保存成功的回调
 */
@Composable
fun WebDavConfigDialog(
    showDialog: Boolean,
    onDismissRequest: () -> Unit,
    onConfigSaved: (serverUrl: String, username: String, password: String) -> Unit = { _, _, _ -> }
) {
    // WebDAV配置弹窗
    if (showDialog) {
        // 获取统一的圆角半径
        val cornerRadius = getAppRoundedCorner()

        Dialog(
            onDismissRequest = onDismissRequest
        ) {
            Surface(
                modifier = Modifier.Companion
                    .fillMaxWidth()
                    .padding(16.dp)
                    .border(1.dp, MiuixTheme.colorScheme.outline, RoundedCornerShape(cornerRadius)),
                color = MiuixTheme.colorScheme.surface,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(cornerRadius)
            ) {
                Column(
                    modifier = Modifier.Companion
                        .padding(16.dp)
                ) {
                    // 标题
                    Text(
                        text = "WebDAV 配置",
                        fontSize = 20.sp,
                        modifier = Modifier.Companion.padding(bottom = 16.dp)
                    )

                    // 配置内容
                    WebDavConfigContent(
                        onDismiss = onDismissRequest,
                        onConfigSaved = onConfigSaved
                    )

                    // 关闭按钮
                    Row(
                        modifier = Modifier.Companion
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(onClick = onDismissRequest) {
                            Text(text = "关闭")
                        }
                    }
                }
            }
        }
    }
}