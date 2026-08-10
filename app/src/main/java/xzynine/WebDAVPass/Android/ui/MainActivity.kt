package xzynine.WebDAVPass.Android.ui

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import xzylib.base.util.ToastUtils
import top.yukonga.miuix.kmp.basic.FabPosition
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Scan
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.MiuixPopupHost
import xzynine.WebDAVPass.Android.ui.navigation.LocalNavigator
import xzynine.WebDAVPass.Android.ui.navigation.Navigator
import xzynine.WebDAVPass.Android.ui.navigation.Route
import xzynine.WebDAVPass.Android.ui.navigation.rememberNavigator
import xzynine.WebDAVPass.Android.ui.Screen.SettingsScreen
import xzynine.WebDAVPass.Android.ui.Screen.DatabaseSettingsScreen
import xzynine.WebDAVPass.Android.theme.AppTheme
import xzynine.WebDAVPass.Android.theme.SetupSystemBars
import xzynine.WebDAVPass.Android.ui.Dialog.CloudLibraryDialog
import xzynine.WebDAVPass.Android.ui.Dialog.CloudMode
import xzynine.WebDAVPass.Android.ui.Dialog.ScanTokenScreen
import xzynine.WebDAVPass.Android.ui.Screen.HomeScreen
import xzynine.WebDAVPass.Android.ui.Screen.WelcomeScreen
import xzynine.WebDAVPass.Android.ui.Screen.TokenListScreen
import xzynine.WebDAVPass.Android.ui.Screen.PasswordListScreen
import xzynine.WebDAVPass.Android.ui.Screen.PasswordEntryDetailScreen
import xzynine.WebDAVPass.Android.ui.Screen.SecurityCheckScreen
import xzynine.WebDAVPass.Android.ui.ViewModel.TokenViewModel
import xzynine.WebDAVPass.Android.ui.ViewModel.PasswordListMode
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 截图防泄密：全局禁止截屏/录屏（涉及密码与令牌内容）
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )
        this.setContent {
            AppTheme {
                SetupSystemBars()
                MainScreen()
            }
        }
    }
}

