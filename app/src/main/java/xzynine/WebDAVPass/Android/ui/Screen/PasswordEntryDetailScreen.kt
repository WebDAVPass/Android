package xzynine.WebDAVPass.Android.ui.Screen

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.widget.ImageView
import xzylib.base.util.ToastUtils
import androidx.compose.foundation.border
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
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import kotlinx.coroutines.launch
import xzynine.WebDAVPass.Android.data.PasswordEntry
import xzynine.WebDAVPass.Android.data.RemainingKeyValue
import xzynine.WebDAVPass.Android.data.RemainingValueType
import xzynine.WebDAVPass.Android.ui.Dialog.ConfirmationDialog
import xzynine.WebDAVPass.Android.ui.ViewModel.TokenViewModel
import xzynine.WebDAVPass.Android.ui.component.EntryIcon
import xzynine.WebDAVPass.Android.ui.component.TokenCard
import xzynine.WebDAVPass.Android.util.QrCodeUtil

@Composable
fun PasswordEntryDetailScreen(
    tokenViewModel: TokenViewModel,
    entryId: Long,
    onNavigateBack: () -> Unit,
    onDeleted: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val tokens by tokenViewModel.tokens.collectAsState(emptyList())
    val currentTimeMillis by tokenViewModel.currentTimeMillis.collectAsState(System.currentTimeMillis())

    var selectedEntry by remember(entryId) { mutableStateOf<PasswordEntry?>(null) }
    var detailLoaded by rememberSaveable(entryId) { mutableStateOf(false) }
    val showDeleteDialog = remember { mutableStateOf(false) }
    var isEditing by rememberSaveable(entryId) { mutableStateOf(false) }

    var editTitle by rememberSaveable(entryId) { mutableStateOf("") }
    var editUsername by rememberSaveable(entryId) { mutableStateOf("") }
    var editPassword by rememberSaveable(entryId) { mutableStateOf("") }
    var editUrl by rememberSaveable(entryId) { mutableStateOf("") }
    var editNotes by rememberSaveable(entryId) { mutableStateOf("") }

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
    }

    LaunchedEffect(entryId) {
        detailLoaded = false
        selectedEntry = tokenViewModel.loadPasswordEntryDetail(entryId)
        selectedEntry?.let { entry -> syncEditFields(entry) }
        detailLoaded = true
    }

    val selectedToken = tokens.firstOrNull { it.id == entryId }
    val tokenCode by tokenViewModel.getTokenCode(entryId).collectAsState(null)
    var showOtpSecret by rememberSaveable(entryId) { mutableStateOf(false) }
    var showQrCode by rememberSaveable(entryId) { mutableStateOf(false) }
    var isPasswordVisible by rememberSaveable(entryId) { mutableStateOf(false) }

    val cornerRadius = 12.dp
    val cardBorderColor = MiuixTheme.colorScheme.onSurfaceSecondary.copy(alpha = 0.18f)

    Scaffold(
        popupHost = {},
        topBar = {
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
                                                notes = editNotes
                                            )
                                        )
                                        if (updated) {
                                            selectedEntry = tokenViewModel.loadPasswordEntryDetail(entryId)
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
                },
                defaultWindowInsetsPadding = true
            )
        }
    ) { paddingValues ->
        if (!detailLoaded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "加载中...")
            }
            return@Scaffold
        }

        val entry = selectedEntry
        if (entry == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "条目不存在")
            }
            return@Scaffold
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
        }
        val usernameValue = when {
            entry.account.isNotBlank() -> entry.account
            usernameField?.rawValue?.isNotBlank() == true -> usernameField.rawValue
            else -> "--"
        }
        val passwordValue = passwordField?.rawValue?.ifBlank { "--" } ?: "--"
        val urlValue = urlField?.rawValue?.ifBlank { "--" } ?: "--"
        val notesValue = notesField?.rawValue?.ifBlank { "--" } ?: "--"

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
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
                        SuperArrow(
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
                                SuperArrow(
                                    title = "账号",
                                    summary = usernameValue,
                                    modifier = Modifier.fillMaxWidth(),
                                    startAction = {
                                        EntryIcon(
                                            customIconBytes = entry.customIconBytes,
                                            standardIconId = entry.standardIconId,
                                            primary = entry.title,
                                            secondary = entry.account,
                                            modifier = Modifier.padding(end = 16.dp),
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
                            SuperArrow(
                                title = "密码",
                                summary = when {
                                    passwordValue == "--" -> "--"
                                    isPasswordVisible -> passwordValue
                                    else -> "••••••"
                                },
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    if (passwordValue == "--") return@SuperArrow
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
                            SuperArrow(
                                title = "网站",
                                summary = urlValue,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {}
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
                            SuperArrow(
                                title = "备注",
                                summary = notesValue,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {}
                            )
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
                                AdditionalFieldRow(item = item)
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
        }
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
}

@Composable
private fun AdditionalFieldRow(item: RemainingKeyValue) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = item.fieldName,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurface
        )
        Text(
            text = item.rawValue,
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurfaceSecondary
        )
    }
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
