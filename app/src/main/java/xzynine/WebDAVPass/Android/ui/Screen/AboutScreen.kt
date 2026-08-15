package xzynine.WebDAVPass.Android.ui.Screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import github.xzynine.checkupdata.CheckUpdateManager
import github.xzynine.checkupdata.download.SystemDownloader
import github.xzynine.checkupdata.model.ReleaseInfo
import github.xzynine.checkupdata.model.UpdateResult
import github.xzynine.checkupdata.version.VersionRule
import xzynine.WebDAVPass.Android.BuildConfig
import xzynine.WebDAVPass.Android.R
import xzynine.WebDAVPass.Android.ui.Dialog.ConfirmationDialog
import xzylib.base.util.ToastUtils
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Download
import xzynine.WebDAVPass.Android.ui.component.Preference
import xzynine.WebDAVPass.Android.ui.component.PreferenceType
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 关于页面。
 * 顶部展示应用图标头图与版本号
 * 下方为更新日志查看与检查更新。
 */
@Composable
fun AboutScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var checkingUpdate by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<UpdateResult?>(null) }
    var loadingChangelog by remember { mutableStateOf(false) }
    var showChangelog by remember { mutableStateOf(false) }
    var changelogReleases by remember { mutableStateOf<List<ReleaseInfo>?>(null) }

    Scaffold(
        popupHost = { },
        topBar = {
            TopAppBar(
                title = "关于",
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {},
                defaultWindowInsetsPadding = true
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 图标头图（占据上方约四分之一，居中，不在 Card 中）
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_playstore),
                contentDescription = "应用图标",
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .padding(top = 24.dp, bottom = 12.dp)
            )

            // 应用名称 + 版本号（居中，非 Preference）
            Text(
                text = "WebDAVPass",
                textAlign = TextAlign.Center
            )
            Text(
                text = "版本 ${BuildConfig.VERSION_NAME}",
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Preference(
                    type = PreferenceType.Arrow,
                    title = "更新日志",
                    summary = if (loadingChangelog) "加载中..." else "查看各版本更新内容",
                    startAction = {
                        Icon(
                            modifier = Modifier.padding(end = 16.dp),
                            imageVector = MiuixIcons.Download,
                            contentDescription = "更新日志",
                        )
                    },
                    onClick = {
                        if (loadingChangelog) {
                            return@Preference
                        }
                        // 若已加载过则直接打开，避免重复网络请求
                        if (changelogReleases != null) {
                            showChangelog = true
                            return@Preference
                        }
                        coroutineScope.launch {
                            loadingChangelog = true
                            val result = CheckUpdateManager(context).checkUpdate(
                                owner = "WebDAVPass",
                                repo = "Android",
                                currentVersion = BuildConfig.VERSION_NAME,
                                rule = VersionRule.LATEST
                            )
                            loadingChangelog = false
                            val releases = when (result) {
                                is UpdateResult.HasUpdate -> result.allReleases
                                is UpdateResult.NoUpdate -> result.allReleases
                                is UpdateResult.Error -> null
                            }
                            if (releases == null) {
                                if (result is UpdateResult.Error) {
                                    ToastUtils.showShortToast(context, "加载更新日志失败：${result.message}")
                                }
                                return@launch
                            }
                            changelogReleases = releases
                            showChangelog = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Preference(
                    type = PreferenceType.Arrow,
                    title = "检查更新",
                    summary = if (checkingUpdate) "正在检查..." else "从 GitHub Releases 检查新版本",
                    startAction = {
                        Icon(
                            modifier = Modifier.padding(end = 16.dp),
                            imageVector = MiuixIcons.Download,
                            contentDescription = "检查更新",
                        )
                    },
                    onClick = {
                        if (checkingUpdate) return@Preference
                        coroutineScope.launch {
                            checkingUpdate = true
                            val result = CheckUpdateManager(context).checkUpdate(
                                owner = "WebDAVPass",
                                repo = "Android",
                                currentVersion = BuildConfig.VERSION_NAME
                            )
                            checkingUpdate = false
                            updateResult = result
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    updateResult?.let { result ->
        UpdateResultDialog(
            result = result,
            onDismiss = { updateResult = null }
        )
    }

    ChangelogDialog(
        show = showChangelog,
        releases = changelogReleases,
        onDismiss = { showChangelog = false }
    )
}

/**
 * 更新结果对话框。
 */
@Composable
private fun UpdateResultDialog(
    result: UpdateResult,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val show = remember { mutableStateOf(true) }

    when (result) {
        is UpdateResult.HasUpdate -> {
            val release = result.releaseInfo
            ConfirmationDialog(
                title = "发现新版本 ${release.version}",
                summary = buildString {
                    append("当前版本：${result.currentVersion}\n")
                    if (release.releaseNotes.isNotBlank()) {
                        append("更新说明：\n${release.releaseNotes.take(500)}")
                    }
                },
                show = show,
                onDismiss = onDismiss,
                confirmButtonText = "下载更新",
                onConfirm = {
                    onDismiss()
                    coroutineScope.launch {
                        val downloadResult = CheckUpdateManager(context).downloadRelease(release, assetFilter = null)
                        when (downloadResult) {
                            is SystemDownloader.DownloadResult.Success ->
                                ToastUtils.showShortToast(context, "已开始下载：${downloadResult.fileName}")

                            else -> ToastUtils.showShortToast(context, "下载失败，请到 GitHub Releases 手动下载")
                        }
                    }
                }
            )
        }

        is UpdateResult.NoUpdate -> {
            ConfirmationDialog(
                title = "已是最新版本",
                summary = "当前版本：${result.currentVersion}",
                show = show,
                onDismiss = onDismiss,
                confirmButtonText = "确定",
                onConfirm = onDismiss
            )
        }

        is UpdateResult.Error -> {
            ConfirmationDialog(
                title = "检查更新失败",
                summary = result.message,
                show = show,
                onDismiss = onDismiss,
                confirmButtonText = "确定",
                onConfirm = onDismiss
            )
        }
    }
}

/**
 * 更新日志弹窗：列出所有已发布版本及其发布说明。
 */
@Composable
private fun ChangelogDialog(
    show: Boolean,
    releases: List<ReleaseInfo>?,
    onDismiss: () -> Unit
) {
    WindowDialog(
        show = show,
        title = "更新日志",
        onDismissRequest = onDismiss
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            val list = releases ?: emptyList()
            items(list.size) { index ->
                val release = list[index]
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp)
                ) {
                    Text(
                        text = "v${release.version}",
                    )
                    release.publishedAt?.let { published ->
                        Text(
                            text = published,
                        )
                    }
                    if (release.releaseNotes.isNotBlank()) {
                        Text(
                            text = release.releaseNotes,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                if (index != list.lastIndex) {
                    HorizontalDivider()
                }
            }
        }
    }
}
