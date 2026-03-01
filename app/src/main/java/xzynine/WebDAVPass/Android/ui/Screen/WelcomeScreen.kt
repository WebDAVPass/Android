package xzynine.WebDAVPass.Android.ui.Screen

import android.net.Uri
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
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
                val path = tokenViewModel.createLocalKdbx(uri)
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
                requestUnlockAndEnter(item)
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
                        localCreateLauncher.launch("WebDavPass.kdbx")
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = MiuixIcons.UploadCloud, contentDescription = "本地新建")
                    Text(text = "本地新建")
                }

                Button(
                    onClick = {
                        showCloudCreateDialog = true
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
                    BasicComponent(
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
                            .border(1.dp, androidx.compose.ui.graphics.Color.LightGray, RoundedCornerShape(cornerRadius))
                    )
                }
            }
        }
    }

    if (showCloudImportDialog) {
        CloudLibraryDialog(
            mode = CloudMode.IMPORT,
            onDismiss = { showCloudImportDialog = false },
            onSelected = {
                coroutineScope.launch {
                    tokenViewModel.openLibraryContext(it)
                    showCloudImportDialog = false
                    requestUnlockAndEnter(it)
                }
            }
        )
    }

    if (showCloudCreateDialog) {
        CloudLibraryDialog(
            mode = CloudMode.CREATE,
            onDismiss = { showCloudCreateDialog = false },
            onSelected = {
                coroutineScope.launch {
                    tokenViewModel.openLibraryContext(it)
                    showCloudCreateDialog = false
                    requestUnlockAndEnter(it)
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
                        Toast.makeText(context, "解锁失败：主密码不正确或文件无效", Toast.LENGTH_SHORT).show()
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
    mode: CloudMode,
    onDismiss: () -> Unit,
    onSelected: (LibraryContext) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var serverUrl by remember { mutableStateOf("https://dav.jianguoyun.com/dav/") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var folder by remember { mutableStateOf("WebDavPass") }
    var manualPath by remember { mutableStateOf("WebDavPass.kdbx") }
    var currentDirectory by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var directoryListing by remember { mutableStateOf<List<String>>(emptyList()) }
    var listing by remember { mutableStateOf<List<String>>(emptyList()) }

    fun normalizeBaseUrl(raw: String, folderName: String): String {
        val base = if (raw.endsWith('/')) raw else "$raw/"
        val normalizedFolder = folderName.trim('/').ifBlank { "WebDavPass" }
        return "$base$normalizedFolder/"
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

    fun buildDirectoryUrl(baseUrl: String, relativeDirectory: String): String {
        val rel = normalizeRelativePath(relativeDirectory)
        return if (rel.isBlank()) baseUrl else "$baseUrl$rel/"
    }

    suspend fun listCurrentDirectory(baseUrl: String, user: String, pass: String, relativeDirectory: String): Pair<List<String>, List<String>> {
        return withContext(Dispatchers.IO) {
            val auth = Authorization(user, pass)
            val dirUrl = buildDirectoryUrl(baseUrl, relativeDirectory)
            val entries = WebDav(dirUrl, auth).listFiles()

            val dirPrefix = normalizeRelativePath(relativeDirectory)
            val dirs = entries
                .filter { it.isDir }
                .map {
                    normalizeRelativePath(
                        if (dirPrefix.isBlank()) it.displayName else "$dirPrefix/${it.displayName}"
                    )
                }
                .distinct()
                .sorted()

            val files = entries
                .filter { !it.isDir && it.displayName.endsWith(".kdbx", ignoreCase = true) }
                .map {
                    normalizeRelativePath(
                        if (dirPrefix.isBlank()) it.displayName else "$dirPrefix/${it.displayName}"
                    )
                }
                .distinct()
                .sorted()

            dirs to files
        }
    }

    suspend fun listKdbxFilesRecursive(baseUrl: String, user: String, pass: String, startRelativeDirectory: String, maxDepth: Int = 8): List<String> {
        return withContext(Dispatchers.IO) {
            val auth = Authorization(user, pass)
            val result = mutableListOf<String>()
            val visited = mutableSetOf<String>()

            suspend fun walk(relativeDirectory: String, depth: Int) {
                if (depth > maxDepth) return
                val normalized = normalizeRelativePath(relativeDirectory)
                val dirUrl = buildDirectoryUrl(baseUrl, normalized)
                if (!visited.add(dirUrl)) return

                val entries = WebDav(dirUrl, auth).listFiles()
                val prefix = normalizeRelativePath(normalized)

                entries.filter { !it.isDir && it.displayName.endsWith(".kdbx", ignoreCase = true) }
                    .forEach {
                        val relPath = normalizeRelativePath(
                            if (prefix.isBlank()) it.displayName else "$prefix/${it.displayName}"
                        )
                        result.add(relPath)
                    }

                entries.filter { it.isDir }.forEach {
                    val next = normalizeRelativePath(
                        if (prefix.isBlank()) it.displayName else "$prefix/${it.displayName}"
                    )
                    walk(next, depth + 1)
                }
            }

            walk(startRelativeDirectory, 0)
            result.distinct().sorted()
        }
    }

    suspend fun importRemote(baseUrl: String, path: String, user: String, pass: String): LibraryContext? {
        return withContext(Dispatchers.IO) {
            val normalized = if (path.startsWith("http://") || path.startsWith("https://")) {
                path
            } else {
                "$baseUrl${path.trimStart('/')}"
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

    suspend fun createRemote(baseUrl: String, path: String, user: String, pass: String): LibraryContext? {
        return withContext(Dispatchers.IO) {
            val normalized = if (path.startsWith("http://") || path.startsWith("https://")) {
                path
            } else {
                "$baseUrl${path.trimStart('/')}"
            }

            val remote = WebDav(normalized, Authorization(user, pass))
            remote.upload(ByteArray(0), "application/octet-stream")

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
            TextField(value = serverUrl, onValueChange = { serverUrl = it }, label = "WebDAV地址")
            TextField(value = username, onValueChange = { username = it }, label = "用户名")
            TextField(value = password, onValueChange = { password = it }, label = "密码")
            TextField(value = folder, onValueChange = { folder = it }, label = "目录（默认 WebDavPass）")
            TextField(
                value = manualPath,
                onValueChange = { manualPath = it },
                label = if (mode == CloudMode.IMPORT) "手动路径或文件名" else "新建文件名（.kdbx）"
            )

            Button(onClick = {
                coroutineScope.launch {
                    try {
                        status = "正在加载当前目录..."
                        val baseUrl = normalizeBaseUrl(serverUrl, folder)
                        val (dirs, files) = listCurrentDirectory(baseUrl, username, password, currentDirectory)
                        directoryListing = dirs
                        listing = files
                        status = "当前目录加载完成"
                    } catch (e: Exception) {
                        status = "加载失败：${e.message}"
                    }
                }
            }) {
                Text("查看当前目录")
            }

            Button(onClick = {
                coroutineScope.launch {
                    try {
                        status = "正在递归搜索 .kdbx..."
                        val baseUrl = normalizeBaseUrl(serverUrl, folder)
                        directoryListing = emptyList()
                        listing = listKdbxFilesRecursive(baseUrl, username, password, currentDirectory)
                        status = "递归搜索完成，共 ${listing.size} 个文件"
                    } catch (e: Exception) {
                        status = "搜索失败：${e.message}"
                    }
                }
            }) {
                Text("递归搜索 .kdbx")
            }

            Text(text = "当前目录: /${normalizeRelativePath(currentDirectory)}")

            if (normalizeRelativePath(currentDirectory).isNotBlank()) {
                Button(onClick = {
                    val current = normalizeRelativePath(currentDirectory)
                    currentDirectory = current.substringBeforeLast('/', "")
                }) {
                    Text("返回上级目录")
                }
            }

            if (directoryListing.isNotEmpty() && mode == CloudMode.IMPORT) {
                Text("子目录")
                directoryListing.forEach { dirPath ->
                    val dirName = dirPath.substringAfterLast('/')
                    BasicComponent(
                        title = "📁 $dirName",
                        summary = dirPath,
                        onClick = {
                            currentDirectory = dirPath
                            status = "已切换目录，点击“查看当前目录”刷新"
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (listing.isNotEmpty() && mode == CloudMode.IMPORT) {
                Text("选择远端文件")
                listing.forEach { filePath ->
                    BasicComponent(
                        title = filePath.substringAfterLast('/'),
                        summary = filePath,
                        onClick = {
                            manualPath = filePath
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
                    val baseUrl = normalizeBaseUrl(serverUrl, folder)
                    val path = if (mode == CloudMode.CREATE && !manualPath.endsWith(".kdbx", ignoreCase = true)) {
                        "$manualPath.kdbx"
                    } else {
                        manualPath
                    }

                    val selected = try {
                        if (mode == CloudMode.IMPORT) {
                            importRemote(baseUrl, path, username, password)
                        } else {
                            createRemote(baseUrl, path, username, password)
                        }
                    } catch (e: Exception) {
                        null
                    }

                    if (selected == null) {
                        Toast.makeText(context, "操作失败，请检查路径和账号信息", Toast.LENGTH_SHORT).show()
                        return@launch
                    }

                    onSelected(selected)
                }
            }) {
                Text(if (mode == CloudMode.IMPORT) "导入并进入" else "新建并进入")
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
