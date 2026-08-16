package xzynine.WebDAVPass.Android.ui.Screen

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.AddFolder
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.CloudFill
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.UploadCloud
import top.yukonga.miuix.kmp.theme.MiuixTheme
import xzylib.base.util.ToastUtils
import xzynine.WebDAVPass.Android.data.LibraryContext
import xzynine.WebDAVPass.Android.data.LibrarySourceType
import xzynine.WebDAVPass.Android.ui.Dialog.CloudLibraryDialog
import xzynine.WebDAVPass.Android.ui.Dialog.CloudMode
import xzynine.WebDAVPass.Android.ui.Dialog.ConfirmationDialog
import xzynine.WebDAVPass.Android.ui.Dialog.CreateMasterPasswordDialog
import xzynine.WebDAVPass.Android.ui.Dialog.CreateMode
import xzynine.WebDAVPass.Android.ui.component.SelectableEntryCard
import xzynine.WebDAVPass.Android.ui.viewmodel.TokenViewModel
import xzynine.WebDAVPass.Android.util.LocalTimeFormatter
import xzynine.WebDAVPass.Android.util.resolveDisplayName

/**
 * 欢迎界面
 */
@Composable
fun WelcomeScreen(
    tokenViewModel: TokenViewModel,
    onEnterLibrary: () -> Unit,
    onBackPressed: () -> Boolean = { false },
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    val history by tokenViewModel.libraryViewModel.libraryHistory.collectAsState()
    val currentLibraryState by tokenViewModel.libraryViewModel.currentLibrary.collectAsState()
    var showCloudImportDialog by remember { mutableStateOf(false) }
    var showCloudCreateDialog by remember { mutableStateOf(false) }
    var showCreateMasterPasswordDialog by remember { mutableStateOf(false) }
    var createMode by remember { mutableStateOf(CreateMode.LOCAL) }
    var pendingCreateMasterPassword by remember { mutableStateOf("") }
    var pendingCreateKeyFileData by remember { mutableStateOf<ByteArray?>(null) }
    var pendingCreateKeyFileUri by remember { mutableStateOf<String?>(null) }
    var pendingUnlockLibrary by remember { mutableStateOf<LibraryContext?>(null) }
    val isSelectionMode = remember { mutableStateOf(false) }
    val selectedHistoryIds = remember { mutableStateMapOf<String, Boolean>() }
    val showDeleteDialog = remember { mutableStateOf(false) }

    /**
     * 退出内联解锁（隐藏解锁面板并收起键盘）。
     */
    fun clearInlineUnlock() {
        pendingUnlockLibrary = null
        keyboardController?.hide()
    }

    /**
     * 退出选择模式并清空选择。
     */
    fun clearSelectionMode() {
        selectedHistoryIds.clear()
        isSelectionMode.value = false
        showDeleteDialog.value = false
    }

    /**
     * 设置历史项选择状态。
     */
    fun setSelection(
        item: LibraryContext,
        checked: Boolean,
    ) {
        if (checked) {
            isSelectionMode.value = true
            selectedHistoryIds[item.id] = true
            clearInlineUnlock()
        } else {
            selectedHistoryIds.remove(item.id)
            if (selectedHistoryIds.isEmpty()) {
                isSelectionMode.value = false
            }
        }
    }

    BackHandler(enabled = isSelectionMode.value) {
        clearSelectionMode()
    }

    BackHandler(enabled = true) {
        if (isSelectionMode.value) {
            clearSelectionMode()
            return@BackHandler
        }
        if (showCloudImportDialog) {
            showCloudImportDialog = false
            return@BackHandler
        }
        if (showCloudCreateDialog) {
            showCloudCreateDialog = false
            return@BackHandler
        }
        if (showCreateMasterPasswordDialog) {
            showCreateMasterPasswordDialog = false
            pendingCreateMasterPassword = ""
            pendingCreateKeyFileData = null
            pendingCreateKeyFileUri = null
            return@BackHandler
        }
        if (showDeleteDialog.value) {
            showDeleteDialog.value = false
            return@BackHandler
        }
        if (pendingUnlockLibrary != null) {
            clearInlineUnlock()
            return@BackHandler
        }
        onBackPressed()
    }

    /**
     * 显示历史库顶部的内联解锁输入行
     */
    fun showInlineUnlock(libraryContext: LibraryContext) {
        pendingUnlockLibrary = libraryContext
    }

    /**
     * 获取指定历史项的最新快照，避免使用过期对象。
     */
    fun resolveLatestLibrary(libraryContext: LibraryContext?): LibraryContext? {
        if (libraryContext == null) {
            return null
        }
        val current = currentLibraryState
        if (current?.id == libraryContext.id) {
            return current
        }
        return history.firstOrNull { it.id == libraryContext.id } ?: libraryContext
    }

    val localImportLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
            onResult = { uri: Uri? ->
                if (uri == null) {
                    return@rememberLauncherForActivityResult
                }
                coroutineScope.launch {
                    val path = tokenViewModel.persistKdbxFromUri(uri)
                    if (path == null) {
                        ToastUtils.showShortToast(context, "导入失败：无法读取文件")
                        return@launch
                    }

                    val displayName = uri.resolveDisplayName(context, fallbackIfEmpty = "未命名.kdbx")

                    val item =
                        LibraryContext(
                            displayName = displayName,
                            sourceType = LibrarySourceType.LOCAL,
                            localPath = path,
                        )
                    tokenViewModel.libraryViewModel.upsertAndSelectLibrary(item)
                    showInlineUnlock(item)
                }
            },
        )

    val localCreateLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
            onResult = { uri: Uri? ->
                if (uri == null) {
                    return@rememberLauncherForActivityResult
                }
                coroutineScope.launch {
                    val path = tokenViewModel.createLocalKdbx(uri, pendingCreateMasterPassword, pendingCreateKeyFileData)
                    if (path == null) {
                        ToastUtils.showShortToast(context, "新建失败：无法创建文件")
                        return@launch
                    }

                    val displayName = uri.resolveDisplayName(context, fallbackIfEmpty = "未命名.kdbx")

                    val item =
                        LibraryContext(
                            displayName = displayName,
                            sourceType = LibrarySourceType.LOCAL,
                            localPath = path,
                            keyFileUri = pendingCreateKeyFileUri,
                        )
                    tokenViewModel.libraryViewModel.upsertAndSelectLibrary(item)
                    val plainPassword = pendingCreateMasterPassword
                    val plainKeyFileData = pendingCreateKeyFileData
                    val unlockOk = tokenViewModel.unlockCurrentLibrary(plainPassword, keyFileData = plainKeyFileData)
                    pendingCreateMasterPassword = ""
                    pendingCreateKeyFileData = null
                    pendingCreateKeyFileUri = null
                    if (unlockOk) {
                        promptAutoUnlockEnroll(context, tokenViewModel, item, plainPassword)
                        onEnterLibrary()
                    } else {
                        showInlineUnlock(item)
                    }
                }
            },
        )

    Scaffold(
        popupHost = {},
        topBar = {
            TopAppBar(
                title = "欢迎使用 WebDAVPass",
                navigationIcon = {},
                actions = {
                    if (isSelectionMode.value) {
                        IconButton(
                            onClick = {
                                if (selectedHistoryIds.isNotEmpty()) {
                                    showDeleteDialog.value = true
                                }
                            },
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Delete,
                                contentDescription = "删除",
                            )
                        }
                        IconButton(
                            onClick = {
                                clearSelectionMode()
                            },
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Close,
                                contentDescription = "取消选择",
                            )
                        }
                    }
                },
                defaultWindowInsetsPadding = true,
            )
        },
    ) {
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(it),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 分组标题（MIUI 设置分组样式）
            item {
                SmallTitle(
                    text = "数据库",
                    insideMargin = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                )
            }

            // 内联解锁面板（选中历史库后展示）
            if (!isSelectionMode.value) {
                resolveLatestLibrary(pendingUnlockLibrary)?.let { unlockLibrary ->
                    item {
                        InlineUnlockPanel(
                            tokenViewModel = tokenViewModel,
                            library = unlockLibrary,
                            onUnlockSuccess = {
                                pendingUnlockLibrary = null
                                onEnterLibrary()
                            },
                            onDismiss = {
                                pendingUnlockLibrary = null
                            },
                        )
                    }
                }
            }

            if (history.isEmpty()) {
                // 空状态
                item {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "暂无历史库",
                            fontSize = 16.sp,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "点击下方按钮导入或新建数据库",
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                // 历史库分组卡片
                item {
                    Card {
                        history.forEachIndexed { index, item ->
                            if (index > 0) {
                                HorizontalDivider()
                            }
                            val cloudSyncSummary =
                                if (item.sourceType == LibrarySourceType.CLOUD) {
                                    val syncText =
                                        when (item.lastSyncStatus) {
                                            "syncing" -> "同步中"
                                            "success" -> "同步成功"
                                            "merged" -> "已自动合并"
                                            "conflict" -> "同步冲突"
                                            "failed" -> "同步失败"
                                            else -> "未同步"
                                        }
                                    // 毫秒级时间戳按设备时区格式化为本地时间，避免直接显示原始数字
                                    val syncAtText =
                                        LocalTimeFormatter
                                            .formatLocalDateTime(item.lastSyncAt)
                                            .takeIf { it.isNotEmpty() }
                                            ?.let { "，上次: $it" }
                                            .orEmpty()
                                    "$syncText$syncAtText"
                                } else {
                                    ""
                                }

                            SelectableEntryCard(
                                itemKey = item.id,
                                title = item.displayName,
                                summary =
                                    if (item.sourceType == LibrarySourceType.CLOUD) {
                                        val remote = item.remoteFilePath ?: item.remoteBaseUrl.orEmpty()
                                        "$remote | $cloudSyncSummary"
                                    } else {
                                        item.localPath
                                    },
                                isSelectionMode = isSelectionMode.value,
                                isSelected = selectedHistoryIds.containsKey(item.id),
                                onLongClick = {
                                    if (!isSelectionMode.value) {
                                        setSelection(item, true)
                                    }
                                },
                                onCheckedChange = { checked ->
                                    setSelection(item, checked)
                                },
                                contentDescription = "历史库图标",
                                startAction = {
                                    Icon(
                                        modifier = Modifier.padding(end = 16.dp),
                                        imageVector = if (item.sourceType == LibrarySourceType.CLOUD) MiuixIcons.CloudFill else MiuixIcons.Folder,
                                        contentDescription = "历史库",
                                    )
                                },
                                onClick = {
                                    if (isSelectionMode.value) {
                                        setSelection(item, !selectedHistoryIds.containsKey(item.id))
                                        return@SelectableEntryCard
                                    }
                                    coroutineScope.launch {
                                        tokenViewModel.libraryViewModel.selectLibraryById(item.id)
                                        val selectedLibrary = currentLibraryState?.takeIf { it.id == item.id } ?: item

                                        showInlineUnlock(selectedLibrary)
                                    }
                                },
                                modifier =
                                    Modifier
                                        .fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 导入（主操作，主色按钮）
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            localImportLauncher.launch(arrayOf("*/*"))
                        },
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(imageVector = MiuixIcons.Folder, contentDescription = "本地导入")
                        Text(text = "本地导入")
                    }

                    Button(
                        onClick = {
                            showCloudImportDialog = true
                        },
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(imageVector = MiuixIcons.CloudFill, contentDescription = "云端导入")
                        Text(text = "云端导入")
                    }
                }
            }

            // 新建（次级操作，次级色按钮弱化）
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            createMode = CreateMode.LOCAL
                            showCreateMasterPasswordDialog = true
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(imageVector = MiuixIcons.AddFolder, contentDescription = "本地新建")
                        Text(text = "本地新建")
                    }

                    Button(
                        onClick = {
                            createMode = CreateMode.CLOUD
                            showCreateMasterPasswordDialog = true
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(imageVector = MiuixIcons.UploadCloud, contentDescription = "云端新建")
                        Text(text = "云端新建")
                    }
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
                    tokenViewModel.libraryViewModel.upsertAndSelectLibrary(library)
                    showCloudImportDialog = false
                    showInlineUnlock(library)
                }
            },
        )
    }

    if (showCloudCreateDialog) {
        CloudLibraryDialog(
            tokenViewModel = tokenViewModel,
            mode = CloudMode.CREATE,
            createMasterPassword = pendingCreateMasterPassword,
            createKeyFileData = pendingCreateKeyFileData,
            createKeyFileUri = pendingCreateKeyFileUri,
            onDismiss = { showCloudCreateDialog = false },
            onSelected = { library, createdMasterPassword ->
                coroutineScope.launch {
                    tokenViewModel.libraryViewModel.upsertAndSelectLibrary(library)
                    showCloudCreateDialog = false
                    val password = createdMasterPassword.orEmpty()
                    val keyFileData = pendingCreateKeyFileData
                    val unlockOk = tokenViewModel.unlockCurrentLibrary(password, keyFileData = keyFileData)
                    pendingCreateMasterPassword = ""
                    pendingCreateKeyFileData = null
                    pendingCreateKeyFileUri = null
                    if (unlockOk) {
                        promptAutoUnlockEnroll(context, tokenViewModel, library, password)
                        onEnterLibrary()
                    } else {
                        showInlineUnlock(library)
                    }
                }
            },
        )
    }

    if (showCreateMasterPasswordDialog) {
        CreateMasterPasswordDialog(
            mode = createMode,
            onDismiss = {
                showCreateMasterPasswordDialog = false
                pendingCreateMasterPassword = ""
                pendingCreateKeyFileData = null
                pendingCreateKeyFileUri = null
            },
            onConfirm = { password, keyFileData, keyFileUri ->
                pendingCreateMasterPassword = password
                pendingCreateKeyFileData = keyFileData
                pendingCreateKeyFileUri = keyFileUri
                showCreateMasterPasswordDialog = false
                if (createMode == CreateMode.LOCAL) {
                    localCreateLauncher.launch("WebDavPass.kdbx")
                } else {
                    showCloudCreateDialog = true
                }
            },
        )
    }

    if (isSelectionMode.value && selectedHistoryIds.isNotEmpty()) {
        ConfirmationDialog(
            title = "确认删除",
            summary = "已选 ${selectedHistoryIds.size} 项，仅从应用内历史中移除，不删除本地或云端文件。",
            show = showDeleteDialog,
            onDismiss = {
                showDeleteDialog.value = false
            },
            confirmButtonText = "删除",
            isDestructive = true,
            onConfirm = {
                val removedIds = selectedHistoryIds.keys.toSet()
                val removedCount =
                    tokenViewModel.libraryViewModel.removeLibraryHistoryByIds(removedIds) { id ->
                        tokenViewModel.autoUnlockViewModel.deleteKey(id)
                    }
                if (removedCount > 0 && pendingUnlockLibrary?.id in removedIds) {
                    clearInlineUnlock()
                }
                clearSelectionMode()
            },
        )
    }
}
