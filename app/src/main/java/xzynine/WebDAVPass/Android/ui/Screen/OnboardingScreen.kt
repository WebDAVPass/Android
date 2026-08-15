package xzynine.WebDAVPass.Android.ui.Screen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.CloudFill
import top.yukonga.miuix.kmp.icon.extended.File
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.theme.MiuixTheme
import xzynine.WebDAVPass.Android.R
import xzynine.WebDAVPass.Android.data.AppDatabaseHolder
import xzynine.WebDAVPass.Android.data.AppSetting

/** 初始引导完成标记（app_settings 键） */
internal const val SETTING_KEY_ONBOARDING_COMPLETED = "onboarding_completed"

/**
 * 判断首次引导是否已完成。
 *
 * 同步读取 app_settings（数据量小，毫秒级），供启动时一次性判断是否展示引导页。
 */
internal fun isOnboardingCompleted(context: Context): Boolean {
    return runBlocking(Dispatchers.IO) {
        runCatching {
            AppDatabaseHolder.getInstance(context)
                .appSettingsDao()
                .getValue(SETTING_KEY_ONBOARDING_COMPLETED)
                ?.value == "true"
        }.getOrDefault(false)
    }
}

/**
 * 引导页数据
 *
 * @param useAppIcon 是否使用应用图标（首屏欢迎页，对应 HyperCeiler 引导的起始 Logo 页）
 */
private data class OnboardingPage(
    val title: String,
    val subtitle: String,
    val icon: ImageVector? = null,
    val useAppIcon: Boolean = false
)

private val onboardingPages = listOf(
    OnboardingPage(
        title = "欢迎使用 WebDAVPass",
        subtitle = "本地加密存储的密码与 2FA 动态令牌管理器，数据只属于你自己。",
        useAppIcon = true
    ),
    OnboardingPage(
        title = "密码与令牌管理",
        subtitle = "以 KeePass 数据库组织密码、账号与动态令牌（OTP），支持二维码扫码添加，条目自动归类。",
        icon = MiuixIcons.File
    ),
    OnboardingPage(
        title = "WebDAV 云同步",
        subtitle = "通过 WebDAV 在多设备间同步数据库，同步冲突自动合并，随时备份与恢复。",
        icon = MiuixIcons.CloudFill
    ),
    OnboardingPage(
        title = "安全防护",
        subtitle = "主密码本地加密，支持超时自动锁定与生物识别解锁；应用内防截屏，守护隐私。",
        icon = MiuixIcons.Lock
    )
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
    onFinish: () -> Unit
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
            .padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            OnboardingPageContent(onboardingPages[page])
        }

        // 页面指示点
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            onboardingPages.indices.forEach { index ->
                val selected = index == pagerState.currentPage
                Box(
                    modifier = Modifier
                        .size(if (selected) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) MiuixTheme.colorScheme.primary
                            else MiuixTheme.colorScheme.outline
                        )
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // 底部下一步/开始使用按钮（对应 HyperCeiler 引导的 next 按钮与完成页进入按钮）
        Button(
            onClick = {
                if (isLastPage) {
                    // 标记引导完成（写入失败不阻塞进入应用）
                    coroutineScope.launch(Dispatchers.IO) {
                        runCatching {
                            AppDatabaseHolder.getInstance(context)
                                .appSettingsDao()
                                .put(AppSetting(SETTING_KEY_ONBOARDING_COMPLETED, "true"))
                        }
                    }
                    onFinish()
                } else {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = if (isLastPage) "开始使用" else "下一步")
        }
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (page.useAppIcon) {
            OnboardingAppLogo(modifier = Modifier.size(96.dp))
        } else if (page.icon != null) {
            Icon(
                imageVector = page.icon,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MiuixTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = page.title,
            fontSize = 24.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = page.subtitle,
            fontSize = 15.sp,
            color = MiuixTheme.colorScheme.onSurfaceSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
    }
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

    val appIcon = remember(context) {
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
            modifier = modifier.clip(shape)
        )
    } else {
        // 兜底：读取失败时使用可被 painterResource 支持的矢量图层拼出图标
        Box(modifier = modifier.clip(shape)) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_background),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
    }
}
