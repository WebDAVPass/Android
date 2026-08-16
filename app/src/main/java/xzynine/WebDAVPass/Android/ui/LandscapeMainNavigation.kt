package xzynine.WebDAVPass.Android.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Scan
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.MiuixPopupHost
import xzynine.WebDAVPass.Android.data.LibraryContext
import xzynine.WebDAVPass.Android.ui.ViewModel.PasswordListMode
import xzynine.WebDAVPass.Android.ui.viewmodel.TokenViewModel
import xzynine.WebDAVPass.Android.ui.component.AppNavigationRail
import xzynine.WebDAVPass.Android.ui.component.CategoryNavigationItem
import xzynine.WebDAVPass.Android.ui.component.LandscapePasswordPanes
import xzynine.WebDAVPass.Android.ui.navigation.Route
import xzynine.WebDAVPass.Android.ui.navigation.pop
import xzynine.WebDAVPass.Android.ui.navigation.replaceAll

@Composable
fun LandscapeMainNavigation(
    backStack: NavBackStack,
    tokenViewModel: TokenViewModel,
    currentLibrary: LibraryContext?,
    selectedNavIndex: Int,
    onNavigationItemSelected: (CategoryNavigationItem) -> Unit,
    railExpanded: Boolean,
    selectedEntryId: Long?,
    onSelectedEntryIdChange: (Long?) -> Unit,
    showWelcome: MutableState<Boolean>,
    showCloudBindingDialog: MutableState<Boolean>,
    showScanBottomSheet: MutableState<Boolean>,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        // 左侧导航栏（顶部为库名，不占内容区高度；设置为第五个导航项）
        AppNavigationRail(
            selectedIndex = selectedNavIndex,
            onItemSelected = onNavigationItemSelected,
            expanded = railExpanded,
            header = {
                Text(
                    text = currentLibrary?.displayName ?: "WebDAVPass",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        )

        // 内容区域（三栏模式的中间/右侧 pane）：
        // clipToBounds 防止转场图层越出 pane 覆盖导航栏；pane 位于屏幕中部，禁用屏幕圆角裁剪
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clipToBounds()
        ) {
            NavDisplay(
                backStack = backStack,
                // 多栏模式：pane 边在屏幕中部，设备屏幕圆角只属于物理屏幕边缘，此处禁用
                effects = NavDisplayEffects(
                    cornerClipRadius = 0.dp,
                    dimAmount = 0f,
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
                    if (selectedEntryId != null) {
                        onSelectedEntryIdChange(null)
                        return@NavDisplay
                    }
                    if (backStack.size > 1) {
                        backStack.pop()
                    }
                    // 栈底返回（回密码列表 tab/双击退出）由 MainScreen 的 BackHandler 统一处理
                },
            ) {
                // 欢迎页/锁定页在横屏下由 PortraitMainNavigation 渲染（MainScreen 路由分流），
                // 此处不注册；其余设置系路由由公共入口提供
                addCommonMainEntries(
                    backStack = backStack,
                    tokenViewModel = tokenViewModel,
                    currentLibrary = currentLibrary,
                    showWelcome = showWelcome,
                    showCloudBindingDialog = showCloudBindingDialog,
                )
                entry<Route.Home> {
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

                    // 横屏主页：不显示四卡片导航，直接以三栏布局接管（列表 + 详情）
                    // 库名与设置入口已移至左侧导航栏顶部，不再占内容区高度
                    LaunchedEffect(Unit) {
                        tokenViewModel.passwordViewModel.setPasswordListMode(PasswordListMode.ALL_PASSWORDS, refreshNow = true)
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        LandscapePasswordPanes(
                            tokenViewModel = tokenViewModel,
                            listMode = PasswordListMode.ALL_PASSWORDS,
                            selectedEntryId = selectedEntryId,
                            onEntryClick = { entryId ->
                                onSelectedEntryIdChange(entryId)
                            },
                            onDetailBack = {
                                onSelectedEntryIdChange(null)
                            },
                            onDetailDeleted = {
                                onSelectedEntryIdChange(null)
                            }
                        )
                        FloatingActionButton(
                            onClick = {
                                showScanBottomSheet.value = true
                            },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp)
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Scan,
                                contentDescription = "扫描二维码"
                            )
                        }
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
                                backStack.replaceAll(listOf(Route.Locked))
                            } else {
                                backStack.replaceAll(listOf(Route.Welcome))
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
                        LandscapePasswordPanes(
                            tokenViewModel = tokenViewModel,
                            listMode = listMode,
                            selectedEntryId = selectedEntryId,
                            onEntryClick = { entryId ->
                                onSelectedEntryIdChange(entryId)
                            },
                            onDetailBack = {
                                onSelectedEntryIdChange(null)
                            },
                            onDetailDeleted = {
                                onSelectedEntryIdChange(null)
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
                                backStack.replaceAll(listOf(Route.Locked))
                            } else {
                                backStack.replaceAll(listOf(Route.Welcome))
                            }
                            showWelcome.value = true
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
                                onSelectedEntryIdChange(clickedEntryId)
                            },
                            onDetailBack = {
                                onSelectedEntryIdChange(null)
                            },
                            onDetailDeleted = {
                                onSelectedEntryIdChange(null)
                            }
                        )
                        MiuixPopupHost()
                    }
                }
            }
        }
    }
}
