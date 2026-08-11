package xzynine.WebDAVPass.Android.ui.Screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import xzynine.WebDAVPass.Android.data.LibrarySourceType
import xzynine.WebDAVPass.Android.ui.ViewModel.TokenViewModel
import github.xzynine.webdav.ui.WebDavSyncStatusSection
import github.xzynine.webdav.ui.WebDavSyncUiState
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back

/**
 * 备份与恢复子页面（原「备份与恢复」分组）。
 */
@Composable
fun BackupSettingsContent(
    viewModel: TokenViewModel,
    onCloudBindingClick: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val backupStatus = viewModel.cloudSyncViewModel.backupStatus.collectAsState()
    val isBackupInProgress = viewModel.cloudSyncViewModel.isBackupInProgress.collectAsState()
    val backupProgress = viewModel.cloudSyncViewModel.backupProgress.collectAsState()
    val isRestoreInProgress = viewModel.cloudSyncViewModel.isRestoreInProgress.collectAsState()
    val restoreProgress = viewModel.cloudSyncViewModel.restoreProgress.collectAsState()
    val currentLibraryState by viewModel.libraryViewModel.currentLibrary.collectAsState()

    /**
     * 当前库是否已具备云端同步所需信息。
     */
    val isCurrentLibraryCloudBound = run {
        val current = currentLibraryState
        current != null
                && current.sourceType == LibrarySourceType.CLOUD
                && !current.remoteFilePath.isNullOrBlank()
                && !current.username.isNullOrBlank()
                && !current.password.isNullOrBlank()
    }

    /**
     * 将同步状态编码映射为可读文案。
     */
    val cloudSyncStatusText = when (currentLibraryState?.lastSyncStatus) {
        "syncing" -> "同步中"
        "success" -> "同步成功"
        "merged" -> "已自动合并"
        "conflict" -> "同步冲突"
        "failed" -> "同步失败"
        else -> "未同步"
    }

    /**
     * 设置页展示的当前库云端摘要。
     */
    val cloudBindingSummary = run {
        val current = currentLibraryState
        if (current == null) {
            "当前未选择数据库文件"
        } else if (isCurrentLibraryCloudBound) {
            val remote = current.remoteFilePath ?: current.remoteBaseUrl.orEmpty()
            "$remote | $cloudSyncStatusText"
        } else {
            "当前库未绑定云端 .kdbx，点击配置"
        }
    }

    Scaffold(
        popupHost = { },
        topBar = {
            TopAppBar(
                title = "备份与恢复",
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
                .verticalScroll(rememberScrollState())
        ) {
            // 子模块提供的同步状态区块（含备份状态、备份与手动恢复条目，各自独立 Card）
            WebDavSyncStatusSection(
                state = WebDavSyncUiState(
                    isBackupInProgress = isBackupInProgress.value,
                    isRestoreInProgress = isRestoreInProgress.value,
                    backupStatus = backupStatus.value,
                    backupProgress = backupProgress.value,
                    restoreProgress = restoreProgress.value,
                    isCloudBound = isCurrentLibraryCloudBound,
                    cloudBindingSummary = cloudBindingSummary
                ),
                onCloudBindingClick = onCloudBindingClick,
                onBackupClick = { viewModel.backupTokens(force = true) },
                onRestoreClick = { viewModel.manualRestoreTokens() }
            )
        }
    }
}
