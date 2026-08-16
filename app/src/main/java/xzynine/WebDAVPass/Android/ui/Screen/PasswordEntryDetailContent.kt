package xzynine.WebDAVPass.Android.ui.Screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.ImageView
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import xzynine.WebDAVPass.Android.ui.component.Preference
import xzynine.WebDAVPass.Android.ui.component.PreferenceType
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xzylib.base.util.ToastUtils
import xzynine.WebDAVPass.Android.data.EditableAttachmentDraft
import xzynine.WebDAVPass.Android.data.EditableFieldDraft
import xzynine.WebDAVPass.Android.data.EntryHistoryInfo
import xzynine.WebDAVPass.Android.data.PasswordEntry
import xzynine.WebDAVPass.Android.data.RemainingValueType
import com.kunzisoft.keepass.model.PasskeyEntryFields
import xzynine.WebDAVPass.Android.ui.viewmodel.TokenViewModel
import xzynine.WebDAVPass.Android.ui.component.EntryIcon
import xzynine.WebDAVPass.Android.ui.component.TokenCard
import xzynine.WebDAVPass.Android.util.LocalTimeFormatter
import xzynine.WebDAVPass.Android.util.QrCodeUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import xzynine.WebDAVPass.Android.data.OtpToken
import xzynine.WebDAVPass.Android.data.TokenCode

