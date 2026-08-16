package xzynine.WebDAVPass.Android.ui.Screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Scaffold
import xzynine.WebDAVPass.Android.data.LibrarySourceType
import xzynine.WebDAVPass.Android.ui.component.SettingsTopAppBar
import xzynine.WebDAVPass.Android.ui.component.WebDavSyncStatusSection
import xzynine.WebDAVPass.Android.ui.component.WebDavSyncUiState
import xzynine.WebDAVPass.Android.ui.viewmodel.TokenViewModel

/**
 * 备份设置子页面
 */
@Composable
fun BackupSettingsContent(
    viewModel: TokenViewModel,
    onCloudBindingClick: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    val isBackupInProgress = viewModel.cloudSyncViewModel.isBackupInProgress.collectAsState()
    val backupProgress = viewModel.cloudSyncViewModel.backupProgress.collectAsState()
    val isRestoreInProgress = viewModel.cloudSyncViewModel.isRestoreInProgress.collectAsState()
    val restoreProgress = viewModel.cloudSyncViewModel.restoreProgress.collectAsState()
    val currentLibraryState by viewModel.libraryViewModel.currentLibrary.collectAsState()

    /**
     * 当前库是否已具备云端同步所需信息。
     */
    val isCurrentLibraryCloudBound =
        run {
            val current = currentLibraryState
            current != null &&
                current.sourceType == LibrarySourceType.CLOUD &&
                !current.remoteFilePath.isNullOrBlank() &&
                !current.username.isNullOrBlank() &&
                !current.password.isNullOrBlank()
        }

    Scaffold(
        popupHost = { },
        topBar = {
            SettingsTopAppBar(
                title = "备份设置",
                onNavigateBack = onNavigateBack,
            )
        },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(it)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
        ) {
            // 备份状态区块（云端绑定配置、备份与手动恢复条目，各自独立 Card）
            WebDavSyncStatusSection(
                state =
                    WebDavSyncUiState(
                        isBackupInProgress = isBackupInProgress.value,
                        isRestoreInProgress = isRestoreInProgress.value,
                        backupProgress = backupProgress.value,
                        restoreProgress = restoreProgress.value,
                        isCloudBound = isCurrentLibraryCloudBound,
                    ),
                onCloudBindingClick = onCloudBindingClick,
                onBackupClick = { viewModel.backupTokens(force = true) },
                onRestoreClick = { viewModel.manualRestoreTokens() },
            )
        }
    }
}
