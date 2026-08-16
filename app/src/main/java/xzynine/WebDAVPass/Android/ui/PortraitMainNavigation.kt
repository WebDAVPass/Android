package xzynine.WebDAVPass.Android.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.FabPosition
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Scan
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.MiuixPopupHost
import xzynine.WebDAVPass.Android.data.LibraryContext
import xzynine.WebDAVPass.Android.ui.Screen.HomeScreen
import xzynine.WebDAVPass.Android.ui.Screen.LockedScreen
import xzynine.WebDAVPass.Android.ui.Screen.PasswordEntryDetailScreen
import xzynine.WebDAVPass.Android.ui.Screen.PasswordListScreen
import xzynine.WebDAVPass.Android.ui.Screen.WelcomeScreen
import xzynine.WebDAVPass.Android.ui.ViewModel.PasswordListMode
import xzynine.WebDAVPass.Android.ui.viewmodel.TokenViewModel
import xzynine.WebDAVPass.Android.ui.navigation.Route
import xzynine.WebDAVPass.Android.ui.navigation.pop
import xzynine.WebDAVPass.Android.ui.navigation.push
import xzynine.WebDAVPass.Android.ui.navigation.replaceAll

@Composable
fun PortraitMainNavigation(
    backStack: NavBackStack,
    tokenViewModel: TokenViewModel,
    currentLibrary: LibraryContext?,
    showWelcome: MutableState<Boolean>,
    showCloudBindingDialog: MutableState<Boolean>,
    showScanBottomSheet: MutableState<Boolean>,
) {
    NavDisplay(
        backStack = backStack,
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
                backStack.pop()
            }
            // 栈底返回（双击退出/回主页）由 MainScreen 的 BackHandler 统一处理
        },
    ) {
        addCommonMainEntries(
            backStack = backStack,
            tokenViewModel = tokenViewModel,
            currentLibrary = currentLibrary,
            showWelcome = showWelcome,
            showCloudBindingDialog = showCloudBindingDialog,
        )
        entry<Route.Welcome> {
            WelcomeScreen(
                tokenViewModel = tokenViewModel,
                onEnterLibrary = {
                    backStack.replaceAll(listOf(Route.Home))
                    showWelcome.value = false
                },
                onBackPressed = {
                    if (backStack.size > 1) {
                        backStack.pop()
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
                        backStack.replaceAll(listOf(Route.Home))
                        showWelcome.value = false
                    }
                    // 当前库被清除：回欢迎页选择/新建库。
                    // 用同步快照兜底：currentLibrary 流在冷启动首帧可能尚未预热，
                    // 此时 lib 为 null 但历史库实际存在，不应误弹回欢迎页。
                    // 读库涉及 Room，放 IO 线程执行避免阻塞主线程。
                    lib == null && withContext(Dispatchers.IO) {
                        tokenViewModel.libraryViewModel.getCurrentLibrarySync() == null
                    } -> {
                        backStack.replaceAll(listOf(Route.Welcome))
                        showWelcome.value = true
                    }
                }
            }

            LockedScreen(
                tokenViewModel = tokenViewModel,
                onUnlocked = {
                    backStack.replaceAll(listOf(Route.Home))
                    showWelcome.value = false
                },
                onSwitchLibrary = {
                    backStack.replaceAll(listOf(Route.Welcome))
                    showWelcome.value = true
                }
            )
        }
        entry<Route.Home> {
            LibraryLockGuard(backStack = backStack, tokenViewModel = tokenViewModel, showWelcome = showWelcome)

            Box(modifier = Modifier.fillMaxSize()) {
                Scaffold(
                    popupHost = {},
                    topBar = {
                        TopAppBar(
                            title = currentLibrary?.displayName ?: "WebDAVPass",
                            navigationIcon = {},
                            actions = {
                                IconButton(onClick = {
                                    backStack.push(Route.Settings)
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
                    floatingActionButtonPosition = FabPosition.End,
                    content = { paddingValues ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(paddingValues)
                        ) {
                            HomeScreen(
                                tokenViewModel = tokenViewModel,
                                onNavigateToPasswordList = { listMode ->
                                    backStack.push(Route.PasswordList(listMode))
                                },
                                onNavigateToTokenList = {
                                    backStack.push(Route.TokenList)
                                },
                                onNavigateToSecurityCheck = {
                                    backStack.push(Route.SecurityCheck)
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

            LaunchedEffect(listMode) {
                tokenViewModel.passwordViewModel.setPasswordListMode(listMode, refreshNow = true)
                tokenViewModel.passwordViewModel.refreshRecentDeletedCount()
            }

            LibraryLockGuard(backStack = backStack, tokenViewModel = tokenViewModel, showWelcome = showWelcome)

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
                        backStack.push(Route.PasswordEntryDetail(entryId))
                    },
                    onNavigateBack = {
                        backStack.pop()
                    }
                )
                MiuixPopupHost()
            }
        }
        entry<Route.PasswordEntryDetail> { key ->
            val entryId = key.entryId
            LibraryLockGuard(backStack = backStack, tokenViewModel = tokenViewModel, showWelcome = showWelcome)

            Box(modifier = Modifier.fillMaxSize()) {
                PasswordEntryDetailScreen(
                    tokenViewModel = tokenViewModel,
                    entryId = entryId,
                    onNavigateBack = {
                        backStack.pop()
                    },
                    onDeleted = {
                        backStack.pop()
                    }
                )
                MiuixPopupHost()
            }
        }
    }
}
