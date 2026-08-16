package xzynine.WebDAVPass.Android.ui.Screen

import android.app.Activity
import android.net.Uri

import xzylib.base.util.ToastUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Hide
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Show
import top.yukonga.miuix.kmp.theme.MiuixTheme
import xzynine.WebDAVPass.Android.R
import xzynine.WebDAVPass.Android.biometric.BiometricKeyStoreManager
import xzynine.WebDAVPass.Android.data.LibraryContext
import xzynine.WebDAVPass.Android.ui.ViewModel.AutoUnlockViewModel
import xzynine.WebDAVPass.Android.ui.viewmodel.TokenViewModel
import xzynine.WebDAVPass.Android.util.resolveDisplayName
import java.io.File

/** 密钥文件大小上限（1 MiB），与 CreateMasterPasswordDialog 保持一致。 */
private const val MAX_KEY_FILE_BYTES = 1024 * 1024

/**
 * 内联解锁面板：主密码输入 + 密钥文件选择 + 凭据/生物识别解锁。
 *
 * 由 WelcomeScreen（历史库解锁）与 LockedScreen（已锁定页解锁）共用，
 * 所有解锁状态在内部管理，随组件移除自动清除。
 *
 * @param library 待解锁的库
 * @param onUnlockSuccess 解锁成功回调（进入主界面）
 * @param onDismiss 取消/退出内联解锁回调
 */
@Composable
fun InlineUnlockPanel(
    tokenViewModel: TokenViewModel,
    library: LibraryContext,
    onUnlockSuccess: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 解锁目标变化时整体重置内联解锁状态（密码/密钥文件/加载等）
    key(library.id) {
        InlineUnlockPanelContent(
            tokenViewModel = tokenViewModel,
            library = library,
            onUnlockSuccess = onUnlockSuccess,
            onDismiss = onDismiss,
            modifier = modifier
        )
    }
}

