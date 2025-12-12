package github.xzynine.two_fas.theme

import androidx.compose.runtime.Composable
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.lightColorScheme
import top.yukonga.miuix.kmp.theme.darkColorScheme

/**
 * 应用的主题配置
 */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    // 使用系统深色模式判断
    val isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    val colorScheme = if (isDarkTheme) darkColorScheme() else lightColorScheme()
    
    MiuixTheme(colors = colorScheme) {
        content()
    }
}