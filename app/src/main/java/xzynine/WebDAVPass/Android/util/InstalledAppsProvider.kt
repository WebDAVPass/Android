package xzynine.WebDAVPass.Android.util

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xzylib.base.util.Logger
import java.io.ByteArrayOutputStream
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 本机已安装应用查询工具。
 *
 * 形态参考 NotifyRelay 的 `AppListHelper` + `LocalAppsViewModel`：
 * - [getInstalledApplications] 过滤系统应用与自身；
 * - [loadInstalledApps] 在 IO 线程读取、只保留可启动的应用并按应用名排序；
 * - [filterInstalledApps] 支持按应用名或包名搜索；
 * - [canQueryInstalledApps] / [isAppListPermissionGranted] 判定应用列表权限是否可用。
 *
 * 与 NotifyRelay 的差异：不落 Room 缓存、不做跨设备图标同步——密码库条目只需要本机
 * 一次性的包名 → 应用名/图标映射。
 */
object InstalledAppsProvider {
    private const val TAG = "InstalledAppsProvider"

    /** MIUI/澎湃在 QUERY_ALL_PACKAGES 之外额外要求的应用列表权限。 */
    private const val MIUI_QUERY_APPS_PERMISSION = "com.android.permission.GET_INSTALLED_APPS"

    /** 提供该权限的系统包名（据此判定是否为 MIUI/澎湃）。 */
    private const val MIUI_SECURITY_PACKAGE = "com.lbe.security.miui"

    /** 写入条目图标的应用图标边长（与品牌图标固化 `BRAND_ICON_SIZE_PX` 保持一致）。 */
    private const val APP_ICON_SIZE_PX = 96

    /**
     * 本机应用信息。
     *
     * @property appName 应用名（读取失败时回落为包名）
     * @property packageName 应用包名
     */
    data class InstalledAppInfo(
        val appName: String,
        val packageName: String,
    )

    /**
     * 应用列表加载状态。
     *
     * @property apps 已加载的应用列表
     * @property isLoading 是否正在读取
     * @property error 读取失败时的错误信息，成功为 null
     */
    data class InstalledAppsState(
        val apps: List<InstalledAppInfo> = emptyList(),
        val isLoading: Boolean = false,
        val error: String? = null,
    )

    /**
     * 获取已安装的用户应用（过滤系统应用与自身）。
     */
    fun getInstalledApplications(context: Context): List<ApplicationInfo> =
        try {
            val apps = context.packageManager.getInstalledApplications(0)
            apps.filter { appInfo ->
                val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val isUpdatedSystemApp = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                val isSelf = appInfo.packageName == context.packageName
                !isSystemApp && !isUpdatedSystemApp && !isSelf
            }
        } catch (e: Exception) {
            Logger.w(TAG, "获取已安装应用列表失败", e)
            emptyList()
        }

    /**
     * 在 IO 线程读取本机可启动的用户应用，并按应用名排序。
     *
     * 只保留有启动 Intent 的应用：条目关联的凭据总是面向用户可见的应用，
     * 无启动入口的宿主/服务类包名没有可选价值。
     */
    suspend fun loadInstalledApps(context: Context): InstalledAppsState =
        withContext(Dispatchers.IO) {
            try {
                val pm = context.packageManager
                val apps =
                    getInstalledApplications(context)
                        .mapNotNull { appInfo ->
                            if (pm.getLaunchIntentForPackage(appInfo.packageName) == null) {
                                null
                            } else {
                                InstalledAppInfo(
                                    appName = getApplicationLabel(context, appInfo.packageName),
                                    packageName = appInfo.packageName,
                                )
                            }
                        }.sortedBy { it.appName.lowercase() }
                InstalledAppsState(apps = apps)
            } catch (e: Exception) {
                Logger.w(TAG, "读取本机应用列表失败", e)
                InstalledAppsState(error = e.message ?: "读取应用列表失败")
            }
        }

    /**
     * 按关键字过滤应用列表：应用名或包名忽略大小写包含匹配；关键字为空时返回全部。
     */
    fun filterInstalledApps(
        apps: List<InstalledAppInfo>,
        query: String,
    ): List<InstalledAppInfo> {
        val keyword = query.trim()
        if (keyword.isEmpty()) return apps
        return apps.filter {
            it.appName.contains(keyword, ignoreCase = true) ||
                it.packageName.contains(keyword, ignoreCase = true)
        }
    }

    /**
     * 读取应用显示名；读取失败时回落为包名（与 NotifyRelay 行为一致）。
     */
    fun getApplicationLabel(
        context: Context,
        packageName: String,
    ): String =
        try {
            context.packageManager
                .getApplicationInfo(packageName, 0)
                .let { context.packageManager.getApplicationLabel(it).toString() }
        } catch (e: Exception) {
            Logger.w(TAG, "获取应用名失败: $packageName", e)
            packageName
        }

