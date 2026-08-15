package xzynine.WebDAVPass.Android.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import top.yukonga.miuix.kmp.basic.NavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailItem
import top.yukonga.miuix.kmp.basic.rememberNavigationRailState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.Recent
import xzynine.WebDAVPass.Android.ui.ViewModel.PasswordListMode

/**
 * 分类导航项定义
 */
enum class CategoryNavigationItem(
    val label: String,
    val icon: ImageVector,
    val listMode: PasswordListMode?
) {
    ALL_PASSWORDS("全部密码", MiuixIcons.ListView, PasswordListMode.ALL_PASSWORDS),
    TOKENS("动态令牌", MiuixIcons.Recent, null),
    SECURITY("安全性", MiuixIcons.Lock, null),
    RECENT_DELETED("最近删除", MiuixIcons.Delete, PasswordListMode.RECENT_DELETED)
}

/**
 * 横屏侧边导航栏
 */
@Composable
fun AppNavigationRail(
    selectedIndex: Int,
    onItemSelected: (CategoryNavigationItem) -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = false
) {
    val railState = rememberNavigationRailState(
        initialValue = if (expanded) {
            top.yukonga.miuix.kmp.basic.NavigationRailValue.Expanded
        } else {
            top.yukonga.miuix.kmp.basic.NavigationRailValue.Collapsed
        }
    )

    NavigationRail(
        state = railState,
        modifier = modifier
    ) {
        CategoryNavigationItem.entries.forEachIndexed { index, item ->
            NavigationRailItem(
                selected = selectedIndex == index,
                onClick = { onItemSelected(item) },
                icon = item.icon,
                label = item.label
            )
        }
    }
}
