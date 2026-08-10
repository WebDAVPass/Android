package xzynine.WebDAVPass.Android.ui.Screen

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import xzynine.WebDAVPass.Android.autofill.KeeAutofillService
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import xzynine.WebDAVPass.Android.R
import xzynine.WebDAVPass.Android.biometric.BiometricKeyStoreManager
import xzynine.WebDAVPass.Android.data.LibrarySourceType
import xzynine.WebDAVPass.Android.theme.getAppRoundedCorner
import xzynine.WebDAVPass.Android.ui.ViewModel.TokenViewModel
import xzynine.WebDAVPass.Android.ui.ViewModel.AutoUnlockViewModel
import xzynine.WebDAVPass.Android.ui.ViewModel.LibraryViewModel
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowSpinnerPreference
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.CloudFill
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Months
import top.yukonga.miuix.kmp.icon.extended.Settings
import github.xzynine.webdav.ui.WebDavSyncStatusSection
import github.xzynine.webdav.ui.WebDavSyncUiState

/**
 * 设置界面组件
 * @param viewModel TokenViewModel实例
 * @param onCloudBindingClick 点击当前库云端设置的回调
 */
@Composable
fun SettingsScreen(
    viewModel: TokenViewModel,
    onCloudBindingClick: () -> Unit,
    onSwitchLibraryClick: () -> Unit,
    onDatabaseSettingsClick: () -> Unit,
    onNavigateBack: () -> Unit
) {
    // 获取统一的圆角半径
    val cornerRadius = getAppRoundedCorner()
    
    // 收集状态流
    val backupStatus = viewModel.cloudSyncViewModel.backupStatus.collectAsState()
    val isBackupInProgress = viewModel.cloudSyncViewModel.isBackupInProgress.collectAsState()
    val backupProgress = viewModel.cloudSyncViewModel.backupProgress.collectAsState()
    val isRestoreInProgress = viewModel.cloudSyncViewModel.isRestoreInProgress.collectAsState()
    val restoreProgress = viewModel.cloudSyncViewModel.restoreProgress.collectAsState()
    val currentLibraryState by viewModel.libraryViewModel.currentLibrary.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val autoUnlockModeItems = remember { listOf("默认", "生物识别", "PIN") }
    val currentLib = currentLibraryState

    /**
     * 将认证模式转换为下拉索引。
     */
    fun authModeToIndex(mode: Int): Int {
        return when (viewModel.autoUnlockViewModel.normalizeAutoUnlockAuthMode(mode)) {
            AutoUnlockViewModel.AUTO_UNLOCK_AUTH_MODE_BIOMETRIC -> 1
            AutoUnlockViewModel.AUTO_UNLOCK_AUTH_MODE_PIN -> 2
            else -> 0
        }
    }

    /**
     * 将下拉索引转换为认证模式。
     */
    fun indexToAuthMode(index: Int): Int {
        return when (index) {
            1 -> AutoUnlockViewModel.AUTO_UNLOCK_AUTH_MODE_BIOMETRIC
            2 -> AutoUnlockViewModel.AUTO_UNLOCK_AUTH_MODE_PIN
            else -> AutoUnlockViewModel.AUTO_UNLOCK_AUTH_MODE_DEFAULT
        }
    }

    var autoUnlockSwitchChecked by remember(currentLib?.id, currentLib?.autoUnlockEnabled) {
        mutableStateOf(currentLib?.autoUnlockEnabled == true)
    }
    var autoUnlockSelectedIndex by remember(
        currentLib?.id,
        currentLib?.autoUnlockEnabled,
        currentLib?.autoUnlockAuthMode
    ) {
        mutableStateOf(
            if (currentLib?.autoUnlockEnabled == true) {
                authModeToIndex(currentLib.autoUnlockAuthMode)
            } else {
                -1
            }
        )
    }
    var pendingSettingAuthLibraryId by remember { mutableStateOf<String?>(null) }
    var pendingSettingAuthMode by remember {
        mutableStateOf(AutoUnlockViewModel.AUTO_UNLOCK_AUTH_MODE_DEFAULT)
    }
    var manualUnlockWindowSwitchChecked by remember(currentLib?.id, currentLib?.forceManualUnlockEvery48Hours) {
        mutableStateOf(currentLib?.forceManualUnlockEvery48Hours != false)
    }
    var manualUnlockClockMillis by remember { mutableStateOf(System.currentTimeMillis()) }

    val settingDeviceCredentialLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            val pendingLibraryId = pendingSettingAuthLibraryId
            val pendingMode = pendingSettingAuthMode
            pendingSettingAuthLibraryId = null
pendingSettingAuthMode = AutoUnlockViewModel.AUTO_UNLOCK_AUTH_MODE_DEFAULT

            val targetLibrary = currentLibraryState?.takeIf { it.id == pendingLibraryId }
                ?: return@rememberLauncherForActivityResult

            if (result.resultCode != Activity.RESULT_OK) {
                autoUnlockSwitchChecked = targetLibrary.autoUnlockEnabled
                autoUnlockSelectedIndex = if (targetLibrary.autoUnlockEnabled) {
                    authModeToIndex(targetLibrary.autoUnlockAuthMode)
                } else {
                    -1
                }
                return@rememberLauncherForActivityResult
            }

            val masterPassword = viewModel.libraryViewModel.getCurrentLibraryMasterPassword()
            if (masterPassword.isNullOrBlank()) {
                xzylib.base.util.ToastUtils.showShortToast(context, "请先手动解锁一次当前库")
                return@rememberLauncherForActivityResult
            }

            val cipher = viewModel.autoUnlockViewModel.getCipherForEnrollment(targetLibrary)
            if (cipher == null) {
                xzylib.base.util.ToastUtils.showShortToast(context, "无法启用自动解锁")
                return@rememberLauncherForActivityResult
            }

            val enabled = viewModel.autoUnlockViewModel.enableAutoUnlock(
                library = targetLibrary,
                cipher = cipher,
                masterPassword = masterPassword,
                resolveLibrary = { viewModel.libraryViewModel.resolveLibrarySnapshot(it) },
                onPersist = { viewModel.libraryViewModel.persistLibraryMetadata(it) },
                authMode = pendingMode
            )
            if (!enabled) {
                xzylib.base.util.ToastUtils.showShortToast(context, "自动解锁启用失败")
            }
        }
    )

    LaunchedEffect(
        currentLib?.id,
        currentLib?.autoUnlockEnabled,
        currentLib?.autoUnlockAuthMode,
        currentLib?.forceManualUnlockEvery48Hours
    ) {
        autoUnlockSwitchChecked = currentLib?.autoUnlockEnabled == true
        autoUnlockSelectedIndex = if (currentLib?.autoUnlockEnabled == true) {
            authModeToIndex(currentLib.autoUnlockAuthMode)
        } else {
            -1
        }
        manualUnlockWindowSwitchChecked = currentLib?.forceManualUnlockEvery48Hours != false
    }

    LaunchedEffect(currentLib?.id, manualUnlockWindowSwitchChecked) {
        if (currentLib == null || !manualUnlockWindowSwitchChecked) {
            return@LaunchedEffect
        }
        manualUnlockClockMillis = System.currentTimeMillis()
        while (true) {
            delay(60_000L)
            manualUnlockClockMillis = System.currentTimeMillis()
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
        onResult = { uri ->
            if (uri == null) {
                return@rememberLauncherForActivityResult
            }
            coroutineScope.launch {
                val ok = viewModel.exportCurrentDatabase(uri)
                if (ok) {
                    xzylib.base.util.ToastUtils.showShortToast(context, "数据库已导出")
                } else {
                    xzylib.base.util.ToastUtils.showShortToast(context, "导出失败")
                }
            }
        }
    )

    /**
     * 当前库是否已具备云端同步所需信息。
     */
    val isCurrentLibraryCloudBound = run {
        val current = currentLibraryState
        current != null
                && current.sourceType == LibrarySourceType.CLOUD
                && !current.remoteFilePath.isNullOrBlank()
                && !current.username.isNullOrBlank()
                && !current.password.isNullOrBlank()
    }

    /**
     * 将同步状态编码映射为可读文案。
     */
    val cloudSyncStatusText = when (currentLibraryState?.lastSyncStatus) {
        "syncing" -> "同步中"
        "success" -> "同步成功"
        "merged" -> "已自动合并"
        "conflict" -> "同步冲突"
        "failed" -> "同步失败"
        else -> "未同步"
    }

    /**
     * 设置页展示的当前库云端摘要。
     */
    val cloudBindingSummary = run {
        val current = currentLibraryState
        if (current == null) {
            "当前未选择数据库文件"
        } else if (isCurrentLibraryCloudBound) {
            val remote = current.remoteFilePath ?: current.remoteBaseUrl.orEmpty()
            "$remote | $cloudSyncStatusText"
        } else {
            "当前库未绑定云端 .kdbx，点击配置"
        }
    }

    Scaffold(
        popupHost = { },
        topBar = {
            TopAppBar(
                title = "设置",
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
                actions = {},
                defaultWindowInsetsPadding = true
            )
        }
    ) {
        Column(
            modifier = Modifier.Companion
                .fillMaxSize()
                .padding(it)
                .padding(16.dp)
                .verticalScroll(androidx.compose.foundation.rememberScrollState())
        ) {
            Text(
                text = "系统设置",
                modifier = Modifier.padding(8.dp)
            )

            ArrowPreference(
                title = "设置为自动填充器",
                summary = "跳转到系统自动填充设置",
                startAction = {
                    Icon(
                        modifier = Modifier.Companion.padding(end = 16.dp),
                        imageVector = MiuixIcons.GridView,
                        contentDescription = "设置为自动填充器",
                    )
                },
                onClick = {
                    val autofillServiceExtra = "android.provider.extra.AUTOFILL_SERVICE"
                    val autofillSettingsAction = "android.settings.AUTOFILL_SETTINGS"
                    val requestIntent = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply {
                        putExtra(
                            autofillServiceExtra,
                            ComponentName(context, KeeAutofillService::class.java)
                        )
                    }
                    val credentialsPickerIntent = Intent().apply {
                        component = ComponentName(
                            "com.android.settings",
                            "com.android.settings.applications.credentials.CredentialsPickerActivity"
                        )
                    }
                    val fallbackIntent = Intent(autofillSettingsAction)
                    val intent = when {
                        requestIntent.resolveActivity(context.packageManager) != null -> requestIntent
                        credentialsPickerIntent.resolveActivity(context.packageManager) != null -> credentialsPickerIntent
                        fallbackIntent.resolveActivity(context.packageManager) != null -> fallbackIntent
                        else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = android.net.Uri.fromParts("package", context.packageName, null)
                        }
                    }
                    context.startActivity(intent)
                },
                modifier = Modifier.Companion
                    .fillMaxWidth()
            )

            Spacer(modifier = Modifier.Companion.height(8.dp))

            ArrowPreference(
                title = "切换数据库文件",
                summary = "返回欢迎页，选择其他 .kdbx",
                startAction = {
                    Icon(
                        modifier = Modifier.Companion.padding(end = 16.dp),
                        imageVector = MiuixIcons.Months,
                        contentDescription = "切换数据库文件",
                    )
                },
                onClick = onSwitchLibraryClick,
                modifier = Modifier.Companion
                    .fillMaxWidth()
            )

            Spacer(modifier = Modifier.Companion.height(16.dp))

            // 数据库设置
            ArrowPreference(
                title = "数据库设置",
                summary = "修改主密码、KDF 算法与压缩设置",
                startAction = {
                    Icon(
                        modifier = Modifier.Companion.padding(end = 16.dp),
                        imageVector = MiuixIcons.Settings,
                        contentDescription = "数据库设置",
                    )
                },
                onClick = onDatabaseSettingsClick,
                modifier = Modifier.Companion
                    .fillMaxWidth()
            )

            // 导出数据库
            ArrowPreference(
                title = "导出数据库",
                summary = "将当前库另存为 .kdbx 文件",
                startAction = {
                    Icon(
                        modifier = Modifier.Companion.padding(end = 16.dp),
                        imageVector = MiuixIcons.Download,
                        contentDescription = "导出数据库",
                    )
                },
                onClick = {
                    exportLauncher.launch("WebDavPass-导出.kdbx")
                },
                modifier = Modifier.Companion
                    .fillMaxWidth()
            )

            Spacer(modifier = Modifier.Companion.height(16.dp))

            // 安全设置
            Text(
                text = "安全",
                modifier = Modifier.padding(8.dp)
            )

            val isAutoUnlockEnabled = currentLib?.autoUnlockEnabled == true
            val isAutoUnlockInvalidated = currentLib?.autoUnlockInvalidated == true

            SwitchPreference(
                title = "自动解锁",
                summary = when {
                    currentLib == null -> "请先选择数据库文件"
                    isAutoUnlockInvalidated -> "自动解锁已失效，需手动主密码后重新验证"
                    isAutoUnlockEnabled -> "已启用自动解锁"
                    autoUnlockSwitchChecked -> "请选择认证方式并完成一次身份验证"
                    else -> "开启后可使用生物识别或 PIN 快速解锁"
                },
                checked = autoUnlockSwitchChecked,
                startAction = {
                    Icon(
                        modifier = Modifier.Companion.padding(end = 16.dp),
                        imageVector = MiuixIcons.Settings,
                        contentDescription = "自动解锁",
                    )
                },
                onCheckedChange = { checked ->
                    if (currentLib == null) {
                        xzylib.base.util.ToastUtils.showShortToast(context, "请先选择数据库文件")
                        autoUnlockSwitchChecked = false
                        return@SwitchPreference
                    }

                    if (!checked) {
                        autoUnlockSwitchChecked = false
                        autoUnlockSelectedIndex = -1
                        if (currentLib.autoUnlockEnabled) {
                            viewModel.autoUnlockViewModel.disableAutoUnlock(currentLib) {
                                viewModel.libraryViewModel.persistLibraryMetadata(it)
                            }
                        }
                        return@SwitchPreference
                    }

                    autoUnlockSwitchChecked = true
                    if (currentLib.autoUnlockEnabled) {
                        autoUnlockSelectedIndex = authModeToIndex(currentLib.autoUnlockAuthMode)
                    } else {
                        autoUnlockSelectedIndex = -1
                    }
                },
                modifier = Modifier.Companion
                    .fillMaxWidth()
            )

            if (autoUnlockSwitchChecked) {
                Spacer(modifier = Modifier.height(8.dp))

                WindowSpinnerPreference(
                    title = "认证方式",
                    summary = if (autoUnlockSelectedIndex >= 0) {
                        "当前：${autoUnlockModeItems[autoUnlockSelectedIndex]}"
                    } else {
                        "请选择认证方式，选择后将触发身份验证"
                    },
                    items = autoUnlockModeItems.map { DropdownItem(text = it) },
                    selectedIndex = if (autoUnlockSelectedIndex >= 0) autoUnlockSelectedIndex else 0,
                    showValue = autoUnlockSelectedIndex >= 0,
                    enabled = currentLib != null,
                    startAction = {
                        val currentAuthIconIndex = if (autoUnlockSelectedIndex >= 0) autoUnlockSelectedIndex else 0
                        Row(
                            modifier = Modifier.padding(end = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            when (currentAuthIconIndex) {
                                1 -> {
                                    Image(
                                        painter = painterResource(id = R.drawable.fingerprint_24),
                                        contentDescription = "生物识别",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                2 -> {
                                    Image(
                                        painter = painterResource(id = R.drawable.key_vertical_24),
                                        contentDescription = "PIN",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                else -> {
                                    Image(
                                        painter = painterResource(id = R.drawable.key_vertical_24),
                                        contentDescription = "默认凭据",
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(text = "/")
                                    Image(
                                        painter = painterResource(id = R.drawable.fingerprint_24),
                                        contentDescription = "默认生物识别",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    },
                    onSelectedIndexChange = { selectedIndex ->
                        val selectedLibrary = currentLibraryState
                        if (selectedLibrary == null) {
                            return@WindowSpinnerPreference
                        }

                        autoUnlockSelectedIndex = selectedIndex
                        val authMode = indexToAuthMode(selectedIndex)

                        if (context !is FragmentActivity) {
                            xzylib.base.util.ToastUtils.showShortToast(context, "当前页面无法发起认证")
                            return@WindowSpinnerPreference
                        }

                        val masterPassword = viewModel.libraryViewModel.getCurrentLibraryMasterPassword()
                        if (masterPassword.isNullOrBlank()) {
                            xzylib.base.util.ToastUtils.showShortToast(context, "请先手动解锁一次当前库")
                            autoUnlockSelectedIndex = if (selectedLibrary.autoUnlockEnabled) {
                                authModeToIndex(selectedLibrary.autoUnlockAuthMode)
                            } else {
                                -1
                            }
                            return@WindowSpinnerPreference
                        }

                        val cipher = viewModel.autoUnlockViewModel.getCipherForEnrollment(selectedLibrary)
                        if (cipher == null) {
                            xzylib.base.util.ToastUtils.showShortToast(context, "无法启用自动解锁")
                            autoUnlockSelectedIndex = if (selectedLibrary.autoUnlockEnabled) {
                                authModeToIndex(selectedLibrary.autoUnlockAuthMode)
                            } else {
                                -1
                            }
                            return@WindowSpinnerPreference
                        }

                        viewModel.autoUnlockViewModel.biometricKeyStoreManager.authenticate(
                            activity = context,
                            cipher = cipher,
                            title = "启用自动解锁",
                            subtitle = "请验证身份以保存自动解锁",
                            authMode = authMode,
                            onSuccess = { authCipher ->
                                if (authCipher != null) {
                                    val enabled = viewModel.autoUnlockViewModel.enableAutoUnlock(
                                        library = selectedLibrary,
                                        cipher = authCipher,
                                        masterPassword = masterPassword,
                                        resolveLibrary = { viewModel.libraryViewModel.resolveLibrarySnapshot(it) },
                                        onPersist = { viewModel.libraryViewModel.persistLibraryMetadata(it) },
                                        authMode = authMode
                                    )
                                    if (!enabled) {
                                        xzylib.base.util.ToastUtils.showShortToast(context, "自动解锁启用失败")
                                    }
                                }
                            },
                            onFailure = { errorCode, _ ->
                                var fallbackLaunched = false
                                if (errorCode == BiometricKeyStoreManager.ERROR_REQUIRE_DEVICE_CREDENTIAL) {
                                    val intent = viewModel.autoUnlockViewModel.biometricKeyStoreManager.createDeviceCredentialIntent(
                                        title = "启用自动解锁",
                                        subtitle = "请使用 PIN/图案/密码完成验证"
                                    )
                                    if (intent != null) {
                                        pendingSettingAuthLibraryId = selectedLibrary.id
                                        pendingSettingAuthMode = authMode
                                        settingDeviceCredentialLauncher.launch(intent)
                                        fallbackLaunched = true
                                    }
                                }

                                if (!fallbackLaunched) {
                                    autoUnlockSelectedIndex = if (selectedLibrary.autoUnlockEnabled) {
                                        authModeToIndex(selectedLibrary.autoUnlockAuthMode)
                                    } else {
                                        -1
                                    }
                                }
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (currentLib != null) {
                Spacer(modifier = Modifier.height(8.dp))

                val manualUnlockRemaining = viewModel.autoUnlockViewModel.getManualUnlockWindowRemainingMillis(
                    library = currentLib,
                    nowMillis = manualUnlockClockMillis
                )

                SwitchPreference(
                    title = "48小时需手动主密码一次",
                    summary = when {
                        !manualUnlockWindowSwitchChecked -> "已关闭48小时主密码校验"
                        !autoUnlockSwitchChecked && !currentLib.autoUnlockEnabled -> "启用自动解锁后生效"
                        manualUnlockRemaining == null -> "48小时主密码校验不可用"
                        manualUnlockRemaining <= 0L -> "已到期：凭据解锁一次后将清理自动解锁"
                        else -> "剩余：${viewModel.autoUnlockViewModel.formatRemainingHoursMinutes(manualUnlockRemaining)}"
                    },
                    checked = manualUnlockWindowSwitchChecked,
                    startAction = {
                        Icon(
                            modifier = Modifier.padding(end = 16.dp),
                            imageVector = MiuixIcons.Settings,
                            contentDescription = "48小时主密码校验"
                        )
                    },
                    onCheckedChange = { checked ->
                        manualUnlockWindowSwitchChecked = checked
                        viewModel.autoUnlockViewModel.updateManualUnlockWindowEnabled(currentLib, checked) {
                            viewModel.libraryViewModel.persistLibraryMetadata(it)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                /*
                 * 测试入口（已隐藏）：手动将48小时窗口标记为到期。
                 * 说明：仅注释 UI，后端测试方法保留。
                 */
                // SuperArrow(
                //     title = "测试：立即结束48小时窗口",
                //     summary = "点击后将当前库标记为已到期，便于验证凭据解锁后的清理逻辑",
                //     startAction = {
                //         Icon(
                //             modifier = Modifier.padding(end = 16.dp),
                //             imageVector = MiuixIcons.Settings,
                //             contentDescription = "测试结束48小时窗口"
                //         )
                //     },
                //     onClick = {
                //         viewModel.forceManualUnlockWindowExpiredForTesting(currentLib)
                //         manualUnlockClockMillis = System.currentTimeMillis()
                //         xzylib.base.util.ToastUtils.showShortToast(context, "已将48小时窗口标记为到期")
                //     },
                //     modifier = Modifier.fillMaxWidth()
                // )
            }

            Spacer(modifier = Modifier.Companion.height(16.dp))

            // WebDAV配置（摘要已合并到备份状态）

            // 备份和恢复标题
            Text(
                text = "备份与恢复",
                modifier = Modifier.padding(8.dp)
            )

            // 子模块提供的同步状态区块（含备份状态、备份与手动恢复条目）
            WebDavSyncStatusSection(
                state = WebDavSyncUiState(
                    isBackupInProgress = isBackupInProgress.value,
                    isRestoreInProgress = isRestoreInProgress.value,
                    backupStatus = backupStatus.value,
                    backupProgress = backupProgress.value,
                    restoreProgress = restoreProgress.value,
                    isCloudBound = isCurrentLibraryCloudBound,
                    cloudBindingSummary = cloudBindingSummary
                ),
                onCloudBindingClick = onCloudBindingClick,
                onBackupClick = { viewModel.backupTokens(force = true) },
                onRestoreClick = { viewModel.manualRestoreTokens() }
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}