    /**
     * 是否能查询到本机应用列表（以能枚举出多于两个包作为判据）。
     */
    fun canQueryInstalledApps(context: Context): Boolean =
        try {
            context.packageManager.getInstalledApplications(0).size > 2
        } catch (e: Exception) {
            Logger.w(TAG, "检查应用列表可查询失败", e)
            false
        }

    /**
     * 应用列表权限是否可用。
     *
     * QUERY_ALL_PACKAGES 是清单权限、无运行时弹窗；但 MIUI/澎湃在此之上还要求
     * 在应用详情页显式授予「访问应用列表」（`GET_INSTALLED_APPS`），未授予时同样读不到应用列表。
     */
    fun isAppListPermissionGranted(context: Context): Boolean =
        canQueryInstalledApps(context) &&
            (
                !isMiuiOrPengpai(context) ||
                    ContextCompat.checkSelfPermission(context, MIUI_QUERY_APPS_PERMISSION) ==
                    PackageManager.PERMISSION_GRANTED
            )

    /**
     * MIUI/澎湃系统额外要求的应用列表权限名，供运行时权限弹窗使用；其他系统返回 null。
     *
     * 非 MIUI 系统没有这个权限，返回 null 以免弹出必然失败的权限请求（此时只能去应用详情页手动开启）。
     */
    fun miuiQueryAppsPermission(context: Context): String? = if (isMiuiOrPengpai(context)) MIUI_QUERY_APPS_PERMISSION else null

    /**
     * 读取应用图标（供应用选择器瓦片展示），失败返回 null。
     */
    suspend fun loadAppIconImage(
        context: Context,
        packageName: String,
    ): ImageBitmap? =
        withContext(Dispatchers.IO) {
            loadAppIconBitmap(context, packageName)?.asImageBitmap()
        }

    /**
     * 读取应用图标并编码为 PNG 字节（供写入条目自定义图标），失败返回 null。
     *
     * 按品牌图标固化的同一范式处理：等比缩放居中画进 [APP_ICON_SIZE_PX] 见方画布，
     * PNG 质量 100，体积远低于自定义图标 512KB 上限。
     */
    suspend fun loadAppIconPngBytes(
        context: Context,
        packageName: String,
    ): ByteArray? =
        withContext(Dispatchers.IO) {
            val drawable =
                try {
                    context.packageManager.getApplicationIcon(packageName)
                } catch (e: Exception) {
                    Logger.w(TAG, "读取应用图标失败: $packageName", e)
                    null
                } ?: return@withContext null

            val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: APP_ICON_SIZE_PX
            val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: APP_ICON_SIZE_PX
            val scale = min(APP_ICON_SIZE_PX.toFloat() / width, APP_ICON_SIZE_PX.toFloat() / height)
            val drawWidth = (width * scale).roundToInt().coerceAtLeast(1)
            val drawHeight = (height * scale).roundToInt().coerceAtLeast(1)
            val left = (APP_ICON_SIZE_PX - drawWidth) / 2
            val top = (APP_ICON_SIZE_PX - drawHeight) / 2
            val bitmap = Bitmap.createBitmap(APP_ICON_SIZE_PX, APP_ICON_SIZE_PX, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.bounds = android.graphics.Rect(left, top, left + drawWidth, top + drawHeight)
            drawable.draw(canvas)

            ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.toByteArray()
            }
        }

    /**
     * 尝试拉起指定包名的应用；无启动入口或启动失败返回 false。
     */
    fun launchApp(
        context: Context,
        packageName: String,
    ): Boolean =
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: Exception) {
            Logger.w(TAG, "拉起应用失败: $packageName", e)
            false
        }

    /**
     * 读取 Drawable 为位图：BitmapDrawable 直接取位图，其余按固有尺寸绘制（尺寸非法时兜底）。
     */
    private fun loadAppIconBitmap(
        context: Context,
        packageName: String,
    ): Bitmap? =
        try {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            if (drawable is BitmapDrawable) {
                drawable.bitmap
            } else {
                val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: APP_ICON_SIZE_PX
                val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: APP_ICON_SIZE_PX
                drawable.toBitmap(width, height, Bitmap.Config.ARGB_8888)
            }
        } catch (e: Exception) {
            Logger.w(TAG, "读取应用图标失败: $packageName", e)
            null
        }

    /**
     * 检测设备是否为 MIUI/澎湃：厂商为 Xiaomi，或系统声明了 MIUI 安全中心的应用列表权限。
     */
    private fun isMiuiOrPengpai(context: Context): Boolean {
        if (android.os.Build.MANUFACTURER
                .equals("Xiaomi", ignoreCase = true)
        ) {
            return true
        }
        return runCatching {
            context.packageManager
                .getPermissionInfo(MIUI_QUERY_APPS_PERMISSION, 0)
                .packageName == MIUI_SECURITY_PACKAGE
        }.getOrDefault(false)
    }
}
