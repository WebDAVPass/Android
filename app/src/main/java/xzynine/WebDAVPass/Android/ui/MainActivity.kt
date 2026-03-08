package xzynine.WebDAVPass.Android.ui

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
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
import xzylib.base.util.ToastUtils
import top.yukonga.miuix.kmp.basic.FabPosition
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.extra.SuperBottomSheet
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Scan
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.MiuixPopupHost
import xzynine.WebDAVPass.Android.ui.Screen.SettingsScreen
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
import xzynine.WebDAVPass.Android.ui.ViewModel.TokenViewModel
import xzynine.WebDAVPass.Android.ui.ViewModel.PasswordListMode
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.ui.NavDisplay
import androidx.compose.runtime.mutableStateListOf
import top.yukonga.miuix.kmp.icon.extended.Back

sealed interface AppScreen : NavKey {
    data object Home : AppScreen
    data object Settings : AppScreen
    data object Welcome : AppScreen
    data object TokenList : AppScreen
    data class PasswordList(val listMode: PasswordListMode) : AppScreen
    data class PasswordEntryDetail(val entryId: Long) : AppScreen
}

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

@Composable
fun MainScreen() {
    val context = LocalContext.current
    // 在顶层创建并共享一个 TokenViewModel，传递给各子界面。
    // 这里不交给 viewModel() 管理，避免多 Activity 共享同一实例时被任一 ViewModelStore 提前 clear。
    val tokenViewModel: TokenViewModel = remember(context.applicationContext) {
        TokenViewModel.getSharedInstance(context.applicationContext)
    }

    // 控制当前库云端绑定弹窗的显示与隐藏
    val showCloudBindingDialog = remember { mutableStateOf(false) }

    // 导航状态管理 - 使用rememberSaveable保存状态，防止配置变更时丢失
    var showWelcome by rememberSaveable { mutableStateOf(true) }
    val currentLibrary by tokenViewModel.currentLibrary.collectAsState()

    // 控制扫描界面的显示与隐藏
    val showScanBottomSheet = remember { mutableStateOf(false) }

    // Navigation3 导航栈
    val backStack = remember {
        if (showWelcome) {
            mutableStateListOf<NavKey>(AppScreen.Welcome)
        } else {
            mutableStateListOf<NavKey>(AppScreen.Home)
        }
    }

    // 导航条目提供者
    val entryProvider = remember(backStack) {
        entryProvider<NavKey> {
            entry(AppScreen.Welcome) {
                WelcomeScreen(
                    tokenViewModel = tokenViewModel,
                    onEnterLibrary = {
                        backStack.clear()
                        backStack.add(AppScreen.Home)
                        showWelcome = false
                    },
                    onBackPressed = {
                        if (backStack.size > 1) {
                            backStack.removeAt(backStack.lastIndex)
                            true
                        } else {
                            false
                        }
                    }
                )
            }
            entry(AppScreen.Home) {
                // 基于Miuix Scaffold的主界面
                Box(modifier = Modifier.fillMaxSize()) {
                    Scaffold(
                        popupHost = {},
                        topBar = {
                            TopAppBar(
                                title = currentLibrary?.displayName ?: "WebDAVPass",
                                navigationIcon = {},
                                actions = {
                                    // 设置按钮
                                    IconButton(onClick = {
                                        backStack.add(AppScreen.Settings)
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
                                        backStack.add(AppScreen.PasswordList(listMode))
                                    },
                                    onNavigateToTokenList = {
                                        backStack.add(AppScreen.TokenList)
                                    }
                                )
                            }
                        }
                    )
                    // 在 Scaffold 外部放置 MiuixPopupHost
                    MiuixPopupHost()
                }
            }
            entry(AppScreen.Settings) {
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
                            tokenViewModel.clearCurrentLibrarySelection()
                            backStack.clear()
                            backStack.add(AppScreen.Welcome)
                            showWelcome = true
                        },
                        onNavigateBack = {
                            backStack.removeAt(backStack.lastIndex)
                        }
                    )
                    MiuixPopupHost()
                }
            }
            entry(AppScreen.TokenList) {
                val isLibraryUnlocked by tokenViewModel.isLibraryUnlocked.collectAsState(false)
                val lib by tokenViewModel.currentLibrary.collectAsState(null)

                LaunchedEffect(isLibraryUnlocked, lib) {
                    if (!isLibraryUnlocked || lib == null) {
                        backStack.clear()
                        backStack.add(AppScreen.Welcome)
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
                                        backStack.removeAt(backStack.lastIndex)
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
                                        backStack.add(AppScreen.PasswordEntryDetail(entryId))
                                    }
                                )
                            }
                        }
                    )
                    MiuixPopupHost()
                }
            }
            entry<AppScreen.PasswordList> { key ->
                val listMode = key.listMode
                val isLibraryUnlocked by tokenViewModel.isLibraryUnlocked.collectAsState(false)
                val lib by tokenViewModel.currentLibrary.collectAsState(null)

                LaunchedEffect(listMode) {
                    tokenViewModel.setPasswordListMode(listMode, refreshNow = true)
                    tokenViewModel.refreshRecentDeletedCount()
                }

                LaunchedEffect(isLibraryUnlocked, lib) {
                    if (!isLibraryUnlocked || lib == null) {
                        backStack.clear()
                        backStack.add(AppScreen.Welcome)
                        showWelcome = true
                    }
                }

                DisposableEffect(listMode) {
                    onDispose {
                        tokenViewModel.resetPasswordGroupStackOnly()
                        if (listMode == PasswordListMode.RECENT_DELETED) {
                            tokenViewModel.setPasswordListMode(PasswordListMode.ALL_PASSWORDS, refreshNow = true)
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
                        onEntryClick = { entryId ->
                            backStack.add(AppScreen.PasswordEntryDetail(entryId))
                        },
                        onNavigateBack = {
                            backStack.removeAt(backStack.lastIndex)
                        }
                    )
                    MiuixPopupHost()
                }
            }
            entry<AppScreen.PasswordEntryDetail> { key ->
                val entryId = key.entryId
                val isLibraryUnlocked by tokenViewModel.isLibraryUnlocked.collectAsState(false)
                val lib by tokenViewModel.currentLibrary.collectAsState(null)

                LaunchedEffect(isLibraryUnlocked, lib) {
                    if (!isLibraryUnlocked || lib == null) {
                        backStack.clear()
                        backStack.add(AppScreen.Welcome)
                        showWelcome = true
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    PasswordEntryDetailScreen(
                        tokenViewModel = tokenViewModel,
                        entryId = entryId,
                        onNavigateBack = {
                            backStack.removeAt(backStack.lastIndex)
                        },
                        onDeleted = {
                            backStack.removeAt(backStack.lastIndex)
                        }
                    )
                    MiuixPopupHost()
                }
            }
        }
    }

    // 装饰导航条目
    val entries = rememberDecoratedNavEntries(
        backStack = backStack,
        entryProvider = entryProvider
    )

    // 渲染导航场景
    NavDisplay(
        entries = entries,
        onBack = {
            if (showCloudBindingDialog.value) {
                showCloudBindingDialog.value = false
                return@NavDisplay
            }
            if (showScanBottomSheet.value) {
                showScanBottomSheet.value = false
                return@NavDisplay
            }
            if (backStack.size > 1) {
                backStack.removeAt(backStack.lastIndex)
            }
        }
    )

    if (showCloudBindingDialog.value) {
        CloudLibraryDialog(
            tokenViewModel = tokenViewModel,
            mode = CloudMode.BIND,
            initialLibraryContext = currentLibrary,
            onDismiss = {
                showCloudBindingDialog.value = false
            },
            onSelected = { library, _ ->
                val saved = tokenViewModel.bindCurrentLibraryToCloud(library)
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
        // 扫描二维码底部抽屉
        SuperBottomSheet(
            show = showScanBottomSheet,
            title = "扫描二维码",
            onDismissRequest = {
                showScanBottomSheet.value = false
            },
            content = {
                // 扫描界面内容
                Box(
                    modifier = Modifier.Companion
                        .fillMaxWidth()
                        .height(500.dp) // 设置固定高度，避免全屏显示
                ) {
                    ScanTokenScreen(
                        tokenViewModel = tokenViewModel,
                        onTokenScanned = {
                            // 关闭抽屉，令牌列表将通过同一个 ViewModel 自动刷新
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
