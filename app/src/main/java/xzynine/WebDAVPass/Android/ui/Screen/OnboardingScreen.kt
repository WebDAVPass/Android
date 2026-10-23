package xzynine.WebDAVPass.Android.ui.Screen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.createBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.CloudFill
import top.yukonga.miuix.kmp.icon.extended.File
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme
import xzylib.base.util.IntentUtils
import xzylib.base.util.ToastUtils
import xzynine.WebDAVPass.Android.R
import xzynine.WebDAVPass.Android.data.AppDatabaseHolder
import xzynine.WebDAVPass.Android.data.AppSetting
import xzynine.WebDAVPass.Android.util.InstalledAppsProvider

/** 初始引导完成标记（app_settings 键） */
internal const val SETTING_KEY_ONBOARDING_COMPLETED = "onboarding_completed"

/**
 * 判断首次引导是否已完成。
 *
 * 异步读取 app_settings（数据量小，毫秒级），供启动时一次性判断是否展示引导页；
 * 由调用方在协程/IO 上下文中调用，避免阻塞主线程。
 */
internal suspend fun isOnboardingCompleted(context: Context): Boolean =
    withContext(Dispatchers.IO) {
        runCatching {
            AppDatabaseHolder
                .getInstance(context)
                .appSettingsDao()
                .getValue(SETTING_KEY_ONBOARDING_COMPLETED)
                ?.value == "true"
        }.getOrDefault(false)
    }

/**
 * 引导页数据
 *
 * @param useAppIcon 是否使用应用图标（首屏欢迎页，对应 HyperCeiler 引导的起始 Logo 页）
 * @param showAppListPermission 是否渲染「应用列表权限」授权项（参考 NotifyRelay 引导页的权限页）
 */
private data class OnboardingPage(
    val title: String,
    val subtitle: String,
    val icon: ImageVector? = null,
    val useAppIcon: Boolean = false,
    val showAppListPermission: Boolean = false,
)

private val onboardingPages =
    listOf(
        OnboardingPage(
            title = "欢迎使用 WebDAVPass",
            subtitle = "本地加密存储的密码与 2FA 动态令牌管理器，数据只属于你自己。",
            useAppIcon = true,
        ),
        OnboardingPage(
            title = "密码与令牌管理",
            subtitle = "以 KeePass 数据库组织密码、账号与动态令牌（OTP），支持二维码扫码添加，条目自动归类。",
            icon = MiuixIcons.File,
        ),
        OnboardingPage(
            title = "WebDAV 云同步",
            subtitle = "通过 WebDAV 在多设备间同步数据库，同步冲突自动合并，随时备份与恢复。",
            icon = MiuixIcons.CloudFill,
        ),
        OnboardingPage(
            title = "安全防护",
            subtitle = "主密码本地加密，支持超时自动锁定与生物识别解锁；应用内防截屏，守护隐私。",
            icon = MiuixIcons.Lock,
        ),
        OnboardingPage(
            title = "应用列表权限",
            subtitle =
                "授权后可按安装包名关联应用：条目的「应用」字段可选择本机应用并自动带出应用图标，" +
                    "自动填充也能按包名匹配条目。未授权不影响密码库本身，随时可在应用详情页开启。",
            icon = MiuixIcons.Settings,
            showAppListPermission = true,
        ),
    )

/**
 * 初始引导页（首次启动展示）。
 *
 * 参考 HyperCeiler provision 引导流程：起始页（大 Logo + 标题）→ 多页功能介绍 →
 * 完成页"开始使用"进入应用。无跳过入口，需看完全部页面。
 *
 * @param onFinish 引导完成回调（进入欢迎页/锁定页）
 */
