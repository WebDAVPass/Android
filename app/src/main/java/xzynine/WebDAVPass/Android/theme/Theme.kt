package xzynine.WebDAVPass.Android.theme

import android.app.Activity
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.utils.*

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
 * 设置系统栏外观
 */
@Composable
fun SetupSystemBars() {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val colorScheme = MiuixTheme.colorScheme
    val barColor = colorScheme.background.toArgb()
    // 使用 LocalConfiguration.current 来判断当前是否为深色主题
    val isDarkTheme = configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES

    SideEffect {
        val activity = context as? Activity ?: return@SideEffect
        val win = activity.window
        val controller = WindowCompat.getInsetsController(win, win.decorView)

        // 设置状态栏和导航栏图标亮度
        controller.isAppearanceLightStatusBars = !isDarkTheme
        controller.isAppearanceLightNavigationBars = !isDarkTheme

        // 设置状态栏和导航栏颜色
        win.statusBarColor = barColor
        win.navigationBarColor = barColor

        // Android 10+ 关闭导航栏对比度强制
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            win.isNavigationBarContrastEnforced = false
        }
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
        val resourceId =
            context.resources.getIdentifier(
                "rounded_corner_radius_top",
                "dimen",
                "android",
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
