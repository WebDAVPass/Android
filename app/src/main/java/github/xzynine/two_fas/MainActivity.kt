package github.xzynine.two_fas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationItem
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import github.xzynine.two_fas.theme.AppTheme
import github.xzynine.two_fas.ui.ScanTokenScreen
import github.xzynine.two_fas.ui.TokenListScreen
import github.xzynine.two_fas.util.SampleData
import github.xzynine.two_fas.viewmodel.TokenViewModel
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.extra.SuperBottomSheet
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.icon.icons.useful.Settings
import top.yukonga.miuix.kmp.icon.icons.useful.Save
import top.yukonga.miuix.kmp.icon.icons.useful.Move
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.icon.icons.useful.Scan

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
    
    // 控制WebDAV配置弹窗的显示与隐藏
    var showWebDavDialog by remember { mutableStateOf(false) }
    
    // WebDAV配置状态
    var serverUrl by remember { mutableStateOf("https://dav.jianguoyun.com/dav/2fas_xzy/") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    
    // 导航状态管理 - 使用rememberSaveable保存状态，防止配置变更时丢失
    var selectedIndex by rememberSaveable { mutableStateOf(0) }
    
    // 导航项配置
    val navigationItems = listOf(
        NavigationItem("首页", MiuixIcons.Useful.Save),
        NavigationItem("文件", MiuixIcons.Useful.Move),
        NavigationItem("设置", MiuixIcons.Useful.Settings)
    )
    
    // 控制扫描界面的显示与隐藏
    var showScanBottomSheet by remember { mutableStateOf(false) }
    
    // 基于Miuix Scaffold的主界面
    Scaffold(
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
                        showScanBottomSheet = true
                    }
                ) {
                    Icon(
                        imageVector = MiuixIcons.Useful.Scan,
                        contentDescription = "扫描二维码"
                    )
                }
            }
        },
        floatingActionButtonPosition = FabPosition.End,
        content = { paddingValues ->
            // 主界面内容区域，根据选中的导航项显示不同内容
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when (selectedIndex) {
                    0 -> {
                        TokenListScreen()
                    }
                    1 -> {
                        // 文件浏览器页面
                        FileBrowserScreen(
                            serverUrl = serverUrl,
                            username = username,
                            password = password
                        )
                    }
                    2 -> {
                        // 设置页面
                        SettingsScreen(
                            onWebDavConfigClick = { showWebDavDialog = true },
                            onAddSampleDataClick = { addSampleData(context) }
                        )
                    }
                }
            }
        },
        bottomBar = {
            // 底部导航栏
            NavigationBar(
                items = navigationItems,
                selected = selectedIndex,
                onClick = { selectedIndex = it }
            )
        }
    )
    
    // 使用外部文件中的WebDAV配置弹窗组件
    WebDavConfigDialog(
        showDialog = showWebDavDialog,
        onDismissRequest = { showWebDavDialog = false },
        onConfigSaved = { url, user, pwd ->
            serverUrl = url
            username = user
            password = pwd
        }
    )
    
    // 扫描二维码底部抽屉
    SuperBottomSheet(
        show = remember { mutableStateOf(showScanBottomSheet) },
        title = "扫描二维码",
        onDismissRequest = { showScanBottomSheet = false },
        content = {
            // 扫描界面内容
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(500.dp) // 设置固定高度，避免全屏显示
            ) {
                ScanTokenScreen(
                    onTokenScanned = {
                        showScanBottomSheet = false
                    },
                    onDismiss = {
                        showScanBottomSheet = false
                    }
                )
            }
        }
    )
}

/**
 * 添加示例数据到数据库
 */
private fun addSampleData(context: android.content.Context) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val tokenViewModel = TokenViewModel(context)
            val sampleTokens = SampleData.generateSampleTokens()
            
            sampleTokens.forEach { token ->
                tokenViewModel.addToken(token)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}