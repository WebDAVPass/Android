package xzynine.WebDAVPass.Android.ui.Screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import xzynine.WebDAVPass.Android.R
import xzynine.WebDAVPass.Android.ui.viewmodel.TokenViewModel
import java.io.File

/**
 * 已锁定界面：应用图标 + "{库文件名}已锁定" 文案 + 解锁/切换库操作。
 *
 * 解锁与切换库按钮仅在应用处于前台时显示，避免后台时在最近任务预览中暴露可操作入口。
 * 前台判定使用 ON_STOP/ON_START 而非 ON_PAUSE/ON_RESUME，避免半透明弹窗误判。
 */
@Composable
fun LockedScreen(
    tokenViewModel: TokenViewModel,
    onUnlocked: () -> Unit,
    onSwitchLibrary: () -> Unit,
) {
    val currentLibrary by tokenViewModel.libraryViewModel.currentLibrary.collectAsState()
    var isForeground by remember { mutableStateOf(true) }
    var showUnlockPanel by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> isForeground = true
                    Lifecycle.Event.ON_STOP -> isForeground = false
                    else -> {}
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 解锁面板展开时，返回键先收起面板
    BackHandler(enabled = showUnlockPanel) {
        showUnlockPanel = false
    }

    // 取含后缀的库文件名（如 WebDavPass.kdbx），解析失败时留空文案
    val libraryName =
        currentLibrary
            ?.localPath
            ?.let { path -> runCatching { File(path).name }.getOrNull() }
            .orEmpty()
    val library = currentLibrary

    Scaffold(
        popupHost = {},
        content = { paddingValues ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.weight(1f))

                // 中央应用图标区（圆角矩形 + 轻微阴影）
                // 用 Play 商店图标 PNG（painterResource 不支持 adaptive icon XML）
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_playstore),
                    contentDescription = "应用图标",
                    modifier =
                        Modifier
                            .size(96.dp)
                            .shadow(elevation = 6.dp, shape = RoundedCornerShape(20.dp))
                            .clip(RoundedCornerShape(20.dp)),
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 状态文案区
                Text(
                    text = if (libraryName.isBlank()) "库已锁定" else "${libraryName}已锁定",
                    fontSize = 18.sp,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                )

                Spacer(modifier = Modifier.height(40.dp))

                // 操作按钮区（仅前台显示）
                if (isForeground && library != null) {
                    if (showUnlockPanel) {
                        InlineUnlockPanel(
                            tokenViewModel = tokenViewModel,
                            library = library,
                            onUnlockSuccess = onUnlocked,
                            onDismiss = { showUnlockPanel = false },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .widthIn(max = 420.dp),
                        )
                    } else {
                        // 标准蓝主按钮，按内容自适应宽度（非全宽）
                        Button(
                            onClick = { showUnlockPanel = true },
                            colors =
                                ButtonDefaults.buttonColors(
                                    color = Color(0xFF3A9FFD),
                                    disabledColor = Color(0xFF8EC8FD),
                                ),
                        ) {
                            Text(text = "解锁")
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // 底部切换库按钮（仅前台显示）
                if (isForeground) {
                    TextButton(
                        text = "切换库",
                        onClick = onSwitchLibrary,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        },
    )
}
