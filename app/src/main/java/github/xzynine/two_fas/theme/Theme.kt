package github.xzynine.two_fas.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ColorSchemeMode

/**
 * 应用的主题配置
 */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    // 使用 ThemeController 控制主题，支持系统深色模式
    val controller = remember { ThemeController(ColorSchemeMode.System) }
    
    MiuixTheme(controller = controller) {
        content()
    }
}