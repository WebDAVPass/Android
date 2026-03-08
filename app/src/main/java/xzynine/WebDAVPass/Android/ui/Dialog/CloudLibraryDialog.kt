package xzynine.WebDAVPass.Android.ui.Dialog

import xzylib.base.util.Logger
import xzylib.base.util.ToastUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.WindowDialog
import top.yukonga.miuix.kmp.icon.MiuixIcons
import xzynine.WebDAVPass.Android.data.LibraryContext
import xzynine.WebDAVPass.Android.ui.ViewModel.TokenViewModel
import xzynine.WebDAVPass.webdav.Authorization
import xzynine.WebDAVPass.webdav.WebDav

/**
 * 云端库操作模式
 */
enum class CloudMode {
    /**
     * 云端导入
     */
    IMPORT,

    /**
     * 云端新建
     */
    CREATE,

    /**
     * 当前库绑定/编辑云端信息
     */
    BIND
}

private const val SEARCH_LOG_TAG = "tag:搜索"

/**
 * 云端库选择弹窗
 * @param tokenViewModel TokenViewModel实例
 * @param mode 操作模式：导入或新建
 * @param createMasterPassword 新建时的主密码
 * @param onDismiss 关闭回调
 * @param onSelected 选择完成回调
 */
@Composable
fun CloudLibraryDialog(
    tokenViewModel: TokenViewModel,
    mode: CloudMode,
    initialLibraryContext: LibraryContext? = null,
    createMasterPassword: String = "",
    onDismiss: () -> Unit,
    onSelected: (LibraryContext, String?) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isImportMode = mode == CloudMode.IMPORT
    val isCreateMode = mode == CloudMode.CREATE
    val isBindMode = mode == CloudMode.BIND
        val isCloudBound = initialLibraryContext?.sourceType == xzynine.WebDAVPass.Android.data.LibrarySourceType.CLOUD
            && !initialLibraryContext.remoteBaseUrl.isNullOrBlank()
            && !initialLibraryContext.remoteFilePath.isNullOrBlank()
            && !initialLibraryContext.username.isNullOrBlank()
            && !initialLibraryContext.password.isNullOrBlank()
        val isCloudBindingStable = initialLibraryContext?.lastSyncStatus == "success"
            || initialLibraryContext?.lastSyncStatus == "merged"
            || (initialLibraryContext?.lastSyncAt ?: 0L) > 0L
        val isBindReadOnly = isBindMode && isCloudBound && isCloudBindingStable

    val initialServerUrl = initialLibraryContext?.remoteBaseUrl
        ?.takeIf { it.isNotBlank() }
        ?: "https://dav.jianguoyun.com/dav/"
    val initialRemoteRelativePath = run {
        val remotePath = initialLibraryContext?.remoteFilePath.orEmpty()
        val remoteBase = initialLibraryContext?.remoteBaseUrl.orEmpty()
        val relative = if (remotePath.isNotBlank() && remoteBase.isNotBlank() && remotePath.startsWith(remoteBase)) {
            remotePath.removePrefix(remoteBase).trimStart('/')
        } else {
            remotePath
        }
        relative.trim().trim('/')
    }

    var serverUrl by remember(mode, initialLibraryContext) { mutableStateOf(initialServerUrl) }
    var username by remember(mode, initialLibraryContext) { mutableStateOf(initialLibraryContext?.username.orEmpty()) }
    var password by remember(mode, initialLibraryContext) { mutableStateOf(initialLibraryContext?.password.orEmpty()) }
    var folder by remember(mode) { mutableStateOf(if (mode == CloudMode.CREATE) "WebDavPass" else "") }
    var manualPath by remember(mode, initialLibraryContext) {
        mutableStateOf(
            when {
                isBindMode -> initialRemoteRelativePath.ifBlank { "WebDavPass.kdbx" }
                else -> "WebDavPass.kdbx"
            }
        )
    }
    var currentDirectory by remember(mode, initialLibraryContext) {
        mutableStateOf(
            if (isBindMode) {
                initialRemoteRelativePath.substringBeforeLast('/', "")
            } else {
                ""
            }
        )
    }
    var status by remember(mode, initialLibraryContext) {
        mutableStateOf(
            if (isBindMode && !initialLibraryContext?.remoteFilePath.isNullOrBlank()) {
                "已加载当前库云端信息"
            } else {
                ""
            }
        )
    }
    var directoryListing by remember { mutableStateOf<List<String>>(emptyList()) }
    var listing by remember { mutableStateOf<List<String>>(emptyList()) }
    var isConnectedForBrowse by remember(mode) { mutableStateOf(mode == CloudMode.CREATE) }
    var createPassword by remember(mode) { mutableStateOf(createMasterPassword) }
    var createPasswordConfirm by remember(mode) { mutableStateOf(createMasterPassword) }
    var accountPasswordVisible by remember(mode) { mutableStateOf(false) }
    var masterPasswordVisible by remember(mode) { mutableStateOf(false) }

    fun normalizeServerRootUrl(raw: String): String {
        return if (raw.endsWith('/')) raw else "$raw/"
    }

    suspend fun listKdbxFiles(baseUrl: String, user: String, pass: String): List<String> {
        return withContext(Dispatchers.IO) {
            val webDav = WebDav(baseUrl, Authorization(user, pass))
            webDav.listFiles()
                .filter { !it.isDir && it.displayName.endsWith(".kdbx", ignoreCase = true) }
                .map { it.displayName }
        }
    }

    fun normalizeRelativePath(path: String): String {
        return path.trim().trim('/').replace("//", "/")
    }

    fun encodeRelativePath(path: String): String {
        return normalizeRelativePath(path)
            .split('/')
            .filter { it.isNotBlank() }
            .joinToString("/") {
                URLEncoder.encode(it, Charsets.UTF_8.name()).replace("+", "%20")
            }
    }

    fun effectiveDirectory(): String {
        val current = normalizeRelativePath(currentDirectory)
        if (current.isNotBlank()) return current
        return normalizeRelativePath(folder)
    }

    fun buildDirectoryUrl(baseUrl: String, relativeDirectory: String): String {
        val rel = encodeRelativePath(relativeDirectory)
        return if (rel.isBlank()) baseUrl else "$baseUrl$rel/"
    }

    suspend fun listCurrentDirectory(baseUrl: String, user: String, pass: String, relativeDirectory: String): Pair<List<String>, List<String>> {
        return withContext(Dispatchers.IO) {
            val auth = Authorization(user, pass)
            val dirUrl = buildDirectoryUrl(baseUrl, relativeDirectory)
            Logger.d(SEARCH_LOG_TAG, "listCurrentDirectory start, baseUrl=$baseUrl, relativeDirectory=$relativeDirectory, dirUrl=$dirUrl")
            val entries = WebDav(dirUrl, auth).listFiles()
            Logger.d(SEARCH_LOG_TAG, "listCurrentDirectory entries=${entries.size}, dirUrl=$dirUrl")

            val dirPrefix = normalizeRelativePath(relativeDirectory)
            val dirs = entries
                .filter { it.isDir }
                .map {
                    val segment = it.urlName.trim('/').substringAfterLast('/').ifBlank {
                        it.displayName.trim('/').substringAfterLast('/')
                    }
                    normalizeRelativePath(
                        if (dirPrefix.isBlank()) segment else "$dirPrefix/$segment"
                    )
                }
                .distinct()
                .sorted()

            val files = entries
                .filter { !it.isDir && it.displayName.endsWith(".kdbx", ignoreCase = true) }
                .map {
                    val segment = it.urlName.trim('/').substringAfterLast('/').ifBlank {
                        it.displayName.trim('/').substringAfterLast('/')
                    }
                    normalizeRelativePath(
                        if (dirPrefix.isBlank()) segment else "$dirPrefix/$segment"
                    )
                }
                .distinct()
                .sorted()

            Logger.d(SEARCH_LOG_TAG, "listCurrentDirectory done, dirs=${dirs.size}, files=${files.size}, relativeDirectory=$relativeDirectory")

            dirs to files
        }
    }

    suspend fun importRemote(baseUrl: String, path: String, user: String, pass: String): LibraryContext? {
        return withContext(Dispatchers.IO) {
            val normalized = if (path.startsWith("http://") || path.startsWith("https://")) {
                path
            } else {
                val encoded = encodeRelativePath(path)
                "$baseUrl$encoded"
            }
            val remote = WebDav(normalized, Authorization(user, pass))
            if (!remote.exists()) {
                return@withContext null
            }
            val remoteModifiedAt = remote.getWebDavFile()?.lastModify?.takeIf { it > 0 }

            val localFileName = normalized.substringAfterLast('/').ifBlank { "WebDavPass.kdbx" }
            val localPath = tokenViewModelSaveRemoteToLocal(context, remote, localFileName)
            return@withContext localPath?.let {
                LibraryContext(
                    displayName = localFileName,
                    sourceType = xzynine.WebDAVPass.Android.data.LibrarySourceType.CLOUD,
                    localPath = it,
                    remoteBaseUrl = baseUrl,
                    remoteFilePath = normalized,
                    username = user,
                    password = pass,
                    autoSyncEnabled = true,
                    lastRemoteModifiedAt = remoteModifiedAt,
                    lastSyncStatus = "idle"
                )
            }
        }
    }

    suspend fun createRemote(baseUrl: String, path: String, user: String, pass: String, masterPassword: String): LibraryContext? {
        return withContext(Dispatchers.IO) {
            val normalized = if (path.startsWith("http://") || path.startsWith("https://")) {
                path
            } else {
                val encoded = encodeRelativePath(path)
                "$baseUrl$encoded"
            }

            val remote = WebDav(normalized, Authorization(user, pass))
            val kdbxBytes = tokenViewModel.createEmptyKdbxBytes(masterPassword)
            remote.upload(kdbxBytes, "application/octet-stream")
            val remoteModifiedAt = remote.getWebDavFile()?.lastModify?.takeIf { it > 0 }

            val localFileName = normalized.substringAfterLast('/').ifBlank { "WebDavPass.kdbx" }
            val localPath = tokenViewModelSaveRemoteToLocal(context, remote, localFileName)
            return@withContext localPath?.let {
                LibraryContext(
                    displayName = localFileName,
                    sourceType = xzynine.WebDAVPass.Android.data.LibrarySourceType.CLOUD,
                    localPath = it,
                    remoteBaseUrl = baseUrl,
                    remoteFilePath = normalized,
                    username = user,
                    password = pass,
                    autoSyncEnabled = true,
                    lastRemoteModifiedAt = remoteModifiedAt,
                    lastSyncStatus = "idle"
                )
            }
        }
    }

    WindowDialog(
        title = when {
            isImportMode -> "云端导入 .kdbx"
            isCreateMode -> "云端新建 .kdbx"
            else -> "当前库云端设置"
        },
        summary = if (isBindMode) {
            if (isBindReadOnly) "当前配置已验证成功，仅可浏览" else "为当前库绑定或更新云端 .kdbx"
        } else {
            "支持列表选择与手动路径"
        },
        show = remember { mutableStateOf(true) },
        onDismissRequest = onDismiss,
        defaultWindowInsetsPadding = true
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if ((isImportMode && !isConnectedForBrowse) || isBindMode) {
                TextField(
                    value = serverUrl,
                    onValueChange = { if (!isBindReadOnly) serverUrl = it },
                    label = "WebDAV地址",
                    readOnly = isBindReadOnly,
                    enabled = true
                )
                TextField(
                    value = username,
                    onValueChange = { if (!isBindReadOnly) username = it },
                    label = "用户名",
                    readOnly = isBindReadOnly,
                    enabled = true
                )
                TextField(
                    value = password,
                    onValueChange = { if (!isBindReadOnly) password = it },
                    label = "密码",
                    visualTransformation = if (accountPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    readOnly = isBindReadOnly,
                    enabled = true
                )
                Button(
                    onClick = {
                        accountPasswordVisible = !accountPasswordVisible
                    },
                    enabled = true
                ) {
                    Text(if (accountPasswordVisible) "隐藏密码" else "显示密码")
                }
                if (isBindMode) {
                    TextField(
                        value = manualPath,
                        onValueChange = { if (!isBindReadOnly) manualPath = it },
                        label = "远端文件路径（可手动输入）",
                        readOnly = isBindReadOnly,
                        enabled = true
                    )
                    if (isBindReadOnly) {
                        Text("当前库已完成云端连接并同步，配置已锁定为只读。")
                    }
                }

                Button(onClick = {
                    coroutineScope.launch {
                        try {
                            status = "正在连接并加载根目录..."
                            val baseUrl = normalizeServerRootUrl(serverUrl)
                            currentDirectory = ""
                            val (dirs, files) = listCurrentDirectory(baseUrl, username, password, "")
                            directoryListing = dirs
                            listing = files
                            isConnectedForBrowse = true
                            status = "已连接，当前为根目录"
                        } catch (e: Exception) {
                            val msg = e.message.orEmpty()
                            Logger.e(SEARCH_LOG_TAG, "UI connectAndBrowseRoot failed, message=$msg", e)
                            status = "连接失败：$msg"
                        }
                    }
                }) {
                    Text("连接并浏览")
                }
            }

            if (isCreateMode || isConnectedForBrowse || isBindMode) {
                if (isCreateMode) {
                    TextField(value = serverUrl, onValueChange = { serverUrl = it }, label = "WebDAV地址")
                    TextField(value = username, onValueChange = { username = it }, label = "用户名")
                    TextField(
                        value = password,
                        onValueChange = { password = it },
                        label = "密码",
                        visualTransformation = if (accountPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true
                    )
                    Button(onClick = { accountPasswordVisible = !accountPasswordVisible }) {
                        Text(if (accountPasswordVisible) "隐藏密码" else "显示密码")
                    }
                    TextField(value = folder, onValueChange = { folder = it }, label = "目录（默认 WebDavPass）")
                    TextField(
                        value = manualPath,
                        onValueChange = { manualPath = it },
                        label = "新建文件名（.kdbx）"
                    )
                }
            }

            if (isCreateMode) {
                TextField(
                    value = createPassword,
                    onValueChange = { createPassword = it },
                    label = "主密码",
                    visualTransformation = if (masterPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true
                )
                TextField(
                    value = createPasswordConfirm,
                    onValueChange = { createPasswordConfirm = it },
                    label = "确认主密码",
                    visualTransformation = if (masterPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true
                )
                Button(onClick = { masterPasswordVisible = !masterPasswordVisible }) {
                    Text(if (masterPasswordVisible) "隐藏主密码" else "显示主密码")
                }
            }

            if ((isImportMode || isBindMode) && isConnectedForBrowse) {
                Text(text = "当前目录: /${normalizeRelativePath(currentDirectory)}")

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        coroutineScope.launch {
                            try {
                                currentDirectory = ""
                                val baseUrl = normalizeServerRootUrl(serverUrl)
                                val (dirs, files) = listCurrentDirectory(baseUrl, username, password, "")
                                directoryListing = dirs
                                listing = files
                                status = "已切换到根目录"
                            } catch (e: Exception) {
                                val msg = e.message.orEmpty()
                                Logger.e(SEARCH_LOG_TAG, "UI goRoot failed, message=$msg", e)
                                status = "加载失败：$msg"
                            }
                        }
                    }, modifier = Modifier.weight(1f)) {
                        Text("回到根目录")
                    }

                    if (normalizeRelativePath(currentDirectory).isNotBlank()) {
                        Button(onClick = {
                            coroutineScope.launch {
                                try {
                                    val current = normalizeRelativePath(currentDirectory)
                                    val parent = current.substringBeforeLast('/', "")
                                    val baseUrl = normalizeServerRootUrl(serverUrl)
                                    val (dirs, files) = listCurrentDirectory(baseUrl, username, password, parent)
                                    currentDirectory = parent
                                    directoryListing = dirs
                                    listing = files
                                    status = "已返回上级目录"
                                } catch (e: Exception) {
                                    val msg = e.message.orEmpty()
                                    Logger.e(SEARCH_LOG_TAG, "UI goParent failed, message=$msg", e)
                                    status = "加载失败：$msg"
                                }
                            }
                        }, modifier = Modifier.weight(1f)) {
                            Text("返回上级目录")
                        }
                    }
                }

                Text("子目录")
                directoryListing.forEach { dirPath ->
                    val dirName = dirPath.substringAfterLast('/')
                    SuperArrow(
                        title = "📁 $dirName",
                        summary = dirPath,
                        onClick = {
                            coroutineScope.launch {
                                try {
                                    val baseUrl = normalizeServerRootUrl(serverUrl)
                                    val (dirs, files) = listCurrentDirectory(baseUrl, username, password, dirPath)
                                    currentDirectory = dirPath
                                    directoryListing = dirs
                                    listing = files
                                    status = "已进入目录：/$dirPath"
                                } catch (e: Exception) {
                                    val msg = e.message.orEmpty()
                                    Logger.e(SEARCH_LOG_TAG, "UI openDir failed, dirPath=$dirPath, message=$msg", e)
                                    status = "目录加载失败：$msg"
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Text("选择远端文件")
                listing.forEach { filePath ->
                    SuperArrow(
                        title = filePath.substringAfterLast('/'),
                        summary = filePath,
                        onClick = {
                            if (isBindMode) {
                                if (isBindReadOnly) {
                                    return@SuperArrow
                                }
                                manualPath = filePath
                                status = "已选择远端文件：$filePath"
                                return@SuperArrow
                            }

                            coroutineScope.launch {
                                val baseUrl = normalizeServerRootUrl(serverUrl)
                                val selected = try {
                                    importRemote(baseUrl, filePath, username, password)
                                } catch (e: Exception) {
                                    val msg = e.message.orEmpty()
                                    Logger.e(SEARCH_LOG_TAG, "UI importFromList failed, filePath=$filePath, message=$msg", e)
                                    null
                                }

                                if (selected == null) {
                                    ToastUtils.showShortToast(context, "导入失败：$filePath")
                                    return@launch
                                }

                                onSelected(selected, null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (status.isNotBlank()) {
                Text(status)
            }

            Button(onClick = {
                coroutineScope.launch {
                    if (isBindMode) {
                        if (isBindReadOnly) {
                            ToastUtils.showShortToast(context, "当前配置已锁定，不允许编辑")
                            return@launch
                        }
                        if (serverUrl.isBlank() || username.isBlank() || password.isBlank() || manualPath.isBlank()) {
                            ToastUtils.showShortToast(context, "请填写地址、用户名、密码和远端文件路径")
                            return@launch
                        }

                        val baseUrl = normalizeServerRootUrl(serverUrl)
                        val normalizedPath = if (manualPath.endsWith(".kdbx", ignoreCase = true)) {
                            manualPath
                        } else {
                            "$manualPath.kdbx"
                        }
                        val remoteFilePath = if (normalizedPath.startsWith("http://") || normalizedPath.startsWith("https://")) {
                            normalizedPath
                        } else {
                            val encoded = encodeRelativePath(normalizedPath)
                            "$baseUrl$encoded"
                        }

                        val current = initialLibraryContext
                        if (current == null) {
                            ToastUtils.showShortToast(context, "当前未选择库，无法保存")
                            return@launch
                        }

                        onSelected(
                            current.copy(
                                // 本地升级云同步时保持原本地定位，不迁移文件位置。
                                localPath = current.localPath,
                                sourceType = xzynine.WebDAVPass.Android.data.LibrarySourceType.CLOUD,
                                remoteBaseUrl = baseUrl,
                                remoteFilePath = remoteFilePath,
                                username = username,
                                password = password,
                                autoSyncEnabled = true,
                                lastSyncStatus = current.lastSyncStatus ?: "idle",
                                lastSyncError = null
                            ),
                            null
                        )
                        return@launch
                    }

                    if (isCreateMode) {
                        if (createPassword.isBlank()) {
                            ToastUtils.showShortToast(context, "请输入主密码")
                            return@launch
                        }
                        if (createPassword != createPasswordConfirm) {
                            ToastUtils.showShortToast(context, "两次主密码不一致")
                            return@launch
                        }
                    }

                    val baseUrl = normalizeServerRootUrl(serverUrl)
                    val path = if (isCreateMode && !manualPath.endsWith(".kdbx", ignoreCase = true)) {
                        "$manualPath.kdbx"
                    } else {
                        manualPath
                    }

                    val selected = try {
                        if (isImportMode) {
                            importRemote(baseUrl, path, username, password)
                        } else {
                            createRemote(baseUrl, path, username, password, createPassword)
                        }
                    } catch (e: Exception) {
                        null
                    }

                    if (selected == null) {
                        ToastUtils.showShortToast(context, "操作失败，请检查路径和账号信息")
                        return@launch
                    }

                    onSelected(selected, if (isCreateMode) createPassword else null)
                }
            }, enabled = isCreateMode || (isBindMode && !isBindReadOnly)) {
                Text(
                    when {
                        isBindMode && isBindReadOnly -> "配置已锁定"
                        isBindMode -> "保存云端绑定"
                        else -> "新建并进入"
                    }
                )
            }
        }
    }
}

/**
 * 下载远端文件到应用私有目录并返回绝对路径。
 *
 * 说明：
 * - 仅用于云端导入/云端新建的本地离线副本；
 * - 本地库升级云同步（BIND）不走该路径，保持原本地文件位置。
 */
private suspend fun tokenViewModelSaveRemoteToLocal(
    context: android.content.Context,
    remoteWebDav: WebDav,
    fileName: String
): String? {
    return runCatching {
        val dir = java.io.File(context.filesDir, "libraries")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val safeName = if (fileName.endsWith(".kdbx", ignoreCase = true)) fileName else "$fileName.kdbx"
        val localFile = java.io.File(dir, safeName)
        val content = remoteWebDav.download()
        localFile.writeBytes(content)
        localFile.absolutePath
    }.getOrNull()
}
