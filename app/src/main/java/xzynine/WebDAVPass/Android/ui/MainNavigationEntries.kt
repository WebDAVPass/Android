package xzynine.WebDAVPass.Android.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.nav.core.NavEntryBuilder
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.MiuixPopupHost
import xzylib.base.util.ToastUtils
import xzynine.WebDAVPass.Android.data.LibraryContext
import xzynine.WebDAVPass.Android.ui.Screen.AboutScreen
import xzynine.WebDAVPass.Android.ui.Screen.BackupSettingsContent
import xzynine.WebDAVPass.Android.ui.Screen.DatabaseSettingsScreen
import xzynine.WebDAVPass.Android.ui.Screen.FillerSettingsContent
import xzynine.WebDAVPass.Android.ui.Screen.SecurityCheckScreen
import xzynine.WebDAVPass.Android.ui.Screen.SecuritySettingsContent
import xzynine.WebDAVPass.Android.ui.Screen.SettingsScreen
import xzynine.WebDAVPass.Android.ui.Screen.TokenListScreen
import xzynine.WebDAVPass.Android.ui.viewmodel.TokenViewModel
import xzynine.WebDAVPass.Android.ui.navigation.Route
import xzynine.WebDAVPass.Android.ui.navigation.pop
import xzynine.WebDAVPass.Android.ui.navigation.push
import xzynine.WebDAVPass.Android.ui.navigation.replaceAll

/**
 * 横竖屏导航共用的路由入口（安全检测、设置系、令牌列表等），
 * 两套导航（LandscapeMainNavigation / PortraitMainNavigation）均在其 NavDisplay 的 entry DSL 中调用。
 *
 * 声明为 inline 的 NavEntryBuilder 扩展函数：inline 展开到调用点后，
 * 内部的 reified entry<Route.X>() 才能绑定到各调用处的具体路由类型。
 */
inline fun NavEntryBuilder.addCommonMainEntries(
    backStack: NavBackStack,
    tokenViewModel: TokenViewModel,
    currentLibrary: LibraryContext?,
    showWelcome: MutableState<Boolean>,
    showCloudBindingDialog: MutableState<Boolean>,
) {
    entry<Route.SecurityCheck> {
        val isLibraryUnlocked by tokenViewModel.libraryViewModel.isLibraryUnlocked.collectAsState()
        val lib by tokenViewModel.libraryViewModel.currentLibrary.collectAsState()

        LaunchedEffect(isLibraryUnlocked, lib) {
            if (!isLibraryUnlocked || lib == null) {
                // 锁定回退：存在已记录的库 → 锁定页；否则回欢迎页
                // showWelcome 仅用于扫描弹窗显隐，锁定页与欢迎页同样置 true
                if (lib != null) {
                    backStack.replaceAll(listOf(Route.Locked))
                } else {
                    backStack.replaceAll(listOf(Route.Welcome))
                }
                showWelcome.value = true
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            SecurityCheckScreen(
                tokenViewModel = tokenViewModel,
                onNavigateBack = {
                    backStack.pop()
                },
                onEntryClick = { entryId ->
                    backStack.push(Route.PasswordEntryDetail(entryId))
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
                    backStack.replaceAll(listOf(Route.Locked))
                } else {
                    backStack.replaceAll(listOf(Route.Welcome))
                }
                showWelcome.value = true
            }
        }

        val context = LocalContext.current
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
                    backStack.replaceAll(listOf(Route.Welcome))
                    showWelcome.value = true
                },
                onDatabaseSettingsClick = {
                    backStack.push(Route.DatabaseSettings)
                },
                onNavigateBack = {
                    backStack.pop()
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
                    backStack.replaceAll(listOf(Route.Locked))
                } else {
                    backStack.replaceAll(listOf(Route.Welcome))
                }
                showWelcome.value = true
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            FillerSettingsContent(
                viewModel = tokenViewModel,
                onNavigateBack = {
                    backStack.pop()
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
                    backStack.replaceAll(listOf(Route.Locked))
                } else {
                    backStack.replaceAll(listOf(Route.Welcome))
                }
                showWelcome.value = true
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            SecuritySettingsContent(
                viewModel = tokenViewModel,
                onNavigateBack = {
                    backStack.pop()
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
                    backStack.replaceAll(listOf(Route.Locked))
                } else {
                    backStack.replaceAll(listOf(Route.Welcome))
                }
                showWelcome.value = true
            }
        }

        val context = LocalContext.current
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
                    backStack.pop()
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
                    backStack.replaceAll(listOf(Route.Locked))
                } else {
                    backStack.replaceAll(listOf(Route.Welcome))
                }
                showWelcome.value = true
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            DatabaseSettingsScreen(
                viewModel = tokenViewModel,
                onNavigateBack = {
                    backStack.pop()
                }
            )
            MiuixPopupHost()
        }
    }
    entry<Route.About> {
        Box(modifier = Modifier.fillMaxSize()) {
            AboutScreen(
                onNavigateBack = {
                    backStack.pop()
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
                    backStack.replaceAll(listOf(Route.Locked))
                } else {
                    backStack.replaceAll(listOf(Route.Welcome))
                }
                showWelcome.value = true
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
                                backStack.pop()
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
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    ) {
                        TokenListScreen(
                            tokenViewModel = tokenViewModel,
                            onEntryClick = { entryId ->
                                backStack.push(Route.PasswordEntryDetail(entryId))
                            }
                        )
                    }
                }
            )
            MiuixPopupHost()
        }
    }
}
