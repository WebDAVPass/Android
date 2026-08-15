package xzynine.WebDAVPass.Android.ui.Screen

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import android.webkit.MimeTypeMap
import android.widget.ImageView
import androidx.documentfile.provider.DocumentFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import xzynine.WebDAVPass.Android.ui.component.Preference
import xzynine.WebDAVPass.Android.ui.component.PreferenceType
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Hide
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Show
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import kotlinx.coroutines.launch
import xzylib.base.util.ToastUtils
import xzynine.WebDAVPass.Android.data.EditableAttachmentDraft
import xzynine.WebDAVPass.Android.data.EditableFieldDraft
import xzynine.WebDAVPass.Android.data.EntryAttachmentInfo
import xzynine.WebDAVPass.Android.data.EntryHistoryInfo
import xzynine.WebDAVPass.Android.data.KdbxTokenRepository
import xzynine.WebDAVPass.Android.data.PasswordEntry
import xzynine.WebDAVPass.Android.data.RemainingKeyValue
import xzynine.WebDAVPass.Android.data.RemainingValueType
import com.kunzisoft.keepass.model.PasskeyEntryFields
import xzynine.WebDAVPass.Android.ui.Dialog.ConfirmationDialog
import xzynine.WebDAVPass.Android.ui.Dialog.EntryHistoryDialog
import xzynine.WebDAVPass.Android.ui.Dialog.IconPickerDialog
import xzynine.WebDAVPass.Android.ui.ViewModel.TokenViewModel
import xzynine.WebDAVPass.Android.ui.component.EntryIcon
import xzynine.WebDAVPass.Android.ui.component.TokenCard
import xzynine.WebDAVPass.Android.util.LocalTimeFormatter
import xzynine.WebDAVPass.Android.util.QrCodeUtil

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

    val selectedToken = tokens.firstOrNull { it.id == entryId }
    val tokenCode by tokenViewModel.getTokenCode(entryId).collectAsState(null)
    var showOtpSecret by rememberSaveable(entryId) { mutableStateOf(false) }
    var showQrCode by rememberSaveable(entryId) { mutableStateOf(false) }
    var isPasswordVisible by rememberSaveable(entryId) { mutableStateOf(false) }

    val cornerRadius = 12.dp
    val cardBorderColor = MiuixTheme.colorScheme.onSurfaceSecondary.copy(alpha = 0.18f)

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

    @Composable
    fun PasswordDetailContent(modifier: Modifier = Modifier) {
        if (!detailLoaded) {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "加载中...")
            }
            return
        }

        val entry = selectedEntry
        if (entry == null) {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "条目不存在")
            }
            return
        }

        val usernameField = entry.keyValues.firstOrNull { it.fieldName.equals("UserName", ignoreCase = true) }
        val passwordField = entry.keyValues.firstOrNull {
            it.fieldName.equals("Password", ignoreCase = true) || it.valueType == RemainingValueType.PASSWORD
        }
        val urlField = entry.keyValues.firstOrNull {
            it.fieldName.equals("URL", ignoreCase = true) || it.valueType == RemainingValueType.URL
        }
        val notesField = entry.keyValues.firstOrNull { it.fieldName.equals("Notes", ignoreCase = true) }
        val otpFields = entry.keyValues.filter { isOtpField(it) }
        val otpSecretField = otpFields.firstOrNull()
        val additionalFields = entry.keyValues.filterNot { item ->
            item == usernameField
                || item == passwordField
                || item == urlField
                || item == notesField
            || (selectedToken != null && isOtpField(item))
        }.filterNot { item ->
            // Passkey 字段在下方独立区块展示
            PasskeyEntryFields.FIELD_USERNAME == item.fieldName
                || PasskeyEntryFields.FIELD_PRIVATE_KEY == item.fieldName
                || PasskeyEntryFields.FIELD_CREDENTIAL_ID == item.fieldName
                || PasskeyEntryFields.FIELD_USER_HANDLE == item.fieldName
                || PasskeyEntryFields.FIELD_RELYING_PARTY == item.fieldName
                || PasskeyEntryFields.FIELD_FLAG_BE == item.fieldName
                || PasskeyEntryFields.FIELD_FLAG_BS == item.fieldName
        }
        val passkeyValues = entry.keyValues.associate { it.fieldName to it.rawValue }
        val passkeyRelyingParty = passkeyValues[PasskeyEntryFields.FIELD_RELYING_PARTY]
        val passkeyUsername = passkeyValues[PasskeyEntryFields.FIELD_USERNAME]
        val passkeyCredentialId = passkeyValues[PasskeyEntryFields.FIELD_CREDENTIAL_ID]
        val hasPasskey = passkeyValues.containsKey(PasskeyEntryFields.FIELD_CREDENTIAL_ID)
        val usernameValue = when {
            entry.account.isNotBlank() -> entry.account
            usernameField?.rawValue?.isNotBlank() == true -> usernameField.rawValue
            else -> "--"
        }
        val passwordValue = passwordField?.rawValue?.ifBlank { "--" } ?: "--"
        val urlValue = urlField?.rawValue?.ifBlank { "--" } ?: "--"
        val notesValue = notesField?.rawValue?.ifBlank { "--" } ?: "--"

        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (selectedToken != null) {
                item {
                    TokenCard(
                        token = selectedToken,
                        tokenCode = if (showOtpSecret) null else tokenCode,
                        currentTimeMillis = currentTimeMillis,
                        onClick = {
                            if (!showOtpSecret) {
                                tokenCode?.let { code ->
                                    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clipData = ClipData.newPlainText("2FA Token", code.code)
                                    clipboardManager.setPrimaryClip(clipData)
                                    ToastUtils.showShortToast(context, "已复制到剪贴板")
                                }
                            }
                        },
                        onLongClick = {
                            if (otpSecretField != null) {
                                showOtpSecret = !showOtpSecret
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (showOtpSecret && otpSecretField != null) {
                    item {
                        Preference(
                            type = PreferenceType.Arrow,
                            title = "OTP 键值",
                            summary = otpSecretField.rawValue,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {}
                        )
                    }

                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = 0.5.dp,
                                    color = cardBorderColor,
                                    shape = RoundedCornerShape(cornerRadius)
                                ),
                            colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surface),
                            cornerRadius = cornerRadius,
                            pressFeedbackType = PressFeedbackType.None,
                            showIndication = false,
                            onClick = {}
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                IconButton(onClick = { showQrCode = !showQrCode }) {
                                    Icon(
                                        imageVector = MiuixIcons.Notes,
                                        contentDescription = if (showQrCode) "隐藏二维码" else "显示二维码"
                                    )
                                }
                                Text(
                                    text = if (showQrCode) "点击图标隐藏二维码" else "点击图标显示二维码",
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                                    modifier = Modifier.padding(top = 8.dp)
                                )

                                if (showQrCode && selectedToken != null) {
                                    Text(
                                        text = "扫描二维码添加到其他设备:",
                                        fontSize = 14.sp,
                                        color = MiuixTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                                    )
                                    AndroidView(
                                        modifier = Modifier.size(200.dp),
                                        factory = { context ->
                                            ImageView(context).apply {
                                                scaleType = ImageView.ScaleType.FIT_CENTER
                                            }
                                        },
                                        update = { imageView ->
                                            val bitmap = QrCodeUtil.generateQrCodeFromToken(selectedToken)
                                            imageView.setImageBitmap(bitmap)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 0.5.dp,
                            color = cardBorderColor,
                            shape = RoundedCornerShape(cornerRadius)
                        ),
                    colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surface),
                    cornerRadius = cornerRadius,
                    pressFeedbackType = PressFeedbackType.None,
                    showIndication = false,
                    onClick = {}
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (isEditing) {
                            TextField(
                                value = editTitle,
                                onValueChange = { editTitle = it },
                                label = "标题",
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 14.dp),
                                thickness = 0.5.dp
                            )
                        }

                        if (selectedToken == null) {
                            if (isEditing) {
                                TextField(
                                    value = editUsername,
                                    onValueChange = { editUsername = it },
                                    label = "账号",
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            } else {
                                Preference(
                            type = PreferenceType.Arrow,
                                    title = "账号",
                                    summary = usernameValue,
                                    modifier = Modifier.fillMaxWidth(),
                                    startAction = {
                                        EntryIcon(
                                            customIconBytes = entry.customIconBytes,
                                            standardIconId = entry.standardIconId,
                                            primary = entry.title,
                                            secondary = entry.account,
                                            modifier = Modifier.padding(end = 16.dp).size(32.dp),
                                            contentDescription = "条目图标"
                                        )
                                    },
                                    onClick = {}
                                )
                            }
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 14.dp),
                                thickness = 0.5.dp
                            )
                        }

                        if (isEditing) {
                            TextField(
                                value = editPassword,
                                onValueChange = { editPassword = it },
                                label = "密码",
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        } else {
                            Preference(
                            type = PreferenceType.Arrow,
                                title = "密码",
                                summary = when {
                                    passwordValue == "--" -> "--"
                                    isPasswordVisible -> passwordValue
                                    else -> "••••••"
                                },
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    if (passwordValue == "--") return@Preference
                                    if (!isPasswordVisible) {
                                        copySensitiveToClipboard(
                                            context = context,
                                            label = "密码",
                                            content = passwordValue
                                        )
                                        ToastUtils.showShortToast(context, "密码已复制到剪贴板")
                                    }
                                    isPasswordVisible = !isPasswordVisible
                                }
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            thickness = 0.5.dp
                        )

                        if (isEditing) {
                            TextField(
                                value = editUrl,
                                onValueChange = { editUrl = it },
                                label = "网站",
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        } else {
                            Preference(
                            type = PreferenceType.Arrow,
                                title = "网站",
                                summary = urlValue,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    if (urlValue != "--") {
                                        openUrl(context, urlValue)
                                    }
                                }
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            thickness = 0.5.dp
                        )

                        if (isEditing) {
                            TextField(
                                value = editNotes,
                                onValueChange = { editNotes = it },
                                label = "备注",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        } else {
                            Preference(
                            type = PreferenceType.Arrow,
                                title = "备注",
                                summary = notesValue,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {}
                            )
                        }

                        if (isEditing) {
                            TextField(
                                value = editTagsText,
                                onValueChange = { editTagsText = it },
                                label = "标签（逗号分隔）",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showIconPicker = true }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    EntryIcon(
                                        customIconBytes = editNewCustomIconBytes ?: entry.customIconBytes,
                                        standardIconId = editIconStandardId,
                                        primary = entry.title,
                                        secondary = entry.account,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Text(
                                        text = "  选择图标",
                                        fontSize = 14.sp,
                                        color = MiuixTheme.colorScheme.primary
                                    )
                                }
                                Icon(
                                    imageVector = MiuixIcons.Edit,
                                    contentDescription = "选择图标",
                                    tint = MiuixTheme.colorScheme.onSurfaceSecondary
                                )
                            }
                        } else if (entry.tags.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                entry.tags.forEach { tag ->
                                    Card(
                                        modifier = Modifier,
                                        colors = CardDefaults.defaultColors(
                                            color = MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
                                        ),
                                        cornerRadius = 12.dp,
                                        pressFeedbackType = PressFeedbackType.None,
                                        showIndication = false,
                                        onClick = {}
                                    ) {
                                        Text(
                                            text = tag,
                                            fontSize = 12.sp,
                                            color = MiuixTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 过期时间（编辑态可设置，非编辑态展示）
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 0.5.dp,
                            color = cardBorderColor,
                            shape = RoundedCornerShape(cornerRadius)
                        ),
                    colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surface),
                    cornerRadius = cornerRadius,
                    pressFeedbackType = PressFeedbackType.None,
                    showIndication = false,
                    onClick = {}
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (isEditing) {
                            ExpiryTimeEditor(
                                value = editExpiryTime,
                                onValueChange = { editExpiryTime = it }
                            )
                        } else if (entry.expiryTime != null) {
                            val expired = entry.isExpired
                            Preference(
                            type = PreferenceType.Arrow,
                                title = "过期时间",
                                summary = formatExpiry(entry.expiryTime, expired),
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {}
                            )
                        }
                    }
                }
            }

            // Passkey（非编辑态展示）
            if (!isEditing && hasPasskey) {
                item {
                    SmallTitle(text = "Passkey")
                }
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 0.5.dp,
                                color = cardBorderColor,
                                shape = RoundedCornerShape(cornerRadius)
                            ),
                        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surface),
                        cornerRadius = cornerRadius,
                        pressFeedbackType = PressFeedbackType.None,
                        showIndication = false,
                        onClick = {}
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            PasskeyInfoRow(
                                label = "依赖方",
                                value = passkeyRelyingParty?.ifBlank { "--" } ?: "--"
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 14.dp),
                                thickness = 0.5.dp
                            )
                            PasskeyInfoRow(
                                label = "用户名",
                                value = passkeyUsername?.ifBlank { "--" } ?: "--"
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 14.dp),
                                thickness = 0.5.dp
                            )
                            PasskeyInfoRow(
                                label = "凭据 ID",
                                value = passkeyCredentialId?.let {
                                    if (it.length > 24) it.take(10) + "…" + it.takeLast(10) else it
                                } ?: "--"
                            )
                        }
                    }
                }
            }

            // 自定义字段（编辑态可增删改，非编辑态在附加信息中已展示，这里提供编辑入口）
            if (isEditing) {
                item {
                    SmallTitle(text = "自定义字段")
                }
                item {
                    CustomFieldsEditor(
                        fields = editCustomFields,
                        onFieldsChange = { editCustomFields = it }
                    )
                }
            }

            // 附件
            item {
                val attachmentCount = if (isEditing) {
                    editAttachments.count { !it.removed }
                } else {
                    entry.attachments.size
                }
                SmallTitle(text = "附件 ($attachmentCount)")
            }
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 0.5.dp,
                            color = cardBorderColor,
                            shape = RoundedCornerShape(cornerRadius)
                        ),
                    colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surface),
                    cornerRadius = cornerRadius,
                    pressFeedbackType = PressFeedbackType.None,
                    showIndication = false,
                    onClick = {}
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (entry.attachments.isEmpty() && !isEditing) {
                            Text(
                                text = "无附件",
                                fontSize = 13.sp,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            )
                        }
                        if (isEditing) {
                            editAttachments.filter { !it.removed }.forEachIndexed { index, att ->
                                AttachmentEditRow(
                                    name = att.name,
                                    sizeText = if (att.isNew) {
                                        "${formatFileSize((att.data?.size ?: 0).toLong())} · 新增"
                                    } else null,
                                    canDelete = true,
                                    onDelete = {
                                        editAttachments = editAttachments.map {
                                            if (it === att) it.copy(removed = true) else it
                                        }
                                    }
                                )
                                if (index < editAttachments.filter { !it.removed }.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 14.dp),
                                        thickness = 0.5.dp
                                    )
                                }
                            }
                        } else {
                            entry.attachments.forEachIndexed { index, att ->
                                AttachmentViewRow(
                                    name = att.name,
                                    sizeText = formatFileSize(att.size),
                                    onClick = {
                                        coroutineScope.launch {
                                            val file = java.io.File(context.cacheDir, "attachments").apply { mkdirs() }
                                                .resolve(att.name.replace("/", "_"))
                                            val ok = runCatching {
                                                file.outputStream().use { out ->
                                                    tokenViewModel.copyEntryAttachmentTo(entryId, att.name, out)
                                                }
                                            }.getOrElse { false }
                                            if (ok) {
                                                openAttachment(context, att.name, file)
                                            } else {
                                                ToastUtils.showShortToast(context, "读取附件失败或附件过大")
                                            }
                                        }
                                    }
                                )
                                if (index < entry.attachments.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 14.dp),
                                        thickness = 0.5.dp
                                    )
                                }
                            }
                        }
                        if (isEditing) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 14.dp),
                                thickness = 0.5.dp
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { attachmentPicker.launch("*/*") }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Edit,
                                    contentDescription = "添加附件",
                                    tint = MiuixTheme.colorScheme.primary
                                )
                                Text(
                                    text = " 添加附件",
                                    fontSize = 14.sp,
                                    color = MiuixTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (additionalFields.isNotEmpty()) {
                item {
                    SmallTitle(
                        text = "附加信息"
                    )
                }

                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 0.5.dp,
                                color = cardBorderColor,
                                shape = RoundedCornerShape(cornerRadius)
                            ),
                        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surface),
                        cornerRadius = cornerRadius,
                        pressFeedbackType = PressFeedbackType.None,
                        showIndication = false,
                        onClick = {}
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            additionalFields.forEachIndexed { index, item ->
                                AdditionalFieldRow(
                                    item = item,
                                    onCopy = {
                                        copySensitiveToClipboard(context, item.fieldName, item.rawValue)
                                        ToastUtils.showShortToast(context, "已复制到剪贴板")
                                    }
                                )
                                if (index < additionalFields.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 14.dp),
                                        thickness = 0.5.dp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 历史记录（非编辑态展示，编辑中不显示以免与保存中的内容混淆）
            if (!isEditing && historyItems.isNotEmpty()) {
                item {
                    SmallTitle(
                        text = "历史记录 (${historyItems.size})"
                    )
                }
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 0.5.dp,
                                color = cardBorderColor,
                                shape = RoundedCornerShape(cornerRadius)
                            ),
                        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surface),
                        cornerRadius = cornerRadius,
                        pressFeedbackType = PressFeedbackType.None,
                        showIndication = false,
                        onClick = {}
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // 列表按时间倒序（最新在前）展示
                            historyItems.asReversed().forEachIndexed { index, history ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showHistoryDialog.value = true }
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = LocalTimeFormatter.formatLocalDateTime(history.lastModificationTime),
                                            fontSize = 14.sp,
                                            color = MiuixTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = history.title.ifBlank { "（无标题）" },
                                            fontSize = 12.sp,
                                            color = MiuixTheme.colorScheme.onSurfaceSecondary,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                }
                                if (index < historyItems.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 14.dp),
                                        thickness = 0.5.dp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 嵌入模式与独立模式共用 Scaffold：保留顶栏功能区（编辑/删除等）
    // 嵌入模式用紧凑 SmallTopAppBar（由 TopAppBar 自行处理状态栏 insets，缓解高度压缩）
    Scaffold(
        popupHost = {},
        topBar = {
            if (isEmbedded) {
                SmallTopAppBar(
                    title = selectedEntry?.title ?: "密码详情",
                    actions = {
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
                                IconButton(
                                    onClick = {
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
                                ) {
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
                                IconButton(
                                    onClick = {
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
                                ) {
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
                )
            }
        }
    ) { paddingValues ->
        PasswordDetailContent(
            modifier = Modifier.padding(paddingValues)
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

@Composable
private fun AdditionalFieldRow(
    item: RemainingKeyValue,
    onCopy: () -> Unit
) {
    // 受保护字段默认以掩码展示，仅在用户主动切换后显示原值
    var showProtectedValue by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val displayName = displayFieldName(item.fieldName)
            Text(
                text = if (item.isProtected) "$displayName（已保护）" else displayName,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface
            )
            Text(
                text = if (item.isProtected && !showProtectedValue) "••••••" else item.rawValue,
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )
        }
        if (item.isProtected) {
            IconButton(onClick = { showProtectedValue = !showProtectedValue }) {
                Icon(
                    imageVector = if (showProtectedValue) MiuixIcons.Hide else MiuixIcons.Show,
                    contentDescription = if (showProtectedValue) "隐藏受保护字段" else "显示受保护字段",
                    tint = MiuixTheme.colorScheme.onSurfaceSecondary
                )
            }
        }
        IconButton(onClick = onCopy) {
            Icon(
                imageVector = MiuixIcons.Copy,
                contentDescription = "复制 ${item.fieldName}"
            )
        }
    }
}

/**
 * 模板装饰字段名（如 [SSID]）去除括号后展示，普通字段名原样返回。
 */
private fun displayFieldName(name: String): String {
    return if (name.startsWith("[") && name.endsWith("]")) {
        name.removePrefix("[").removeSuffix("]")
    } else {
        name
    }
}

/**
 * Passkey 信息行（依赖方/用户名/凭据 ID 展示）。
 */
@Composable
private fun PasskeyInfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurfaceSecondary,
            modifier = Modifier.width(72.dp)
        )
        Text(
            text = value,
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 通过系统浏览器打开网址，无 scheme 时自动补充 https://。
 */
private fun openUrl(context: Context, rawUrl: String) {
    runCatching {
        val schemePattern = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")
        val url = if (schemePattern.containsMatchIn(rawUrl)) rawUrl else "https://$rawUrl"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(Intent.createChooser(intent, "打开网址"))
        } else {
            ToastUtils.showShortToast(context, "没有可打开该网址的应用")
        }
    }.onFailure {
        ToastUtils.showShortToast(context, "网址无法打开")
    }
}

/**
 * 将逗号/分号分隔的标签文本解析为去重后的标签列表。
 */
private fun parseTagsText(text: String): List<String> {
    return text.split(',', ';')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
}

private fun isOtpField(item: RemainingKeyValue): Boolean {
    return item.valueType == RemainingValueType.OTP
        || item.fieldName.equals("otp", ignoreCase = true)
        || item.rawValue.startsWith("otpauth://", ignoreCase = true)
}

private fun copySensitiveToClipboard(
    context: Context,
    label: String,
    content: String
) {
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clipData = ClipData.newPlainText(label, content)
    val sensitiveExtras = PersistableBundle().apply {
        val sensitiveKey = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ClipDescription.EXTRA_IS_SENSITIVE
        } else {
            "android.content.extra.IS_SENSITIVE"
        }
        putBoolean(sensitiveKey, true)
    }
    clipData.description.extras = sensitiveExtras
    clipboardManager.setPrimaryClip(clipData)
}

@Composable
private fun AttachmentViewRow(
    name: String,
    sizeText: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurface
            )
            Text(
                text = sizeText,
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )
        }
    }
}

@Composable
private fun AttachmentEditRow(
    name: String,
    sizeText: String?,
    canDelete: Boolean,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurface
            )
            if (sizeText != null) {
                Text(
                    text = sizeText,
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary
                )
            }
        }
        if (canDelete) {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = MiuixIcons.Delete,
                    contentDescription = "删除附件"
                )
            }
        }
    }
}

/**
 * 将已写入缓存目录的附件通过系统查看器打开，便于用户查看。
 *
 * MIME 通过文件扩展名推断（[MimeTypeMap]）而非依赖 FileProvider 的 getType（其常返回 null），
 * 避免兜底为 application/octet-stream 导致选择器无可用处理器而抛出 ActivityNotFoundException。
 */
private fun openAttachment(context: Context, name: String, file: java.io.File) {
    runCatching {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider",
            file
        )
        val ext = name.substringAfterLast('.', "").lowercase()
        val mime = if (ext.isNotEmpty()) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
                ?: "application/octet-stream"
        } else {
            "application/octet-stream"
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(Intent.createChooser(intent, "打开附件"))
        } else {
            ToastUtils.showShortToast(context, "没有可打开该类型附件的应用")
        }
    }.onFailure {
        ToastUtils.showShortToast(context, "打开附件失败")
    }
}
