package xzynine.WebDAVPass.Android.ui.Dialog

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import xzynine.WebDAVPass.Android.ui.Screen.openUrl

/**
 * 本项目使用的外部开源代码信息。
 */
private data class OpenSourceItem(
    val name: String,
    val license: String,
    val url: String,
)

private data class OpenSourceGroup(
    val category: String,
    val items: List<OpenSourceItem>,
)

private val openSourceGroups =
    listOf(
        OpenSourceGroup(
            category = "UI / 组件库 / 图标",
            items =
                listOf(
                    OpenSourceItem("MIUIX", "Apache-2.0", "https://github.com/miuix-kotlin/miuix"),
                    OpenSourceItem("dev.rikka.parcelablelist", "Apache-2.0", "https://github.com/RikkaApps/rikkaX"),
                    OpenSourceItem("KeePassDX 图标包 (icon-pack 模块)", "LGPL-2.1 / CC-BY-4.0", "https://github.com/Kunzisoft/KeePassDX"),
                    OpenSourceItem("FreeOTP Plus (token-images 模块)", "GPL-3.0", "https://github.com/openintents/FreeOTPPlus"),
                ),
        ),
        OpenSourceGroup(
            category = "数据库 / 加密 / 密码格式",
            items =
                listOf(
                    OpenSourceItem("KeePassDX (database/crypto 模块)", "GPL-3.0", "https://github.com/Kunzisoft/KeePassDX"),
                    OpenSourceItem("Keepass2Android (webdav 模块)", "GPL-3.0", "https://github.com/PhilippC/keepass2android"),
                    OpenSourceItem("Bouncy Castle", "MIT", "https://www.bouncycastle.org/"),
                    OpenSourceItem("Joda-Time", "Apache-2.0", "https://www.joda.org/joda-time/"),
                    OpenSourceItem("Apache Commons IO", "Apache-2.0", "https://commons.apache.org/proper/commons-io/"),
                    OpenSourceItem("Apache Commons Codec", "Apache-2.0", "https://commons.apache.org/proper/commons-codec/"),
                ),
        ),
        OpenSourceGroup(
            category = "网络 / 解析 / 工具",
            items =
                listOf(
                    OpenSourceItem("OkHttp", "Apache-2.0", "https://square.github.io/okhttp/"),
                    OpenSourceItem("Jsoup", "MIT", "https://jsoup.org/"),
                    OpenSourceItem("Hutool", "MulanPSL-2.0", "https://github.com/chinabugotech/hutool"),
                    OpenSourceItem("Gson", "Apache-2.0", "https://github.com/google/gson"),
                    OpenSourceItem("SemVer", "MIT", "https://github.com/z4kn4fein/semver-kt"),
                    OpenSourceItem("Kotlin Coroutines", "Apache-2.0", "https://github.com/Kotlin/kotlinx.coroutines"),
                    OpenSourceItem("Kotlin Serialization", "Apache-2.0", "https://github.com/Kotlin/kotlinx.serialization"),
                ),
        ),
        OpenSourceGroup(
            category = "二维码 / 相机",
            items =
                listOf(
                    OpenSourceItem("ZXing", "Apache-2.0", "https://github.com/zxing/zxing"),
                    OpenSourceItem("zxing-android-embedded", "Apache-2.0", "https://github.com/journeyapps/zxing-android-embedded"),
                ),
        ),
        OpenSourceGroup(
            category = "构建期",
            items =
                listOf(
                    OpenSourceItem("JGit", "BSD-3-Clause / EDL-1.0", "https://www.eclipse.org/jgit/"),
                ),
        ),
    )

/**
 * 开源代码抽屉弹窗：从底部滑出，按分类列出本项目使用的外部开源库及其许可证与项目地址。
 */
@Composable
fun OpenSourceDrawer(
    show: Boolean,
    onDismiss: () -> Unit,
    context: Context,
) {
    WindowBottomSheet(
        show = show,
        title = "开源代码",
        onDismissRequest = onDismiss,
    ) {
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(MiuixTheme.colorScheme.background)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            openSourceGroups.forEach { group ->
                item {
                    Text(
                        text = group.category,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                    )
                }
                items(group.items) { item ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MiuixTheme.colorScheme.surface)
                                .clickable { openUrl(context, item.url) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.name,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "点击打开项目主页",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(top = 3.dp),
                            )
                        }
                        Box(
                            modifier =
                                Modifier
                                    .padding(start = 12.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MiuixTheme.colorScheme.primary)
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = item.license,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                }
                item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            }
        }
    }
}
