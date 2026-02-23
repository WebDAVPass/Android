package xzynine.WebDAVPass.Android.ui.Dialog

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.DpSize
import xzynine.WebDAVPass.Android.data.WebDavConfig
import xzynine.WebDAVPass.webdav.Authorization
import xzynine.WebDAVPass.webdav.WebDav
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.Info

/**
 * WebDAV配置内容组件，用于在弹窗中显示
 * @param onDismiss 关闭弹窗的回调
 * @param onConfigSaved 配置保存成功的回调
 * @param existingConfig 现有的WebDAV配置，如果为null则表示首次配置
 */
@Composable
fun WebDavConfigContent(
    onDismiss: () -> Unit,
    onConfigSaved: (config: WebDavConfig) -> Unit,
    existingConfig: WebDavConfig? = null
) {
    val context = LocalContext.current
    // 处理现有配置的URL，移除自定义路径
    val originalUrl = existingConfig?.url?.let {
        if (it.endsWith("/2fas_xzy/") || it.endsWith("/2fas_xzy")) {
            it.substringBeforeLast("/2fas_xzy")
        } else {
            it
        }
    } ?: "https://dav.jianguoyun.com/dav/"
    
    // 设置默认服务器URL为坚果云WebDAV地址
    var serverUrl by remember { mutableStateOf(originalUrl) }
    var username by remember { mutableStateOf(existingConfig?.username ?: "") }
    var password by remember { mutableStateOf("") }
    var isTesting by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    var urlError by remember { mutableStateOf<String?>(null) }
    val isExistingConfig = existingConfig != null
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
                    imageVector = MiuixIcons.Info,
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
                    imageVector = MiuixIcons.Contacts,
                    contentDescription = "用户名",
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        )

        // 密码输入框
        TextField(
            value = password,
            onValueChange = { password = it },
            label = if (isExistingConfig) "密码（留空则保持原密码）" else "密码",
            modifier = Modifier.Companion.fillMaxWidth(),
            singleLine = true,
            // 支持切换密码可见性
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            leadingIcon = {
                Icon(
                    imageVector = MiuixIcons.Back,
                    contentDescription = "密码",
                    modifier = Modifier.Companion.padding(horizontal = 12.dp)
                )
            },
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
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
                
                // 测试连接时，如果密码为空且是现有配置，则使用现有密码
                val testPassword = if (password.isEmpty() && isExistingConfig) {
                    existingConfig!!.password
                } else {
                    password
                }
                
                if (testPassword.isEmpty()) {
                    Toast.makeText(context, "请输入密码", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                
                isTesting = true
                Toast.makeText(context, "正在测试连接...", Toast.LENGTH_SHORT).show()

                // 在协程中测试连接
                coroutineScope.launch {
                    try {
                        val webDav = WebDav(serverUrl, Authorization(username, testPassword))
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

                // 如果密码为空且是现有配置，则使用现有密码
                val finalPassword = if (password.isEmpty() && isExistingConfig) {
                    existingConfig!!.password
                } else {
                    password
                }
                
                if (finalPassword.isEmpty()) {
                    Toast.makeText(context, "请输入密码", Toast.LENGTH_SHORT).show()
                    return@Button
                }

                // 创建包含2fas_xzy子目录的URL
                val webdavUrl = if (serverUrl.endsWith("/")) {
                    "${serverUrl}2fas_xzy/"
                } else {
                    "${serverUrl}/2fas_xzy/"
                }

                // 创建或更新配置
                val config = if (isExistingConfig) {
                    // 更新现有配置
                    existingConfig!!.copy(
                        url = webdavUrl,
                        username = username,
                        password = finalPassword
                    )
                } else {
                    // 创建新配置
                    WebDavConfig(
                        id = 0,
                        name = "默认WebDAV",
                        url = webdavUrl,
                        username = username,
                        password = finalPassword
                    )
                }

                // 保存配置并关闭弹窗
                onConfigSaved(config)
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
 * @param existingConfig 现有的WebDAV配置，如果为null则表示首次配置
 */
@Composable
fun WebDavConfigDialog(
    showDialog: Boolean,
    onDismissRequest: () -> Unit,
    onConfigSaved: (config: WebDavConfig) -> Unit = { _ -> },
    existingConfig: WebDavConfig? = null
) {
    // 使用可变状态来控制弹窗显示，直接使用外部传入的 showDialog 值
    val isVisible = remember {
        mutableStateOf(showDialog)
    }
    
    // 当外部 showDialog 变化时，更新内部状态
    LaunchedEffect(showDialog) {
        isVisible.value = showDialog
    }
    
    SuperDialog(
        title = if (existingConfig != null) "编辑 WebDAV 配置" else "WebDAV 配置",
        summary = if (existingConfig != null) "修改您的 WebDAV 服务器设置" else "配置 WebDAV 服务器以同步令牌",
        show = isVisible,
        onDismissRequest = onDismissRequest,
        defaultWindowInsetsPadding = true,
        insideMargin = DpSize(16.dp, 16.dp)
    ) {
        // 配置内容
        WebDavConfigContent(
            onDismiss = onDismissRequest,
            onConfigSaved = onConfigSaved,
            existingConfig = existingConfig
        )
    }
}