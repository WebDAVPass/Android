package xzynine.WebDAVPass.Android.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.VerticalDivider
import top.yukonga.miuix.kmp.theme.MiuixTheme
import xzynine.WebDAVPass.Android.ui.Screen.PasswordEntryDetailScreen
import xzynine.WebDAVPass.Android.ui.Screen.PasswordListScreen
import xzynine.WebDAVPass.Android.ui.ViewModel.PasswordListMode
import xzynine.WebDAVPass.Android.ui.ViewModel.TokenViewModel

/**
 * 横屏三栏布局的中间（密码列表）与右侧（密码详情）两栏。
 *
 * 与左侧 NavigationRail 配合构成完整三栏；点击列表项只选中条目，
 * 不产生导航跳转，详情栏随 selectedEntryId 联动显示。
 * 右侧详情栏始终占位（未选中条目时显示空态提示），保证三栏结构恒定。
 *
 * @param listMode 密码列表模式
 * @param selectedEntryId 当前选中的条目 id（null 时详情栏显示空态）
 * @param onEntryClick 点击列表项回调（用于更新选中状态）
 * @param onDetailBack 详情栏返回/关闭回调
 * @param onDetailDeleted 详情栏条目删除回调
 * @param statusBarsPadding 是否为顶部留出状态栏高度（路由自身无 Scaffold/TopAppBar 时开启）
 */
@Composable
fun LandscapePasswordPanes(
    tokenViewModel: TokenViewModel,
    listMode: PasswordListMode,
    selectedEntryId: Long?,
    onEntryClick: (Long) -> Unit,
    onDetailBack: () -> Unit,
    onDetailDeleted: () -> Unit,
    statusBarsPadding: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .then(if (statusBarsPadding) Modifier.statusBarsPadding() else Modifier)
    ) {
        // 中间：密码列表
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            PasswordListScreen(
                tokenViewModel = tokenViewModel,
                title = if (listMode == PasswordListMode.RECENT_DELETED) "最近删除" else "全部密码",
                emptyStateText = if (listMode == PasswordListMode.RECENT_DELETED) "暂无最近删除条目" else "暂无条目",
                emptySearchStateText = "无匹配条目",
                enableGroupNavigation = listMode == PasswordListMode.ALL_PASSWORDS,
                enableRecycleBinActions = listMode == PasswordListMode.RECENT_DELETED,
                onEntryClick = onEntryClick,
                onNavigateBack = {},
                isEmbedded = true
            )
        }
        // 列表与详情之间的分割线
        VerticalDivider()
        // 右侧：密码详情（始终占位；未选中条目时显示空态）
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            val entryId = selectedEntryId
            if (entryId != null) {
                PasswordEntryDetailScreen(
                    tokenViewModel = tokenViewModel,
                    entryId = entryId,
                    onNavigateBack = onDetailBack,
                    onDeleted = onDetailDeleted,
                    isEmbedded = true
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "选择一个条目查看详情",
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
