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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import xzynine.WebDAVPass.Android.ui.Screen.SettingsScreen
import xzynine.WebDAVPass.Android.ui.Dialog.WebDavConfigDialog
import xzynine.WebDAVPass.Android.theme.AppTheme
import xzynine.WebDAVPass.Android.ui.Screen.ScanTokenScreen
import xzynine.WebDAVPass.Android.ui.Screen.TokenListScreen
import xzynine.WebDAVPass.Android.ui.ViewModel.TokenViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import top.yukonga.miuix.kmp.basic.FabPosition
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.extra.SuperBottomSheet
import top.yukonga.miuix.kmp.icon.MiuixIcons
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.icon.extended.Months
import top.yukonga.miuix.kmp.icon.extended.Scan
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.MiuixPopupHost

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.setContent {
            AppTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    // 在顶层创建并共享一个 TokenViewModel，传递给各子界面
    val appContext = context.applicationContext
    val tokenViewModel = remember { TokenViewModel(appContext) }

    // 控制WebDAV配置弹窗的显示与隐藏
    var showWebDavDialog by remember { mutableStateOf(false) }

    // 导航状态管理 - 使用rememberSaveable保存状态，防止配置变更时丢失
    var selectedIndex by rememberSaveable { mutableStateOf(0) }

    // 导航项配置
    val navigationItems = listOf(
        NavigationItem("首页", MiuixIcons.Months),
        NavigationItem("设置", MiuixIcons.Settings)
    )

    // 控制扫描界面的显示与隐藏
    val showScanBottomSheet = remember { mutableStateOf(false) }



    // 基于Miuix Scaffold的主界面
    Scaffold(
        popupHost = { MiuixPopupHost() },
        topBar = {
            // 只有在首页时显示标题
            if (selectedIndex == 0) {
                TopAppBar(
                    title = "2FA 管理器",
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
                        TokenListScreen(tokenViewModel = tokenViewModel)
                    }

                    1 -> {
                        // 设置页面
                        SettingsScreen(
                            viewModel = tokenViewModel,
                            onWebDavConfigClick = { showWebDavDialog = true }
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

    // 使用外部文件中的WebDAV配置弹窗组件
    WebDavConfigDialog(
        showDialog = showWebDavDialog,
        onDismissRequest = { showWebDavDialog = false },
        onConfigSaved = { config ->
            // 在协程中保存配置到数据库
            CoroutineScope(Dispatchers.IO).launch {
                if (config.id == 0L) {
                    // 新配置，插入数据库
                    tokenViewModel.addWebDavConfig(config)
                } else {
                    // 现有配置，更新数据库
                    tokenViewModel.updateWebDavConfig(config)
                }
            }
        },
        existingConfig = null
    )

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

