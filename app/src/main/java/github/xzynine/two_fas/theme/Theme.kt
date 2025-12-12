package github.xzynine.two_fas.theme

import androidx.compose.runtime.Composable
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/**
 * 应用的主题配置
 */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    // 暂时使用固定的浅色主题
    val colorScheme = lightColorScheme()
    
    MiuixTheme(colors = colorScheme) {
        content()
    }
}