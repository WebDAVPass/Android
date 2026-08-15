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
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import top.yukonga.miuix.kmp.basic.FabPosition
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Scan
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.MiuixPopupHost
import xzynine.WebDAVPass.Android.data.LibraryContext
import xzynine.WebDAVPass.Android.ui.Screen.HomeScreen
import xzynine.WebDAVPass.Android.ui.Screen.LockedScreen
import xzynine.WebDAVPass.Android.ui.Screen.PasswordEntryDetailScreen
import xzynine.WebDAVPass.Android.ui.Screen.PasswordListScreen
import xzynine.WebDAVPass.Android.ui.Screen.WelcomeScreen
import xzynine.WebDAVPass.Android.ui.ViewModel.PasswordListMode
import xzynine.WebDAVPass.Android.ui.ViewModel.TokenViewModel
import xzynine.WebDAVPass.Android.ui.navigation.Navigator
import xzynine.WebDAVPass.Android.ui.navigation.Route

@Composable
fun PortraitMainNavigation(
    navigator: Navigator,
    tokenViewModel: TokenViewModel,
    currentLibrary: LibraryContext?,
    showWelcome: MutableState<Boolean>,
    showCloudBindingDialog: MutableState<Boolean>,
    showScanBottomSheet: MutableState<Boolean>,
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
            addCommonMainEntries(
                scope = this,
                navigator = navigator,
                tokenViewModel = tokenViewModel,
                currentLibrary = currentLibrary,
                showWelcome = showWelcome,
                showCloudBindingDialog = showCloudBindingDialog,
            )
            entry<Route.Welcome> {
                WelcomeScreen(
                    tokenViewModel = tokenViewModel,
                    onEnterLibrary = {
                        navigator.replaceAll(listOf(Route.Home))
                        showWelcome.value = false
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
                            showWelcome.value = false
                        }
                        // 当前库被清除：回欢迎页选择/新建库。
                        // 用同步快照兜底：currentLibrary 流在冷启动首帧可能尚未预热，
                        // 此时 lib 为 null 但历史库实际存在，不应误弹回欢迎页。
                        lib == null && tokenViewModel.libraryViewModel.getCurrentLibrarySync() == null -> {
                            navigator.replaceAll(listOf(Route.Welcome))
                            showWelcome.value = true
                        }
                    }
                }

                LockedScreen(
                    tokenViewModel = tokenViewModel,
                    onUnlocked = {
                        navigator.replaceAll(listOf(Route.Home))
                        showWelcome.value = false
                    },
                    onSwitchLibrary = {
                        navigator.replaceAll(listOf(Route.Welcome))
                        showWelcome.value = true
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
                        showWelcome.value = true
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
                        showWelcome.value = true
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
                        // 锁定回退：存在已记录的库 → 锁定页；否则回欢迎页
                        // showWelcome 仅用于扫描弹窗显隐，锁定页与欢迎页同样置 true
                        if (lib != null) {
                            navigator.replaceAll(listOf(Route.Locked))
                        } else {
                            navigator.replaceAll(listOf(Route.Welcome))
                        }
                        showWelcome.value = true
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
