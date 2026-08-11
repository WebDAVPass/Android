package xzynine.WebDAVPass.Android.ui.component

import androidx.compose.runtime.Composable
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back

/**
 * 设置页统一顶栏（标准小标题形式，标题居中）。
 */
@Composable
fun SettingsTopAppBar(
    title: String,
    onNavigateBack: () -> Unit
) {
    SmallTopAppBar(
        title = title,
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = MiuixIcons.Back,
                    contentDescription = "返回"
                )
            }
        },
        defaultWindowInsetsPadding = true
    )
}
