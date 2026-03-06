package xzynine.WebDAVPass.Android.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
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
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.extra.SuperBottomSheet
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Months
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
import xzynine.WebDAVPass.Android.ui.ViewModel.TokenViewModel
import xzynine.WebDAVPass.Android.ui.utils.NavigationEventDispatcherProvider

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.setContent {
            NavigationEventDispatcherProvider {
                AppTheme {
                    // 设置系统栏外观
                    SetupSystemBars()
                    MainScreen()
                }
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
    var selectedIndex by rememberSaveable { mutableStateOf(0) }
    var showWelcome by rememberSaveable { mutableStateOf(true) }
    val currentLibrary by tokenViewModel.currentLibrary.collectAsState()

    // 导航项配置
    val navigationItems = listOf(
        NavigationItem("首页", MiuixIcons.Months),
        NavigationItem("设置", MiuixIcons.Settings)
    )

    // 控制扫描界面的显示与隐藏
    val showScanBottomSheet = remember { mutableStateOf(false) }



    if (showWelcome) {
        WelcomeScreen(
            tokenViewModel = tokenViewModel,
            onEnterLibrary = {
                showWelcome = false
            }
        )
    } else {
        // 基于Miuix Scaffold的主界面
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
            popupHost = {},
            topBar = {
                // 只有在首页时显示标题
                if (selectedIndex == 0) {
                    TopAppBar(
                        title = currentLibrary?.displayName ?: "WebDAVPass",
                        navigationIcon = {},
                        actions = {}
                    )
                }
            },
            floatingActionButton = {
                // 只有在首页时显示悬浮扫描按钮
                if (selectedIndex == 0) {
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
                }
            },
            floatingActionButtonPosition = FabPosition.Companion.End,
            content = { paddingValues ->
                // 主界面内容区域，根据选中的导航项显示不同内容
                Box(
                    modifier = Modifier.Companion
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    when (selectedIndex) {
                        0 -> {
                            HomeScreen(
                                tokenViewModel = tokenViewModel
                            )
                        }

                        1 -> {
                            // 设置页面
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
                                    selectedIndex = 0
                                    showWelcome = true
                                }
                            )
                        }
                    }
                }
            },
            bottomBar = {
                // 底部导航栏
                NavigationBar {
                    navigationItems.forEachIndexed { index, item ->
                        NavigationBarItem(
                            selected = selectedIndex == index,
                            onClick = { selectedIndex = index },
                            icon = item.icon,
                            label = item.label
                        )
                    }
                }
            }
            )
            // 在 Scaffold 外部放置 MiuixPopupHost
            MiuixPopupHost()
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

