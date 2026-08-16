package xzynine.WebDAVPass.Android.ui.Dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import github.xzynine.webdav.Authorization
import github.xzynine.webdav.WebDav
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.window.WindowDialog
import xzylib.base.util.Logger
import xzylib.base.util.ToastUtils
import xzynine.WebDAVPass.Android.data.LibraryContext
import xzynine.WebDAVPass.Android.ui.component.Preference
import xzynine.WebDAVPass.Android.ui.component.PreferenceType
import xzynine.WebDAVPass.Android.ui.component.WebDavBrowseMode
import xzynine.WebDAVPass.Android.ui.component.WebDavFileBrowserDialog
import xzynine.WebDAVPass.Android.ui.viewmodel.TokenViewModel
import java.net.URLEncoder

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
    BIND,
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
    createKeyFileData: ByteArray? = null,
    createKeyFileUri: String? = null,
    onDismiss: () -> Unit,
    onSelected: (LibraryContext, String?) -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isImportMode = mode == CloudMode.IMPORT
    val isCreateMode = mode == CloudMode.CREATE
    val isBindMode = mode == CloudMode.BIND
    val isCloudBound =
        initialLibraryContext?.sourceType == xzynine.WebDAVPass.Android.data.LibrarySourceType.CLOUD &&
            !initialLibraryContext.remoteBaseUrl.isNullOrBlank() &&
            !initialLibraryContext.remoteFilePath.isNullOrBlank() &&
            !initialLibraryContext.username.isNullOrBlank() &&
            !initialLibraryContext.password.isNullOrBlank()
    val isCloudBindingStable =
        initialLibraryContext?.lastSyncStatus == "success" ||
            initialLibraryContext?.lastSyncStatus == "merged" ||
            (initialLibraryContext?.lastSyncAt ?: 0L) > 0L
    val isBindReadOnly = isBindMode && isCloudBound && isCloudBindingStable

    val initialServerUrl =
        initialLibraryContext
            ?.remoteBaseUrl
            ?.takeIf { it.isNotBlank() }
            ?: "https://dav.jianguoyun.com/dav/"
    val initialRemoteRelativePath =
        run {
            val remotePath = initialLibraryContext?.remoteFilePath.orEmpty()
            val remoteBase = initialLibraryContext?.remoteBaseUrl.orEmpty()
            val relative =
                if (remotePath.isNotBlank() && remoteBase.isNotBlank() && remotePath.startsWith(remoteBase)) {
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
            },
        )
    }
    var status by remember(mode, initialLibraryContext) {
        mutableStateOf(
            if (isBindMode && !initialLibraryContext?.remoteFilePath.isNullOrBlank()) {
                "已加载当前库云端信息"
            } else {
                ""
            },
        )
    }
    var createPassword by remember(mode) { mutableStateOf(createMasterPassword) }
    var createPasswordConfirm by remember(mode) { mutableStateOf(createMasterPassword) }
    var accountPasswordVisible by remember(mode) { mutableStateOf(false) }
    var masterPasswordVisible by remember(mode) { mutableStateOf(false) }
    var showBrowser by remember(mode) { mutableStateOf(false) }

    // 已保存的 WebDAV 账号列表（数据库解密后的数据源）
    val savedConfigs by tokenViewModel.webDavConfigViewModel.webDavConfigs.collectAsState(emptyList())
    val savedAccounts =
        remember(savedConfigs) {
            savedConfigs.distinctBy { it.url to it.username }
        }

    fun normalizeServerRootUrl(raw: String): String = if (raw.endsWith('/')) raw else "$raw/"

    fun normalizeRelativePath(path: String): String = path.trim().trim('/').replace("//", "/")

    /**
     * 提取服务器主机名用于账号命名
     */
    fun extractHost(raw: String): String = runCatching { java.net.URI(raw).host }.getOrNull() ?: raw

    /**
     * 自动保存云端账号到已保存账号表。
     *
     * 说明：导入/新建/绑定成功后调用，账号数据从远端完整路径推导目录后落库，
     * 下次打开弹窗可直接从下拉选择。
     */
    suspend fun autoSaveWebDavAccount(
        baseUrl: String,
        fullRemotePath: String,
        user: String,
        pass: String,
        name: String,
    ) {
        runCatching {
            // 仅当远端路径以根地址开头时推导目录；手填完整 URL 等异常场景目录留空（url 整体充当）
            val relative =
                if (fullRemotePath.startsWith(baseUrl)) {
                    fullRemotePath.removePrefix(baseUrl).trimStart('/')
                } else {
                    ""
                }
            val directory =
                relative
                    .substringBeforeLast('/', "")
                    .trim('/')
                    .takeIf { it.isNotBlank() }
            tokenViewModel.webDavConfigViewModel.autoSaveAccount(
                baseUrl = baseUrl,
                directory = directory,
                username = user,
                password = pass,
                name = name.ifBlank { extractHost(baseUrl) },
            )
        }
    }

    fun encodeRelativePath(path: String): String =
        normalizeRelativePath(path)
            .split('/')
            .filter { it.isNotBlank() }
            .joinToString("/") {
                URLEncoder.encode(it, Charsets.UTF_8.name()).replace("+", "%20")
            }

    suspend fun importRemote(
        baseUrl: String,
        path: String,
        user: String,
        pass: String,
    ): LibraryContext? {
        return withContext(Dispatchers.IO) {
            val normalized =
                if (path.startsWith("http://") || path.startsWith("https://")) {
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
            // 导入成功后将账号保存到已保存账号表，供下拉选择复用
            if (localPath != null) {
                autoSaveWebDavAccount(baseUrl, normalized, user, pass, localFileName)
            }
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
                    lastSyncStatus = "idle",
                )
            }
        }
    }

    suspend fun createRemote(
        baseUrl: String,
        path: String,
        user: String,
        pass: String,
        masterPassword: String,
        keyFileData: ByteArray? = null,
        keyFileUri: String? = null,
    ): LibraryContext? {
        return withContext(Dispatchers.IO) {
            val normalized =
                if (path.startsWith("http://") || path.startsWith("https://")) {
                    path
                } else {
                    val encoded = encodeRelativePath(path)
                    "$baseUrl$encoded"
                }

            val remote = WebDav(normalized, Authorization(user, pass))
            val kdbxBytes = tokenViewModel.createEmptyKdbxBytes(masterPassword, keyFileData)
            remote.upload(kdbxBytes, "application/octet-stream")
            val remoteModifiedAt = remote.getWebDavFile()?.lastModify?.takeIf { it > 0 }

            val localFileName = normalized.substringAfterLast('/').ifBlank { "WebDavPass.kdbx" }
            val localPath = tokenViewModelSaveRemoteToLocal(context, remote, localFileName)
            // 新建成功后将账号保存到已保存账号表，供下拉选择复用
            if (localPath != null) {
                autoSaveWebDavAccount(baseUrl, normalized, user, pass, localFileName)
            }
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
                    lastSyncStatus = "idle",
                    keyFileUri = keyFileUri,
                )
            }
        }
    }

    // 派生当前表单对应的已保存账号索引（无额外状态，手动编辑字段后自动回到未选中态）
    val selectedAccountIndex =
        savedAccounts.indexOfFirst {
            normalizeServerRootUrl(it.url) == normalizeServerRootUrl(serverUrl) &&
                it.username == username &&
                it.password == password
        }

    // 打开浏览器时隐藏配置弹窗，避免窗口层叠；关闭浏览器后恢复
    if (!showBrowser) {
        WindowDialog(
            title =
                when {
                    isImportMode -> "云端导入 .kdbx"
                    isCreateMode -> "云端新建 .kdbx"
                    else -> "当前库云端设置"
                },
            summary =
                if (isBindMode) {
                    if (isBindReadOnly) "当前配置已验证成功，仅可浏览" else "为当前库绑定或更新云端 .kdbx"
                } else {
                    "支持列表选择与手动路径"
                },
            show = true,
            onDismissRequest = onDismiss,
            defaultWindowInsetsPadding = true,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (savedAccounts.isNotEmpty()) {
                    Preference(
                        type = PreferenceType.Spinner,
                        title = "已保存的 WebDAV 账号",
                        summary =
                            if (selectedAccountIndex >= 0) {
                                "当前：${savedAccounts[selectedAccountIndex].name}"
                            } else {
                                "选择已保存账号自动填入表单"
                            },
                        items = savedAccounts.map { DropdownItem(text = it.name) },
                        selectedIndex = selectedAccountIndex.coerceAtLeast(0),
                        showValue = selectedAccountIndex >= 0,
                        enabled = !isBindReadOnly,
                        onSelectedIndexChange = { index ->
                            val account = savedAccounts[index]
                            serverUrl = normalizeServerRootUrl(account.url)
                            username = account.username
                            password = account.password
                            val dir = account.directory?.trim()?.trim('/')
                            manualPath = if (dir.isNullOrBlank()) "WebDavPass.kdbx" else "$dir/WebDavPass.kdbx"
                            if (isBindMode) {
                                status = "已选择账号：${account.name}"
                            }
                        },
                    )
                }
                TextField(
                    value = serverUrl,
                    onValueChange = { if (!isBindReadOnly) serverUrl = it },
                    label = "WebDAV地址",
                    readOnly = isBindReadOnly,
                    enabled = true,
                )
                TextField(
                    value = username,
                    onValueChange = { if (!isBindReadOnly) username = it },
                    label = "用户名",
                    readOnly = isBindReadOnly,
                    enabled = true,
                )
                TextField(
                    value = password,
                    onValueChange = { if (!isBindReadOnly) password = it },
                    label = "密码",
                    visualTransformation = if (accountPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    readOnly = isBindReadOnly,
                    enabled = true,
                )
                Button(
                    onClick = {
                        accountPasswordVisible = !accountPasswordVisible
                    },
                    enabled = true,
                ) {
                    Text(if (accountPasswordVisible) "隐藏密码" else "显示密码")
                }
                if (isBindMode) {
                    TextField(
                        value = manualPath,
                        onValueChange = { if (!isBindReadOnly) manualPath = it },
                        label = "远端文件路径（可手动输入）",
                        readOnly = isBindReadOnly,
                        enabled = true,
                    )
                    if (isBindReadOnly) {
                        Text("当前库已完成云端连接并同步，配置已锁定为只读。")
                    }
                }
                if (isCreateMode) {
                    TextField(value = folder, onValueChange = { folder = it }, label = "目录（默认 WebDavPass）")
                    TextField(
                        value = manualPath,
                        onValueChange = { manualPath = it },
                        label = "新建文件名（.kdbx）",
                    )
                }

                if (isImportMode || isBindMode) {
                    Button(
                        onClick = {
                            if (isBindReadOnly) {
                                ToastUtils.showShortToast(context, "当前配置已锁定，不允许编辑")
                            } else {
                                showBrowser = true
                            }
                        },
                        enabled = true,
                    ) {
                        Text("连接并浏览")
                    }
                }

                if (isCreateMode) {
                    TextField(
                        value = createPassword,
                        onValueChange = { createPassword = it },
                        label = "主密码",
                        visualTransformation = if (masterPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                    )
                    TextField(
                        value = createPasswordConfirm,
                        onValueChange = { createPasswordConfirm = it },
                        label = "确认主密码",
                        visualTransformation = if (masterPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                    )
                    Button(onClick = { masterPasswordVisible = !masterPasswordVisible }) {
                        Text(if (masterPasswordVisible) "隐藏主密码" else "显示主密码")
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
                            val normalizedPath =
                                if (manualPath.endsWith(".kdbx", ignoreCase = true)) {
                                    manualPath
                                } else if (manualPath.isNotBlank()) {
                                    "$manualPath.kdbx"
                                } else {
                                    manualPath
                                }
                            val remoteFilePath =
                                if (normalizedPath.startsWith("http://") || normalizedPath.startsWith("https://")) {
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

                            // 绑定成功后将账号保存到已保存账号表，供下拉选择复用
                            autoSaveWebDavAccount(baseUrl, remoteFilePath, username, password, current.displayName)

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
                                    lastSyncError = null,
                                ),
                                null,
                            )
                            return@launch
                        }

                        if (isCreateMode) {
                            if (manualPath.isBlank()) {
                                ToastUtils.showShortToast(context, "请输入远端文件路径")
                                return@launch
                            }
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
                        val path =
                            if (isCreateMode && manualPath.isNotBlank() && !manualPath.endsWith(".kdbx", ignoreCase = true)) {
                                "$manualPath.kdbx"
                            } else {
                                manualPath
                            }

                        val selected =
                            try {
                                if (isImportMode) {
                                    importRemote(baseUrl, path, username, password)
                                } else {
                                    createRemote(baseUrl, path, username, password, createPassword, createKeyFileData, createKeyFileUri)
                                }
                            } catch (e: Exception) {
                                Logger.e(SEARCH_LOG_TAG, "云端库操作失败（import/create/bind），path=$path", e)
                                null
                            }

                        if (selected == null) {
                            ToastUtils.showShortToast(context, "操作失败，请检查路径和账号信息")
                            return@launch
                        }

                        onSelected(selected, if (isCreateMode) createPassword else null)
                    }
                }, enabled = isCreateMode || isImportMode || (isBindMode && !isBindReadOnly)) {
                    Text(
                        when {
                            isBindMode && isBindReadOnly -> "配置已锁定"
                            isBindMode -> "保存云端绑定"
                            isImportMode -> "导入并进入"
                            else -> "新建并进入"
                        },
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }

    // 子模块提供的 WebDAV 文件浏览弹窗
    if (showBrowser) {
        WebDavFileBrowserDialog(
            initialServerUrl = serverUrl,
            initialUsername = username,
            initialPassword = password,
            initialDirectory = if (isBindMode) initialRemoteRelativePath.substringBeforeLast('/', "") else "",
            mode = WebDavBrowseMode.PICK_FILE,
            fileExtensionFilter = ".kdbx",
            title = if (isImportMode) "选择云端 .kdbx 文件" else "选择远端文件",
            onDismiss = { showBrowser = false },
            onSelected = { baseUrl, relativePath ->
                showBrowser = false
                if (isBindMode) {
                    manualPath = relativePath
                    status = "已选择远端文件：$relativePath"
                } else {
                    coroutineScope.launch {
                        val selected =
                            try {
                                importRemote(baseUrl, relativePath, username, password)
                            } catch (e: Exception) {
                                val msg = e.message.orEmpty()
                                Logger.e(SEARCH_LOG_TAG, "UI importFromBrowser failed, path=$relativePath, message=$msg", e)
                                null
                            }

                        if (selected == null) {
                            ToastUtils.showShortToast(context, "导入失败：$relativePath")
                            return@launch
                        }

                        onSelected(selected, null)
                    }
                }
            },
        )
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
    fileName: String,
): String? =
    runCatching {
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
