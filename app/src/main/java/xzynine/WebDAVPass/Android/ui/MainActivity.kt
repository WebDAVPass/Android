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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme
import xzynine.WebDAVPass.Android.ui.navigation.LocalNavigator
import xzynine.WebDAVPass.Android.ui.navigation.Route
import xzynine.WebDAVPass.Android.ui.navigation.replaceAll
import xzynine.WebDAVPass.Android.theme.AppTheme
import xzynine.WebDAVPass.Android.theme.SetupSystemBars
import xzynine.WebDAVPass.Android.ui.Dialog.CloudLibraryDialog
import xzynine.WebDAVPass.Android.ui.Dialog.CloudMode
import xzynine.WebDAVPass.Android.ui.Dialog.ScanTokenScreen
import xzynine.WebDAVPass.Android.ui.Screen.OnboardingScreen
import xzynine.WebDAVPass.Android.ui.Screen.isOnboardingCompleted
import xzynine.WebDAVPass.Android.ui.viewmodel.TokenViewModel
import xzynine.WebDAVPass.Android.ui.ViewModel.PasswordListMode
import xzynine.WebDAVPass.Android.ui.component.CategoryNavigationItem

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
    val showWelcomeState = rememberSaveable { mutableStateOf(true) }
    var showWelcome by showWelcomeState
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
    val backStack = rememberNavBackStack<Route>(startRoute)

    // 横屏模式下导航项选择处理
    val handleNavigationItemSelected = remember(tokenViewModel, backStack) {
        { item: CategoryNavigationItem ->
            selectedEntryId = null
            when (item) {
                CategoryNavigationItem.ALL_PASSWORDS -> {
                    selectedNavIndex = 0
                    backStack.replaceAll(listOf(Route.PasswordList(PasswordListMode.ALL_PASSWORDS)))
                }
                CategoryNavigationItem.TOKENS -> {
                    selectedNavIndex = 1
                    backStack.replaceAll(listOf(Route.TokenList))
                }
                CategoryNavigationItem.SECURITY -> {
                    selectedNavIndex = 2
                    backStack.replaceAll(listOf(Route.SecurityCheck))
                }
                CategoryNavigationItem.RECENT_DELETED -> {
                    selectedNavIndex = 3
                    backStack.replaceAll(listOf(Route.PasswordList(PasswordListMode.RECENT_DELETED)))
                }
                CategoryNavigationItem.SETTINGS -> {
                    selectedNavIndex = 4
                    backStack.replaceAll(listOf(Route.Settings))
                }
            }
        }
    }

    CompositionLocalProvider(LocalNavigator provides backStack) {
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
            } else if (isLandscapeWideScreen && backStack.lastOrNull() !is Route.Welcome && backStack.lastOrNull() !is Route.Locked) {
                // 横屏三栏布局（欢迎页/锁定页不使用三栏）
                LandscapeMainNavigation(
                    backStack = backStack,
                    tokenViewModel = tokenViewModel,
                    currentLibrary = currentLibrary,
                    selectedNavIndex = selectedNavIndex,
                    onNavigationItemSelected = handleNavigationItemSelected,
                    railExpanded = configuration.screenWidthDp >= 1200,
                    selectedEntryId = selectedEntryId,
                    onSelectedEntryIdChange = { selectedEntryId = it },
                    showWelcome = showWelcomeState,
                    showCloudBindingDialog = showCloudBindingDialog,
                    showScanBottomSheet = showScanBottomSheet
                )
            } else {
                // 竖屏单栏布局
                PortraitMainNavigation(
                    backStack = backStack,
                    tokenViewModel = tokenViewModel,
                    currentLibrary = currentLibrary,
                    showWelcome = showWelcomeState,
                    showCloudBindingDialog = showCloudBindingDialog,
                    showScanBottomSheet = showScanBottomSheet
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
