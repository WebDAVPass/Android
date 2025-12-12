package github.xzynine.two_fas

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import github.xzynine.two_fas.theme.AppTheme
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.icon.icons.useful.Settings

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
    
    // 基于Miuix Scaffold的主界面
    Scaffold(
        topBar = {
            TopAppBar(
                title = "2FA 管理器",
                navigationIcon = {},
                actions = {
                    IconButton(onClick = { showWebDavDialog = true }) {
                        Icon(
                            imageVector = MiuixIcons.Useful.Settings,
                            contentDescription = "设置"
                        )
                    }
                }
            )
        },
        content = {
            // 主界面内容区域，暂时置空
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(it),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "2FA 管理器主界面",
                    fontSize = 18.sp,
                    color = MiuixTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    )
    
    // 使用外部文件中的WebDAV配置弹窗组件
    WebDavConfigDialog(
        showDialog = showWebDavDialog,
        onDismissRequest = { showWebDavDialog = false }
    )
}