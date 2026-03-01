package xzynine.WebDAVPass.Android.ui.Dialog

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
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
import androidx.activity.compose.BackHandler
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
import top.yukonga.miuix.kmp.extra.WindowDialog
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.Hide
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Show

/**
 * WebDAV配置内容组件
 */
@Composable
fun WebDavConfigContent(
    onDismiss: () -> Unit,
    onConfigSaved: (config: WebDavConfig) -> Unit,
    existingConfig: WebDavConfig? = null
) {
    val context = LocalContext.current
    val originalUrl = existingConfig?.url?.let {
        if (it.endsWith("/WebDavPass/") || it.endsWith("/WebDavPass")) {
            it.substringBeforeLast("/WebDavPass")
        } else {
            it
        }
    } ?: "https://dav.jianguoyun.com/dav/"

    var serverUrl by remember { mutableStateOf(originalUrl) }
    var username by remember { mutableStateOf(existingConfig?.username ?: "") }
    var password by remember { mutableStateOf("") }
    var remoteFolder by remember { mutableStateOf("WebDavPass") }
    var defaultFileName by remember { mutableStateOf("WebDavPass.kdbx") }
    var listStatus by remember { mutableStateOf("") }
    var listing by remember { mutableStateOf<List<String>>(emptyList()) }
    var isTesting by remember { mutableStateOf(false) }
    // 密码默认隐藏，并且不可解除隐藏
    val passwordVisible = false
    var urlError by remember { mutableStateOf<String?>(null) }
    val isExistingConfig = existingConfig != null
    val coroutineScope = rememberCoroutineScope()

    fun validateUrl(url: String): String? {
        return if (url.isNotEmpty() && !url.matches(Regex("^https?://.*"))) {
            "请输入有效的HTTP/HTTPS URL"
        } else null
    }

    fun normalizeBaseUrl(raw: String, folderName: String): String {
        val base = if (raw.endsWith('/')) raw else "$raw/"
        val normalizedFolder = folderName.trim('/').ifBlank { "WebDavPass" }
        return "$base$normalizedFolder/"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TextField(
            value = serverUrl,
            onValueChange = {
                serverUrl = it
                urlError = validateUrl(it)
            },
            label = "服务器地址",
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = {
                Icon(
                    imageVector = MiuixIcons.Info,
                    contentDescription = "服务器地址",
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        )

        if (urlError != null) {
            Text(
                text = urlError!!,
                color = MiuixTheme.colorScheme.error,
                fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 4.dp)
            )
        }

        TextField(
            value = username,
            onValueChange = { username = it },
            label = "用户名",
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = {
                Icon(
                    imageVector = MiuixIcons.Contacts,
                    contentDescription = "用户名",
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        )

        TextField(
            value = remoteFolder,
            onValueChange = { remoteFolder = it },
            label = "远端目录（默认 WebDavPass）",
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = {
                Icon(
                    imageVector = MiuixIcons.Info,
                    contentDescription = "远端目录",
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        )

        TextField(
            value = defaultFileName,
            onValueChange = { defaultFileName = it },
            label = "默认文件名（.kdbx）",
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = {
                Icon(
                    imageVector = MiuixIcons.Info,
                    contentDescription = "默认文件名",
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        )

        TextField(
            value = password,
            onValueChange = { password = it },
            label = if (isExistingConfig) "密码（留空则保持原密码）" else "密码",
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            leadingIcon = {
                Icon(
                    imageVector = MiuixIcons.Lock,
                    contentDescription = "密码",
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        )

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

                coroutineScope.launch(Dispatchers.IO) {
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
            modifier = Modifier.fillMaxWidth(),
            enabled = !isTesting
        ) {
            Text(text = if (isTesting) "测试中..." else "测试连接")
        }

        Button(
            onClick = {
                if (serverUrl.trim().isEmpty() || username.isBlank() || password.isBlank()) {
                    Toast.makeText(context, "请先填写地址、用户名和密码", Toast.LENGTH_SHORT).show()
                    return@Button
                }

                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        listStatus = "正在加载 .kdbx 列表..."
                        val webDav = WebDav(normalizeBaseUrl(serverUrl, remoteFolder), Authorization(username, password))
                        val files = webDav.listFiles()
                            .filter { !it.isDir && it.displayName.endsWith(".kdbx", ignoreCase = true) }
                            .map { it.displayName }

                        withContext(Dispatchers.Main) {
                            listing = files
                            listStatus = if (files.isEmpty()) "未找到 .kdbx 文件" else "请选择文件或手动填写"
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            listStatus = "加载失败: ${e.message}"
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isTesting
        ) {
            Text(text = "浏览远端 .kdbx")
        }

        if (listStatus.isNotBlank()) {
            Text(text = listStatus, fontSize = 12.sp)
        }

        listing.forEach { file ->
            Button(
                onClick = { defaultFileName = file },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isTesting
            ) {
                Text(text = file)
            }
        }

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

                val finalPassword = if (password.isEmpty() && isExistingConfig) {
                    existingConfig!!.password
                } else {
                    password
                }

                if (finalPassword.isEmpty()) {
                    Toast.makeText(context, "请输入密码", Toast.LENGTH_SHORT).show()
                    return@Button
                }

                val normalizedFolder = remoteFolder.trim('/').ifBlank { "WebDavPass" }
                val webdavUrl = if (serverUrl.endsWith("/")) {
                    "${serverUrl}${normalizedFolder}/"
                } else {
                    "${serverUrl}/${normalizedFolder}/"
                }

                val config = if (isExistingConfig) {
                    existingConfig!!.copy(
                        url = webdavUrl,
                        username = username,
                        password = finalPassword
                    )
                } else {
                    WebDavConfig(
                        id = 0,
                        name = "默认WebDAV",
                        url = webdavUrl,
                        username = username,
                        password = finalPassword
                    )
                }

                onConfigSaved(config)
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isTesting
        ) {
            Text(text = "保存配置")
        }
    }
}

/**
 * WebDAV配置弹窗组件
 */
@Composable
fun WebDavConfigDialog(
    showDialog: MutableState<Boolean>,
    onDismissRequest: () -> Unit,
    onConfigSaved: (config: WebDavConfig) -> Unit = { _ -> },
    existingConfig: WebDavConfig? = null
) {
    WindowDialog(
        title = if (existingConfig != null) "编辑 WebDAV 配置" else "WebDAV 配置",
        summary = if (existingConfig != null) "修改您的 WebDAV 服务器设置" else "配置 WebDAV 服务器以同步令牌",
        show = showDialog,
        onDismissRequest = onDismissRequest,
        defaultWindowInsetsPadding = true,
        insideMargin = DpSize(16.dp, 16.dp)
    ) {
        BackHandler(enabled = true) {
            onDismissRequest()
        }

        WebDavConfigContent(
            onDismiss = onDismissRequest,
            onConfigSaved = onConfigSaved,
            existingConfig = existingConfig
        )
    }
}