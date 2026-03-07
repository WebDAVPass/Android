package xzynine.WebDAVPass.Android.ui.Screen

import android.app.Activity
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.CloudFill
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.UploadCloud
import androidx.fragment.app.FragmentActivity
import xzynine.WebDAVPass.Android.biometric.BiometricKeyStoreManager
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
    val currentLibraryState by tokenViewModel.currentLibrary.collectAsState()
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
    var manualUnlockClockMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    val isSelectionMode = remember { mutableStateOf(false) }
    val selectedHistoryIds = remember { mutableStateMapOf<String, Boolean>() }
    val showDeleteDialog = remember { mutableStateOf(false) }
    var pendingDeviceCredentialLibrary by remember { mutableStateOf<LibraryContext?>(null) }
    var pendingDeviceCredentialMasterPassword by remember { mutableStateOf<String?>(null) }
    var pendingDeviceCredentialFlow by remember { mutableStateOf("") }
    var pendingDeviceCredentialAuthMode by remember {
        mutableStateOf(TokenViewModel.AUTO_UNLOCK_AUTH_MODE_DEFAULT)
    }

    val deviceCredentialLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            val pendingLibrary = pendingDeviceCredentialLibrary
            val pendingPassword = pendingDeviceCredentialMasterPassword
            val pendingFlow = pendingDeviceCredentialFlow
            val pendingMode = pendingDeviceCredentialAuthMode

            pendingDeviceCredentialLibrary = null
            pendingDeviceCredentialMasterPassword = null
            pendingDeviceCredentialFlow = ""
            pendingDeviceCredentialAuthMode = TokenViewModel.AUTO_UNLOCK_AUTH_MODE_DEFAULT

            if (pendingLibrary == null) {
                return@rememberLauncherForActivityResult
            }

            if (result.resultCode != Activity.RESULT_OK) {
                if (pendingFlow == "enroll") {
                    tokenViewModel.setAutoUnlockEnrollDismissed(pendingLibrary)
                }
                if (pendingFlow == "unlock") {
                    pendingUnlockLibrary = pendingLibrary
                    inlineUnlockPassword = ""
                    showInlinePassword = false
                    inlineUnlockFocusNonce++
                }
                if (pendingFlow == "reactivate") {
                    ToastUtils.showShortToast(context, "身份验证失败")
                }
                return@rememberLauncherForActivityResult
            }

            if (pendingFlow == "enroll") {
                if (pendingPassword.isNullOrBlank()) {
                    return@rememberLauncherForActivityResult
                }
                val targetLibrary = currentLibraryState?.takeIf { it.id == pendingLibrary.id } ?: pendingLibrary
                val cipher = tokenViewModel.getCipherForEnrollment(targetLibrary)
                if (cipher == null) {
                    return@rememberLauncherForActivityResult
                }
                val enabled = tokenViewModel.enableAutoUnlock(
                    library = targetLibrary,
                    cipher = cipher,
                    masterPassword = pendingPassword,
                    authMode = pendingMode
                )
                if (!enabled) {
                    ToastUtils.showShortToast(context, "自动解锁启用失败，可在设置中重试")
                }
                return@rememberLauncherForActivityResult
            }

            if (pendingFlow == "unlock") {
                coroutineScope.launch {
                    val targetLibrary = currentLibraryState?.takeIf { it.id == pendingLibrary.id } ?: pendingLibrary
                    val authCipher = tokenViewModel.getCipherForAutoUnlock(targetLibrary)
                    if (authCipher != null && tokenViewModel.unlockWithBiometric(targetLibrary, authCipher)) {
                        val cleared = tokenViewModel.applyPostCredentialUnlockPolicy(targetLibrary)
                        if (cleared) {
                            ToastUtils.showShortToast(context, "已超过48小时，自动解锁已失效，请输入主密码并再次验证恢复")
                        }
                        pendingUnlockLibrary = null
                        inlineUnlockPassword = ""
                        showInlinePassword = false
                        inlineUnlockLoading = false
                        keyboardController?.hide()
                        onEnterLibrary()
                    } else {
                        ToastUtils.showShortToast(context, "自动解锁失败，请手动输入密码")
                        pendingUnlockLibrary = targetLibrary
                        inlineUnlockPassword = ""
                        showInlinePassword = false
                        inlineUnlockFocusNonce++
                    }
                }
                return@rememberLauncherForActivityResult
            }

            if (pendingFlow == "reactivate") {
                if (pendingPassword.isNullOrBlank()) {
                    return@rememberLauncherForActivityResult
                }
                coroutineScope.launch {
                    inlineUnlockLoading = true
                    val unlocked = tokenViewModel.unlockCurrentLibrary(
                        masterPassword = pendingPassword,
                        isManualUnlock = true
                    )
                    inlineUnlockLoading = false
                    if (!unlocked) {
                        val message = tokenViewModel.getLastUnlockErrorMessage()
                            ?: "解锁失败：主密码不正确或文件无效"
                        ToastUtils.showShortToast(context, message)
                        return@launch
                    }

                    val targetLibrary = currentLibraryState?.takeIf { it.id == pendingLibrary.id } ?: pendingLibrary
                    val cipher = tokenViewModel.getCipherForEnrollment(targetLibrary)
                    if (cipher == null) {
                        ToastUtils.showShortToast(context, "自动解锁恢复失败，请重试")
                        return@launch
                    }
                    val enabled = tokenViewModel.enableAutoUnlock(
                        library = targetLibrary,
                        cipher = cipher,
                        masterPassword = pendingPassword,
                        authMode = pendingMode
                    )
                    if (!enabled) {
                        ToastUtils.showShortToast(context, "自动解锁恢复失败，请重试")
                        return@launch
                    }

                    pendingUnlockLibrary = null
                    inlineUnlockPassword = ""
                    showInlinePassword = false
                    inlineUnlockLoading = false
                    keyboardController?.hide()
                    onEnterLibrary()
                }
            }
        }
    )

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
        manualUnlockClockMillis = System.currentTimeMillis()
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

    /**
     * 手动解锁成功后触发首次自动解锁引导。
     */
    fun tryEnrollAutoUnlockAfterManualUnlock(
        library: LibraryContext,
        masterPassword: String,
        forcePrompt: Boolean = false
    ) {
        if (context !is FragmentActivity) {
            return
        }

        val targetLibrary = currentLibraryState?.takeIf { it.id == library.id } ?: library
        if (!forcePrompt && !tokenViewModel.shouldPromptAutoUnlockEnroll(targetLibrary)) {
            return
        }

        val authMode = tokenViewModel.normalizeAutoUnlockAuthMode(targetLibrary.autoUnlockAuthMode)
        val cipher = tokenViewModel.getCipherForEnrollment(targetLibrary)
        if (cipher == null) {
            return
        }

        tokenViewModel.biometricKeyStoreManager.authenticate(
            activity = context,
            cipher = cipher,
            title = "启用自动解锁",
            subtitle = "首次解锁成功，验证身份后可下次快速解锁",
            authMode = authMode,
            onSuccess = { authCipher ->
                if (authCipher != null) {
                    val enabled = tokenViewModel.enableAutoUnlock(
                        library = targetLibrary,
                        cipher = authCipher,
                        masterPassword = masterPassword,
                        authMode = authMode
                    )
                    if (!enabled) {
                        ToastUtils.showShortToast(context, "自动解锁启用失败，可在设置中重试")
                    }
                }
            },
            onFailure = { errorCode, _ ->
                var fallbackLaunched = false
                if (errorCode == BiometricKeyStoreManager.ERROR_REQUIRE_DEVICE_CREDENTIAL) {
                    val intent = tokenViewModel.biometricKeyStoreManager.createDeviceCredentialIntent(
                        title = "启用自动解锁",
                        subtitle = "请使用 PIN/图案/密码完成验证"
                    )
                    if (intent != null) {
                        pendingDeviceCredentialLibrary = targetLibrary
                        pendingDeviceCredentialMasterPassword = masterPassword
                        pendingDeviceCredentialFlow = "enroll"
                        pendingDeviceCredentialAuthMode = authMode
                        deviceCredentialLauncher.launch(intent)
                        fallbackLaunched = true
                    }
                }

                if (!fallbackLaunched) {
                    tokenViewModel.setAutoUnlockEnrollDismissed(targetLibrary)
                }
            }
        )
    }

    /**
     * 从内联输入区域触发凭据/生物识别解锁。
     */
    fun launchCredentialUnlockFromInline(libraryContext: LibraryContext) {
        val targetLibrary = resolveLatestLibrary(libraryContext) ?: libraryContext
        val authMode = tokenViewModel.normalizeAutoUnlockAuthMode(targetLibrary.autoUnlockAuthMode)

        // 分支1：输入框有内容 -> 先验证主密码，再验证凭据/生物。
        if (inlineUnlockPassword.isNotBlank()) {
            coroutineScope.launch {
                inlineUnlockLoading = true
                val plainPassword = inlineUnlockPassword
                val verified = tokenViewModel.verifyCurrentLibraryPassword(
                    masterPassword = plainPassword,
                    updateManualTimestamp = true
                )
                inlineUnlockLoading = false
                if (!verified) {
                    inlineUnlockPassword = ""
                    val message = tokenViewModel.getLastUnlockErrorMessage()
                        ?: "解锁失败：主密码不正确或文件无效"
                    ToastUtils.showShortToast(context, message)
                    return@launch
                }

                val unlockedLibrary = resolveLatestLibrary(targetLibrary) ?: targetLibrary
                if (context !is FragmentActivity) {
                    ToastUtils.showShortToast(context, "当前页面无法发起身份验证")
                    return@launch
                }

                val enrollCipher = tokenViewModel.getCipherForEnrollment(unlockedLibrary)
                if (enrollCipher == null) {
                    ToastUtils.showShortToast(context, "自动解锁恢复失败，请重试")
                    return@launch
                }

                tokenViewModel.biometricKeyStoreManager.authenticate(
                    activity = context,
                    cipher = enrollCipher,
                    title = "验证身份",
                    subtitle = "请验证身份以完成解锁",
                    authMode = authMode,
                    onSuccess = { authCipher ->
                        if (authCipher == null) {
                            ToastUtils.showShortToast(context, "身份验证失败")
                            return@authenticate
                        }

                        coroutineScope.launch {
                            inlineUnlockLoading = true
                            val unlocked = tokenViewModel.unlockCurrentLibrary(
                                masterPassword = plainPassword,
                                isManualUnlock = true
                            )
                            inlineUnlockLoading = false
                            if (!unlocked) {
                                val message = tokenViewModel.getLastUnlockErrorMessage()
                                    ?: "解锁失败：主密码不正确或文件无效"
                                ToastUtils.showShortToast(context, message)
                                return@launch
                            }

                            val enabled = tokenViewModel.enableAutoUnlock(
                                library = unlockedLibrary,
                                cipher = authCipher,
                                masterPassword = plainPassword,
                                authMode = authMode
                            )
                            if (!enabled) {
                                ToastUtils.showShortToast(context, "自动解锁恢复失败，请重试")
                                return@launch
                            }

                            clearInlineUnlock()
                            onEnterLibrary()
                        }
                    },
                    onFailure = { errorCode, _ ->
                        var fallbackLaunched = false
                        if (errorCode == BiometricKeyStoreManager.ERROR_REQUIRE_DEVICE_CREDENTIAL) {
                            val intent = tokenViewModel.biometricKeyStoreManager.createDeviceCredentialIntent(
                                title = "验证身份",
                                subtitle = "请使用 PIN/图案/密码完成验证"
                            )
                            if (intent != null) {
                                pendingDeviceCredentialLibrary = unlockedLibrary
                                pendingDeviceCredentialMasterPassword = plainPassword
                                pendingDeviceCredentialFlow = "reactivate"
                                pendingDeviceCredentialAuthMode = authMode
                                deviceCredentialLauncher.launch(intent)
                                fallbackLaunched = true
                            }
                        }
                        if (!fallbackLaunched) {
                            ToastUtils.showShortToast(context, "身份验证失败")
                        }
                    }
                )
            }
            return
        }

        // 分支2：输入框无内容 -> 原始凭据/生物解锁，不重置48小时计时。
        if (!tokenViewModel.isAutoUnlockAvailable(targetLibrary)) {
            ToastUtils.showShortToast(context, "自动解锁不可用，请先输入主密码")
            return
        }

        val cipher = tokenViewModel.getCipherForAutoUnlock(targetLibrary)
        if (cipher == null) {
            ToastUtils.showShortToast(context, "自动解锁不可用，请手动输入主密码")
            return
        }

        if (context !is FragmentActivity) {
            ToastUtils.showShortToast(context, "当前页面无法发起身份验证")
            return
        }

        tokenViewModel.biometricKeyStoreManager.authenticate(
            activity = context,
            cipher = cipher,
            authMode = authMode,
            onSuccess = { authCipher ->
                if (authCipher != null) {
                    coroutineScope.launch {
                        if (tokenViewModel.unlockWithBiometric(targetLibrary, authCipher)) {
                            val cleared = tokenViewModel.applyPostCredentialUnlockPolicy(targetLibrary)
                            if (cleared) {
                                ToastUtils.showShortToast(context, "已超过48小时，自动解锁已失效，请输入主密码并再次验证恢复")
                            }
                            clearInlineUnlock()
                            onEnterLibrary()
                        } else {
                            ToastUtils.showShortToast(context, "自动解锁失败，请手动输入密码")
                        }
                    }
                } else {
                    showInlineUnlock(targetLibrary)
                }
            },
            onFailure = { errorCode, _ ->
                var fallbackLaunched = false
                if (errorCode == BiometricKeyStoreManager.ERROR_REQUIRE_DEVICE_CREDENTIAL) {
                    val intent = tokenViewModel.biometricKeyStoreManager.createDeviceCredentialIntent(
                        title = "验证身份",
                        subtitle = "请使用 PIN/图案/密码解锁"
                    )
                    if (intent != null) {
                        pendingDeviceCredentialLibrary = targetLibrary
                        pendingDeviceCredentialMasterPassword = null
                        pendingDeviceCredentialFlow = "unlock"
                        pendingDeviceCredentialAuthMode = authMode
                        deviceCredentialLauncher.launch(intent)
                        fallbackLaunched = true
                    }
                }
                if (!fallbackLaunched) {
                    ToastUtils.showShortToast(context, "身份验证失败")
                    showInlineUnlock(targetLibrary)
                }
            }
        )
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

    LaunchedEffect(pendingUnlockLibrary?.id) {
        if (pendingUnlockLibrary == null) {
            return@LaunchedEffect
        }
        manualUnlockClockMillis = System.currentTimeMillis()
        while (true) {
            delay(60_000L)
            manualUnlockClockMillis = System.currentTimeMillis()
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
                val plainPassword = pendingCreateMasterPassword
                val unlockOk = tokenViewModel.unlockCurrentLibrary(plainPassword)
                pendingCreateMasterPassword = ""
                if (unlockOk) {
                    tryEnrollAutoUnlockAfterManualUnlock(item, plainPassword)
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
                        IconButton(
                            onClick = {
                                if (selectedHistoryIds.isNotEmpty()) {
                                    showDeleteDialog.value = true
                                }
                            }
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Delete,
                                contentDescription = "删除"
                            )
                        }
                        IconButton(
                            onClick = {
                                clearSelectionMode()
                            }
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Close,
                                contentDescription = "取消选择"
                            )
                        }
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
                resolveLatestLibrary(pendingUnlockLibrary)?.let { unlockLibrary ->
                    val autoUnlockAvailable = tokenViewModel.isAutoUnlockAvailable(unlockLibrary)
                    val autoUnlockInvalidated = tokenViewModel.isAutoUnlockInvalidated(unlockLibrary)
                    val isManualWindowEnabled = tokenViewModel.isManualUnlockWindowEnabled(unlockLibrary)
                    val manualWindowRemaining = tokenViewModel.getManualUnlockWindowRemainingMillis(
                        library = unlockLibrary,
                        nowMillis = manualUnlockClockMillis
                    )
                    Text(text = "解锁: ${unlockLibrary.displayName}")
                    Text(
                        text = when {
                            autoUnlockInvalidated -> "自动解锁状态：已失效（需主密码+认证恢复）"
                            !isManualWindowEnabled -> "48小时主密码校验：已关闭"
                            manualWindowRemaining == null -> "48小时主密码校验：不可用"
                            manualWindowRemaining <= 0L -> "48小时主密码校验：已到期（本次凭据解锁后将标记失效）"
                            else -> "48小时主密码校验剩余：${tokenViewModel.formatRemainingHoursMinutes(manualWindowRemaining)}"
                        },
                        fontSize = 12.sp
                    )

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
                                        val unlockedLibrary = resolveLatestLibrary(unlockLibrary)
                                            ?: return@launch
                                        val plainPassword = inlineUnlockPassword
                                        clearInlineUnlock()
                                        tryEnrollAutoUnlockAfterManualUnlock(unlockedLibrary, plainPassword)
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

                    TextButton(
                        text = if (autoUnlockAvailable) {
                            "使用凭据/生物识别解锁"
                        } else if (autoUnlockInvalidated) {
                            "使用凭据/生物识别解锁（已失效，输入主密码后恢复）"
                        } else {
                            "使用凭据/生物识别解锁（输入主密码后可启用）"
                        },
                        onClick = {
                            launchCredentialUnlockFromInline(unlockLibrary)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !inlineUnlockLoading
                    )
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
                            if (isSelectionMode.value) {
                                setSelection(item, !selectedHistoryIds.containsKey(item.id))
                                return@SelectableEntryCard
                            }
                            coroutineScope.launch {
                                tokenViewModel.switchLibrary(item.id)
                                val selectedLibrary = currentLibraryState?.takeIf { it.id == item.id } ?: item

                                showInlineUnlock(selectedLibrary)
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
                        tryEnrollAutoUnlockAfterManualUnlock(library, password)
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



