package xzynine.WebDAVPass.Android.ui.Screen

import android.net.Uri
import android.provider.OpenableColumns
import xzylib.base.util.ToastUtils
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.CloudFill
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.UploadCloud
import xzynine.WebDAVPass.Android.data.LibraryContext
import xzynine.WebDAVPass.Android.data.LibrarySourceType
import xzynine.WebDAVPass.Android.ui.Dialog.ConfirmationDialog
import xzynine.WebDAVPass.Android.ui.Dialog.CloudLibraryDialog
import xzynine.WebDAVPass.Android.ui.Dialog.CloudMode
import xzynine.WebDAVPass.Android.ui.Dialog.CreateMasterPasswordDialog
import xzynine.WebDAVPass.Android.ui.Dialog.CreateMode
import xzynine.WebDAVPass.Android.ui.ViewModel.TokenViewModel
import xzynine.WebDAVPass.Android.ui.component.SelectableEntryCard



/**
 * 欢迎界面
 */
@Composable
fun WelcomeScreen(
    tokenViewModel: TokenViewModel,
    onEnterLibrary: () -> Unit
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    val inlineUnlockFocusRequester = remember { FocusRequester() }
    val history by tokenViewModel.libraryHistory.collectAsState()
    var showCloudImportDialog by remember { mutableStateOf(false) }
    var showCloudCreateDialog by remember { mutableStateOf(false) }
    var showCreateMasterPasswordDialog by remember { mutableStateOf(false) }
    var createMode by remember { mutableStateOf(CreateMode.LOCAL) }
    var pendingCreateMasterPassword by remember { mutableStateOf("") }
    var pendingUnlockLibrary by remember { mutableStateOf<LibraryContext?>(null) }
    var inlineUnlockPassword by remember { mutableStateOf("") }
    var showInlinePassword by remember { mutableStateOf(false) }
    var inlineUnlockLoading by remember { mutableStateOf(false) }
    var inlineUnlockFocusNonce by remember { mutableStateOf(0) }
    val isSelectionMode = remember { mutableStateOf(false) }
    val selectedHistoryIds = remember { mutableStateMapOf<String, Boolean>() }
    val showDeleteDialog = remember { mutableStateOf(false) }

    /**
     * 清理内联解锁输入状态。
     */
    fun clearInlineUnlock() {
        pendingUnlockLibrary = null
        inlineUnlockPassword = ""
        showInlinePassword = false
        inlineUnlockLoading = false
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
    fun setSelection(item: LibraryContext, checked: Boolean) {
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

    /**
     * 显示历史库顶部的内联解锁输入行
     */
    fun showInlineUnlock(libraryContext: LibraryContext) {
        pendingUnlockLibrary = libraryContext
        inlineUnlockPassword = ""
        showInlinePassword = false
        inlineUnlockFocusNonce++
    }

    /**
     * 解析 Uri 展示名称。
     */
    fun resolveUriDisplayName(uri: Uri): String {
        val cursor = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?: return uri.lastPathSegment ?: "未命名.kdbx"
        return try {
            if (!cursor.moveToFirst()) {
                return uri.lastPathSegment ?: "未命名.kdbx"
            }
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index < 0 || cursor.isNull(index)) {
                uri.lastPathSegment ?: "未命名.kdbx"
            } else {
                cursor.getString(index).ifBlank { uri.lastPathSegment ?: "未命名.kdbx" }
            }
        } finally {
            cursor.close()
        }
    }

    LaunchedEffect(pendingUnlockLibrary?.id, inlineUnlockFocusNonce) {
        if (pendingUnlockLibrary != null) {
            inlineUnlockFocusRequester.requestFocus()
            keyboardController?.show()
        }
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
                    ToastUtils.showShortToast(context, "导入失败：无法读取文件")
                    return@launch
                }

                val displayName = resolveUriDisplayName(uri)

                val item = LibraryContext(
                    displayName = displayName,
                    sourceType = LibrarySourceType.LOCAL,
                    localPath = path
                )
                tokenViewModel.openLibraryContext(item)
                showInlineUnlock(item)
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
                    ToastUtils.showShortToast(context, "新建失败：无法创建文件")
                    return@launch
                }

                val displayName = resolveUriDisplayName(uri)

                val item = LibraryContext(
                    displayName = displayName,
                    sourceType = LibrarySourceType.LOCAL,
                    localPath = path
                )
                tokenViewModel.openLibraryContext(item)
                val unlockOk = tokenViewModel.unlockCurrentLibrary(pendingCreateMasterPassword)
                pendingCreateMasterPassword = ""
                if (unlockOk) {
                    onEnterLibrary()
                } else {
                    showInlineUnlock(item)
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
                actions = {
                    if (isSelectionMode.value) {
                        TextButton(
                            text = "删除",
                            onClick = {
                                if (selectedHistoryIds.isNotEmpty()) {
                                    showDeleteDialog.value = true
                                }
                            }
                        )
                        TextButton(
                            text = "取消",
                            onClick = {
                                clearSelectionMode()
                            }
                        )
                    }
                },
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

            Text(text = "历史库")

            if (!isSelectionMode.value) {
                pendingUnlockLibrary?.let { unlockLibrary ->
                    Text(text = "解锁: ${unlockLibrary.displayName}")

                    TextField(
                        value = inlineUnlockPassword,
                        onValueChange = { inlineUnlockPassword = it },
                        label = "请输入主密码",
                        visualTransformation = if (showInlinePassword) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            TextButton(
                                text = if (showInlinePassword) "隐藏" else "显示",
                                onClick = { showInlinePassword = !showInlinePassword }
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(inlineUnlockFocusRequester),
                        singleLine = true
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            text = "取消",
                            onClick = {
                                clearInlineUnlock()
                            },
                            modifier = Modifier.weight(1f)
                        )

                        Button(
                            onClick = {
                                if (inlineUnlockPassword.isBlank()) {
                                    ToastUtils.showShortToast(context, "请输入主密码")
                                    return@Button
                                }
                                coroutineScope.launch {
                                    inlineUnlockLoading = true
                                    val ok = tokenViewModel.unlockCurrentLibrary(inlineUnlockPassword)
                                    inlineUnlockLoading = false
                                    if (ok) {
                                        clearInlineUnlock()
                                        onEnterLibrary()
                                    } else {
                                        val message = tokenViewModel.getLastUnlockErrorMessage()
                                            ?: "解锁失败：主密码不正确或文件无效"
                                        ToastUtils.showShortToast(context, message)
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !inlineUnlockLoading
                        ) {
                            Text(text = if (inlineUnlockLoading) "解锁中..." else "解锁")
                        }
                    }
                }
            }

            if (history.isEmpty()) {
                Text(text = "暂无历史记录")
            } else {
                history.forEach { item ->
                    val cloudSyncSummary = if (item.sourceType == LibrarySourceType.CLOUD) {
                        val syncText = when (item.lastSyncStatus) {
                            "syncing" -> "同步中"
                            "success" -> "同步成功"
                            "merged" -> "已自动合并"
                            "conflict" -> "同步冲突"
                            "failed" -> "同步失败"
                            else -> "未同步"
                        }
                        val syncAtText = item.lastSyncAt?.let { "，上次: $it" }.orEmpty()
                        "$syncText$syncAtText"
                    } else {
                        ""
                    }

                    SelectableEntryCard(
                        itemKey = item.id,
                        title = item.displayName,
                        summary = if (item.sourceType == LibrarySourceType.CLOUD) {
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
                                imageVector = if (item.sourceType == LibrarySourceType.CLOUD) MiuixIcons.CloudFill else MiuixIcons.Download,
                                contentDescription = "历史库"
                            )
                        },
                        onClick = {
                            coroutineScope.launch {
                                tokenViewModel.switchLibrary(item.id)
                                showInlineUnlock(item)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

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
                    showInlineUnlock(library)
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
                        showInlineUnlock(library)
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
                val removedCount = tokenViewModel.removeLibraryHistoryByIds(removedIds)
                if (removedCount > 0 && pendingUnlockLibrary?.id in removedIds) {
                    clearInlineUnlock()
                }
                clearSelectionMode()
            }
        )
    }
}



