package xzynine.WebDAVPass.Android.ui.Screen

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.window.WindowDialog
import xzynine.WebDAVPass.Android.autofillbridge.AppAutofillPreferences
import xzynine.WebDAVPass.Android.ui.component.Preference
import xzynine.WebDAVPass.Android.ui.component.PreferenceType
import xzynine.WebDAVPass.Android.ui.component.SettingsTopAppBar
import xzynine.WebDAVPass.Android.ui.viewmodel.TokenViewModel
import xzynine.WebDAVPass.Autofill.core.KeeAutofillService

/**
 * 填充器设置子页面（自动填充相关）。
 */
@Composable
fun FillerSettingsContent(
    viewModel: TokenViewModel,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current

    var enabledChecked by remember { mutableStateOf(AppAutofillPreferences.autofillSuggestionsEnabled) }
    var inlineChecked by remember { mutableStateOf(AppAutofillPreferences.inlineSuggestionsEnabled) }
    var manualChecked by remember { mutableStateOf(AppAutofillPreferences.manualSelectionEnabled) }
    var askToSaveChecked by remember { mutableStateOf(AppAutofillPreferences.askToSaveData) }

    var showAppBlockDialog by remember { mutableStateOf(false) }
    var showWebBlockDialog by remember { mutableStateOf(false) }
    var blockEditText by remember { mutableStateOf("") }

    // 进入设置页时从本地设置同步内存缓存
    LaunchedEffect(Unit) {
        AppAutofillPreferences.load(context)
        enabledChecked = AppAutofillPreferences.autofillSuggestionsEnabled
        inlineChecked = AppAutofillPreferences.inlineSuggestionsEnabled
        manualChecked = AppAutofillPreferences.manualSelectionEnabled
        askToSaveChecked = AppAutofillPreferences.askToSaveData
    }

    Scaffold(
        popupHost = { },
        topBar = {
            SettingsTopAppBar(
                title = "填充器设置",
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
            Card(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Preference(
                    type = PreferenceType.Arrow,
                    title = "设置为自动填充器",
                    summary = "跳转到系统自动填充设置",
                    startAction = {
                        Icon(
                            modifier = Modifier.padding(end = 16.dp),
                            imageVector = MiuixIcons.GridView,
                            contentDescription = "设置为自动填充器",
                        )
                    },
                    onClick = {
                        val autofillServiceExtra = "android.provider.extra.AUTOFILL_SERVICE"
                        val autofillSettingsAction = "android.settings.AUTOFILL_SETTINGS"
                        val requestIntent =
                            Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply {
                                putExtra(
                                    autofillServiceExtra,
                                    ComponentName(context, KeeAutofillService::class.java),
                                )
                            }
                        val credentialsPickerIntent =
                            Intent().apply {
                                component =
                                    ComponentName(
                                        "com.android.settings",
                                        "com.android.settings.applications.credentials.CredentialsPickerActivity",
                                    )
                            }
                        val fallbackIntent = Intent(autofillSettingsAction)
                        val intent =
                            when {
                                requestIntent.resolveActivity(context.packageManager) != null -> requestIntent
                                credentialsPickerIntent.resolveActivity(context.packageManager) != null -> credentialsPickerIntent
                                fallbackIntent.resolveActivity(context.packageManager) != null -> fallbackIntent
                                else ->
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                    }
                            }
                        context.startActivity(intent)
                    },
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Preference(
                    type = PreferenceType.Switch,
                    title = "启用自动填充",
                    summary = "在支持的输入框中自动匹配密码库条目",
                    checked = enabledChecked,
                    startAction = {
                        Icon(
                            modifier = Modifier.padding(end = 16.dp),
                            imageVector = MiuixIcons.Edit,
                            contentDescription = "启用自动填充",
                        )
                    },
                    onCheckedChange = { checked ->
                        enabledChecked = checked
                        AppAutofillPreferences.setEnabled(context, checked)
                    },
                )
                Preference(
                    type = PreferenceType.Switch,
                    title = "键盘内联建议",
                    summary = "在兼容的输入法候选栏中直接展示填充建议",
                    checked = inlineChecked,
                    startAction = {
                        Icon(
                            modifier = Modifier.padding(end = 16.dp),
                            imageVector = MiuixIcons.Edit,
                            contentDescription = "键盘内联建议",
                        )
                    },
                    onCheckedChange = { checked ->
                        inlineChecked = checked
                        AppAutofillPreferences.setInlineEnabled(context, checked)
                    },
                )
                Preference(
                    type = PreferenceType.Switch,
                    title = "手动选择条目",
                    summary = "在候选列表中提供「手动选择」入口，展示全部条目",
                    checked = manualChecked,
                    startAction = {
                        Icon(
                            modifier = Modifier.padding(end = 16.dp),
                            imageVector = MiuixIcons.Edit,
                            contentDescription = "手动选择条目",
                        )
                    },
                    onCheckedChange = { checked ->
                        manualChecked = checked
                        AppAutofillPreferences.setManualSelectionEnabled(context, checked)
                    },
                )
                Preference(
                    type = PreferenceType.Switch,
                    title = "自动填充时提示保存",
                    summary = "在表单提交后询问是否将账号密码保存到密码库",
                    checked = askToSaveChecked,
                    startAction = {
                        Icon(
                            modifier = Modifier.padding(end = 16.dp),
                            imageVector = MiuixIcons.Edit,
                            contentDescription = "自动填充时提示保存",
                        )
                    },
                    onCheckedChange = { checked ->
                        askToSaveChecked = checked
                        AppAutofillPreferences.setAskToSaveData(context, checked)
                    },
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Preference(
                    type = PreferenceType.Arrow,
                    title = "应用黑名单",
                    summary = "以下应用包名（逗号分隔）将不提供自动填充",
                    onClick = {
                        blockEditText = AppAutofillPreferences.applicationIdBlocklist.joinToString(", ")
                        showAppBlockDialog = true
                    },
                )
                Preference(
                    type = PreferenceType.Arrow,
                    title = "网站黑名单",
                    summary = "以下网站域名（逗号分隔）将不提供自动填充",
                    onClick = {
                        blockEditText = AppAutofillPreferences.webDomainBlocklist.joinToString(", ")
                        showWebBlockDialog = true
                    },
                )
            }
        }
    }

    if (showAppBlockDialog) {
        BlocklistDialog(
            title = "应用黑名单",
            text = blockEditText,
            onTextChange = { blockEditText = it },
            onDismiss = { showAppBlockDialog = false },
            onConfirm = {
                AppAutofillPreferences.setApplicationIdBlocklist(
                    context,
                    blockEditText
                        .split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .toSet(),
                )
                showAppBlockDialog = false
            },
        )
    }
    if (showWebBlockDialog) {
        BlocklistDialog(
            title = "网站黑名单",
            text = blockEditText,
            onTextChange = { blockEditText = it },
            onDismiss = { showWebBlockDialog = false },
            onConfirm = {
                AppAutofillPreferences.setWebDomainBlocklist(
                    context,
                    blockEditText
                        .split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .toSet(),
                )
                showWebBlockDialog = false
            },
        )
    }
}

@Composable
private fun BlocklistDialog(
    title: String,
    text: String,
    onTextChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    WindowDialog(
        title = title,
        show = true,
        onDismissRequest = onDismiss,
    ) {
        TextField(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
            value = text,
            maxLines = 1,
            onValueChange = onTextChange,
            label = "使用逗号分隔多个条目",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                text = "取消",
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                text = "保存",
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColorsPrimary(),
                modifier = Modifier.weight(1f),
            )
        }
    }
}
