package xzynine.WebDAVPass.Android.ui.Dialog

import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import xzylib.base.util.IntentUtils
import xzynine.WebDAVPass.Android.util.InstalledAppsProvider
import xzynine.WebDAVPass.Android.util.InstalledAppsProvider.InstalledAppInfo

/**
 * 应用选择对话框（条目「应用」字段的取包名入口）。
 *
 * 界面形态参考 NotifyRelay 的本地应用列表（`LocalAppsContent` + `LocalAppItem`）：
 * 图标瓦片网格 + 应用名、加载/空/错误三态、按应用名或包名搜索、图标内存缓存；
 * 承载方式沿用本项目既有的 miuix 弹窗（与 `IconPickerDialog` 一致的网格高度上限）。
 *
 * 未获得应用列表权限时不加载列表，而是提示并提供跳转应用详情页的入口。
 *
 * @param selectedPackageName 当前已关联的包名，用于高亮选中项
 * @param onPick 选中应用回调（点击瓦片即选中并关闭）
 */
@Composable
fun AppPickerDialog(
    show: Boolean,
    selectedPackageName: String,
    onDismiss: () -> Unit,
    onPick: (InstalledAppInfo) -> Unit,
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf<List<InstalledAppInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var permissionGranted by remember { mutableStateOf(true) }
    val iconCache = remember { mutableStateMapOf<String, ImageBitmap?>() }

    // 每次打开重新判定权限并加载列表；权限在系统设置中授予后回到本弹窗也能立即生效
    LaunchedEffect(show) {
        if (!show) return@LaunchedEffect
        query = ""
        permissionGranted = InstalledAppsProvider.isAppListPermissionGranted(context)
        if (!permissionGranted) return@LaunchedEffect
        isLoading = true
        val state = InstalledAppsProvider.loadInstalledApps(context)
        apps = state.apps
        errorText = state.error
        isLoading = false
    }

    // 图标缓存：与 NotifyRelay 相同策略，列表变化时逐个补齐已缓存的图标
    LaunchedEffect(apps) {
        apps.forEach { app ->
            if (!iconCache.containsKey(app.packageName)) {
                iconCache[app.packageName] = InstalledAppsProvider.loadAppIconImage(context, app.packageName)
            }
        }
    }

    val visibleApps = InstalledAppsProvider.filterInstalledApps(apps, query)

    WindowDialog(
        title = "选择应用",
        summary = "选择后自动写入包名，并为条目应用该应用的图标",
        show = show,
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextField(
                value = query,
                onValueChange = { query = it },
                label = "搜索应用名或包名",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    !permissionGranted ->
                        AppPickerHint(
                            text = "未获得应用列表权限，无法列出本机应用。请在应用详情页的权限管理中开启「访问应用列表」。",
                            actionText = "去授权",
                            onAction = {
                                IntentUtils.startActivity(
                                    context = context,
                                    action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    data = Uri.fromParts("package", context.packageName, null),
                                    addNewTaskFlag = true,
                                )
                            },
                        )

                    isLoading -> CircularProgressIndicator()

                    errorText != null -> AppPickerHint(text = errorText ?: "")

                    visibleApps.isEmpty() ->
                        AppPickerHint(
                            text = if (query.isBlank()) "暂无应用" else "未找到匹配「$query」的应用",
                        )

                    else ->
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(80.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            items(visibleApps, key = { it.packageName }) { app ->
                                AppPickerItem(
                                    app = app,
                                    iconBitmap = iconCache[app.packageName],
                                    selected = app.packageName == selectedPackageName,
                                    onClick = { onPick(app) },
                                )
                            }
                        }
                }
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    text = "取消",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * 选择器占位/提示区域（无权限、无结果、读取失败共用）。
 */
@Composable
private fun AppPickerHint(
    text: String,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 12.dp),
    ) {
        Icon(
            imageVector = MiuixIcons.Settings,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = MiuixTheme.colorScheme.onSurfaceSecondary,
        )
        Text(
            text = text,
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurfaceSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (actionText != null && onAction != null) {
            TextButton(text = actionText, onClick = onAction)
        }
    }
}

/**
 * 应用瓦片（56dp 圆角图标 + 两行居中的应用名），对齐 NotifyRelay 的 `LocalAppItem`。
 */
@Composable
private fun AppPickerItem(
    app: InstalledAppInfo,
    iconBitmap: ImageBitmap?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MiuixTheme.colorScheme.surfaceVariant)
                    .then(
                        if (selected) {
                            Modifier.border(
                                width = 2.dp,
                                color = MiuixTheme.colorScheme.primary,
                                shape = RoundedCornerShape(12.dp),
                            )
                        } else {
                            Modifier
                        },
                    ),
            contentAlignment = Alignment.Center,
        ) {
            if (iconBitmap != null) {
                Image(
                    bitmap = iconBitmap,
                    contentDescription = app.appName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Icon(
                    imageVector = MiuixIcons.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MiuixTheme.colorScheme.onSurfaceSecondary,
                )
            }
        }
        Text(
            text = app.appName,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            fontSize = 11.sp,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
