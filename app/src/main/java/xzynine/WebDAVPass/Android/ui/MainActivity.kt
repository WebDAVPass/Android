package xzynine.WebDAVPass.Android.ui

import android.app.Activity
import android.app.ActivityManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
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
import top.yukonga.miuix.kmp.theme.MiuixTheme
import xzynine.WebDAVPass.Android.ui.navigation.LocalNavigator
import xzynine.WebDAVPass.Android.ui.navigation.Navigator
import xzynine.WebDAVPass.Android.ui.navigation.Route
import xzynine.WebDAVPass.Android.ui.navigation.rememberNavigator
import xzynine.WebDAVPass.Android.ui.Screen.SettingsScreen
import xzynine.WebDAVPass.Android.ui.Screen.FillerSettingsContent
import xzynine.WebDAVPass.Android.ui.Screen.SecuritySettingsContent
import xzynine.WebDAVPass.Android.ui.Screen.BackupSettingsContent
import xzynine.WebDAVPass.Android.ui.Screen.DatabaseSettingsScreen
import xzynine.WebDAVPass.Android.ui.Screen.AboutScreen
import xzynine.WebDAVPass.Android.theme.AppTheme
import xzynine.WebDAVPass.Android.theme.SetupSystemBars
import xzynine.WebDAVPass.Android.ui.Dialog.CloudLibraryDialog
import xzynine.WebDAVPass.Android.ui.Dialog.CloudMode
import xzynine.WebDAVPass.Android.ui.Dialog.ScanTokenScreen
import xzynine.WebDAVPass.Android.ui.Screen.HomeScreen
import xzynine.WebDAVPass.Android.ui.Screen.OnboardingScreen
import xzynine.WebDAVPass.Android.ui.Screen.WelcomeScreen
import xzynine.WebDAVPass.Android.ui.Screen.isOnboardingCompleted
import xzynine.WebDAVPass.Android.ui.Screen.LockedScreen
import xzynine.WebDAVPass.Android.ui.Screen.TokenListScreen
import xzynine.WebDAVPass.Android.ui.Screen.PasswordListScreen
import xzynine.WebDAVPass.Android.ui.Screen.PasswordEntryDetailScreen
import xzynine.WebDAVPass.Android.ui.Screen.SecurityCheckScreen
import xzynine.WebDAVPass.Android.ui.ViewModel.TokenViewModel
import xzynine.WebDAVPass.Android.ui.ViewModel.PasswordListMode
import xzynine.WebDAVPass.Android.ui.component.AppNavigationRail
import xzynine.WebDAVPass.Android.ui.component.CategoryNavigationItem
import xzynine.WebDAVPass.Android.ui.component.LandscapePasswordPanes
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.NavDisplayTransitionEffects

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

    // 截屏防护：按开关动态启停 FLAG_SECURE（临时关闭 5 分钟后自动恢复开启，进程重建默认恢复防护）
    val isSecureRecentsEnabled by tokenViewModel.isSecureRecentsEnabled.collectAsState()
    val activity = context as? Activity
    LaunchedEffect(isSecureRecentsEnabled) {
        val window = activity?.window ?: return@LaunchedEffect
        if (isSecureRecentsEnabled) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    // 最近任务占位预览颜色：FLAG_SECURE 生效时系统按该颜色绘制占位块（API 33+，随主题色变化）
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val placeholderColor = MiuixTheme.colorScheme.surface.toArgb()
        LaunchedEffect(placeholderColor) {
            activity?.setTaskDescription(
                ActivityManager.TaskDescription.Builder()
                    .setBackgroundColor(placeholderColor)
                    .build()
            )
        }
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
    // 首次启动引导：未完成时先展示引导页，完成后露出初始路由（欢迎页/锁定页）
    val onboardingCompleted = remember { isOnboardingCompleted(context) }
    var showOnboarding by remember { mutableStateOf(!onboardingCompleted) }
    val currentLibrary by tokenViewModel.libraryViewModel.currentLibrary.collectAsState()
    val showScanBottomSheet = remember { mutableStateOf(false) }

    // 横屏模式检测
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isWideScreen = configuration.screenWidthDp >= 600
    val isLandscapeWideScreen = isLandscape && isWideScreen

    // 横屏模式下的导航状态
    var selectedNavIndex by remember { mutableIntStateOf(0) }
    var selectedEntryId by remember { mutableStateOf<Long?>(null) }

    // 初始路由：存在已记录的库文件 → 锁定页（解锁目标为上次库）；否则欢迎页。
    // 用同步快照一次性决定，避免首帧 currentLibrary 流尚未预热导致初始路由闪烁或旋转后丢失导航栈。
    val startRoute = remember {
        if (tokenViewModel.libraryViewModel.getCurrentLibrarySync() != null) {
            Route.Locked
        } else {
            Route.Welcome
        }
    }
    val navigator = rememberNavigator(startRoute)

    // 横屏模式下导航项选择处理
    val handleNavigationItemSelected = remember(tokenViewModel, navigator) {
        { item: CategoryNavigationItem ->
            selectedEntryId = null
            when (item) {
                CategoryNavigationItem.ALL_PASSWORDS -> {
                    selectedNavIndex = 0
                    navigator.replaceAll(listOf(Route.PasswordList(PasswordListMode.ALL_PASSWORDS)))
                }
                CategoryNavigationItem.TOKENS -> {
                    selectedNavIndex = 1
                    navigator.replaceAll(listOf(Route.TokenList))
                }
                CategoryNavigationItem.SECURITY -> {
                    selectedNavIndex = 2
                    navigator.replaceAll(listOf(Route.SecurityCheck))
                }
                CategoryNavigationItem.RECENT_DELETED -> {
                    selectedNavIndex = 3
                    navigator.replaceAll(listOf(Route.PasswordList(PasswordListMode.RECENT_DELETED)))
                }
            }
        }
    }

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
            if (showOnboarding) {
                OnboardingScreen(
                    onFinish = {
                        showOnboarding = false
                    }
                )
            } else if (isLandscapeWideScreen && navigator.current() !is Route.Welcome && navigator.current() !is Route.Locked) {
                // 横屏三栏布局（欢迎页/锁定页不使用三栏）
                Row(modifier = Modifier.fillMaxSize()) {
                    // 左侧导航栏
                    AppNavigationRail(
                        selectedIndex = selectedNavIndex,
                        onItemSelected = handleNavigationItemSelected,
                        expanded = configuration.screenWidthDp >= 1200
                    )

                    // 内容区域
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        NavDisplay(
                            backStack = navigator.backStack,
                            entryDecorators = listOf(
                                rememberSaveableStateHolderNavEntryDecorator()
                            ),
                            transitionSpec = {
                                ContentTransform(
                                    targetContentEnter = fadeIn(animationSpec = tween(200)),
                                    initialContentExit = fadeOut(animationSpec = tween(200))
                                )
                            },
                            popTransitionSpec = {
                                ContentTransform(
                                    targetContentEnter = fadeIn(animationSpec = tween(200)),
                                    initialContentExit = fadeOut(animationSpec = tween(200))
                                )
                            },
                            transitionEffects = NavDisplayTransitionEffects.None,
                            onBack = {
                                if (showCloudBindingDialog.value) {
                                    showCloudBindingDialog.value = false
                                    return@NavDisplay
                                }
                                if (showScanBottomSheet.value) {
                                    showScanBottomSheet.value = false
                                    return@NavDisplay
                                }
                                if (selectedEntryId != null) {
                                    selectedEntryId = null
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
                entry<Route.Locked> {
                    val isLibraryUnlocked by tokenViewModel.libraryViewModel.isLibraryUnlocked.collectAsState()
                    val lib by tokenViewModel.libraryViewModel.currentLibrary.collectAsState()

                    LaunchedEffect(isLibraryUnlocked, lib) {
                        when {
                            // 已解锁：进入主界面
                            isLibraryUnlocked && lib != null -> {
                                navigator.replaceAll(listOf(Route.Home))
                                showWelcome = false
                            }
                            // 当前库被清除：回欢迎页选择/新建库。
                            // 用同步快照兜底：currentLibrary 流在冷启动首帧可能尚未预热，
                            // 此时 lib 为 null 但历史库实际存在，不应误弹回欢迎页。
                            lib == null && tokenViewModel.libraryViewModel.getCurrentLibrarySync() == null -> {
                                navigator.replaceAll(listOf(Route.Welcome))
                                showWelcome = true
                            }
                        }
                    }

                    LockedScreen(
                        tokenViewModel = tokenViewModel,
                        onUnlocked = {
                            navigator.replaceAll(listOf(Route.Home))
                            showWelcome = false
                        },
                        onSwitchLibrary = {
                            navigator.replaceAll(listOf(Route.Welcome))
                            showWelcome = true
                        }
                    )
                }
                entry<Route.Home> {
                    val isLibraryUnlocked by tokenViewModel.libraryViewModel.isLibraryUnlocked.collectAsState()
                    val lib by tokenViewModel.libraryViewModel.currentLibrary.collectAsState()

                    LaunchedEffect(isLibraryUnlocked, lib) {
                        if (!isLibraryUnlocked || lib == null) {
                            // 锁定回退：存在已记录的库 → 锁定页；否则回欢迎页
                            // showWelcome 仅用于扫描弹窗显隐，锁定页与欢迎页同样置 true
                            if (lib != null) {
                                navigator.replaceAll(listOf(Route.Locked))
                            } else {
                                navigator.replaceAll(listOf(Route.Welcome))
                            }
                            showWelcome = true
                        }
                    }

                    // 横屏主页：不显示四卡片导航，直接以三栏布局接管（列表 + 详情）
                    LaunchedEffect(Unit) {
                        tokenViewModel.passwordViewModel.setPasswordListMode(PasswordListMode.ALL_PASSWORDS, refreshNow = true)
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
                                    LandscapePasswordPanes(
                                        tokenViewModel = tokenViewModel,
                                        listMode = PasswordListMode.ALL_PASSWORDS,
                                        selectedEntryId = selectedEntryId,
                                        onEntryClick = { entryId ->
                                            selectedEntryId = entryId
                                        },
                                        onDetailBack = {
                                            selectedEntryId = null
                                        },
                                        onDetailDeleted = {
                                            selectedEntryId = null
                                        }
                                    )
                                }
                            }
                        )
                        MiuixPopupHost()
                    }
                }
                entry<Route.SecurityCheck> {
                    val isLibraryUnlocked by tokenViewModel.libraryViewModel.isLibraryUnlocked.collectAsState()
                    val lib by tokenViewModel.libraryViewModel.currentLibrary.collectAsState()

                    LaunchedEffect(isLibraryUnlocked, lib) {
                        if (!isLibraryUnlocked || lib == null) {
                            // 锁定回退：存在已记录的库 → 锁定页；否则回欢迎页
                            // showWelcome 仅用于扫描弹窗显隐，锁定页与欢迎页同样置 true
                            if (lib != null) {
                                navigator.replaceAll(listOf(Route.Locked))
                            } else {
                                navigator.replaceAll(listOf(Route.Welcome))
                            }
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
                    val isLibraryUnlocked by tokenViewModel.libraryViewModel.isLibraryUnlocked.collectAsState()
                    val lib by tokenViewModel.libraryViewModel.currentLibrary.collectAsState()

                    LaunchedEffect(isLibraryUnlocked, lib) {
                        if (!isLibraryUnlocked || lib == null) {
                            // 锁定回退：存在已记录的库 → 锁定页；否则回欢迎页
                            // showWelcome 仅用于扫描弹窗显隐，锁定页与欢迎页同样置 true
                            if (lib != null) {
                                navigator.replaceAll(listOf(Route.Locked))
                            } else {
                                navigator.replaceAll(listOf(Route.Welcome))
                            }
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
                entry<Route.GeneralSettings> {
                    val isLibraryUnlocked by tokenViewModel.libraryViewModel.isLibraryUnlocked.collectAsState()
                    val lib by tokenViewModel.libraryViewModel.currentLibrary.collectAsState()

                    LaunchedEffect(isLibraryUnlocked, lib) {
                        if (!isLibraryUnlocked || lib == null) {
                            // 锁定回退：存在已记录的库 → 锁定页；否则回欢迎页
                            // showWelcome 仅用于扫描弹窗显隐，锁定页与欢迎页同样置 true
                            if (lib != null) {
                                navigator.replaceAll(listOf(Route.Locked))
                            } else {
                                navigator.replaceAll(listOf(Route.Welcome))
                            }
                            showWelcome = true
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        FillerSettingsContent(
                            viewModel = tokenViewModel,
                            onNavigateBack = {
                                navigator.pop()
                            }
                        )
                        MiuixPopupHost()
                    }
                }
                entry<Route.SecuritySettings> {
                    val isLibraryUnlocked by tokenViewModel.libraryViewModel.isLibraryUnlocked.collectAsState()
                    val lib by tokenViewModel.libraryViewModel.currentLibrary.collectAsState()

                    LaunchedEffect(isLibraryUnlocked, lib) {
                        if (!isLibraryUnlocked || lib == null) {
                            // 锁定回退：存在已记录的库 → 锁定页；否则回欢迎页
                            // showWelcome 仅用于扫描弹窗显隐，锁定页与欢迎页同样置 true
                            if (lib != null) {
                                navigator.replaceAll(listOf(Route.Locked))
                            } else {
                                navigator.replaceAll(listOf(Route.Welcome))
                            }
                            showWelcome = true
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        SecuritySettingsContent(
                            viewModel = tokenViewModel,
                            onNavigateBack = {
                                navigator.pop()
                            }
                        )
                        MiuixPopupHost()
                    }
                }
                entry<Route.BackupSettings> {
                    val isLibraryUnlocked by tokenViewModel.libraryViewModel.isLibraryUnlocked.collectAsState()
                    val lib by tokenViewModel.libraryViewModel.currentLibrary.collectAsState()

                    LaunchedEffect(isLibraryUnlocked, lib) {
                        if (!isLibraryUnlocked || lib == null) {
                            // 锁定回退：存在已记录的库 → 锁定页；否则回欢迎页
                            // showWelcome 仅用于扫描弹窗显隐，锁定页与欢迎页同样置 true
                            if (lib != null) {
                                navigator.replaceAll(listOf(Route.Locked))
                            } else {
                                navigator.replaceAll(listOf(Route.Welcome))
                            }
                            showWelcome = true
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        BackupSettingsContent(
                            viewModel = tokenViewModel,
                            onCloudBindingClick = {
                                if (currentLibrary == null) {
                                    ToastUtils.showShortToast(context, "请先选择数据库文件")
                                } else {
                                    showCloudBindingDialog.value = true
                                }
                            },
                            onNavigateBack = {
                                navigator.pop()
                            }
                        )
                        MiuixPopupHost()
                    }
                }
                entry<Route.DatabaseSettings> {
                    val isLibraryUnlocked by tokenViewModel.libraryViewModel.isLibraryUnlocked.collectAsState()
                    val lib by tokenViewModel.libraryViewModel.currentLibrary.collectAsState()

                    LaunchedEffect(isLibraryUnlocked, lib) {
                        if (!isLibraryUnlocked || lib == null) {
                            // 锁定回退：存在已记录的库 → 锁定页；否则回欢迎页
                            // showWelcome 仅用于扫描弹窗显隐，锁定页与欢迎页同样置 true
                            if (lib != null) {
                                navigator.replaceAll(listOf(Route.Locked))
                            } else {
                                navigator.replaceAll(listOf(Route.Welcome))
                            }
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
                entry<Route.About> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AboutScreen(
                            onNavigateBack = {
                                navigator.pop()
                            }
                        )
                        MiuixPopupHost()
                    }
                }
                entry<Route.TokenList> {
                    val isLibraryUnlocked by tokenViewModel.libraryViewModel.isLibraryUnlocked.collectAsState()
                    val lib by tokenViewModel.libraryViewModel.currentLibrary.collectAsState()

                    LaunchedEffect(isLibraryUnlocked, lib) {
                        if (!isLibraryUnlocked || lib == null) {
                            // 锁定回退：存在已记录的库 → 锁定页；否则回欢迎页
                            // showWelcome 仅用于扫描弹窗显隐，锁定页与欢迎页同样置 true
                            if (lib != null) {
                                navigator.replaceAll(listOf(Route.Locked))
                            } else {
                                navigator.replaceAll(listOf(Route.Welcome))
                            }
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
                    val isLibraryUnlocked by tokenViewModel.libraryViewModel.isLibraryUnlocked.collectAsState()
                    val lib by tokenViewModel.libraryViewModel.currentLibrary.collectAsState()

                    LaunchedEffect(listMode) {
                        tokenViewModel.passwordViewModel.setPasswordListMode(listMode, refreshNow = true)
                        tokenViewModel.passwordViewModel.refreshRecentDeletedCount()
                    }

                    LaunchedEffect(isLibraryUnlocked, lib) {
                        if (!isLibraryUnlocked || lib == null) {
                            // 锁定回退：存在已记录的库 → 锁定页；否则回欢迎页
                            // showWelcome 仅用于扫描弹窗显隐，锁定页与欢迎页同样置 true
                            if (lib != null) {
                                navigator.replaceAll(listOf(Route.Locked))
                            } else {
                                navigator.replaceAll(listOf(Route.Welcome))
                            }
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
                        LandscapePasswordPanes(
                            tokenViewModel = tokenViewModel,
                            listMode = listMode,
                            selectedEntryId = selectedEntryId,
                            onEntryClick = { entryId ->
                                selectedEntryId = entryId
                            },
                            onDetailBack = {
                                selectedEntryId = null
                            },
                            onDetailDeleted = {
                                selectedEntryId = null
                            },
                            statusBarsPadding = true
                        )
                        MiuixPopupHost()
                    }
                }
                entry<Route.PasswordEntryDetail> { key ->
                    val entryId = key.entryId
                    val isLibraryUnlocked by tokenViewModel.libraryViewModel.isLibraryUnlocked.collectAsState()
                    val lib by tokenViewModel.libraryViewModel.currentLibrary.collectAsState()

                    LaunchedEffect(isLibraryUnlocked, lib) {
                        if (!isLibraryUnlocked || lib == null) {
                            // 锁定回退：存在已记录的库 → 锁定页；否则回欢迎页
                            // showWelcome 仅用于扫描弹窗显隐，锁定页与欢迎页同样置 true
                            if (lib != null) {
                                navigator.replaceAll(listOf(Route.Locked))
                            } else {
                                navigator.replaceAll(listOf(Route.Welcome))
                            }
                            showWelcome = true
                        }
                    }

                    // 横屏详情：中间列表跟随导航栏分类，右侧显示详情
                    LaunchedEffect(selectedNavIndex) {
                        val mode = if (selectedNavIndex == 3) {
                            PasswordListMode.RECENT_DELETED
                        } else {
                            PasswordListMode.ALL_PASSWORDS
                        }
                        tokenViewModel.passwordViewModel.setPasswordListMode(mode, refreshNow = true)
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        LandscapePasswordPanes(
                            tokenViewModel = tokenViewModel,
                            listMode = if (selectedNavIndex == 3) {
                                PasswordListMode.RECENT_DELETED
                            } else {
                                PasswordListMode.ALL_PASSWORDS
                            },
                            selectedEntryId = selectedEntryId ?: entryId,
                            onEntryClick = { clickedEntryId ->
                                selectedEntryId = clickedEntryId
                            },
                            onDetailBack = {
                                selectedEntryId = null
                            },
                            onDetailDeleted = {
                                selectedEntryId = null
                            },
                            statusBarsPadding = true
                        )
                        MiuixPopupHost()
                    }
                }
            }
        )
                }
            }
            } else {
                // 竖屏单栏布局
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
                entry<Route.Locked> {
                    val isLibraryUnlocked by tokenViewModel.libraryViewModel.isLibraryUnlocked.collectAsState()
                    val lib by tokenViewModel.libraryViewModel.currentLibrary.collectAsState()

                    LaunchedEffect(isLibraryUnlocked, lib) {
                        when {
                            isLibraryUnlocked && lib != null -> {
                                navigator.replaceAll(listOf(Route.Home))
                                showWelcome = false
                            }
                            lib == null && tokenViewModel.libraryViewModel.getCurrentLibrarySync() == null -> {
                                navigator.replaceAll(listOf(Route.Welcome))
                                showWelcome = true
                            }
                        }
                    }

                    LockedScreen(
                        tokenViewModel = tokenViewModel,
                        onUnlocked = {
                            navigator.replaceAll(listOf(Route.Home))
                            showWelcome = false
                        },
                        onSwitchLibrary = {
                            navigator.replaceAll(listOf(Route.Welcome))
                            showWelcome = true
                        }
                    )
                }
                entry<Route.Home> {
                    val isLibraryUnlocked by tokenViewModel.libraryViewModel.isLibraryUnlocked.collectAsState()
                    val lib by tokenViewModel.libraryViewModel.currentLibrary.collectAsState()

                    LaunchedEffect(isLibraryUnlocked, lib) {
                        if (!isLibraryUnlocked || lib == null) {
                            if (lib != null) {
                                navigator.replaceAll(listOf(Route.Locked))
                            } else {
                                navigator.replaceAll(listOf(Route.Welcome))
                            }
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
                    val isLibraryUnlocked by tokenViewModel.libraryViewModel.isLibraryUnlocked.collectAsState()
                    val lib by tokenViewModel.libraryViewModel.currentLibrary.collectAsState()

                    LaunchedEffect(isLibraryUnlocked, lib) {
                        if (!isLibraryUnlocked || lib == null) {
                            if (lib != null) {
                                navigator.replaceAll(listOf(Route.Locked))
                            } else {
                                navigator.replaceAll(listOf(Route.Welcome))
                            }
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
                    val isLibraryUnlocked by tokenViewModel.libraryViewModel.isLibraryUnlocked.collectAsState()
                    val lib by tokenViewModel.libraryViewModel.currentLibrary.collectAsState()

                    LaunchedEffect(isLibraryUnlocked, lib) {
                        if (!isLibraryUnlocked || lib == null) {
                            if (lib != null) {
                                navigator.replaceAll(listOf(Route.Locked))
                            } else {
                                navigator.replaceAll(listOf(Route.Welcome))
                            }
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
                entry<Route.GeneralSettings> {
                    val isLibraryUnlocked by tokenViewModel.libraryViewModel.isLibraryUnlocked.collectAsState()
                    val lib by tokenViewModel.libraryViewModel.currentLibrary.collectAsState()

                    LaunchedEffect(isLibraryUnlocked, lib) {
                        if (!isLibraryUnlocked || lib == null) {
                            if (lib != null) {
                                navigator.replaceAll(listOf(Route.Locked))
                            } else {
                                navigator.replaceAll(listOf(Route.Welcome))
                            }
                            showWelcome = true
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        FillerSettingsContent(
                            viewModel = tokenViewModel,
                            onNavigateBack = {
                                navigator.pop()
                            }
                        )
                        MiuixPopupHost()
                    }
                }
                entry<Route.SecuritySettings> {
                    val isLibraryUnlocked by tokenViewModel.libraryViewModel.isLibraryUnlocked.collectAsState()
                    val lib by tokenViewModel.libraryViewModel.currentLibrary.collectAsState()

                    LaunchedEffect(isLibraryUnlocked, lib) {
                        if (!isLibraryUnlocked || lib == null) {
                            if (lib != null) {
                                navigator.replaceAll(listOf(Route.Locked))
                            } else {
                                navigator.replaceAll(listOf(Route.Welcome))
                            }
                            showWelcome = true
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        SecuritySettingsContent(
                            viewModel = tokenViewModel,
                            onNavigateBack = {
                                navigator.pop()
                            }
                        )
                        MiuixPopupHost()
                    }
                }
                entry<Route.BackupSettings> {
                    val isLibraryUnlocked by tokenViewModel.libraryViewModel.isLibraryUnlocked.collectAsState()
                    val lib by tokenViewModel.libraryViewModel.currentLibrary.collectAsState()

                    LaunchedEffect(isLibraryUnlocked, lib) {
                        if (!isLibraryUnlocked || lib == null) {
                            if (lib != null) {
                                navigator.replaceAll(listOf(Route.Locked))
                            } else {
                                navigator.replaceAll(listOf(Route.Welcome))
                            }
                            showWelcome = true
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        BackupSettingsContent(
                            viewModel = tokenViewModel,
                            onCloudBindingClick = {
                                if (currentLibrary == null) {
                                    ToastUtils.showShortToast(context, "请先选择数据库文件")
                                } else {
                                    showCloudBindingDialog.value = true
                                }
                            },
                            onNavigateBack = {
                                navigator.pop()
                            }
                        )
                        MiuixPopupHost()
                    }
                }
                entry<Route.DatabaseSettings> {
                    val isLibraryUnlocked by tokenViewModel.libraryViewModel.isLibraryUnlocked.collectAsState()
                    val lib by tokenViewModel.libraryViewModel.currentLibrary.collectAsState()

                    LaunchedEffect(isLibraryUnlocked, lib) {
                        if (!isLibraryUnlocked || lib == null) {
                            if (lib != null) {
                                navigator.replaceAll(listOf(Route.Locked))
                            } else {
                                navigator.replaceAll(listOf(Route.Welcome))
                            }
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
                entry<Route.About> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AboutScreen(
                            onNavigateBack = {
                                navigator.pop()
                            }
                        )
                        MiuixPopupHost()
                    }
                }
                entry<Route.TokenList> {
                    val isLibraryUnlocked by tokenViewModel.libraryViewModel.isLibraryUnlocked.collectAsState()
                    val lib by tokenViewModel.libraryViewModel.currentLibrary.collectAsState()

                    LaunchedEffect(isLibraryUnlocked, lib) {
                        if (!isLibraryUnlocked || lib == null) {
                            if (lib != null) {
                                navigator.replaceAll(listOf(Route.Locked))
                            } else {
                                navigator.replaceAll(listOf(Route.Welcome))
                            }
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
                    val isLibraryUnlocked by tokenViewModel.libraryViewModel.isLibraryUnlocked.collectAsState()
                    val lib by tokenViewModel.libraryViewModel.currentLibrary.collectAsState()

                    LaunchedEffect(listMode) {
                        tokenViewModel.passwordViewModel.setPasswordListMode(listMode, refreshNow = true)
                        tokenViewModel.passwordViewModel.refreshRecentDeletedCount()
                    }

                    LaunchedEffect(isLibraryUnlocked, lib) {
                        if (!isLibraryUnlocked || lib == null) {
                            if (lib != null) {
                                navigator.replaceAll(listOf(Route.Locked))
                            } else {
                                navigator.replaceAll(listOf(Route.Welcome))
                            }
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
                    val isLibraryUnlocked by tokenViewModel.libraryViewModel.isLibraryUnlocked.collectAsState()
                    val lib by tokenViewModel.libraryViewModel.currentLibrary.collectAsState()

                    LaunchedEffect(isLibraryUnlocked, lib) {
                        if (!isLibraryUnlocked || lib == null) {
                            if (lib != null) {
                                navigator.replaceAll(listOf(Route.Locked))
                            } else {
                                navigator.replaceAll(listOf(Route.Welcome))
                            }
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
                    modifier = Modifier
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
