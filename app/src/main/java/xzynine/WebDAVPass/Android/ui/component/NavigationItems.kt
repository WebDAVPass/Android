package xzynine.WebDAVPass.Android.ui.component

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import top.yukonga.miuix.kmp.basic.NavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailItem
import top.yukonga.miuix.kmp.basic.rememberNavigationRailState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Recent
import top.yukonga.miuix.kmp.icon.extended.Settings

/**
 * 分类导航项定义
 */
enum class CategoryNavigationItem(
    val label: String,
    val icon: ImageVector,
) {
    ALL_PASSWORDS("全部密码", MiuixIcons.ListView),
    TOKENS("动态令牌", MiuixIcons.Recent),
    SECURITY("安全性", MiuixIcons.Lock),
    RECENT_DELETED("最近删除", MiuixIcons.Delete),
    SETTINGS("设置", MiuixIcons.Settings),
}

/**
 * 横屏侧边导航栏
 *
 * @param header 顶部区域内容（如库名、设置入口），不占用内容区高度
 */
@Composable
fun AppNavigationRail(
    selectedIndex: Int,
    onItemSelected: (CategoryNavigationItem) -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
    header: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val railState =
        rememberNavigationRailState(
            initialValue =
                if (expanded) {
                    top.yukonga.miuix.kmp.basic.NavigationRailValue.Expanded
                } else {
                    top.yukonga.miuix.kmp.basic.NavigationRailValue.Collapsed
                },
        )
    // initialValue 仅在首次创建时读取，窗口宽度变化后需主动同步展开/收起
    LaunchedEffect(expanded) {
        if (expanded) {
            railState.expand()
        } else {
            railState.collapse()
        }
    }

    NavigationRail(
        state = railState,
        header = header,
        modifier = modifier,
    ) {
        CategoryNavigationItem.entries.forEachIndexed { index, item ->
            NavigationRailItem(
                selected = selectedIndex == index,
                onClick = { onItemSelected(item) },
                icon = item.icon,
                label = item.label,
            )
        }
    }
}