@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { onboardingPages.size })
    val isLastPage = pagerState.currentPage == onboardingPages.lastIndex

    // 非第一页时返回键回退一页
    BackHandler(enabled = pagerState.currentPage > 0) {
        coroutineScope.launch {
            pagerState.animateScrollToPage(pagerState.currentPage - 1)
        }
    }

    // 授权入口放在引导页内：权限页与 MainActivity 一起常驻组合，
    // 因此在宿主的组合作用域内请求厂商权限弹窗
    var queryAppsGranted by remember { mutableStateOf(false) }
    val queryAppsPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                ToastUtils.showShortToast(context, "已获得应用列表权限")
            } else {
                ToastUtils.showShortToast(context, "需要应用列表权限才能识别本机应用")
                openAppDetailsSettings(context)
            }
            queryAppsGranted = InstalledAppsProvider.isAppListPermissionGranted(context)
        }

    fun refreshQueryAppsState() {
        queryAppsGranted = InstalledAppsProvider.isAppListPermissionGranted(context)
    }

    // 从应用详情页返回时重新读取授权状态（运行时权限弹窗本身不会触发 onResume）
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    refreshQueryAppsState()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun requestQueryAppsPermission() {
        val miuiPermission = InstalledAppsProvider.miuiQueryAppsPermission(context)
        if (miuiPermission != null) {
            // MIUI/澎湃：在 QUERY_ALL_PACKAGES 之外还需弹窗申请厂商的应用列表权限
            queryAppsPermissionLauncher.launch(miuiPermission)
        } else {
            ToastUtils.showShortToast(context, "请在应用信息页面的权限管理-其他权限中允许<访问应用列表>")
            openAppDetailsSettings(context)
        }
    }

    // 首次进入时读取一次（ON_RESUME 观察者在组合后才注册，收不到首次事件）
    LaunchedEffect(Unit) {
        refreshQueryAppsState()
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp)
                .padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
        ) { page ->
            val current = onboardingPages[page]
            OnboardingPageContent(
                page = current,
                queryAppsGranted = if (current.showAppListPermission) queryAppsGranted else null,
                onRequestQueryAppsPermission = ::requestQueryAppsPermission,
            )
        }

        // 页面指示点
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            onboardingPages.indices.forEach { index ->
                val selected = index == pagerState.currentPage
                Box(
                    modifier =
                        Modifier
                            .size(if (selected) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) {
                                    MiuixTheme.colorScheme.primary
                                } else {
                                    MiuixTheme.colorScheme.outline
                                },
                            ),
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // 底部下一步/开始使用按钮（对应 HyperCeiler 引导的 next 按钮与完成页进入按钮）
        Button(
            onClick = {
                if (isLastPage) {
                    // 标记引导完成：先完成写入再回调 onFinish()，
                    // 避免协程挂起期间 composition 结束导致写入被取消、引导重复出现
                    coroutineScope.launch {
                        withContext(Dispatchers.IO) {
                            runCatching {
                                AppDatabaseHolder
                                    .getInstance(context)
                                    .appSettingsDao()
                                    .put(AppSetting(SETTING_KEY_ONBOARDING_COMPLETED, "true"))
                            }
                        }
                        onFinish()
                    }
                } else {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = if (isLastPage) "开始使用" else "下一步")
        }
    }
}

@Composable
private fun OnboardingPageContent(
    page: OnboardingPage,
    queryAppsGranted: Boolean? = null,
    onRequestQueryAppsPermission: () -> Unit = {},
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (page.useAppIcon) {
            OnboardingAppLogo(modifier = Modifier.size(96.dp))
        } else if (page.icon != null) {
            Icon(
                imageVector = page.icon,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MiuixTheme.colorScheme.primary,
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = page.title,
            fontSize = 24.sp,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = page.subtitle,
            fontSize = 15.sp,
            color = MiuixTheme.colorScheme.onSurfaceSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
        )

        if (page.showAppListPermission && queryAppsGranted != null) {
            Spacer(modifier = Modifier.height(28.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = onRequestQueryAppsPermission,
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "应用列表权限", fontSize = 15.sp)
                        Text(
                            text =
                                if (queryAppsGranted) {
                                    "已允许查询本机已安装应用，可关联条目应用并自动带出图标"
                                } else {
                                    "用于列出本机应用、读取应用名与图标，未开启时相关功能受限"
                                },
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Text(
                        text = if (queryAppsGranted) "已开启" else "去开启",
                        fontSize = 14.sp,
                        color =
                            if (queryAppsGranted) {
                                MiuixTheme.colorScheme.onSurfaceSecondary
                            } else {
                                MiuixTheme.colorScheme.primary
                            },
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
        }
    }
}

/**
 * 跳转到本应用的系统详情页，供用户在「权限管理-其他权限」中开启「访问应用列表」。
 */
private fun openAppDetailsSettings(context: Context) {
    IntentUtils.startActivity(
        context = context,
        action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        data = Uri.fromParts("package", context.packageName, null),
        addNewTaskFlag = true,
    )
}

/**
 * 引导页首屏大 Logo。
 *
 * 参考 Notify-Relay GuideAppLogo：通过 PackageManager 读取系统实际的应用图标
 * （兼容 adaptive-icon，painterResource 不支持该类型），圆角裁切展示。
 */
@Composable
private fun OnboardingAppLogo(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(24.dp)

    val appIcon =
        remember(context) {
            runCatching {
                val drawable = context.packageManager.getApplicationIcon(context.packageName)
                if (drawable is BitmapDrawable) {
                    drawable.bitmap.asImageBitmap()
                } else {
                    val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 96
                    val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 96
                    val bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    drawable.setBounds(0, 0, width, height)
                    drawable.draw(canvas)
                    bitmap.asImageBitmap()
                }
            }.getOrNull()
        }

    if (appIcon != null) {
        Image(
            bitmap = appIcon,
            contentDescription = "应用图标",
            modifier = modifier.clip(shape),
        )
    } else {
        // 兜底：读取失败时使用可被 painterResource 支持的矢量图层拼出图标
        Box(modifier = modifier.clip(shape)) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_background),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
            )
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    }
}
