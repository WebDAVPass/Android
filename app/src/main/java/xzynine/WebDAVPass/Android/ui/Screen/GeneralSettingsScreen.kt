package xzynine.WebDAVPass.Android.ui.Screen

import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
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
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.GridView
import xzynine.WebDAVPass.Android.autofill.AutofillSavePreferences
import xzynine.WebDAVPass.Android.autofill.KeeAutofillService
import xzynine.WebDAVPass.Android.ui.component.Preference
import xzynine.WebDAVPass.Android.ui.component.PreferenceType
import xzynine.WebDAVPass.Android.ui.component.SettingsTopAppBar
import xzynine.WebDAVPass.Android.ui.viewmodel.TokenViewModel

/**
 * 填充器设置子页面（自动填充相关）。
 */
@Composable
fun FillerSettingsContent(
    viewModel: TokenViewModel,
    onNavigateBack: () -> Unit,
) {
    val context = LocalContext.current

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
                                        data = android.net.Uri.fromParts("package", context.packageName, null)
                                    }
                            }
                        context.startActivity(intent)
                    },
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            var askToSaveChecked by remember {
                mutableStateOf(AutofillSavePreferences.askToSaveData)
            }
            // 进入设置页时从本地设置同步（服务可能尚未连接，内存缓存可能过期）
            LaunchedEffect(Unit) {
                AutofillSavePreferences.load(context)
                askToSaveChecked = AutofillSavePreferences.askToSaveData
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
            ) {
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
                        AutofillSavePreferences.setAskToSaveData(context, checked)
                    },
                )
            }
        }
    }
}
