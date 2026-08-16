package xzynine.WebDAVPass.Android.ui.Screen

import androidx.documentfile.provider.DocumentFile
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Ok
import kotlinx.coroutines.launch
import xzylib.base.util.ToastUtils
import xzynine.WebDAVPass.Android.data.EditableAttachmentDraft
import xzynine.WebDAVPass.Android.data.EditableFieldDraft
import xzynine.WebDAVPass.Android.data.EntryHistoryInfo
import xzynine.WebDAVPass.Android.data.KdbxTokenRepository
import xzynine.WebDAVPass.Android.data.PasswordEntry
import xzynine.WebDAVPass.Android.data.RemainingValueType
import xzynine.WebDAVPass.Android.ui.Dialog.ConfirmationDialog
import xzynine.WebDAVPass.Android.ui.Dialog.EntryHistoryDialog
import xzynine.WebDAVPass.Android.ui.Dialog.IconPickerDialog
import xzynine.WebDAVPass.Android.ui.viewmodel.TokenViewModel

@Composable
fun PasswordEntryDetailScreen(
    tokenViewModel: TokenViewModel,
    entryId: Long,
    onNavigateBack: () -> Unit,
    onDeleted: () -> Unit = {},
    isEmbedded: Boolean = false
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val tokens by tokenViewModel.tokens.collectAsState(emptyList())
    val currentTimeMillis by tokenViewModel.currentTimeMillis.collectAsState(System.currentTimeMillis())

    var selectedEntry by remember(entryId) { mutableStateOf<PasswordEntry?>(null) }
    var detailLoaded by rememberSaveable(entryId) { mutableStateOf(false) }
    val showDeleteDialog = remember { mutableStateOf(false) }
    var isEditing by rememberSaveable(entryId) { mutableStateOf(false) }

    // 历史记录（非编辑态展示）
    var historyItems by remember(entryId) { mutableStateOf<List<EntryHistoryInfo>>(emptyList()) }
    val showHistoryDialog = remember { mutableStateOf(false) }

    var editTitle by rememberSaveable(entryId) { mutableStateOf("") }
    var editUsername by rememberSaveable(entryId) { mutableStateOf("") }
    var editPassword by rememberSaveable(entryId) { mutableStateOf("") }
    var editUrl by rememberSaveable(entryId) { mutableStateOf("") }
    var editNotes by rememberSaveable(entryId) { mutableStateOf("") }
    var editTagsText by rememberSaveable(entryId) { mutableStateOf("") }

    // 附件 / 自定义字段 / 过期时间 / 图标（编辑态）
    // 注意：附件含 ByteArray，不能用 rememberSaveable（无法序列化）
    var editAttachments by remember(entryId) { mutableStateOf<List<EditableAttachmentDraft>>(emptyList()) }
    var editCustomFields by remember(entryId) { mutableStateOf<List<EditableFieldDraft>>(emptyList()) }
    var editExpiryTime by remember(entryId) { mutableStateOf<Long?>(null) }
    var editIconStandardId by remember(entryId) { mutableStateOf(0) }
    var editCustomIconUuid by remember(entryId) { mutableStateOf<String?>(null) }
    var editNewCustomIconBytes by remember(entryId) { mutableStateOf<ByteArray?>(null) }
    var showIconPicker by remember { mutableStateOf(false) }

    fun syncEditFields(entry: PasswordEntry) {
        val usernameField = entry.keyValues.firstOrNull { it.fieldName.equals("UserName", ignoreCase = true) }
        val passwordField = entry.keyValues.firstOrNull {
            it.fieldName.equals("Password", ignoreCase = true) || it.valueType == RemainingValueType.PASSWORD
        }
        val urlField = entry.keyValues.firstOrNull {
            it.fieldName.equals("URL", ignoreCase = true) || it.valueType == RemainingValueType.URL
        }
        val notesField = entry.keyValues.firstOrNull { it.fieldName.equals("Notes", ignoreCase = true) }

        editTitle = entry.title
        editUsername = if (entry.account.isNotBlank()) entry.account else (usernameField?.rawValue ?: "")
        editPassword = passwordField?.rawValue ?: ""
        editUrl = urlField?.rawValue ?: ""
        editNotes = notesField?.rawValue ?: ""
        editAttachments = entry.attachments.map { EditableAttachmentDraft(name = it.name) }
        // 仅排除真正的标准字段（isStandard），保留同名但属于额外字段（extra）的合法字段，
        // 避免从其他工具迁移来的、命名为 Password 等的 extra 在保存时被静默删除。
        editCustomFields = entry.keyValues
            .filter { !it.isStandard }
            .map {
                EditableFieldDraft(
                    name = it.fieldName,
                    originalName = it.fieldName,
                    value = it.rawValue,
                    isProtected = it.isProtected,
                    valueType = it.valueType,
                    isStandard = it.isStandard
                )
            }
        editExpiryTime = entry.expiryTime
        editIconStandardId = entry.standardIconId
        editCustomIconUuid = entry.customIconUuid
        editNewCustomIconBytes = null
        editTagsText = entry.tags.joinToString(", ")
    }

    LaunchedEffect(entryId) {
        detailLoaded = false
        selectedEntry = tokenViewModel.loadPasswordEntryDetail(entryId)
        selectedEntry?.let { entry -> syncEditFields(entry) }
        historyItems = tokenViewModel.loadEntryHistory(entryId)
        detailLoaded = true
    }

    // 附件缓存清理：离开页面（返回/切换库/锁定时路由销毁）清空解密后的明文附件，
    // 避免 cacheDir/attachments 长期残留敏感明文。
    // 注意不可在 onStop 清理：打开系统查看器时 Activity 会 onStop，清理会破坏正在查看的文件。
    DisposableEffect(Unit) {
        onDispose {
            java.io.File(context.cacheDir, "attachments").listFiles()?.forEach { it.delete() }
        }
    }

    val selectedToken = tokens.firstOrNull { it.id == entryId }
    val tokenCode by tokenViewModel.getTokenCode(entryId).collectAsState(null)
    var showOtpSecret by rememberSaveable(entryId) { mutableStateOf(false) }
    var showQrCode by rememberSaveable(entryId) { mutableStateOf(false) }
    var isPasswordVisible by rememberSaveable(entryId) { mutableStateOf(false) }

    // 附件导入：选择文件后增量读取字节加入编辑态附件列表（带硬性大小上限，避免 OOM）
    val attachmentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        // 先用声明大小预检（content provider 可能返回 -1，故仅作软提示，真正上限在增量读取处强制）
        val declaredSize = DocumentFile.fromSingleUri(context, uri)?.length() ?: -1L
        if (declaredSize > KdbxTokenRepository.MAX_ATTACHMENT_BYTES) {
            ToastUtils.showShortToast(context, "附件过大（上限 ${KdbxTokenRepository.MAX_ATTACHMENT_BYTES / 1024 / 1024} MiB），已取消")
            return@rememberLauncherForActivityResult
        }
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                // 增量读取并强制上限：超过则抛异常，拒绝进入内存
                val buffer = java.io.ByteArrayOutputStream(8 * 1024)
                val chunk = ByteArray(8 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(chunk)
                    if (read < 0) break
                    total += read
                    if (total > KdbxTokenRepository.MAX_ATTACHMENT_BYTES) {
                        throw IllegalStateException("附件过大")
                    }
                    buffer.write(chunk, 0, read)
                }
                val bytes = buffer.toByteArray()
                val name = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
                    ?: "attachment_${System.currentTimeMillis()}"
                editAttachments = editAttachments + EditableAttachmentDraft(
                    name = name,
                    data = bytes,
                    isNew = true
                )
            }
        }.onFailure {
            ToastUtils.showShortToast(context, "附件过大或读取失败，已取消")
        }
    }

    // 保存当前编辑内容（编辑态顶栏保存按钮共用）
    fun saveEntry() {
        coroutineScope.launch {
            val existingDraft = tokenViewModel.loadPasswordEntryDraft(entryId) ?: return@launch
            val updated = tokenViewModel.updatePasswordEntry(
                existingDraft.copy(
                    title = editTitle.trim(),
                    username = editUsername.trim(),
                    password = editPassword,
                    url = editUrl.trim(),
                    notes = editNotes,
                    tags = parseTagsText(editTagsText),
                    customFields = editCustomFields,
                    attachments = editAttachments,
                    expiryTime = editExpiryTime,
                    customIconUuid = editCustomIconUuid,
                    iconStandardId = editIconStandardId,
                    newCustomIconBytes = editNewCustomIconBytes
                )
            )
            if (updated) {
                selectedEntry = tokenViewModel.loadPasswordEntryDetail(entryId)
                selectedEntry?.let { syncEditFields(it) }
                isEditing = false
            }
        }
    }

    // 详情顶栏操作按钮（编辑/删除/保存/取消），横竖屏两种顶栏共用，避免双处重复
    val detailActions: @Composable () -> Unit = {
        if (selectedEntry != null) {
            if (isEditing) {
                IconButton(
                    onClick = {
                        selectedEntry?.let { syncEditFields(it) }
                        isEditing = false
                    }
                ) {
                    Icon(
                        imageVector = MiuixIcons.Close,
                        contentDescription = "取消编辑"
                    )
                }
                IconButton(onClick = { saveEntry() }) {
                    Icon(
                        imageVector = MiuixIcons.Ok,
                        contentDescription = "保存"
                    )
                }
            } else {
                IconButton(
                    onClick = {
                        showDeleteDialog.value = true
                    }
                ) {
                    Icon(
                        imageVector = MiuixIcons.Delete,
                        contentDescription = "删除"
                    )
                }
                IconButton(onClick = { isEditing = true }) {
                    Icon(
                        imageVector = MiuixIcons.Edit,
                        contentDescription = "编辑"
                    )
                }
            }
        }
    }

    Scaffold(
        popupHost = {},
        topBar = {
            if (isEmbedded) {
                SmallTopAppBar(
                    title = selectedEntry?.title ?: "密码详情",
                    actions = {
                        detailActions()
                    }
                )
            } else {
                TopAppBar(
                    title = selectedEntry?.title ?: "密码详情",
                    navigationIcon = {
                        IconButton(
                            onClick = onNavigateBack
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = "返回"
                            )
                        }
                    },
                    actions = {
                        detailActions()
                    }
                )
            }
        }
    ) { paddingValues ->
        PasswordEntryDetailContent(
            modifier = Modifier.padding(paddingValues),
            detailLoaded = detailLoaded,
            selectedEntry = selectedEntry,
            selectedToken = selectedToken,
            tokenCode = tokenCode,
            currentTimeMillis = currentTimeMillis,
            context = context,
            isEditing = isEditing,
            editTitle = editTitle,
            onEditTitleChange = { editTitle = it },
            editUsername = editUsername,
            onEditUsernameChange = { editUsername = it },
            editPassword = editPassword,
            onEditPasswordChange = { editPassword = it },
            editUrl = editUrl,
            onEditUrlChange = { editUrl = it },
            editNotes = editNotes,
            onEditNotesChange = { editNotes = it },
            editTagsText = editTagsText,
            onEditTagsTextChange = { editTagsText = it },
            editAttachments = editAttachments,
            onEditAttachmentsChange = { editAttachments = it },
            editCustomFields = editCustomFields,
            onEditCustomFieldsChange = { editCustomFields = it },
            editExpiryTime = editExpiryTime,
            onEditExpiryTimeChange = { editExpiryTime = it },
            editIconStandardId = editIconStandardId,
            editNewCustomIconBytes = editNewCustomIconBytes,
            showOtpSecret = showOtpSecret,
            onShowOtpSecretChange = { showOtpSecret = it },
            showQrCode = showQrCode,
            onShowQrCodeChange = { showQrCode = it },
            isPasswordVisible = isPasswordVisible,
            onPasswordVisibleChange = { isPasswordVisible = it },
            historyItems = historyItems,
            onHistoryClick = { showHistoryDialog.value = true },
            onPickIcon = { showIconPicker = true },
            onAddAttachment = { attachmentPicker.launch("*/*") },
            entryId = entryId,
            tokenViewModel = tokenViewModel,
            coroutineScope = coroutineScope
        )
    }

    selectedEntry?.let { entry ->
        ConfirmationDialog(
            title = "确认删除",
            summary = "\"${entry.title}\" 将移入回收站。",
            show = showDeleteDialog,
            onDismiss = {
                showDeleteDialog.value = false
            },
            confirmButtonText = "删除",
            isDestructive = true,
            onConfirm = {
                coroutineScope.launch {
                    val deleted = tokenViewModel.deletePasswordEntry(entryId)
                    if (deleted) {
                        showDeleteDialog.value = false
                        onDeleted()
                    }
                }
            }
        )
    }

    EntryHistoryDialog(
        show = showHistoryDialog,
        histories = historyItems,
        onDismiss = {
            showHistoryDialog.value = false
        },
        onRestore = { history ->
            coroutineScope.launch {
                val restored = tokenViewModel.restoreEntryFromHistory(entryId, history.index)
                if (restored) {
                    ToastUtils.showShortToast(context, "已恢复该历史版本")
                    // 刷新详情与历史列表（当前版本已进历史，原历史保留）
                    selectedEntry = tokenViewModel.loadPasswordEntryDetail(entryId)
                    selectedEntry?.let { syncEditFields(it) }
                    historyItems = tokenViewModel.loadEntryHistory(entryId)
                } else {
                    ToastUtils.showShortToast(context, "恢复失败")
                }
            }
        }
    )

    if (showIconPicker) {
        IconPickerDialog(
            show = showIconPicker,
            currentStandardIconId = editIconStandardId,
            currentCustomIconBytes = editNewCustomIconBytes ?: selectedEntry?.customIconBytes,
            iconPrimary = selectedEntry?.title,
            iconSecondary = selectedEntry?.account,
            onDismiss = { showIconPicker = false },
            onPick = { standardId, bytes ->
                showIconPicker = false
                val currentBytes = selectedEntry?.customIconBytes
                // 与打开时的图标完全一致视为无改动：保留原自定义图标，避免重复写入图标池
                val unchanged = bytes != null && currentBytes != null &&
                    bytes.contentEquals(currentBytes) && standardId == editIconStandardId
                if (unchanged) {
                    editNewCustomIconBytes = null
                } else {
                    editIconStandardId = standardId ?: 0
                    editCustomIconUuid = null
                    editNewCustomIconBytes = bytes
                }
            }
        )
    }
}

