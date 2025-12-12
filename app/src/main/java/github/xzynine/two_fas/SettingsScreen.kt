package github.xzynine.two_fas

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.icons.useful.Personal
import github.xzynine.two_fas.theme.getAppRoundedCorner



/**
 * 设置界面组件
 * @param onWebDavConfigClick 点击WebDAV配置的回调
 */
@Composable
fun SettingsScreen(
    onWebDavConfigClick: () -> Unit
) {
    // 获取统一的圆角半径
    val cornerRadius = getAppRoundedCorner()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = "设置",
                navigationIcon = {},
                actions = {},
                defaultWindowInsetsPadding = true
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
                .padding(16.dp)
        ) {
            BasicComponent(
                title = "WebDAV 配置",
                leftAction = {
                    Icon(
                        modifier = Modifier.padding(end = 16.dp),
                        imageVector = MiuixIcons.Useful.Personal,
                        contentDescription = "WebDAV 配置",
                    )
                },
                onClick = onWebDavConfigClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color.LightGray, RoundedCornerShape(cornerRadius))
            )
        }
    }
}
