/*
 * Copyright 2026 WebDAVPass.
 *
 * 本文件属于 WebDAVPass for Android（GPLv3）。
 * 自动填充「保存注册」界面：由库的 KeeAutofillService.onSaveRequest 拉起。
 *
 * 复刻旧 AutofillPickerActivity 的 RegistrationContent，改为使用自动填充库的
 * AutofillRegisterInfo 模型，保存动作经宿主 AutofillSaveHandler（桥接）落地。
 */

package xzynine.WebDAVPass.Android.autofillbridge

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import xzylib.base.util.ToastUtils
import xzynine.WebDAVPass.Android.data.GroupNodeInfo
import xzynine.WebDAVPass.Android.theme.AppTheme
import xzynine.WebDAVPass.Android.ui.Dialog.GroupPickerDialog
import xzynine.WebDAVPass.Android.ui.viewmodel.TokenViewModel
import xzynine.WebDAVPass.Autofill.AutofillBridge
import xzynine.WebDAVPass.Autofill.core.AutofillHelper
import xzynine.WebDAVPass.Autofill.model.AutofillRegisterInfo

class AutofillRegistrationActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "AutofillRegistrationActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 注册界面会展示明文密码，禁止截屏/录屏
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
        val registerInfo = AutofillHelper.getRegisterInfoFromIntent(intent)
        if (registerInfo == null) {
            Log.w(TAG, "No register info provided")
            cancelAndFinish()
            return
        }
        setContent {
            AppTheme {
                RegistrationContent(registerInfo = registerInfo, activity = this@AutofillRegistrationActivity)
            }
        }
    }

    private fun cancelAndFinish() {
        setResult(Activity.RESULT_CANCELED)
        finish()
    }
}

/**
 * 注册（保存表单）界面：预览表单值，解锁后选择目标分组并创建条目。
 */
@Composable
private fun RegistrationContent(
    registerInfo: AutofillRegisterInfo,
    activity: AutofillRegistrationActivity,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val tokenViewModel = remember { TokenViewModel.getSharedInstance(context.applicationContext) }
    val isUnlocked by tokenViewModel.libraryViewModel.isLibraryUnlocked.collectAsState(false)
    val hasLibrary by tokenViewModel.libraryViewModel.currentLibrary.collectAsState(null)

    var masterPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var unlockLoading by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    // 待保存的表单密码默认遮蔽，防止旁观者读取（FLAG_SECURE 只能阻止截屏/录屏）
    var showFormPassword by remember { mutableStateOf(false) }

    var showGroupPicker by remember { mutableStateOf(false) }
    var pickerGroups by remember { mutableStateOf<List<GroupNodeInfo>>(emptyList()) }
    var selectedGroupId by remember { mutableStateOf<Long?>(null) }

    val site =
        registerInfo.searchInfo.webDomain
            ?: registerInfo.searchInfo.applicationId
            ?: "自动填充"

    fun finishWithResult(ok: Boolean) {
        activity.setResult(if (ok) Activity.RESULT_OK else Activity.RESULT_CANCELED)
        activity.finish()
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "保存表单到密码库",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onSurface,
        )
        Text(
            text = "站点：$site",
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.onSurface,
        )
        Text(
            text = "账号：${registerInfo.username.orEmpty()}",
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.onSurface,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text =
                    "密码：" +
                        if (showFormPassword) {
                            registerInfo.password.orEmpty()
                        } else {
                            "•".repeat(registerInfo.password?.length ?: 0)
                        },
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurface,
            )
            TextButton(
                text = if (showFormPassword) "隐藏密码" else "显示密码",
                onClick = { showFormPassword = !showFormPassword },
            )
        }

        when {
            hasLibrary == null -> {
                Text(
                    text = "尚未选择数据库文件，请先在应用中打开一个 .kdbx 库",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.error,
                )
            }

            !isUnlocked -> {
                TextField(
                    value = masterPassword,
                    onValueChange = { masterPassword = it },
                    label = "主密码",
                    visualTransformation =
                        if (showPassword) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (masterPassword.isBlank()) {
                                ToastUtils.showShortToast(context, "请输入主密码")
                                return@Button
                            }
                            coroutineScope.launch {
                                unlockLoading = true
                                val ok = tokenViewModel.unlockCurrentLibrary(masterPassword)
                                unlockLoading = false
                                if (!ok) {
                                    ToastUtils.showShortToast(
                                        context,
                                        "解锁失败：主密码错误；若该库使用密钥文件，请先在应用内解锁一次后重试",
                                    )
                                }
                            }
                        },
                        enabled = !unlockLoading,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (unlockLoading) "解锁中..." else "解锁")
                    }
                    TextButton(
                        text = if (showPassword) "隐藏" else "显示",
                        onClick = { showPassword = !showPassword },
                    )
                }
            }

            else -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "目标分组",
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary,
                        )
                        Text(
                            text =
                                pickerGroups
                                    .firstOrNull { it.groupId == selectedGroupId }
                                    ?.title
                                    ?.ifBlank { "未命名分组" }
                                    ?: "根目录",
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.primary,
                        )
                    }
                    TextButton(
                        text = "选择分组",
                        onClick = {
                            coroutineScope.launch {
                                pickerGroups = tokenViewModel.loadAllPasswordGroups()
                                showGroupPicker = true
                            }
                        },
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        text = "不保存",
                        onClick = { finishWithResult(false) },
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = {
                            if (registerInfo.password.isNullOrBlank()) {
                                ToastUtils.showShortToast(context, "密码不能为空")
                                return@Button
                            }
                            coroutineScope.launch {
                                saving = true
                                try {
                                    val saved =
                                        AutofillBridge
                                            .requireConfig()
                                            .saveHandler
                                            .save(registerInfo.copy(targetGroupId = selectedGroupId))
                                    if (saved) {
                                        ToastUtils.showShortToast(context, "已保存到密码库")
                                        finishWithResult(true)
                                    } else {
                                        ToastUtils.showShortToast(context, "保存失败")
                                    }
                                } catch (e: Exception) {
                                    ToastUtils.showShortToast(context, "保存失败：${e.message}")
                                } finally {
                                    saving = false
                                }
                            }
                        },
                        enabled = !saving,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (saving) "保存中..." else "保存")
                    }
                }
            }
        }
    }

    if (showGroupPicker) {
        GroupPickerDialog(
            title = "保存到分组",
            show = showGroupPicker,
            groups = pickerGroups,
            onDismiss = { showGroupPicker = false },
            onPick = { targetGroupId ->
                selectedGroupId = targetGroupId
                showGroupPicker = false
            },
            initialSelectedGroupId = selectedGroupId,
        )
    }
}