@Composable
private fun InlineUnlockPanelContent(
    tokenViewModel: TokenViewModel,
    library: LibraryContext,
    onUnlockSuccess: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    val inlineUnlockFocusRequester = remember { FocusRequester() }
    val currentLibraryState by tokenViewModel.libraryViewModel.currentLibrary.collectAsState()
    val history by tokenViewModel.libraryViewModel.libraryHistory.collectAsState()

    var inlineUnlockPassword by remember { mutableStateOf("") }
    var showInlinePassword by remember { mutableStateOf(false) }
    var inlineUnlockLoading by remember { mutableStateOf(false) }
    var inlineUnlockFocusNonce by remember { mutableStateOf(0) }
    var inlineKeyFileName by remember { mutableStateOf("") }
    var inlineKeyFileData by remember { mutableStateOf<ByteArray?>(null) }
    var inlineKeyFileLoading by remember { mutableStateOf(false) }
    var manualUnlockClockMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var pendingDeviceCredentialLibrary by remember { mutableStateOf<LibraryContext?>(null) }
    var pendingDeviceCredentialMasterPassword by remember { mutableStateOf<String?>(null) }
    var pendingDeviceCredentialFlow by remember { mutableStateOf("") }
    var pendingDeviceCredentialAuthMode by remember {
        mutableStateOf(AutoUnlockViewModel.AUTO_UNLOCK_AUTH_MODE_DEFAULT)
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
            pendingDeviceCredentialAuthMode = AutoUnlockViewModel.AUTO_UNLOCK_AUTH_MODE_DEFAULT

            if (pendingLibrary == null) {
                return@rememberLauncherForActivityResult
            }

            if (result.resultCode != Activity.RESULT_OK) {
                if (pendingFlow == "enroll") {
                    tokenViewModel.autoUnlockViewModel.setAutoUnlockEnrollDismissed(pendingLibrary) {
                        tokenViewModel.libraryViewModel.persistLibraryMetadata(it)
                    }
                }
                if (pendingFlow == "unlock") {
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
                val cipher = tokenViewModel.autoUnlockViewModel.getCipherForEnrollment(targetLibrary)
                if (cipher == null) {
                    return@rememberLauncherForActivityResult
                }
                val enabled = tokenViewModel.autoUnlockViewModel.enableAutoUnlock(
                    library = targetLibrary,
                    cipher = cipher,
                    masterPassword = pendingPassword,
                    resolveLibrary = { tokenViewModel.libraryViewModel.resolveLibrarySnapshot(it) },
                    onPersist = { tokenViewModel.libraryViewModel.persistLibraryMetadata(it) },
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
                    val authCipher = tokenViewModel.autoUnlockViewModel.getCipherForAutoUnlock(targetLibrary) {
                        tokenViewModel.autoUnlockViewModel.invalidateAutoUnlock(it) { lib ->
                            tokenViewModel.libraryViewModel.persistLibraryMetadata(lib)
                        }
                    }
                    if (authCipher != null && tokenViewModel.unlockWithBiometric(targetLibrary, authCipher)) {
                        val cleared = tokenViewModel.applyPostCredentialUnlockPolicy(targetLibrary)
                        if (cleared) {
                            ToastUtils.showShortToast(context, "已超过48小时，自动解锁已失效，请输入主密码并再次验证恢复")
                        }
                        inlineUnlockPassword = ""
                        showInlinePassword = false
                        inlineUnlockLoading = false
                        keyboardController?.hide()
                        onUnlockSuccess()
                    } else {
                        ToastUtils.showShortToast(context, "自动解锁失败，请手动输入密码")
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
                        isManualUnlock = true,
                        keyFileData = inlineKeyFileData
                    )
                    inlineUnlockLoading = false
                    if (!unlocked) {
                        val message = tokenViewModel.libraryViewModel.getLastUnlockErrorMessage()
                            ?: "解锁失败：主密码不正确或文件无效"
                        ToastUtils.showShortToast(context, message)
                        return@launch
                    }

                    val targetLibrary = currentLibraryState?.takeIf { it.id == pendingLibrary.id } ?: pendingLibrary
                    val cipher = tokenViewModel.autoUnlockViewModel.getCipherForEnrollment(targetLibrary)
                    if (cipher == null) {
                        ToastUtils.showShortToast(context, "自动解锁恢复失败，请重试")
                        return@launch
                    }
                    val enabled = tokenViewModel.autoUnlockViewModel.enableAutoUnlock(
                        library = targetLibrary,
                        cipher = cipher,
                        masterPassword = pendingPassword,
                        resolveLibrary = { tokenViewModel.libraryViewModel.resolveLibrarySnapshot(it) },
                        onPersist = { tokenViewModel.libraryViewModel.persistLibraryMetadata(it) },
                        authMode = pendingMode
                    )
                    if (!enabled) {
                        ToastUtils.showShortToast(context, "自动解锁恢复失败，请重试")
                        return@launch
                    }

                    inlineUnlockPassword = ""
                    showInlinePassword = false
                    inlineUnlockLoading = false
                    keyboardController?.hide()
                    onUnlockSuccess()
                }
            }
        }
    )

    val inlineKeyFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            if (uri == null) {
                return@rememberLauncherForActivityResult
            }
            // 先清除上一次选择，避免读取失败时仍显示旧文件名
            inlineKeyFileName = ""
            inlineKeyFileData = null
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val buffer = java.io.ByteArrayOutputStream(8 * 1024)
                    val chunk = ByteArray(8 * 1024)
                    var total = 0
                    while (true) {
                        val read = input.read(chunk)
                        if (read < 0) break
                        total += read
                        if (total > MAX_KEY_FILE_BYTES) {
                            throw IllegalStateException("密钥文件过大")
                        }
                        buffer.write(chunk, 0, read)
                    }
                    val bytes = buffer.toByteArray()
                    if (bytes.isNotEmpty()) {
                        inlineKeyFileName = uri.resolveDisplayName(context, fallbackIfEmpty = "未命名.kdbx")
                        inlineKeyFileData = bytes
                        // 持久化密钥文件 URI：锁定页/下次解锁可自动加载，避免每次手动重新选择
                        val target = currentLibraryState?.takeIf { it.id == library.id } ?: library
                        tokenViewModel.libraryViewModel.persistLibraryMetadata(
                            target.copy(keyFileUri = uri.toString())
                        )
                    }
                } ?: throw IllegalStateException("无法读取所选文件")
            }.onFailure {
                ToastUtils.showShortToast(context, "密钥文件读取失败：${it.message ?: "未知错误"}")
            }
        }
    )

    /**
     * 清理内联解锁输入状态。
     */
    fun clearInlineUnlock() {
        inlineUnlockPassword = ""
        showInlinePassword = false
        inlineUnlockLoading = false
        inlineKeyFileName = ""
        inlineKeyFileData = null
        keyboardController?.hide()
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
     * 从内联输入区域触发凭据/生物识别解锁。
     */
    fun launchCredentialUnlockFromInline(libraryContext: LibraryContext) {
        val targetLibrary = resolveLatestLibrary(libraryContext) ?: libraryContext
        val authMode = tokenViewModel.autoUnlockViewModel.normalizeAutoUnlockAuthMode(targetLibrary.autoUnlockAuthMode)

        // 分支1：输入框有内容 -> 先验证主密码，再验证凭据/生物。
        if (inlineUnlockPassword.isNotBlank()) {
            coroutineScope.launch {
                inlineUnlockLoading = true
                val plainPassword = inlineUnlockPassword
                val verified = tokenViewModel.verifyCurrentLibraryPassword(
                    masterPassword = plainPassword,
                    updateManualTimestamp = true,
                    keyFileData = inlineKeyFileData
                )
                inlineUnlockLoading = false
                if (!verified) {
                    inlineUnlockPassword = ""
                    val message = tokenViewModel.libraryViewModel.getLastUnlockErrorMessage()
                        ?: "解锁失败：主密码不正确或文件无效"
                    ToastUtils.showShortToast(context, message)
                    return@launch
                }

                val unlockedLibrary = resolveLatestLibrary(targetLibrary) ?: targetLibrary
                if (context !is FragmentActivity) {
                    ToastUtils.showShortToast(context, "当前页面无法发起身份验证")
                    return@launch
                }

                val enrollCipher = tokenViewModel.autoUnlockViewModel.getCipherForEnrollment(unlockedLibrary)
                if (enrollCipher == null) {
                    ToastUtils.showShortToast(context, "自动解锁恢复失败，请重试")
                    return@launch
                }

                tokenViewModel.autoUnlockViewModel.biometricKeyStoreManager.authenticate(
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
                                isManualUnlock = true,
                                keyFileData = inlineKeyFileData
                            )
                            inlineUnlockLoading = false
                            if (!unlocked) {
                                val message = tokenViewModel.libraryViewModel.getLastUnlockErrorMessage()
                                    ?: "解锁失败：主密码不正确或文件无效"
                                ToastUtils.showShortToast(context, message)
                                return@launch
                            }

                            val enabled = tokenViewModel.autoUnlockViewModel.enableAutoUnlock(
                                library = unlockedLibrary,
                                cipher = authCipher,
                                masterPassword = plainPassword,
                                resolveLibrary = { tokenViewModel.libraryViewModel.resolveLibrarySnapshot(it) },
                                onPersist = { tokenViewModel.libraryViewModel.persistLibraryMetadata(it) },
                                authMode = authMode
                            )
                            if (!enabled) {
                                ToastUtils.showShortToast(context, "自动解锁恢复失败，请重试")
                                return@launch
                            }

                            clearInlineUnlock()
                            onUnlockSuccess()
                        }
                    },
                    onFailure = { errorCode, _ ->
                        var fallbackLaunched = false
                        if (errorCode == BiometricKeyStoreManager.ERROR_REQUIRE_DEVICE_CREDENTIAL) {
                            val intent = tokenViewModel.autoUnlockViewModel.biometricKeyStoreManager.createDeviceCredentialIntent(
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
        // 超过 64 小时硬性截止：凭据解锁直接拒绝，只能手动输入主密码。
        if (tokenViewModel.autoUnlockViewModel.isCredentialUnlockExpired(targetLibrary)) {
            ToastUtils.showShortToast(context, "已超过64小时，自动解锁不可用，请手动输入主密码")
            return
        }
        if (!tokenViewModel.autoUnlockViewModel.isAutoUnlockAvailable(targetLibrary)) {
            ToastUtils.showShortToast(context, "自动解锁不可用，请先输入主密码")
            return
        }

        val cipher = tokenViewModel.autoUnlockViewModel.getCipherForAutoUnlock(targetLibrary) {
            tokenViewModel.autoUnlockViewModel.invalidateAutoUnlock(it) { lib ->
                tokenViewModel.libraryViewModel.persistLibraryMetadata(lib)
            }
        }
        if (cipher == null) {
            ToastUtils.showShortToast(context, "自动解锁不可用，请手动输入主密码")
            return
        }

        if (context !is FragmentActivity) {
            ToastUtils.showShortToast(context, "当前页面无法发起身份验证")
            return
        }

        tokenViewModel.autoUnlockViewModel.biometricKeyStoreManager.authenticate(
            activity = context,
            cipher = cipher,
            authMode = authMode,
            libraryFileName = targetLibrary.localPath
                ?.let { path -> runCatching { File(path).name }.getOrNull() },
            onSuccess = { authCipher ->
                if (authCipher != null) {
                    coroutineScope.launch {
                        if (tokenViewModel.unlockWithBiometric(targetLibrary, authCipher)) {
                            val cleared = tokenViewModel.applyPostCredentialUnlockPolicy(targetLibrary)
                            if (cleared) {
                                ToastUtils.showShortToast(context, "已超过48小时，自动解锁已失效，请输入主密码并再次验证恢复")
                            }
                            clearInlineUnlock()
                            onUnlockSuccess()
                        } else {
                            ToastUtils.showShortToast(context, "自动解锁失败，请手动输入密码")
                        }
                    }
                } else {
                    inlineUnlockPassword = ""
                    showInlinePassword = false
                    inlineUnlockFocusNonce++
                }
            },
            onFailure = { errorCode, _ ->
                var fallbackLaunched = false
                if (errorCode == BiometricKeyStoreManager.ERROR_REQUIRE_DEVICE_CREDENTIAL) {
                    val fileName = targetLibrary.localPath
                        ?.let { path -> runCatching { File(path).name }.getOrNull() }
                        .orEmpty()
                    val intent = tokenViewModel.autoUnlockViewModel.biometricKeyStoreManager.createDeviceCredentialIntent(
                        title = if (fileName.isBlank()) "验证身份" else "验证身份并自动解锁$fileName",
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
                    inlineUnlockPassword = ""
                    showInlinePassword = false
                    inlineUnlockFocusNonce++
                }
            }
        )
    }

    // 持久化的密钥文件 URI 自动加载，避免用户每次手动选择。
    // 加载完成前禁止解锁提交，避免密钥文件保护的库因凭据缺失被误判为密码错误。
    // 加载完成后（顺序保证）若自动解锁可用且 48 小时窗口未过期，主动唤起识别接口（PIN/生物识别）；
    // 密钥文件加载失败（data 为 null）时不自动唤起，避免凭据缺失的解锁误判。
    LaunchedEffect(library.id) {
        val persistedKeyFileUri = library.keyFileUri
        if (!persistedKeyFileUri.isNullOrBlank()) {
            inlineKeyFileLoading = true
            val loaded = loadKeyFileFromUri(context, persistedKeyFileUri)
            if (loaded != null) {
                inlineKeyFileName = loaded.first
                inlineKeyFileData = loaded.second
            }
            inlineKeyFileLoading = false
            // 加载失败时不提示，用户仍可手动选择密钥文件
        }
        if (tokenViewModel.autoUnlockViewModel.isAutoUnlockAvailable(library) &&
            !tokenViewModel.autoUnlockViewModel.isManualUnlockWindowExpired(library)
        ) {
            if (!persistedKeyFileUri.isNullOrBlank() && inlineKeyFileData == null) {
                return@LaunchedEffect
            }
            launchCredentialUnlockFromInline(library)
        }
    }

    // 面板出现时聚焦主密码输入框。
    // 自动解锁可用且 48 小时窗口未过期时改为主动唤起识别接口（见下方自动唤起 Effect），不弹键盘；
    // 其余情况（未开启/已失效/窗口已过期）保持现状聚焦键盘。
    LaunchedEffect(inlineUnlockFocusNonce) {
        val shouldAutoPrompt = tokenViewModel.autoUnlockViewModel.isAutoUnlockAvailable(library) &&
            !tokenViewModel.autoUnlockViewModel.isManualUnlockWindowExpired(library)
        if (!shouldAutoPrompt) {
            inlineUnlockFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    // 强制主密码校验剩余时间的定时刷新（每分钟）
    LaunchedEffect(Unit) {
        manualUnlockClockMillis = System.currentTimeMillis()
        while (true) {
            delay(60_000L)
            manualUnlockClockMillis = System.currentTimeMillis()
        }
    }

    val unlockLibrary = library
    val autoUnlockAvailable = tokenViewModel.autoUnlockViewModel.isAutoUnlockAvailable(unlockLibrary)
    val autoUnlockInvalidated = tokenViewModel.autoUnlockViewModel.isAutoUnlockInvalidated(unlockLibrary)
    val isManualWindowEnabled = tokenViewModel.autoUnlockViewModel.isManualUnlockWindowEnabled(unlockLibrary)
    val manualWindowRemaining = tokenViewModel.autoUnlockViewModel.getManualUnlockWindowRemainingMillis(
        library = unlockLibrary,
        nowMillis = manualUnlockClockMillis
    )
    val credentialUnlockRemaining = tokenViewModel.autoUnlockViewModel.getCredentialUnlockRemainingMillis(
        library = unlockLibrary,
        nowMillis = manualUnlockClockMillis
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "解锁: ${unlockLibrary.displayName}")
        Text(
            text = when {
                autoUnlockInvalidated -> "自动解锁状态：已失效（需主密码+认证恢复）"
                !isManualWindowEnabled -> "强制主密码校验：已关闭"
                manualWindowRemaining == null -> "强制主密码校验：不可用"
                manualWindowRemaining <= 0L -> {
                    val deadlineRemaining = credentialUnlockRemaining
                    if (deadlineRemaining != null && deadlineRemaining > 0L) {
                        "已超过48小时，最后一次凭据解锁机会（剩余${tokenViewModel.autoUnlockViewModel.formatRemainingHoursMinutes(deadlineRemaining)}）"
                    } else {
                        "已超过64小时，仅支持手动输入主密码"
                    }
                }
                else -> "强制主密码校验剩余：${tokenViewModel.autoUnlockViewModel.formatRemainingHoursMinutes(manualWindowRemaining)}"
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
                IconButton(onClick = { showInlinePassword = !showInlinePassword }) {
                    Icon(
                        imageVector = if (showInlinePassword) MiuixIcons.Hide else MiuixIcons.Show,
                        contentDescription = if (showInlinePassword) "隐藏密码" else "显示密码"
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(inlineUnlockFocusRequester),
            singleLine = true
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { inlineKeyFilePicker.launch(arrayOf("application/octet-stream", "*/*")) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = MiuixIcons.Lock,
                contentDescription = "密钥文件",
                tint = MiuixTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                Text(
                    text = "密钥文件（可选）",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary
                )
                Text(
                    text = if (inlineKeyFileName.isBlank()) "点击选择密钥文件" else inlineKeyFileName,
                    fontSize = 13.sp,
                    color = if (inlineKeyFileName.isBlank()) MiuixTheme.colorScheme.primary
                    else MiuixTheme.colorScheme.onSurface
                )
            }
            if (inlineKeyFileName.isNotBlank()) {
                IconButton(onClick = {
                    inlineKeyFileName = ""
                    inlineKeyFileData = null
                }) {
                    Icon(
                        imageVector = MiuixIcons.Delete,
                        contentDescription = "清除密钥文件",
                        tint = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                text = "取消",
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            )

            Button(
                onClick = {
                    if (inlineUnlockPassword.isBlank()) {
                        ToastUtils.showShortToast(context, "请输入主密码")
                        return@Button
                    }
                    // 该库持久化了密钥文件但尚未加载完成：直接提交会用空凭据解锁而误报密码错误
                    if (inlineKeyFileLoading) {
                        ToastUtils.showShortToast(context, "正在加载密钥文件，请稍候")
                        return@Button
                    }
                    if (!library.keyFileUri.isNullOrBlank() && inlineKeyFileData == null) {
                        ToastUtils.showShortToast(context, "密钥文件加载失败，请手动重新选择")
                        return@Button
                    }
                    coroutineScope.launch {
                        inlineUnlockLoading = true
                        val ok = tokenViewModel.unlockCurrentLibrary(
                            inlineUnlockPassword,
                            keyFileData = inlineKeyFileData
                        )
                        inlineUnlockLoading = false
                        if (ok) {
                            val unlockedLibrary = resolveLatestLibrary(unlockLibrary)
                                ?: return@launch
                            val plainPassword = inlineUnlockPassword
                            clearInlineUnlock()
                            promptAutoUnlockEnroll(
                                context = context,
                                tokenViewModel = tokenViewModel,
                                library = unlockedLibrary,
                                masterPassword = plainPassword,
                                onLaunchDeviceCredential = { intent, library, password, authMode ->
                                    pendingDeviceCredentialLibrary = library
                                    pendingDeviceCredentialMasterPassword = password
                                    pendingDeviceCredentialFlow = "enroll"
                                    pendingDeviceCredentialAuthMode = authMode
                                    deviceCredentialLauncher.launch(intent)
                                    true
                                }
                            )
                            onUnlockSuccess()
                        } else {
                            inlineUnlockPassword = ""
                            val message = tokenViewModel.libraryViewModel.getLastUnlockErrorMessage()
                                ?: "解锁失败：主密码不正确或文件无效"
                            ToastUtils.showShortToast(context, message)
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = !inlineUnlockLoading
            ) {
                Image(
                    painter = painterResource(id = R.drawable.lock_open_48),
                    contentDescription = "解锁",
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .size(20.dp)
                )
                Text(text = if (inlineUnlockLoading) "解锁中..." else "解锁")
            }
        }

        // 凭据/生物识别解锁按钮：仅自动解锁开启且可用时显示（未开启/失效状态不显示）。
        if (autoUnlockAvailable) {
            Button(
                onClick = {
                    launchCredentialUnlockFromInline(unlockLibrary)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !inlineUnlockLoading
            ) {
                Text(text = "使用")
                Spacer(modifier = Modifier.size(6.dp))
                Image(
                    painter = painterResource(id = R.drawable.key_vertical_24),
                    contentDescription = "凭据解锁",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.size(4.dp))
                Text(text = "/")
                Spacer(modifier = Modifier.size(4.dp))
                Image(
                    painter = painterResource(id = R.drawable.fingerprint_24),
                    contentDescription = "生物识别解锁",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text(text = "解锁")
            }
        }
    }
}

/**
 * 从持久化的密钥文件 URI 读取密钥文件内容，返回 (显示名, 字节) 对。
 * 读取失败时返回 null，调用方可让用户手动选择。
 */
private suspend fun loadKeyFileFromUri(
    context: android.content.Context,
    uriString: String
): Pair<String, ByteArray>? {
    return withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val uri = Uri.parse(uriString)
            val name = uri.resolveDisplayName(context, fallbackIfEmpty = "keyfile")
            context.contentResolver.openInputStream(uri)?.use { input ->
                val buffer = java.io.ByteArrayOutputStream(8 * 1024)
                val chunk = ByteArray(8 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(chunk)
                    if (read < 0) break
                    total += read
                    if (total > 1024 * 1024) return@use null
                    buffer.write(chunk, 0, read)
                }
                val bytes = buffer.toByteArray()
                if (bytes.isEmpty()) return@use null
                name to bytes
            }
        }.getOrNull()
    }
}

/**
 * 手动解锁成功后触发首次自动解锁引导。
 *
 * 设备未录入生物识别/需要设备凭据（PIN/图案/密码）时，通过 [onLaunchDeviceCredential]
 * 兜底发起系统身份验证，返回 true 表示已发起；未提供兜底或验证失败时标记引导已拒绝。
 */
fun promptAutoUnlockEnroll(
    context: android.content.Context,
    tokenViewModel: TokenViewModel,
    library: LibraryContext,
    masterPassword: String,
    forcePrompt: Boolean = false,
    onLaunchDeviceCredential: (
        intent: android.content.Intent,
        library: LibraryContext,
        masterPassword: String,
        authMode: Int
    ) -> Boolean = { _, _, _, _ -> false }
) {
    if (context !is FragmentActivity) {
        return
    }

    val currentLibraryState = tokenViewModel.libraryViewModel.currentLibrary.value
    val targetLibrary = currentLibraryState?.takeIf { it.id == library.id } ?: library
    if (!forcePrompt && !tokenViewModel.autoUnlockViewModel.shouldPromptAutoUnlockEnroll(targetLibrary)) {
        return
    }

    val authMode = tokenViewModel.autoUnlockViewModel.normalizeAutoUnlockAuthMode(targetLibrary.autoUnlockAuthMode)
    val cipher = tokenViewModel.autoUnlockViewModel.getCipherForEnrollment(targetLibrary)
    if (cipher == null) {
        return
    }

    tokenViewModel.autoUnlockViewModel.biometricKeyStoreManager.authenticate(
        activity = context,
        cipher = cipher,
        title = "启用自动解锁",
        subtitle = "首次解锁成功，验证身份后可下次快速解锁",
        authMode = authMode,
        onSuccess = { authCipher ->
            if (authCipher != null) {
                val enabled = tokenViewModel.autoUnlockViewModel.enableAutoUnlock(
                    library = targetLibrary,
                    cipher = authCipher,
                    masterPassword = masterPassword,
                    resolveLibrary = { tokenViewModel.libraryViewModel.resolveLibrarySnapshot(it) },
                    onPersist = { tokenViewModel.libraryViewModel.persistLibraryMetadata(it) },
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
                val intent = tokenViewModel.autoUnlockViewModel.biometricKeyStoreManager.createDeviceCredentialIntent(
                    title = "启用自动解锁",
                    subtitle = "请使用 PIN/图案/密码完成验证"
                )
                if (intent != null) {
                    fallbackLaunched = onLaunchDeviceCredential(intent, targetLibrary, masterPassword, authMode)
                }
            }

            if (!fallbackLaunched) {
                tokenViewModel.autoUnlockViewModel.setAutoUnlockEnrollDismissed(targetLibrary) {
                    tokenViewModel.libraryViewModel.persistLibraryMetadata(it)
                }
            }
        }
    )
}