@Composable
fun PasswordEntryDetailContent(
    modifier: Modifier = Modifier,
    detailLoaded: Boolean,
    selectedEntry: PasswordEntry?,
    selectedToken: OtpToken?,
    tokenCode: TokenCode?,
    currentTimeMillis: Long,
    context: Context,
    isEditing: Boolean,
    editTitle: String,
    onEditTitleChange: (String) -> Unit,
    editUsername: String,
    onEditUsernameChange: (String) -> Unit,
    editPassword: String,
    onEditPasswordChange: (String) -> Unit,
    editUrl: String,
    onEditUrlChange: (String) -> Unit,
    editNotes: String,
    onEditNotesChange: (String) -> Unit,
    editTagsText: String,
    onEditTagsTextChange: (String) -> Unit,
    editAttachments: List<EditableAttachmentDraft>,
    onEditAttachmentsChange: (List<EditableAttachmentDraft>) -> Unit,
    editCustomFields: List<EditableFieldDraft>,
    onEditCustomFieldsChange: (List<EditableFieldDraft>) -> Unit,
    editExpiryTime: Long?,
    onEditExpiryTimeChange: (Long?) -> Unit,
    editIconStandardId: Int,
    editNewCustomIconBytes: ByteArray?,
    showOtpSecret: Boolean,
    onShowOtpSecretChange: (Boolean) -> Unit,
    showQrCode: Boolean,
    onShowQrCodeChange: (Boolean) -> Unit,
    isPasswordVisible: Boolean,
    onPasswordVisibleChange: (Boolean) -> Unit,
    historyItems: List<EntryHistoryInfo>,
    onHistoryClick: () -> Unit,
    onPickIcon: () -> Unit,
    onAddAttachment: () -> Unit,
    entryId: Long,
    tokenViewModel: TokenViewModel,
    coroutineScope: CoroutineScope,
) {
    val cornerRadius = 12.dp
    val cardBorderColor = MiuixTheme.colorScheme.onSurfaceSecondary.copy(alpha = 0.18f)

        if (!detailLoaded) {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "加载中...")
            }
            return
        }

    if (selectedEntry == null) {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "条目不存在")
            }
            return
        }

        val usernameField = selectedEntry.keyValues.firstOrNull { it.fieldName.equals("UserName", ignoreCase = true) }
        val passwordField = selectedEntry.keyValues.firstOrNull {
            it.fieldName.equals("Password", ignoreCase = true) || it.valueType == RemainingValueType.PASSWORD
        }
        val urlField = selectedEntry.keyValues.firstOrNull {
            it.fieldName.equals("URL", ignoreCase = true) || it.valueType == RemainingValueType.URL
        }
        val notesField = selectedEntry.keyValues.firstOrNull { it.fieldName.equals("Notes", ignoreCase = true) }
        val otpFields = selectedEntry.keyValues.filter { isOtpField(it) }
        val otpSecretField = otpFields.firstOrNull()
        val additionalFields = selectedEntry.keyValues.filterNot { item ->
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
        val passkeyValues = selectedEntry.keyValues.associate { it.fieldName to it.rawValue }
        val passkeyRelyingParty = passkeyValues[PasskeyEntryFields.FIELD_RELYING_PARTY]
        val passkeyUsername = passkeyValues[PasskeyEntryFields.FIELD_USERNAME]
        val passkeyCredentialId = passkeyValues[PasskeyEntryFields.FIELD_CREDENTIAL_ID]
        val hasPasskey = passkeyValues.containsKey(PasskeyEntryFields.FIELD_CREDENTIAL_ID)
        val usernameValue = when {
            selectedEntry.account.isNotBlank() -> selectedEntry.account
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
                                onShowOtpSecretChange(!showOtpSecret)
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
                                IconButton(onClick = { onShowQrCodeChange(!showQrCode) }) {
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

                                if (showQrCode) {
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
                                onValueChange = { onEditTitleChange(it) },
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
                                    onValueChange = { onEditUsernameChange(it) },
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
                                            customIconBytes = selectedEntry.customIconBytes,
                                            standardIconId = selectedEntry.standardIconId,
                                            primary = selectedEntry.title,
                                            secondary = selectedEntry.account,
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
                                onValueChange = { onEditPasswordChange(it) },
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
                                    onPasswordVisibleChange(!isPasswordVisible)
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
                                onValueChange = { onEditUrlChange(it) },
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
                                onValueChange = { onEditNotesChange(it) },
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
                                onValueChange = { onEditTagsTextChange(it) },
                                label = "标签（逗号分隔）",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPickIcon() }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    EntryIcon(
                                        customIconBytes = editNewCustomIconBytes ?: selectedEntry.customIconBytes,
                                        standardIconId = editIconStandardId,
                                        primary = selectedEntry.title,
                                        secondary = selectedEntry.account,
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
                        } else if (selectedEntry.tags.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                selectedEntry.tags.forEach { tag ->
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
                                onValueChange = { onEditExpiryTimeChange(it) }
                            )
                        } else if (selectedEntry.expiryTime != null) {
                            val expired = selectedEntry.isExpired
                            Preference(
                            type = PreferenceType.Arrow,
                                title = "过期时间",
                                summary = formatExpiry(selectedEntry.expiryTime, expired),
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
                        onFieldsChange = { onEditCustomFieldsChange(it) }
                    )
                }
            }

            // 附件
            item {
                val attachmentCount = if (isEditing) {
                    editAttachments.count { !it.removed }
                } else {
                    selectedEntry.attachments.size
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
                        if (selectedEntry.attachments.isEmpty() && !isEditing) {
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
                                        onEditAttachmentsChange(editAttachments.map {
                                            if (it === att) it.copy(removed = true) else it
                                        })
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
                            selectedEntry.attachments.forEachIndexed { index, att ->
                                AttachmentViewRow(
                                    name = att.name,
                                    sizeText = formatFileSize(att.size),
                                    onClick = {
                                        coroutineScope.launch {
                                            val dir = java.io.File(context.cacheDir, "attachments").apply { mkdirs() }
                                            val file = dir.resolve(att.name.replace("/", "_"))
                                            // 解密写入放 IO 线程；先删同名旧文件，避免附件更新后残留旧明文
                                            val ok = withContext(Dispatchers.IO) {
                                                runCatching {
                                                    file.delete()
                                                    file.outputStream().use { out ->
                                                        tokenViewModel.copyEntryAttachmentTo(entryId, att.name, out)
                                                    }
                                                    true
                                                }.getOrElse { false }
                                            }
                                            if (ok) {
                                                openAttachment(context, att.name, file)
                                            } else {
                                                ToastUtils.showShortToast(context, "读取附件失败或附件过大")
                                            }
                                        }
                                    }
                                )
                                if (index < selectedEntry.attachments.lastIndex) {
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
                                    .clickable { onAddAttachment() }
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
                                        .clickable { onHistoryClick() }
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
