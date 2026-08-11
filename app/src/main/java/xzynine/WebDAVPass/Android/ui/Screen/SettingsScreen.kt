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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import xzynine.WebDAVPass.Android.ui.ViewModel.TokenViewModel
import xzynine.WebDAVPass.Android.ui.navigation.LocalNavigator
import xzynine.WebDAVPass.Android.ui.navigation.Route
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.CloudFill
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Months
import top.yukonga.miuix.kmp.icon.extended.Settings
import xzynine.WebDAVPass.Android.ui.component.Preference
import xzynine.WebDAVPass.Android.ui.component.PreferenceType

/**
 * 设置界面主索引页。
 *
 * 将原有单屏拆为多级结构：点击分组进入对应的二级子设置页。
 * 每个入口与子页面的设置项均包裹在圆角浅色卡片中。
 */
@Composable
fun SettingsScreen(
    viewModel: TokenViewModel,
    onCloudBindingClick: () -> Unit,
    onSwitchLibraryClick: () -> Unit,
    onDatabaseSettingsClick: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val navigator = LocalNavigator.current

    Scaffold(
        popupHost = { },
        topBar = {
            TopAppBar(
                title = "设置",
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack
                    ) {
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
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Preference(
                    type = PreferenceType.Arrow,
                    title = "切换数据库文件",
                    summary = "返回欢迎页，选择其他 .kdbx",
                    startAction = {
                        Icon(
                            modifier = Modifier.padding(end = 16.dp),
                            imageVector = MiuixIcons.Months,
                            contentDescription = "切换数据库文件",
                        )
                    },
                    onClick = onSwitchLibraryClick,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Preference(
                    type = PreferenceType.Arrow,
                    title = "填充器设置",
                    summary = "自动填充相关设置",
                    startAction = {
                        Icon(
                            modifier = Modifier.padding(end = 16.dp),
                            imageVector = MiuixIcons.GridView,
                            contentDescription = "填充器设置",
                        )
                    },
                    onClick = { navigator.push(Route.GeneralSettings) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Preference(
                    type = PreferenceType.Arrow,
                    title = "安全",
                    summary = "自动解锁、超时锁定与后台锁定",
                    startAction = {
                        Icon(
                            modifier = Modifier.padding(end = 16.dp),
                            imageVector = MiuixIcons.Lock,
                            contentDescription = "安全",
                        )
                    },
                    onClick = { navigator.push(Route.SecuritySettings) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Preference(
                    type = PreferenceType.Arrow,
                    title = "备份与恢复",
                    summary = "云端同步、备份与手动恢复",
                    startAction = {
                        Icon(
                            modifier = Modifier.padding(end = 16.dp),
                            imageVector = MiuixIcons.CloudFill,
                            contentDescription = "备份与恢复",
                        )
                    },
                    onClick = { navigator.push(Route.BackupSettings) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Preference(
                    type = PreferenceType.Arrow,
                    title = "数据库设置",
                    summary = "导出/合并与修改主密码、KDF 算法、压缩设置",
                    startAction = {
                        Icon(
                            modifier = Modifier.padding(end = 16.dp),
                            imageVector = MiuixIcons.Settings,
                            contentDescription = "数据库设置",
                        )
                    },
                    onClick = onDatabaseSettingsClick,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Preference(
                    type = PreferenceType.Arrow,
                    title = "关于",
                    summary = "版本、更新日志与检查更新",
                    startAction = {
                        Icon(
                            modifier = Modifier.padding(end = 16.dp),
                            imageVector = MiuixIcons.Info,
                            contentDescription = "关于",
                        )
                    },
                    onClick = { navigator.push(Route.About) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
