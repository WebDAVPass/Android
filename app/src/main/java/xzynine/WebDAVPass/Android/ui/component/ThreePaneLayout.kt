package xzynine.WebDAVPass.Android.ui.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 三栏布局容器
 *
 * @param navigationContent 左侧导航栏内容
 * @param listContent 中间列表内容
 * @param detailContent 右侧详情内容（可选，为null时不显示详情栏）
 */
@Composable
fun ThreePaneLayout(
    navigationContent: @Composable () -> Unit,
    listContent: @Composable () -> Unit,
    detailContent: (@Composable () -> Unit)? = null
) {
    Row(modifier = Modifier.fillMaxSize()) {
        // 左侧导航栏
        navigationContent()

        // 中间列表
        listContent()

        // 右侧详情（如果有）
        detailContent?.invoke()
    }
}
