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
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
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
import xzynine.WebDAVPass.Android.ui.navigation.Navigator
import xzynine.WebDAVPass.Android.ui.navigation.Route

/**
 * 横竖屏导航共用的路由入口（安全检测、设置系、令牌列表等），
 * 两套导航（LandscapeMainNavigation / PortraitMainNavigation）均在其 entryProvider 中调用。
 *
 * @param scope entryProvider 的 DSL 作用域（调用处传入 entryProvider 块的 this）。
 *   不声明为 EntryProviderScope 的扩展函数：调用处 entryProvider 的 T 由 backStack
 *   （SnapshotStateList<NavKey>）推断为 NavKey，声明为 EntryProviderScope<Route> 的扩展
 *   在调用点会因 receiver 类型不匹配而无法解析；类型参数固定为 NavKey 后，
 *   泛型函数内部仍无法证明 Route.X : T，故路由注册改用 addEntryProvider(key) 值版本。
 */
fun addCommonMainEntries(
    scope: EntryProviderScope<NavKey>,
    navigator: Navigator,
    tokenViewModel: TokenViewModel,
    currentLibrary: LibraryContext?,
    showWelcome: MutableState<Boolean>,
    showCloudBindingDialog: MutableState<Boolean>,
) {
    // 注：用 addEntryProvider(key) 而非 reified 版本 entry<Route.X>()——
    // 此处 T 由 NavDisplay 的 backStack 推断为 NavKey，reified 版本要求 Route.X : T 无法在泛型函数中保证；
    // 公共路由均为 data object（单例），按 key 匹配与按类匹配等价。
    scope.addEntryProvider(key = Route.SecurityCheck) {
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
    scope.addEntryProvider(key = Route.Settings) {
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
                    navigator.replaceAll(listOf(Route.Welcome))
                    showWelcome.value = true
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
    scope.addEntryProvider(key = Route.GeneralSettings) {
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
            FillerSettingsContent(
                viewModel = tokenViewModel,
                onNavigateBack = {
                    navigator.pop()
                }
            )
            MiuixPopupHost()
        }
    }
    scope.addEntryProvider(key = Route.SecuritySettings) {
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
            SecuritySettingsContent(
                viewModel = tokenViewModel,
                onNavigateBack = {
                    navigator.pop()
                }
            )
            MiuixPopupHost()
        }
    }
    scope.addEntryProvider(key = Route.BackupSettings) {
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
                    navigator.pop()
                }
            )
            MiuixPopupHost()
        }
    }
    scope.addEntryProvider(key = Route.DatabaseSettings) {
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
            DatabaseSettingsScreen(
                viewModel = tokenViewModel,
                onNavigateBack = {
                    navigator.pop()
                }
            )
            MiuixPopupHost()
        }
    }
    scope.addEntryProvider(key = Route.About) {
        Box(modifier = Modifier.fillMaxSize()) {
            AboutScreen(
                onNavigateBack = {
                    navigator.pop()
                }
            )
            MiuixPopupHost()
        }
    }
    scope.addEntryProvider(key = Route.TokenList) {
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
                        modifier = Modifier
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
}