/** 后台自动锁定阈值：退到后台超过该时长，回到前台即锁定。 */
private const val BACKGROUND_LOCK_THRESHOLD_MS = 30_000L

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val tokenViewModel: TokenViewModel = remember(context.applicationContext) {
        TokenViewModel.getSharedInstance(context.applicationContext)
    }

    // 超时锁定：
    // 1) 后台自动锁定：退到后台超过 30 秒（开关开启时）回到前台即锁定；
    // 2) 无操作超时：前台无任何操作超过设定分钟数（默认 5 分钟）即锁定。
    //    用 AtomicLong 保存时间戳，触摸事件高频写入时不触发重组。
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var lastBackgroundAt by remember { mutableStateOf(0L) }
    val lastActivityAt = remember { java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis()) }

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                // 使用 ON_STOP/ON_START 而非 ON_PAUSE/ON_RESUME 判断后台：
                // ON_PAUSE 在半透明 Activity、系统权限对话框和生物识别提示覆盖时也会触发，
                // 此时应用并未真正退到后台，会误触发锁定。
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                    lastBackgroundAt = System.currentTimeMillis()
                }

                androidx.lifecycle.Lifecycle.Event.ON_START -> {
                    // 后台自动锁定：退后台 ≥30 秒则锁定
                    val backgroundAt = lastBackgroundAt
                    lastBackgroundAt = 0L
                    if (backgroundAt > 0L && tokenViewModel.lockOnBackground.value &&
                        System.currentTimeMillis() - backgroundAt >= BACKGROUND_LOCK_THRESHOLD_MS
                    ) {
                        tokenViewModel.libraryViewModel.lockCurrentLibrary()
                    }
                    // 回到前台重新开始无操作计时
                    lastActivityAt.set(System.currentTimeMillis())
                }

                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 无操作超时：任何触摸操作重置计时，无操作超过设定分钟数即锁定（每 30 秒检查一次）
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            val timeoutMinutes = tokenViewModel.lockTimeoutMinutes.value
            val timeoutMs = timeoutMinutes * 60_000L
            if (timeoutMs > 0L &&
                System.currentTimeMillis() - lastActivityAt.get() >= timeoutMs
            ) {
                tokenViewModel.libraryViewModel.lockCurrentLibrary()
                // 锁定后重新计时，避免解锁页停留期间反复触发
                lastActivityAt.set(System.currentTimeMillis())
            }
        }
    }

    val showCloudBindingDialog = remember { mutableStateOf(false) }
    var showWelcome by rememberSaveable { mutableStateOf(true) }
    val currentLibrary by tokenViewModel.libraryViewModel.currentLibrary.collectAsState()
    val showScanBottomSheet = remember { mutableStateOf(false) }

    val startRoute = remember(showWelcome) {
        if (showWelcome) Route.Welcome else Route.Home
    }
    val navigator = rememberNavigator(startRoute)

    CompositionLocalProvider(LocalNavigator provides navigator) {
        // 全局触摸监听：任何触摸操作都重置无操作超时计时
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown()
                        lastActivityAt.set(System.currentTimeMillis())
                    }
                }
        ) {
            NavDisplay(
                backStack = navigator.backStack,
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator()
                ),
                onBack = {
                    if (showCloudBindingDialog.value) {
                        showCloudBindingDialog.value = false
                        return@NavDisplay
                    }
                    if (showScanBottomSheet.value) {
                        showScanBottomSheet.value = false
                        return@NavDisplay
                    }
                    if (navigator.backStackSize() > 1) {
                        navigator.pop()
                    }
                },
            entryProvider = entryProvider {
                entry<Route.Welcome> {
                    WelcomeScreen(
                        tokenViewModel = tokenViewModel,
                        onEnterLibrary = {
                            navigator.replaceAll(listOf(Route.Home))
                            showWelcome = false
                        },
                        onBackPressed = {
                            if (navigator.backStackSize() > 1) {
                                navigator.pop()
                                true
                            } else {
                                false
                            }
                        }
                    )
                }
                entry<Route.Home> {
                    val isLibraryUnlocked by tokenViewModel.libraryViewModel.isLibraryUnlocked.collectAsState(false)
                    val lib by tokenViewModel.libraryViewModel.currentLibrary.collectAsState(null)

                    LaunchedEffect(isLibraryUnlocked, lib) {
                        if (!isLibraryUnlocked || lib == null) {
                            navigator.replaceAll(listOf(Route.Welcome))
                            showWelcome = true
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        Scaffold(
                            popupHost = {},
                            topBar = {
                                TopAppBar(
                                    title = currentLibrary?.displayName ?: "WebDAVPass",
                                    navigationIcon = {},
                                    actions = {
                                        IconButton(onClick = {
                                            navigator.push(Route.Settings)
                                        }) {
                                            Icon(
                                                imageVector = MiuixIcons.Settings,
                                                contentDescription = "设置"
                                            )
                                        }
                                    }
                                )
                            },
                            floatingActionButton = {
                                FloatingActionButton(
                                    onClick = {
                                        showScanBottomSheet.value = true
                                    }
                                ) {
                                    Icon(
                                        imageVector = MiuixIcons.Scan,
                                        contentDescription = "扫描二维码"
                                    )
                                }
                            },
                            floatingActionButtonPosition = FabPosition.Companion.End,
                            content = { paddingValues ->
                                Box(
                                    modifier = Modifier.Companion
                                        .fillMaxSize()
                                        .padding(paddingValues)
                                ) {
                                    HomeScreen(
                                        tokenViewModel = tokenViewModel,
                                        onNavigateToPasswordList = { listMode ->
                                            navigator.push(Route.PasswordList(listMode))
                                        },
                                        onNavigateToTokenList = {
                                            navigator.push(Route.TokenList)
                                        },
                                        onNavigateToSecurityCheck = {
                                            navigator.push(Route.SecurityCheck)
                                        }
                                    )
                                }
                            }
                        )
                        MiuixPopupHost()
                    }
                }
                entry<Route.SecurityCheck> {
                    val isLibraryUnlocked by tokenViewModel.libraryViewModel.isLibraryUnlocked.collectAsState(false)
                    val lib by tokenViewModel.libraryViewModel.currentLibrary.collectAsState(null)

                    LaunchedEffect(isLibraryUnlocked, lib) {
                        if (!isLibraryUnlocked || lib == null) {
                            navigator.replaceAll(listOf(Route.Welcome))
                            showWelcome = true
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        SecurityCheckScreen(
                            tokenViewModel = tokenViewModel,
                            onNavigateBack = {
                                navigator.pop()
                            },
                            onEntryClick = { entryId ->
                                navigator.push(Route.PasswordEntryDetail(entryId))
                            }
                        )
                        MiuixPopupHost()
                    }
                }
                entry<Route.Settings> {
                    val isLibraryUnlocked by tokenViewModel.libraryViewModel.isLibraryUnlocked.collectAsState(false)
                    val lib by tokenViewModel.libraryViewModel.currentLibrary.collectAsState(null)

                    LaunchedEffect(isLibraryUnlocked, lib) {
                        if (!isLibraryUnlocked || lib == null) {
                            navigator.replaceAll(listOf(Route.Welcome))
                            showWelcome = true
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        SettingsScreen(
                            viewModel = tokenViewModel,
                            onCloudBindingClick = {
                                if (currentLibrary == null) {
                                    ToastUtils.showShortToast(context, "请先选择数据库文件")
                                } else {
                                    showCloudBindingDialog.value = true
                                }
                            },
                            onSwitchLibraryClick = {
                                tokenViewModel.libraryViewModel.clearCurrentLibrarySelection()
                                navigator.replaceAll(listOf(Route.Welcome))
                                showWelcome = true
                            },
                            onDatabaseSettingsClick = {
                                navigator.push(Route.DatabaseSettings)
                            },
                            onNavigateBack = {
                                navigator.pop()
                            }
                        )
                        MiuixPopupHost()
                    }
                }
                entry<Route.DatabaseSettings> {
                    val isLibraryUnlocked by tokenViewModel.libraryViewModel.isLibraryUnlocked.collectAsState(false)
                    val lib by tokenViewModel.libraryViewModel.currentLibrary.collectAsState(null)

                    LaunchedEffect(isLibraryUnlocked, lib) {
                        if (!isLibraryUnlocked || lib == null) {
                            navigator.replaceAll(listOf(Route.Welcome))
                            showWelcome = true
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        DatabaseSettingsScreen(
                            viewModel = tokenViewModel,
                            onNavigateBack = {
                                navigator.pop()
                            }
                        )
                        MiuixPopupHost()
                    }
                }
                entry<Route.TokenList> {
                    val isLibraryUnlocked by tokenViewModel.libraryViewModel.isLibraryUnlocked.collectAsState(false)
                    val lib by tokenViewModel.libraryViewModel.currentLibrary.collectAsState(null)

                    LaunchedEffect(isLibraryUnlocked, lib) {
                        if (!isLibraryUnlocked || lib == null) {
                            navigator.replaceAll(listOf(Route.Welcome))
                            showWelcome = true
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        Scaffold(
                            popupHost = {},
                            topBar = {
                                TopAppBar(
                                    title = "令牌列表",
                                    navigationIcon = {
                                        IconButton(onClick = {
                                            navigator.pop()
                                        }) {
                                            Icon(
                                                imageVector = MiuixIcons.Back,
                                                contentDescription = "返回"
                                            )
                                        }
                                    },
                                    actions = {}
                                )
                            },
                            content = { paddingValues ->
                                Box(
                                    modifier = Modifier.Companion
                                        .fillMaxSize()
                                        .padding(paddingValues)
                                ) {
                                    TokenListScreen(
                                        tokenViewModel = tokenViewModel,
                                        onEntryClick = { entryId ->
                                            navigator.push(Route.PasswordEntryDetail(entryId))
                                        }
                                    )
                                }
                            }
                        )
                        MiuixPopupHost()
                    }
                }
                entry<Route.PasswordList> { key ->
                    val listMode = key.listMode
                    val isLibraryUnlocked by tokenViewModel.libraryViewModel.isLibraryUnlocked.collectAsState(false)
                    val lib by tokenViewModel.libraryViewModel.currentLibrary.collectAsState(null)

                    LaunchedEffect(listMode) {
                        tokenViewModel.passwordViewModel.setPasswordListMode(listMode, refreshNow = true)
                        tokenViewModel.passwordViewModel.refreshRecentDeletedCount()
                    }

                    LaunchedEffect(isLibraryUnlocked, lib) {
                        if (!isLibraryUnlocked || lib == null) {
                            navigator.replaceAll(listOf(Route.Welcome))
                            showWelcome = true
                        }
                    }

                    DisposableEffect(listMode) {
                        onDispose {
                            tokenViewModel.passwordViewModel.resetPasswordGroupStackOnly()
                            if (listMode == PasswordListMode.RECENT_DELETED) {
                                tokenViewModel.passwordViewModel.setPasswordListMode(PasswordListMode.ALL_PASSWORDS, refreshNow = true)
                            }
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        PasswordListScreen(
                            tokenViewModel = tokenViewModel,
                            title = if (listMode == PasswordListMode.RECENT_DELETED) "最近删除" else "全部密码",
                            emptyStateText = if (listMode == PasswordListMode.RECENT_DELETED) "暂无最近删除条目" else "暂无条目",
                            emptySearchStateText = "无匹配条目",
                            enableGroupNavigation = listMode == PasswordListMode.ALL_PASSWORDS,
                            enableRecycleBinActions = listMode == PasswordListMode.RECENT_DELETED,
                            onEntryClick = { entryId ->
                                navigator.push(Route.PasswordEntryDetail(entryId))
                            },
                            onNavigateBack = {
                                navigator.pop()
                            }
                        )
                        MiuixPopupHost()
                    }
                }
                entry<Route.PasswordEntryDetail> { key ->
                    val entryId = key.entryId
                    val isLibraryUnlocked by tokenViewModel.libraryViewModel.isLibraryUnlocked.collectAsState(false)
                    val lib by tokenViewModel.libraryViewModel.currentLibrary.collectAsState(null)

                    LaunchedEffect(isLibraryUnlocked, lib) {
                        if (!isLibraryUnlocked || lib == null) {
                            navigator.replaceAll(listOf(Route.Welcome))
                            showWelcome = true
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        PasswordEntryDetailScreen(
                            tokenViewModel = tokenViewModel,
                            entryId = entryId,
                            onNavigateBack = {
                                navigator.pop()
                            },
                            onDeleted = {
                                navigator.pop()
                            }
                        )
                        MiuixPopupHost()
                    }
                }
            }
        )
        }
    }

    if (showCloudBindingDialog.value) {
        CloudLibraryDialog(
            tokenViewModel = tokenViewModel,
            mode = CloudMode.BIND,
            initialLibraryContext = currentLibrary,
            onDismiss = {
                showCloudBindingDialog.value = false
            },
            onSelected = { library, _ ->
                val saved = tokenViewModel.libraryViewModel.bindCurrentLibraryToCloud(library)
                if (saved) {
                    ToastUtils.showShortToast(context, "当前库云端绑定已保存")
                    showCloudBindingDialog.value = false
                } else {
                    ToastUtils.showShortToast(context, "保存失败，请重新选择当前库")
                }
            }
        )
    }

    if (!showWelcome) {
        WindowBottomSheet(
            show = showScanBottomSheet.value,
            title = "扫描二维码",
            onDismissRequest = {
                showScanBottomSheet.value = false
            },
            content = {
                Box(
                    modifier = Modifier.Companion
                        .fillMaxWidth()
                        .height(500.dp)
                ) {
                    ScanTokenScreen(
                        tokenViewModel = tokenViewModel,
                        onTokenScanned = {
                            showScanBottomSheet.value = false
                        },
                        onDismiss = {
                            showScanBottomSheet.value = false
                        }
                    )
                }
            }
        )
    }
}
