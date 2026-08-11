package xzynine.WebDAVPass.Android.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Backup
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.UploadCloud
import top.yukonga.miuix.kmp.preference.ArrowPreference

/**
 * 同步界面状态
 *
 * @param isBackupInProgress 备份是否进行中
 * @param isRestoreInProgress 恢复是否进行中
 * @param backupProgress 备份进度百分比
 * @param restoreProgress 恢复进度百分比
 * @param isCloudBound 当前库是否已绑定云端
 */
data class WebDavSyncUiState(
    val isBackupInProgress: Boolean = false,
    val isRestoreInProgress: Boolean = false,
    val backupProgress: Int = 0,
    val restoreProgress: Int = 0,
    val isCloudBound: Boolean = false
)

/**
 * 备份状态区块
 *
 * 展示云端绑定配置、备份与手动恢复三个条目，含进行中进度与按钮联动。
 *
 * @param state 同步界面状态
 * @param onCloudBindingClick 云端绑定配置条目点击回调
 * @param onBackupClick 备份按钮点击回调
 * @param onRestoreClick 手动恢复按钮点击回调
 */
@Composable
fun WebDavSyncStatusSection(
    state: WebDavSyncUiState,
    onCloudBindingClick: () -> Unit,
    onBackupClick: () -> Unit,
    onRestoreClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // 云端绑定配置入口
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            ArrowPreference(
                title = "备份状态",
                startAction = {
                    Icon(
                        modifier = Modifier.padding(end = 16.dp),
                        imageVector = MiuixIcons.Backup,
                        contentDescription = "备份状态",
                    )
                },
                onClick = onCloudBindingClick,
                modifier = Modifier
                    .fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 备份按钮
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            ArrowPreference(
                title = if (state.isBackupInProgress) {
                    "备份中..."
                } else if (state.isCloudBound) {
                    "备份令牌"
                } else {
                    "绑定后可备份"
                },
                summary = if (state.isBackupInProgress) {
                    "正在备份到WebDAV服务器... ${state.backupProgress}%"
                } else if (state.isCloudBound) {
                    "点击开始备份"
                } else {
                    "当前库未绑定云端 .kdbx"
                },
                startAction = {
                    if (state.isBackupInProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 16.dp)
                        )
                    } else {
                        Icon(
                            modifier = Modifier.padding(end = 16.dp),
                            imageVector = MiuixIcons.UploadCloud,
                            contentDescription = "备份令牌",
                        )
                    }
                },
                onClick = {
                    if (!state.isBackupInProgress && state.isCloudBound) {
                        onBackupClick()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 手动恢复按钮
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            ArrowPreference(
                title = if (state.isRestoreInProgress) {
                    "恢复中..."
                } else if (state.isCloudBound) {
                    "手动恢复"
                } else {
                    "绑定后可恢复"
                },
                summary = if (state.isRestoreInProgress) {
                    "正在从WebDAV服务器恢复... ${state.restoreProgress}%"
                } else if (state.isCloudBound) {
                    "点击开始手动恢复"
                } else {
                    "当前库未绑定云端 .kdbx"
                },
                startAction = {
                    if (state.isRestoreInProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 16.dp)
                        )
                    } else {
                        Icon(
                            modifier = Modifier.padding(end = 16.dp),
                            imageVector = MiuixIcons.Download,
                            contentDescription = "手动恢复",
                        )
                    }
                },
                onClick = {
                    if (!state.isRestoreInProgress && state.isCloudBound) {
                        onRestoreClick()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
            )
        }
    }
}
