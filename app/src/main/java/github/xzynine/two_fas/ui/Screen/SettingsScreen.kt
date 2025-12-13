package github.xzynine.two_fas.ui.Screen

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import github.xzynine.two_fas.theme.getAppRoundedCorner
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.icons.useful.Personal

/**
 * 设置界面组件
 * @param onWebDavConfigClick 点击WebDAV配置的回调
 * @param onAddSampleDataClick 点击添加示例数据的回调
 */
@Composable
fun SettingsScreen(
    onWebDavConfigClick: () -> Unit,
    onAddSampleDataClick: () -> Unit
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
            modifier = Modifier.Companion
                .fillMaxSize()
                .padding(it)
                .padding(16.dp)
        ) {
            BasicComponent(
                title = "WebDAV 配置",
                leftAction = {
                    Icon(
                        modifier = Modifier.Companion.padding(end = 16.dp),
                        imageVector = MiuixIcons.Useful.Personal,
                        contentDescription = "WebDAV 配置",
                    )
                },
                onClick = onWebDavConfigClick,
                modifier = Modifier.Companion
                    .fillMaxWidth()
                    .border(1.dp, Color.Companion.LightGray, RoundedCornerShape(cornerRadius))
            )

            Spacer(modifier = Modifier.Companion.height(16.dp))

            // 示例数据按钮（主要用于调试）
            BasicComponent(
                title = "添加示例数据",
                leftAction = {
                    Icon(
                        modifier = Modifier.Companion.padding(end = 16.dp),
                        imageVector = MiuixIcons.Useful.Personal,
                        contentDescription = "添加示例数据",
                    )
                },
                onClick = onAddSampleDataClick,
                modifier = Modifier.Companion
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        Color.Companion.LightGray,
                        androidx.compose.foundation.shape.RoundedCornerShape(cornerRadius)
                    )
            )
        }
    }
}