package xzynine.WebDAVPass.Android.ui.Screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.window.WindowDialog
import xzylib.base.util.ToastUtils
import xzynine.WebDAVPass.Android.data.AppPackageField.normalizeAppPackage
import xzynine.WebDAVPass.Android.data.EditableAttachmentDraft
import xzynine.WebDAVPass.Android.data.PasswordEntryEditDraft
import xzynine.WebDAVPass.Android.ui.Dialog.IconPickerDialog
import xzynine.WebDAVPass.Android.ui.component.EntryIcon

/** 附件导入大小上限（与仓库 SMALL_BINARY_SIZE 一致），防止无界读入内存导致 OOM。 */
private const val MAX_ATTACHMENT_BYTES = 1024 * 1024

/**
 * 条目编辑对话框（用于新增）。
 *
 * 支持标题/账号/密码/网站/应用（安装包名）/备注、自定义字段、附件、过期时间与图标。
 *
 * 表单内容整体可滚动：miuix `WindowDialog` 的内容容器不带滚动，
 * 超出可用高度后尾部子项（含底部操作行）会被测量为 0 高度而"消失"。
 */
@Composable
fun PasswordEntryEditorDialog(
    title: String,
    show: androidx.compose.runtime.MutableState<Boolean>,
    initialDraft: PasswordEntryEditDraft = PasswordEntryEditDraft(title = "", username = "", password = "", url = "", notes = ""),
    onDismiss: () -> Unit,
    onConfirm: (PasswordEntryEditDraft) -> Unit,
) {
    val context = LocalContext.current
    val formScrollState = rememberScrollState()

    var entryTitle by remember { mutableStateOf(initialDraft.title) }
    var entryUsername by remember { mutableStateOf(initialDraft.username) }
    var entryPassword by remember { mutableStateOf(initialDraft.password) }
    var entryUrl by remember { mutableStateOf(initialDraft.url) }
    var entryNotes by remember { mutableStateOf(initialDraft.notes) }
    var entryTagsText by remember { mutableStateOf(initialDraft.tags.joinToString(", ")) }
    var entryAppPackage by remember { mutableStateOf(initialDraft.appPackageName) }
    var customFields by remember { mutableStateOf(initialDraft.customFields) }
    var attachments by remember { mutableStateOf(initialDraft.attachments.map { it.copy() }) }
    var expiryTime by remember { mutableStateOf(initialDraft.expiryTime) }
    var iconStandardId by remember { mutableStateOf(initialDraft.iconStandardId) }
    var customIconUuid by remember { mutableStateOf(initialDraft.customIconUuid) }
    var newCustomIconBytes by remember { mutableStateOf<ByteArray?>(initialDraft.newCustomIconBytes) }
    var showIconPicker by remember { mutableStateOf(false) }

    LaunchedEffect(show.value) {
        if (show.value) {
            entryTitle = initialDraft.title
            entryUsername = initialDraft.username
            entryPassword = initialDraft.password
            entryUrl = initialDraft.url
            entryNotes = initialDraft.notes
            entryTagsText = initialDraft.tags.joinToString(", ")
            entryAppPackage = initialDraft.appPackageName
            customFields = initialDraft.customFields
            attachments = initialDraft.attachments.map { it.copy() }
            expiryTime = initialDraft.expiryTime
            iconStandardId = initialDraft.iconStandardId
            customIconUuid = initialDraft.customIconUuid
            newCustomIconBytes = initialDraft.newCustomIconBytes
            showIconPicker = false
            // 每次打开回到表单顶部，避免沿用上一次的滚动位置
            formScrollState.scrollTo(0)
        }
    }

    val attachmentPicker =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent(),
        ) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            val declaredSize = DocumentFile.fromSingleUri(context, uri)?.length() ?: -1L
            if (declaredSize > MAX_ATTACHMENT_BYTES) {
                ToastUtils.showShortToast(context, "附件过大（上限 ${MAX_ATTACHMENT_BYTES / 1024 / 1024} MiB），已取消")
                return@rememberLauncherForActivityResult
            }
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    // 增量读取并强制上限：content provider 可能返回 -1 的声明大小，故真正限制在这里施加
                    val buffer = java.io.ByteArrayOutputStream(8 * 1024)
                    val chunk = ByteArray(8 * 1024)
                    var total = 0
                    while (true) {
                        val read = input.read(chunk)
                        if (read < 0) break
                        total += read
                        if (total > MAX_ATTACHMENT_BYTES) {
                            throw IllegalStateException("附件过大")
                        }
                        buffer.write(chunk, 0, read)
                    }
                    val bytes = buffer.toByteArray()
                    val name =
                        uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
                            ?: "attachment_${System.currentTimeMillis()}"
                    attachments = attachments + EditableAttachmentDraft(name = name, data = bytes, isNew = true)
                }
            }.onFailure {
                ToastUtils.showShortToast(context, "附件过大或读取失败，已取消")
            }
        }

    WindowDialog(
        title = title,
        show = show.value,
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(formScrollState),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TextField(
                value = entryTitle,
                onValueChange = { entryTitle = it },
                label = "标题",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            TextField(
                value = entryUsername,
                onValueChange = { entryUsername = it },
                label = "账号",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            TextField(
                value = entryPassword,
                onValueChange = { entryPassword = it },
                label = "密码",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            TextField(
                value = entryUrl,
                onValueChange = { entryUrl = it },
                label = "网站",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            // 应用（安装包名）默认空白：可手工键入，也可从本机应用列表选择；
            // 写入包名且条目尚无自定义图标时，自动把应用图标作为条目图标
            AppPackageFieldEditor(
                value = entryAppPackage,
                onValueChange = { entryAppPackage = it },
                hasCustomIcon = newCustomIconBytes != null,
                onAppIconPicked = { newCustomIconBytes = it },
            )
            TextField(
                value = entryNotes,
                onValueChange = { entryNotes = it },
                label = "备注",
                modifier = Modifier.fillMaxWidth(),
            )
            TextField(
                value = entryTagsText,
                onValueChange = { entryTagsText = it },
                label = "标签（逗号或分号分隔）",
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { showIconPicker = true },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EntryIcon(
                        customIconBytes = newCustomIconBytes,
                        standardIconId = iconStandardId,
                        primary = null,
                        secondary = null,
                        modifier = Modifier.size(36.dp),
                    )
                    Text(
                        text = "  选择图标",
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.primary,
                    )
                }
                Icon(
                    imageVector = MiuixIcons.Edit,
                    contentDescription = "选择图标",
                    tint = MiuixTheme.colorScheme.onSurfaceSecondary,
                )
            }

            SmallTitle(text = "自定义字段")
            CustomFieldsEditor(fields = customFields) { customFields = it }

            ExpiryTimeEditor(value = expiryTime) { expiryTime = it }

            SmallTitle(text = "附件 (${attachments.count { !it.removed }})")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surface),
                cornerRadius = 12.dp,
                pressFeedbackType = PressFeedbackType.None,
                showIndication = false,
                onClick = {},
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    attachments.filter { !it.removed }.forEachIndexed { index, att ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = att.name, fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurface)
                                if (att.isNew) {
                                    Text(
                                        text = formatFileSize((att.data?.size ?: 0).toLong()) + " · 新增",
                                        fontSize = 12.sp,
                                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                                    )
                                }
                            }
                            IconButton(onClick = {
                                attachments =
                                    attachments.map {
                                        if (it === att) it.copy(removed = true) else it
                                    }
                            }) {
                                Icon(imageVector = MiuixIcons.Delete, contentDescription = "删除附件")
                            }
                        }
                        if (index < attachments.filter { !it.removed }.lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp), thickness = 0.5.dp)
                        }
                    }
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { attachmentPicker.launch("*/*") }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(imageVector = MiuixIcons.Edit, contentDescription = "添加附件", tint = MiuixTheme.colorScheme.primary)
                        Text(
                            text = " 添加附件",
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = "取消",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        onConfirm(
                            initialDraft.copy(
                                title = entryTitle.trim(),
                                username = entryUsername.trim(),
                                password = entryPassword,
                                url = entryUrl.trim(),
                                notes = entryNotes,
                                tags =
                                    entryTagsText
                                        .split(',', ';', '，', '；')
                                        .map { it.trim() }
                                        .filter { it.isNotEmpty() }
                                        .distinct(),
                                customFields = customFields,
                                attachments = attachments,
                                expiryTime = expiryTime,
                                customIconUuid = customIconUuid,
                                iconStandardId = iconStandardId,
                                newCustomIconBytes = newCustomIconBytes,
                                appPackageName = normalizeAppPackage(entryAppPackage),
                            ),
                        )
                    },
                    enabled = entryTitle.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = MiuixIcons.Ok,
                        contentDescription = null,
                    )
                    Text(
                        text = "保存",
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        }
    }

    if (showIconPicker) {
        IconPickerDialog(
            show = showIconPicker,
            currentStandardIconId = iconStandardId,
            currentCustomIconBytes = newCustomIconBytes,
            iconPrimary = entryTitle,
            iconSecondary = entryUsername,
            onDismiss = { showIconPicker = false },
            onPick = { standardId, bytes ->
                iconStandardId = standardId ?: 0
                customIconUuid = null
                newCustomIconBytes = bytes
                showIconPicker = false
            },
        )
    }
}
