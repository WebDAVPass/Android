package xzynine.WebDAVPass.Android.ui.Screen

import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.WindowDialog
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.CloudFill
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.UploadCloud
import xzynine.WebDAVPass.Android.data.LibraryContext
import xzynine.WebDAVPass.Android.data.LibrarySourceType
import xzynine.WebDAVPass.Android.theme.getAppRoundedCorner
import xzynine.WebDAVPass.Android.ui.Dialog.PasswordDialog
import xzynine.WebDAVPass.Android.ui.ViewModel.TokenViewModel
import xzynine.WebDAVPass.webdav.Authorization
import xzynine.WebDAVPass.webdav.WebDav

/**
 * 欢迎页操作模式
 */
private enum class CloudMode {
    /**
     * 云端导入
     */
    IMPORT,

    /**
     * 云端新建
     */
    CREATE
}

private enum class CreateMode {
    LOCAL,
    CLOUD
}

private const val SEARCH_LOG_TAG = "tag:搜索"

/**
 * 欢迎界面
 */
@Composable
fun WelcomeScreen(
    tokenViewModel: TokenViewModel,
    onEnterLibrary: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val cornerRadius = getAppRoundedCorner()
    val history by tokenViewModel.libraryHistory.collectAsState()
    var showCloudImportDialog by remember { mutableStateOf(false) }
    var showCloudCreateDialog by remember { mutableStateOf(false) }
    var showCreateMasterPasswordDialog by remember { mutableStateOf(false) }
    var createMode by remember { mutableStateOf(CreateMode.LOCAL) }
    var pendingCreateMasterPassword by remember { mutableStateOf("") }
    var pendingUnlockLibrary by remember { mutableStateOf<LibraryContext?>(null) }
    val showUnlockDialog = remember { mutableStateOf(false) }

    fun requestUnlockAndEnter(libraryContext: LibraryContext) {
        pendingUnlockLibrary = libraryContext
        showUnlockDialog.value = true
    }

    val localImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            if (uri == null) {
                return@rememberLauncherForActivityResult
            }
            coroutineScope.launch {
                val path = tokenViewModel.persistKdbxFromUri(uri)
                if (path == null) {
                    Toast.makeText(context, "导入失败：无法读取文件", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val item = LibraryContext(
                    displayName = path.substringAfterLast('/'),
                    sourceType = LibrarySourceType.LOCAL,
                    localPath = path
                )
                tokenViewModel.openLibraryContext(item)
                requestUnlockAndEnter(item)
            }
        }
    )

    val localCreateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
        onResult = { uri: Uri? ->
            if (uri == null) {
                return@rememberLauncherForActivityResult
            }
            coroutineScope.launch {
                val path = tokenViewModel.createLocalKdbx(uri, pendingCreateMasterPassword)
                if (path == null) {
                    Toast.makeText(context, "新建失败：无法创建文件", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val item = LibraryContext(
                    displayName = path.substringAfterLast('/'),
                    sourceType = LibrarySourceType.LOCAL,
                    localPath = path
                )
                tokenViewModel.openLibraryContext(item)
                val unlockOk = tokenViewModel.unlockCurrentLibrary(pendingCreateMasterPassword)
                pendingCreateMasterPassword = ""
                if (unlockOk) {
                    onEnterLibrary()
                } else {
                    requestUnlockAndEnter(item)
                }
            }
        }
    )

    Scaffold(
        popupHost = {},
        topBar = {
            TopAppBar(
                title = "欢迎使用 WebDAVPass",
                navigationIcon = {},
                actions = {},
                defaultWindowInsetsPadding = true
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "请选择数据库来源", fontSize = 18.sp)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        localImportLauncher.launch(arrayOf("*/*"))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = MiuixIcons.Download, contentDescription = "本地导入")
                    Text(text = "本地导入")
                }

                Button(
                    onClick = {
                        showCloudImportDialog = true
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = MiuixIcons.CloudFill, contentDescription = "云端导入")
                    Text(text = "云端导入")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        createMode = CreateMode.LOCAL
                        showCreateMasterPasswordDialog = true
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = MiuixIcons.UploadCloud, contentDescription = "本地新建")
                    Text(text = "本地新建")
                }

                Button(
                    onClick = {
                        createMode = CreateMode.CLOUD
                        showCreateMasterPasswordDialog = true
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = MiuixIcons.CloudFill, contentDescription = "云端新建")
                    Text(text = "云端新建")
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "历史库")

            if (history.isEmpty()) {
                Text(text = "暂无历史记录")
            } else {
                history.forEach { item ->
                    SuperArrow(
                        title = item.displayName,
                        summary = if (item.sourceType == LibrarySourceType.CLOUD) {
                            item.remoteFilePath ?: item.remoteBaseUrl.orEmpty()
                        } else {
                            item.localPath
                        },
                        startAction = {
                            Icon(
                                modifier = Modifier.padding(end = 16.dp),
                                imageVector = if (item.sourceType == LibrarySourceType.CLOUD) MiuixIcons.CloudFill else MiuixIcons.Download,
                                contentDescription = "历史库"
                            )
                        },
                        onClick = {
                            coroutineScope.launch {
                                tokenViewModel.switchLibrary(item.id)
                                requestUnlockAndEnter(item)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                    )
                }
            }
        }
    }

    if (showCloudImportDialog) {
        CloudLibraryDialog(
            tokenViewModel = tokenViewModel,
            mode = CloudMode.IMPORT,
            onDismiss = { showCloudImportDialog = false },
            onSelected = { library, _ ->
                coroutineScope.launch {
                    tokenViewModel.openLibraryContext(library)
                    showCloudImportDialog = false
                    requestUnlockAndEnter(library)
                }
            }
        )
    }

    if (showCloudCreateDialog) {
        CloudLibraryDialog(
            tokenViewModel = tokenViewModel,
            mode = CloudMode.CREATE,
            createMasterPassword = pendingCreateMasterPassword,
            onDismiss = { showCloudCreateDialog = false },
            onSelected = { library, createdMasterPassword ->
                coroutineScope.launch {
                    tokenViewModel.openLibraryContext(library)
                    showCloudCreateDialog = false
                    val password = createdMasterPassword.orEmpty()
                    val unlockOk = tokenViewModel.unlockCurrentLibrary(password)
                    pendingCreateMasterPassword = ""
                    if (unlockOk) {
                        onEnterLibrary()
                    } else {
                        requestUnlockAndEnter(library)
                    }
                }
            }
        )
    }

    if (showCreateMasterPasswordDialog) {
        CreateMasterPasswordDialog(
            mode = createMode,
            onDismiss = {
                showCreateMasterPasswordDialog = false
                pendingCreateMasterPassword = ""
            },
            onConfirm = { password ->
                pendingCreateMasterPassword = password
                showCreateMasterPasswordDialog = false
                if (createMode == CreateMode.LOCAL) {
                    localCreateLauncher.launch("WebDavPass.kdbx")
                } else {
                    showCloudCreateDialog = true
                }
            }
        )
    }

    if (showUnlockDialog.value) {
        PasswordDialog(
            title = "解锁数据库",
            summary = pendingUnlockLibrary?.displayName,
            show = showUnlockDialog,
            onDismiss = {
                pendingUnlockLibrary = null
                showUnlockDialog.value = false
            },
            onConfirm = { password ->
                coroutineScope.launch {
                    val ok = tokenViewModel.unlockCurrentLibrary(password)
                    if (ok) {
                        showUnlockDialog.value = false
                        pendingUnlockLibrary = null
                        onEnterLibrary()
                    } else {
                        val message = tokenViewModel.getLastUnlockErrorMessage()
                            ?: "解锁失败：主密码不正确或文件无效"
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                }
            },
            confirmButtonText = "解锁"
        )
    }
}

/**
 * 云端库选择弹窗
 */
@Composable
private fun CloudLibraryDialog(
    tokenViewModel: TokenViewModel,
    mode: CloudMode,
    createMasterPassword: String = "",
    onDismiss: () -> Unit,
    onSelected: (LibraryContext, String?) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var serverUrl by remember { mutableStateOf("https://dav.jianguoyun.com/dav/") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var folder by remember(mode) { mutableStateOf(if (mode == CloudMode.CREATE) "WebDavPass" else "") }
    var manualPath by remember { mutableStateOf("WebDavPass.kdbx") }
    var currentDirectory by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var directoryListing by remember { mutableStateOf<List<String>>(emptyList()) }
    var listing by remember { mutableStateOf<List<String>>(emptyList()) }
    var isConnectedForBrowse by remember(mode) { mutableStateOf(mode == CloudMode.CREATE) }
    var createPassword by remember(mode) { mutableStateOf(createMasterPassword) }
    var createPasswordConfirm by remember(mode) { mutableStateOf(createMasterPassword) }

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
            Log.d(SEARCH_LOG_TAG, "listCurrentDirectory start, baseUrl=$baseUrl, relativeDirectory=$relativeDirectory, dirUrl=$dirUrl")
            val entries = WebDav(dirUrl, auth).listFiles()
            Log.d(SEARCH_LOG_TAG, "listCurrentDirectory entries=${entries.size}, dirUrl=$dirUrl")

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

            Log.d(SEARCH_LOG_TAG, "listCurrentDirectory done, dirs=${dirs.size}, files=${files.size}, relativeDirectory=$relativeDirectory")

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

            val localFileName = normalized.substringAfterLast('/').ifBlank { "WebDavPass.kdbx" }
            val localPath = tokenViewModelSaveRemoteToLocal(context, remote, localFileName)
            return@withContext localPath?.let {
                LibraryContext(
                    displayName = localFileName,
                    sourceType = LibrarySourceType.CLOUD,
                    localPath = it,
                    remoteBaseUrl = baseUrl,
                    remoteFilePath = normalized,
                    username = user,
                    password = pass
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

            val localFileName = normalized.substringAfterLast('/').ifBlank { "WebDavPass.kdbx" }
            val localPath = tokenViewModelSaveRemoteToLocal(context, remote, localFileName)
            return@withContext localPath?.let {
                LibraryContext(
                    displayName = localFileName,
                    sourceType = LibrarySourceType.CLOUD,
                    localPath = it,
                    remoteBaseUrl = baseUrl,
                    remoteFilePath = normalized,
                    username = user,
                    password = pass
                )
            }
        }
    }

    WindowDialog(
        title = if (mode == CloudMode.IMPORT) "云端导入 .kdbx" else "云端新建 .kdbx",
        summary = "支持列表选择与手动路径",
        show = remember { mutableStateOf(true) },
        onDismissRequest = onDismiss,
        defaultWindowInsetsPadding = true
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (mode == CloudMode.IMPORT && !isConnectedForBrowse) {
                TextField(value = serverUrl, onValueChange = { serverUrl = it }, label = "WebDAV地址")
                TextField(value = username, onValueChange = { username = it }, label = "用户名")
                TextField(value = password, onValueChange = { password = it }, label = "密码")

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
                            Log.e(SEARCH_LOG_TAG, "UI connectAndBrowseRoot failed, message=$msg", e)
                            status = "连接失败：$msg"
                        }
                    }
                }) {
                    Text("连接并浏览")
                }
            }

            if (mode == CloudMode.CREATE || isConnectedForBrowse) {
                if (mode == CloudMode.CREATE) {
                    TextField(value = serverUrl, onValueChange = { serverUrl = it }, label = "WebDAV地址")
                    TextField(value = username, onValueChange = { username = it }, label = "用户名")
                    TextField(value = password, onValueChange = { password = it }, label = "密码")
                    TextField(value = folder, onValueChange = { folder = it }, label = "目录（默认 WebDavPass）")
                    TextField(
                        value = manualPath,
                        onValueChange = { manualPath = it },
                        label = "新建文件名（.kdbx）"
                    )
                }
            }

            if (mode == CloudMode.CREATE) {
                TextField(
                    value = createPassword,
                    onValueChange = { createPassword = it },
                    label = "主密码",
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true
                )
                TextField(
                    value = createPasswordConfirm,
                    onValueChange = { createPasswordConfirm = it },
                    label = "确认主密码",
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true
                )
            }

            if (mode == CloudMode.IMPORT && isConnectedForBrowse) {
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
                                Log.e(SEARCH_LOG_TAG, "UI goRoot failed, message=$msg", e)
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
                                    Log.e(SEARCH_LOG_TAG, "UI goParent failed, message=$msg", e)
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
                                    Log.e(SEARCH_LOG_TAG, "UI openDir failed, dirPath=$dirPath, message=$msg", e)
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
                            coroutineScope.launch {
                                val baseUrl = normalizeServerRootUrl(serverUrl)
                                val selected = try {
                                    importRemote(baseUrl, filePath, username, password)
                                } catch (e: Exception) {
                                    val msg = e.message.orEmpty()
                                    Log.e(SEARCH_LOG_TAG, "UI importFromList failed, filePath=$filePath, message=$msg", e)
                                    null
                                }

                                if (selected == null) {
                                    Toast.makeText(context, "导入失败：$filePath", Toast.LENGTH_SHORT).show()
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
                    if (mode == CloudMode.CREATE) {
                        if (createPassword.isBlank()) {
                            Toast.makeText(context, "请输入主密码", Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                        if (createPassword != createPasswordConfirm) {
                            Toast.makeText(context, "两次主密码不一致", Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                    }

                    val baseUrl = normalizeServerRootUrl(serverUrl)
                    val path = if (mode == CloudMode.CREATE && !manualPath.endsWith(".kdbx", ignoreCase = true)) {
                        "$manualPath.kdbx"
                    } else {
                        manualPath
                    }

                    val selected = try {
                        if (mode == CloudMode.IMPORT) {
                            importRemote(baseUrl, path, username, password)
                        } else {
                            createRemote(baseUrl, path, username, password, createPassword)
                        }
                    } catch (e: Exception) {
                        null
                    }

                    if (selected == null) {
                        Toast.makeText(context, "操作失败，请检查路径和账号信息", Toast.LENGTH_SHORT).show()
                        return@launch
                    }

                    onSelected(selected, if (mode == CloudMode.CREATE) createPassword else null)
                }
            }, enabled = mode == CloudMode.CREATE) {
                Text("新建并进入")
            }
        }
    }
}

@Composable
private fun CreateMasterPasswordDialog(
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

/**
 * 下载远端文件到本地并返回绝对路径
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
