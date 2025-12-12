package github.xzynine.two_fas.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.utils.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import kotlin.math.max

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

/**
 * 获取统一的圆角半径
 * 1. 首先尝试使用 Miuix 框架提供的小米设备圆角获取功能
 * 2. 若失败，尝试直接从 MIUI 系统资源获取 rounded_corner_radius_top 值
 * 3. 最后当所有尝试失败时，使用 16.dp 作为预设值
 * 确保跨平台统一性和小米设备的最佳适配
 * @return 统一的圆角半径值
 */
@Composable
fun getAppRoundedCorner(): Dp {
    // 1. 尝试使用 Miuix 框架提供的小米设备圆角获取功能
    val miuixCorner = getRoundedCorner()
    if (miuixCorner.value > 0) {
        return miuixCorner
    }
    
    // 2. 尝试直接从 MIUI 系统资源获取圆角值（仅 Android 平台）
    val context = LocalContext.current
    return try {
        // 获取 MIUI 系统资源中的屏幕上方圆角值
        val resourceId = context.resources.getIdentifier(
            "rounded_corner_radius_top",
            "dimen",
            "android"
        )
        
        if (resourceId > 0) {
            // 将像素值转换为 Dp 值
            val pixelRadius = context.resources.getDimensionPixelSize(resourceId).toFloat()
            val dpRadius = pixelRadius / context.resources.displayMetrics.density
            if (dpRadius > 0) {
                return dpRadius.dp
            }
        }
        
        // 3. 所有尝试失败，使用 16.dp 作为预设值
        16.dp
    } catch (e: Exception) {
        // 出现异常时使用预设值
        16.dp
    }
}
