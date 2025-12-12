package github.xzynine.two_fas

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationItem
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import github.xzynine.two_fas.theme.AppTheme
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.icon.icons.useful.Settings
import top.yukonga.miuix.kmp.icon.icons.useful.Save
import top.yukonga.miuix.kmp.icon.icons.useful.Move

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
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
    // 控制WebDAV配置弹窗的显示与隐藏
    var showWebDavDialog by remember { mutableStateOf(false) }
    
    // WebDAV配置状态
    var serverUrl by remember { mutableStateOf("https://dav.jianguoyun.com/dav/2fas_xzy/") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    
    // 导航状态管理
    var selectedIndex by remember { mutableStateOf(0) }
    
    // 导航项配置
    val navigationItems = listOf(
        NavigationItem("首页", MiuixIcons.Useful.Save),
        NavigationItem("文件", MiuixIcons.Useful.Move),
        NavigationItem("设置", MiuixIcons.Useful.Settings)
    )
    
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
        content = {
            // 主界面内容区域，根据选中的导航项显示不同内容
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                when (selectedIndex) {
                    0 -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(it)
                                .padding(top = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "2FA 管理器主界面",
                                fontSize = 18.sp,
                                color = MiuixTheme.colorScheme.onSecondaryContainer
                            )
                        }
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
                            onWebDavConfigClick = { showWebDavDialog = true }
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
}