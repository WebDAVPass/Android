package github.xzynine.two_fas.ui.Screen

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import github.xzynine.two_fas.theme.getAppRoundedCorner
import github.xzynine.two_fas.ui.Dialog.PasswordDialog
import github.xzynine.two_fas.ui.ViewModel.TokenViewModel
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.icons.useful.Personal

/**
 * 设置界面组件
 * @param viewModel TokenViewModel实例
 * @param onWebDavConfigClick 点击WebDAV配置的回调
 * @param onAddSampleDataClick 点击添加示例数据的回调
 */
@Composable
fun SettingsScreen(
    viewModel: TokenViewModel,
    onWebDavConfigClick: () -> Unit,
    onAddSampleDataClick: () -> Unit
) {
    // 获取统一的圆角半径
    val cornerRadius = getAppRoundedCorner()

    Scaffold(
        topBar = {
            TopAppBar(
                title = "设置",
                navigationIcon = {},
                actions = {},
                defaultWindowInsetsPadding = true
            )
        }
    ) {
        Column(
            modifier = Modifier.Companion
                .fillMaxSize()
                .padding(it)
                .padding(16.dp)
        ) {
            // WebDAV配置
            BasicComponent(
                title = "WebDAV 配置",
                leftAction = {
                    Icon(
                        modifier = Modifier.Companion.padding(end = 16.dp),
                        imageVector = MiuixIcons.Useful.Personal,
                        contentDescription = "WebDAV 配置",
                    )
                },
                onClick = onWebDavConfigClick,
                modifier = Modifier.Companion
                    .fillMaxWidth()
                    .border(1.dp, Color.Companion.LightGray, RoundedCornerShape(cornerRadius))
            )

            Spacer(modifier = Modifier.Companion.height(16.dp))

            // 备份和恢复标题
            Text(
                text = "备份与恢复",
                modifier = Modifier.padding(8.dp)
            )

            // 备份状态显示
            BasicComponent(
                title = "备份状态",
                summary = viewModel.backupStatus.value,
                leftAction = {
                    Icon(
                        modifier = Modifier.Companion.padding(end = 16.dp),
                        imageVector = MiuixIcons.Useful.Personal,
                        contentDescription = "备份状态",
                    )
                },
                modifier = Modifier.Companion
                    .fillMaxWidth()
                    .border(1.dp, Color.Companion.LightGray, RoundedCornerShape(cornerRadius))
            )

            Spacer(modifier = Modifier.Companion.height(8.dp))

            // 备份按钮
            BasicComponent(
                title = if (viewModel.isBackupInProgress.value) "备份中..." else "备份令牌",
                summary = if (viewModel.isBackupInProgress.value) "正在备份到WebDAV服务器... ${viewModel.backupProgress.value}%" else "点击开始备份",
                leftAction = {
                    if (viewModel.isBackupInProgress.value) {
                        CircularProgressIndicator(
                            modifier = Modifier.Companion.padding(end = 16.dp)
                        )
                    } else {
                        Icon(
                            modifier = Modifier.Companion.padding(end = 16.dp),
                            imageVector = MiuixIcons.Useful.Personal,
                            contentDescription = "备份令牌",
                        )
                    }
                },
                onClick = {
                    if (!viewModel.isBackupInProgress.value) {
                        viewModel.backupTokens()
                    }
                },
                modifier = Modifier.Companion
                    .fillMaxWidth()
                    .border(1.dp, Color.Companion.LightGray, RoundedCornerShape(cornerRadius))
            )

            Spacer(modifier = Modifier.Companion.height(8.dp))

            // 手动恢复按钮
            val showRestorePasswordDialog = remember { mutableStateOf(false) }
            BasicComponent(
                title = if (viewModel.isRestoreInProgress.value) "恢复中..." else "手动恢复",
                summary = if (viewModel.isRestoreInProgress.value) "正在从WebDAV服务器恢复... ${viewModel.restoreProgress.value}%" else "点击开始手动恢复",
                leftAction = {
                    if (viewModel.isRestoreInProgress.value) {
                        CircularProgressIndicator(
                            modifier = Modifier.Companion.padding(end = 16.dp)
                        )
                    } else {
                        Icon(
                            modifier = Modifier.Companion.padding(end = 16.dp),
                            imageVector = MiuixIcons.Useful.Personal,
                            contentDescription = "手动恢复",
                        )
                    }
                },
                onClick = {
                    if (!viewModel.isRestoreInProgress.value) {
                        showRestorePasswordDialog.value = true
                    }
                },
                modifier = Modifier.Companion
                    .fillMaxWidth()
                    .border(1.dp, Color.Companion.LightGray, RoundedCornerShape(cornerRadius))
            )

            Spacer(modifier = Modifier.Companion.height(8.dp))

            // 自定义加密密码设置
            val showCustomPasswordDialog = remember { mutableStateOf(false) }
            BasicComponent(
                title = "自定义加密密码",
                summary = if (viewModel.customEncryptionPassword.value != null) "已设置" else "未设置",
                leftAction = {
                    Icon(
                        modifier = Modifier.Companion.padding(end = 16.dp),
                        imageVector = MiuixIcons.Useful.Personal,
                        contentDescription = "自定义加密密码",
                    )
                },
                onClick = {
                    showCustomPasswordDialog.value = true
                },
                modifier = Modifier.Companion
                    .fillMaxWidth()
                    .border(1.dp, Color.Companion.LightGray, RoundedCornerShape(cornerRadius))
            )

            Spacer(modifier = Modifier.Companion.height(16.dp))

            // 示例数据按钮（主要用于调试）
            BasicComponent(
                title = "添加示例数据",
                leftAction = {
                    Icon(
                        modifier = Modifier.Companion.padding(end = 16.dp),
                        imageVector = MiuixIcons.Useful.Personal,
                        contentDescription = "添加示例数据",
                    )
                },
                onClick = onAddSampleDataClick,
                modifier = Modifier.Companion
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        Color.Companion.LightGray,
                        androidx.compose.foundation.shape.RoundedCornerShape(cornerRadius)
                    )
            )

            // 恢复密码对话框
            PasswordDialog(
                title = "手动恢复",
                summary = "请输入备份加密密码",
                show = showRestorePasswordDialog.value,
                onDismiss = { showRestorePasswordDialog.value = false },
                onConfirm = {
                    viewModel.manualRestoreTokens(it)
                    showRestorePasswordDialog.value = false
                }
            )

            // 自定义加密密码对话框
            PasswordDialog(
                title = "设置自定义加密密码",
                summary = "请输入自定义加密密码，留空则使用WebDAV密码",
                show = showCustomPasswordDialog.value,
                onDismiss = { showCustomPasswordDialog.value = false },
                onConfirm = {
                    viewModel.setCustomEncryptionPassword(if (it.isBlank()) null else it)
                    showCustomPasswordDialog.value = false
                }
            )
        }
    }
}