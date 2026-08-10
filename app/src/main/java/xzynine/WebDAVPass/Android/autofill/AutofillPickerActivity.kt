package xzynine.WebDAVPass.Android.autofill

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.autofill.AutofillManager
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import xzylib.base.util.ToastUtils
import xzynine.WebDAVPass.Android.R
import xzynine.WebDAVPass.Android.data.GroupNodeInfo
import xzynine.WebDAVPass.Android.data.PasswordEntry
import xzynine.WebDAVPass.Android.data.PasswordEntryEditDraft
import xzynine.WebDAVPass.Android.data.RemainingValueType
import xzynine.WebDAVPass.Android.model.RegisterInfo
import xzynine.WebDAVPass.Android.model.SearchInfo
import xzynine.WebDAVPass.Android.ui.Dialog.GroupPickerDialog
import xzynine.WebDAVPass.Android.ui.ViewModel.TokenViewModel
import xzynine.WebDAVPass.Android.theme.AppTheme

class AutofillPickerActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "AutofillPickerActivity"
        private const val KEY_PENDING_INTENT_BUNDLE = "xzynine.WebDAVPass.Android.extra.BUNDLE"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 注册界面会展示明文密码，禁止截屏/录屏
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )

        val bundle = intent.getBundleExtra(KEY_PENDING_INTENT_BUNDLE)
        if (bundle == null) {
            Log.w(TAG, "No bundle provided")
            cancelAndFinish()
            return
        }

        // 注册（保存表单）模式：展示表单值并选择目标分组后创建条目
        if (AutofillHelper.getSpecialModeFromBundle(bundle) == SpecialMode.REGISTRATION) {
            val registerInfo = AutofillHelper.getRegisterInfoFromBundle(bundle)
            if (registerInfo == null) {
                Log.w(TAG, "No register info provided")
                cancelAndFinish()
                return
            }
            setContent {
                AppTheme {
                    RegistrationContent(registerInfo = registerInfo, activity = this@AutofillPickerActivity)
                }
            }
            return
        }

        setContentView(R.layout.activity_autofill_picker)

        val searchInfo = AutofillHelper.getSearchInfoFromBundle(bundle)
        val autofillComponent = AutofillHelper.getAutofillComponentFromBundle(bundle)
        if (autofillComponent == null) {
            Log.w(TAG, "No autofill component provided")
            cancelAndFinish()
            return
        }

        val structure = autofillComponent.assistStructure
        val parseResult = StructureParser(structure).parse(saveValue = false)

        if (parseResult == null || !parseResult.isValid()) {
            Log.w(TAG, "Unable to parse structure or structure is invalid")
            cancelAndFinish()
            return
        }

        loadEntriesAndRespond(searchInfo, parseResult, autofillComponent)
    }

    private fun loadEntriesAndRespond(
        searchInfo: SearchInfo?,
        parseResult: StructureParser.Result,
        autofillComponent: AutofillComponent
    ) {
        lifecycleScope.launch {
            try {
                val tokenViewModel = TokenViewModel.getSharedInstance(applicationContext)
                val passwordViewModel = tokenViewModel.passwordViewModel
                
                val entries = passwordViewModel.passwordEntries.first()
                Log.d(TAG, "Loaded ${entries.size} entries from database")
                
                val autofillEntries = convertToAutofillEntries(entries, searchInfo)
                Log.d(TAG, "Converted to ${autofillEntries.size} autofill entries")
                
                if (autofillEntries.isEmpty()) {
                    Log.w(TAG, "No matching entries found")
                    cancelAndFinish()
                    return@launch
                }
                
                val response = AutofillHelper.buildFillResponse(
                    context = this@AutofillPickerActivity,
                    entries = autofillEntries,
                    parseResult = parseResult,
                    autofillComponent = autofillComponent
                )
                
                if (response != null) {
                    Log.d(TAG, "Successfully built fill response with ${autofillEntries.size} entries")
                    val replyIntent = Intent().putExtra(
                        AutofillManager.EXTRA_AUTHENTICATION_RESULT,
                        response
                    )
                    setResult(Activity.RESULT_OK, replyIntent)
                } else {
                    Log.w(TAG, "Failed to build fill response")
                    setResult(Activity.RESULT_CANCELED)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading entries", e)
                setResult(Activity.RESULT_CANCELED)
            }
            finish()
        }
    }

    private fun convertToAutofillEntries(
        entries: List<PasswordEntry>,
        searchInfo: SearchInfo?
    ): List<AutofillEntryInfo> {
        val autofillEntries = entries.mapNotNull { entry ->
            if (entry.isFolderGroup) return@mapNotNull null
            
            val username = entry.keyValues.find { 
                it.valueType == RemainingValueType.TEXT && 
                (it.fieldName.equals("username", ignoreCase = true) || 
                 it.fieldName.equals("user", ignoreCase = true) ||
                 it.fieldName.equals("email", ignoreCase = true))
            }?.rawValue ?: ""
            
            val password = entry.keyValues.find { 
                it.valueType == RemainingValueType.PASSWORD 
            }?.rawValue ?: ""
            
            val url = entry.keyValues.find { 
                it.valueType == RemainingValueType.URL 
            }?.rawValue ?: ""
            
            val otpToken = entry.keyValues.find { 
                it.valueType == RemainingValueType.OTP 
            }?.rawValue

            AutofillEntryInfo(
                id = entry.entryId,
                title = entry.title,
                username = username,
                password = password,
                url = url,
                otpToken = otpToken
            )
        }

        if (searchInfo == null || searchInfo.containsOnlyNullValues()) {
            return autofillEntries
        }

        val domain = searchInfo.webDomain
        val appId = searchInfo.applicationId

        return autofillEntries.filter { entry ->
            if (!domain.isNullOrEmpty()) {
                entry.url.contains(domain, ignoreCase = true) ||
                entry.title.contains(domain, ignoreCase = true) ||
                entry.username.contains(domain, ignoreCase = true)
            } else if (!appId.isNullOrEmpty()) {
                entry.title.contains(appId, ignoreCase = true) ||
                entry.url.contains(appId, ignoreCase = true)
            } else {
                true
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
    registerInfo: RegisterInfo,
    activity: AutofillPickerActivity
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

    val site = registerInfo.searchInfo.webDomain
        ?: registerInfo.searchInfo.applicationId
        ?: "自动填充"

    fun finishWithResult(ok: Boolean) {
        activity.setResult(if (ok) Activity.RESULT_OK else Activity.RESULT_CANCELED)
        activity.finish()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "保存表单到密码库",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onSurface
        )
        Text(
            text = "站点：$site",
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.onSurface
        )
        Text(
            text = "账号：${registerInfo.username.orEmpty()}",
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.onSurface
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "密码：" + if (showFormPassword) {
                    registerInfo.password.orEmpty()
                } else {
                    "•".repeat(registerInfo.password?.length ?: 0)
                },
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurface
            )
            TextButton(
                text = if (showFormPassword) "隐藏密码" else "显示密码",
                onClick = { showFormPassword = !showFormPassword }
            )
        }

        when {
            hasLibrary == null -> {
                Text(
                    text = "尚未选择数据库文件，请先在应用中打开一个 .kdbx 库",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.error
                )
            }

            !isUnlocked -> {
                TextField(
                    value = masterPassword,
                    onValueChange = { masterPassword = it },
                    label = "主密码",
                    visualTransformation = if (showPassword) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
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
                                        "解锁失败：主密码错误；若该库使用密钥文件，请先在应用内解锁一次后重试"
                                    )
                                }
                            }
                        },
                        enabled = !unlockLoading,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (unlockLoading) "解锁中..." else "解锁")
                    }
                    TextButton(
                        text = if (showPassword) "隐藏" else "显示",
                        onClick = { showPassword = !showPassword }
                    )
                }
            }

            else -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "目标分组",
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary
                        )
                        Text(
                            text = pickerGroups
                                .firstOrNull { it.groupId == selectedGroupId }
                                ?.title
                                ?.ifBlank { "未命名分组" }
                                ?: "根目录",
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.primary
                        )
                    }
                    TextButton(
                        text = "选择分组",
                        onClick = {
                            coroutineScope.launch {
                                pickerGroups = tokenViewModel.loadAllPasswordGroups()
                                showGroupPicker = true
                            }
                        }
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        text = "不保存",
                        onClick = { finishWithResult(false) },
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                saving = true
                                val username = registerInfo.username.orEmpty()
                                val password = registerInfo.password.orEmpty()
                                val entryId = tokenViewModel.createPasswordEntry(
                                    PasswordEntryEditDraft(
                                        parentGroupId = selectedGroupId,
                                        title = site,
                                        username = username,
                                        password = password,
                                        url = registerInfo.searchInfo.webDomain
                                            ?.let { "https://$it" }.orEmpty(),
                                        notes = ""
                                    )
                                )
                                saving = false
                                if (entryId != null) {
                                    ToastUtils.showShortToast(context, "已保存到密码库")
                                    finishWithResult(true)
                                } else {
                                    ToastUtils.showShortToast(context, "保存失败")
                                    finishWithResult(false)
                                }
                            }
                        },
                        enabled = !saving,
                        modifier = Modifier.weight(1f)
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
            }
        )
    }
}
