package xzynine.WebDAVPass.Android.ui.Screen

import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import xzynine.WebDAVPass.Android.data.LibrarySourceType
import xzynine.WebDAVPass.Android.service.TwoFasAutofillService
import xzynine.WebDAVPass.Android.theme.getAppRoundedCorner
import xzynine.WebDAVPass.Android.ui.ViewModel.TokenViewModel
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Backup
import top.yukonga.miuix.kmp.icon.extended.CloudFill
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Months
import top.yukonga.miuix.kmp.icon.extended.UploadCloud

/**
 * 设置界面组件
 * @param viewModel TokenViewModel实例
 * @param onCloudBindingClick 点击当前库云端设置的回调
 */
@Composable
fun SettingsScreen(
    viewModel: TokenViewModel,
    onCloudBindingClick: () -> Unit,
    onSwitchLibraryClick: () -> Unit
) {
    // 获取统一的圆角半径
    val cornerRadius = getAppRoundedCorner()
    
    // 收集状态流
    val backupStatus = viewModel.backupStatus.collectAsState()
    val isBackupInProgress = viewModel.isBackupInProgress.collectAsState()
    val backupProgress = viewModel.backupProgress.collectAsState()
    val isRestoreInProgress = viewModel.isRestoreInProgress.collectAsState()
    val restoreProgress = viewModel.restoreProgress.collectAsState()
    val currentLibraryState by viewModel.currentLibrary.collectAsState()
    val context = LocalContext.current

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
                .verticalScroll(androidx.compose.foundation.rememberScrollState())
        ) {
            Text(
                text = "系统设置",
                modifier = Modifier.padding(8.dp)
            )

            SuperArrow(
                title = "设置为自动填充器",
                summary = "跳转到系统自动填充设置",
                startAction = {
                    Icon(
                        modifier = Modifier.Companion.padding(end = 16.dp),
                        imageVector = MiuixIcons.GridView,
                        contentDescription = "设置为自动填充器",
                    )
                },
                onClick = {
                    val autofillServiceExtra = "android.provider.extra.AUTOFILL_SERVICE"
                    val autofillSettingsAction = "android.settings.AUTOFILL_SETTINGS"
                    val requestIntent = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply {
                        putExtra(
                            autofillServiceExtra,
                            ComponentName(context, TwoFasAutofillService::class.java)
                        )
                    }
                    val credentialsPickerIntent = Intent().apply {
                        component = ComponentName(
                            "com.android.settings",
                            "com.android.settings.applications.credentials.CredentialsPickerActivity"
                        )
                    }
                    val fallbackIntent = Intent(autofillSettingsAction)
                    val intent = when {
                        requestIntent.resolveActivity(context.packageManager) != null -> requestIntent
                        credentialsPickerIntent.resolveActivity(context.packageManager) != null -> credentialsPickerIntent
                        fallbackIntent.resolveActivity(context.packageManager) != null -> fallbackIntent
                        else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = android.net.Uri.fromParts("package", context.packageName, null)
                        }
                    }
                    context.startActivity(intent)
                },
                modifier = Modifier.Companion
                    .fillMaxWidth()
            )

            Spacer(modifier = Modifier.Companion.height(16.dp))

            // WebDAV配置
            SuperArrow(
                title = "当前库云端设置",
                summary = cloudBindingSummary,
                startAction = {
                    Icon(
                        modifier = Modifier.Companion.padding(end = 16.dp),
                        imageVector = MiuixIcons.CloudFill,
                        contentDescription = "当前库云端设置",
                    )
                },
                onClick = onCloudBindingClick,
                modifier = Modifier.Companion
                    .fillMaxWidth()
            )

            Spacer(modifier = Modifier.Companion.height(8.dp))

            SuperArrow(
                title = "切换数据库文件",
                summary = "返回欢迎页，选择其他 .kdbx",
                startAction = {
                    Icon(
                        modifier = Modifier.Companion.padding(end = 16.dp),
                        imageVector = MiuixIcons.Months,
                        contentDescription = "切换数据库文件",
                    )
                },
                onClick = onSwitchLibraryClick,
                modifier = Modifier.Companion
                    .fillMaxWidth()
            )

            Spacer(modifier = Modifier.Companion.height(16.dp))

            // 备份和恢复标题
            Text(
                text = "备份与恢复",
                modifier = Modifier.padding(8.dp)
            )

            // 备份状态显示
            SuperArrow(
                title = "备份状态",
                summary = backupStatus.value,
                startAction = {
                    Icon(
                        modifier = Modifier.Companion.padding(end = 16.dp),
                        imageVector = MiuixIcons.Backup,
                        contentDescription = "备份状态",
                    )
                },
                modifier = Modifier.Companion
                    .fillMaxWidth()
            )

            Spacer(modifier = Modifier.Companion.height(8.dp))

            // 备份按钮
            SuperArrow(
                title = if (isBackupInProgress.value) "备份中..." else if (isCurrentLibraryCloudBound) "备份令牌" else "绑定后可备份",
                summary = if (isBackupInProgress.value) {
                    "正在备份到WebDAV服务器... ${backupProgress.value}%"
                } else if (isCurrentLibraryCloudBound) {
                    "点击开始备份"
                } else {
                    "当前库未绑定云端 .kdbx"
                },
                startAction = {
                    if (isBackupInProgress.value) {
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
                    if (!isBackupInProgress.value && isCurrentLibraryCloudBound) {
                        viewModel.backupTokens(force = true)
                    }
                },
                modifier = Modifier.Companion
                    .fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 手动恢复按钮
            SuperArrow(
                title = if (isRestoreInProgress.value) "恢复中..." else if (isCurrentLibraryCloudBound) "手动恢复" else "绑定后可恢复",
                summary = if (isRestoreInProgress.value) {
                    "正在从WebDAV服务器恢复... ${restoreProgress.value}%"
                } else if (isCurrentLibraryCloudBound) {
                    "点击开始手动恢复"
                } else {
                    "当前库未绑定云端 .kdbx"
                },
                startAction = {
                    if (isRestoreInProgress.value) {
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
                    if (!isRestoreInProgress.value && isCurrentLibraryCloudBound) {
                        viewModel.manualRestoreTokens()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}