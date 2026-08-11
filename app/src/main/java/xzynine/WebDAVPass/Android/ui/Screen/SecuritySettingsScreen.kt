package xzynine.WebDAVPass.Android.ui.Screen

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import xzynine.WebDAVPass.Android.R
import xzynine.WebDAVPass.Android.biometric.BiometricKeyStoreManager
import xzynine.WebDAVPass.Android.ui.ViewModel.AutoUnlockViewModel
import xzynine.WebDAVPass.Android.ui.ViewModel.TokenViewModel
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme
import xzynine.WebDAVPass.Android.ui.component.Preference
import xzynine.WebDAVPass.Android.ui.component.PreferenceType
import xzynine.WebDAVPass.Android.ui.component.SettingsTopAppBar

/**
 * 安全设置子页面（原「安全」分组）。
 */
@Composable
fun SecuritySettingsContent(
    viewModel: TokenViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val currentLibraryState by viewModel.libraryViewModel.currentLibrary.collectAsState()
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

    Scaffold(
        popupHost = { },
        topBar = {
            SettingsTopAppBar(
                title = "安全",
                onNavigateBack = onNavigateBack
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            val isAutoUnlockEnabled = currentLib?.autoUnlockEnabled == true
            val isAutoUnlockInvalidated = currentLib?.autoUnlockInvalidated == true

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Preference(
                    type = PreferenceType.Switch,
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
                            modifier = Modifier.padding(end = 16.dp),
                            imageVector = MiuixIcons.Settings,
                            contentDescription = "自动解锁",
                        )
                    },
                    onCheckedChange = { checked ->
                        if (currentLib == null) {
                            xzylib.base.util.ToastUtils.showShortToast(context, "请先选择数据库文件")
                            autoUnlockSwitchChecked = false
                            return@Preference
                        }

                        if (!checked) {
                            autoUnlockSwitchChecked = false
                            autoUnlockSelectedIndex = -1
                            if (currentLib.autoUnlockEnabled) {
                                viewModel.autoUnlockViewModel.disableAutoUnlock(currentLib) {
                                    viewModel.libraryViewModel.persistLibraryMetadata(it)
                                }
                            }
                            return@Preference
                        }

                        autoUnlockSwitchChecked = true
                        if (currentLib.autoUnlockEnabled) {
                            autoUnlockSelectedIndex = authModeToIndex(currentLib.autoUnlockAuthMode)
                        } else {
                            autoUnlockSelectedIndex = -1
                        }
                    },
                )
            }

            if (autoUnlockSwitchChecked) {
                Spacer(modifier = Modifier.height(12.dp))

                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Preference(
                        type = PreferenceType.Spinner,
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
                                return@Preference
                            }

                            autoUnlockSelectedIndex = selectedIndex
                            val authMode = indexToAuthMode(selectedIndex)

                            if (context !is FragmentActivity) {
                                xzylib.base.util.ToastUtils.showShortToast(context, "当前页面无法发起认证")
                                return@Preference
                            }

                            val masterPassword = viewModel.libraryViewModel.getCurrentLibraryMasterPassword()
                            if (masterPassword.isNullOrBlank()) {
                                xzylib.base.util.ToastUtils.showShortToast(context, "请先手动解锁一次当前库")
                                autoUnlockSelectedIndex = if (selectedLibrary.autoUnlockEnabled) {
                                    authModeToIndex(selectedLibrary.autoUnlockAuthMode)
                                } else {
                                    -1
                                }
                                return@Preference
                            }

                            val cipher = viewModel.autoUnlockViewModel.getCipherForEnrollment(selectedLibrary)
                            if (cipher == null) {
                                xzylib.base.util.ToastUtils.showShortToast(context, "无法启用自动解锁")
                                autoUnlockSelectedIndex = if (selectedLibrary.autoUnlockEnabled) {
                                    authModeToIndex(selectedLibrary.autoUnlockAuthMode)
                                } else {
                                    -1
                                }
                                return@Preference
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
                    )
                }
            }

            if (currentLib != null) {
                Spacer(modifier = Modifier.height(12.dp))

                val manualUnlockRemaining = viewModel.autoUnlockViewModel.getManualUnlockWindowRemainingMillis(
                    library = currentLib,
                    nowMillis = manualUnlockClockMillis
                )

                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Preference(
                        type = PreferenceType.Switch,
                        title = "强制超时主密码校验",
                        summary = when {
                            !manualUnlockWindowSwitchChecked -> "已关闭强制主密码校验"
                            !autoUnlockSwitchChecked && !currentLib.autoUnlockEnabled -> "启用自动解锁后生效"
                            manualUnlockRemaining == null -> "强制主密码校验不可用"
                            manualUnlockRemaining <= 0L -> "已到期：凭据解锁一次后将清理自动解锁"
                            else -> "剩余：${viewModel.autoUnlockViewModel.formatRemainingHoursMinutes(manualUnlockRemaining)}"
                        },
                        checked = manualUnlockWindowSwitchChecked,
                        startAction = {
                            Icon(
                                modifier = Modifier.padding(end = 16.dp),
                                imageVector = MiuixIcons.Settings,
                                contentDescription = "强制超时主密码校验"
                            )
                        },
                        onCheckedChange = { checked ->
                            manualUnlockWindowSwitchChecked = checked
                            viewModel.autoUnlockViewModel.updateManualUnlockWindowEnabled(currentLib, checked) {
                                viewModel.libraryViewModel.persistLibraryMetadata(it)
                            }
                        },
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                val lockTimeoutItems = remember { listOf("不锁定", "1 分钟", "5 分钟", "15 分钟", "30 分钟", "60 分钟") }
                val lockTimeoutValues = listOf(0, 1, 5, 15, 30, 60)
                val lockTimeoutMinutes by viewModel.lockTimeoutMinutes.collectAsState()
                val lockTimeoutIndex = lockTimeoutValues.indexOf(lockTimeoutMinutes).coerceAtLeast(0)
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Preference(
                        type = PreferenceType.Spinner,
                        title = "应用超时锁定",
                        summary = null,
                        items = lockTimeoutItems.map { DropdownItem(text = it) },
                        selectedIndex = lockTimeoutIndex,
                        showValue = lockTimeoutMinutes > 0,
                        bottomAction = if (lockTimeoutMinutes > 0) {
                            {
                                val lockTimeoutText = buildAnnotatedString {
                                    append("应用超过")
                                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                        append("${lockTimeoutMinutes} 分钟")
                                    }
                                    append("未操作后将自动锁定")
                                }
                                Text(
                                    text = lockTimeoutText,
                                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                                )
                            }
                        } else null,
                        startAction = {
                            Icon(
                                modifier = Modifier.padding(end = 16.dp),
                                imageVector = MiuixIcons.Lock,
                                contentDescription = "应用超时锁定"
                            )
                        },
                        onSelectedIndexChange = { index ->
                            viewModel.setLockTimeoutMinutes(lockTimeoutValues[index])
                        },
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                val lockOnBackground by viewModel.lockOnBackground.collectAsState()
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Preference(
                        type = PreferenceType.Switch,
                        title = "后台自动锁定",
                        summary = "退出到后台将自动锁定应用",
                        checked = lockOnBackground,
                        startAction = {
                            Icon(
                                modifier = Modifier.padding(end = 16.dp),
                                imageVector = MiuixIcons.Lock,
                                contentDescription = "后台自动锁定"
                            )
                        },
                        onCheckedChange = { checked ->
                            viewModel.setLockOnBackground(checked)
                        },
                    )
                }
            }
        }
    }
}
