/*
 * Copyright 2026 WebDAVPass.
 *
 * 本文件属于 WebDAVPass for Android（GPLv3）。
 * 自动填充「选择条目」界面：由库的 KeeAutofillService 在检索未命中 / 手动选择时拉起。
 *
 * 复刻旧 AutofillPickerActivity 的选择逻辑，改为使用自动填充库的 API
 * （StructureParser / AutofillHelper / AutofillBridge），数据源来自宿主桥接。
 */

package xzynine.WebDAVPass.Android.autofillbridge

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.view.autofill.AutofillManager
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import xzylib.base.util.ToastUtils
import xzynine.WebDAVPass.Android.theme.AppTheme
import xzynine.WebDAVPass.Android.ui.viewmodel.TokenViewModel
import xzynine.WebDAVPass.Autofill.AutofillBridge
import xzynine.WebDAVPass.Autofill.core.AutofillComponent
import xzynine.WebDAVPass.Autofill.core.AutofillHelper
import xzynine.WebDAVPass.Autofill.core.StructureParser
import xzynine.WebDAVPass.Autofill.model.AutofillQueryResult
import xzynine.WebDAVPass.Autofill.model.AutofillSearchInfo

class AutofillPickerActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "AutofillPickerActivity"
    }

    // 由 onCreate 解析后传入 Compose 内容，解锁校验通过后再用于检索/回填
    private var searchInfo: AutofillSearchInfo? = null
    private var parseResult: StructureParser.Result? = null
    private var autofillComponent: AutofillComponent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 展示条目（及解锁界面的主密码）时防截屏
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )

        // 注册模式交由 AutofillRegistrationActivity 处理
        if (AutofillHelper.getSpecialModeFromIntent(intent) == AutofillHelper.MODE_REGISTRATION) {
            cancelAndFinish()
            return
        }

        val searchInfo = AutofillHelper.getSearchInfoFromIntent(intent)
        val autofillComponent = AutofillHelper.getAutofillComponentFromIntent(intent)
        if (searchInfo == null || autofillComponent == null) {
            cancelAndFinish()
            return
        }
        // 与 KeePassDX 一致：优先在当前 Activity 进程重新解析 AssistStructure，
        // 以获得与本次填充会话绑定、可正确回填的 AutofillId。
        // 仅当重读抛异常（如 MIUI 在 Activity 进程重读结构会抛 SecurityException）时，
        // 才回退到服务侧经 Intent 跨进程传递的已解析 Result。
        val parseResult =
            runCatching { StructureParser(autofillComponent.assistStructure).parse(saveValue = false) }
                .getOrNull()
                ?: AutofillHelper.getParseResultFromIntent(intent)
        if (parseResult == null || !parseResult.isValid()) {
            cancelAndFinish()
            return
        }

        this.searchInfo = searchInfo
        this.parseResult = parseResult
        this.autofillComponent = autofillComponent

        setContent {
            AppTheme {
                PickerContent()
            }
        }
    }

    @Composable
    private fun PickerContent() {
        val context = LocalContext.current
        val tokenViewModel = remember { TokenViewModel.getSharedInstance(context.applicationContext) }
        val isUnlocked by tokenViewModel.libraryViewModel.isLibraryUnlocked.collectAsState(false)
        var masterPassword by remember { mutableStateOf("") }
        var showPassword by remember { mutableStateOf(false) }
        var unlockLoading by remember { mutableStateOf(false) }
        val coroutineScope = rememberCoroutineScope()

        if (!isUnlocked) {
            // 库锁定：先展示主密码解锁界面（交互与 AutofillRegistrationActivity 保持一致），
            // 解锁成功后 isLibraryUnlocked 翻转，下方 else 分支会自动检索并回填。
            UnlockSection(
                masterPassword = masterPassword,
                onMasterPasswordChange = { masterPassword = it },
                showPassword = showPassword,
                onToggleShow = { showPassword = !showPassword },
                unlockLoading = unlockLoading,
                onUnlock = {
                    if (masterPassword.isBlank()) {
                        ToastUtils.showShortToast(context, "请输入主密码")
                    } else {
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
                            // 成功则交由下方 else 分支（isUnlocked 已翻转）检索并回填
                        }
                    }
                },
            )
        } else {
            // 已解锁：检索条目并构建回填响应（loadEntriesAndRespond 内含第三道防线断言）
            LaunchedEffect(Unit) {
                loadEntriesAndRespond(
                    searchInfo = searchInfo!!,
                    parseResult = parseResult!!,
                    autofillComponent = autofillComponent!!,
                )
            }
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("正在加载条目…", color = MiuixTheme.colorScheme.onSurface)
            }
        }
    }

    @Composable
    private fun UnlockSection(
        masterPassword: String,
        onMasterPasswordChange: (String) -> Unit,
        showPassword: Boolean,
        onToggleShow: () -> Unit,
        unlockLoading: Boolean,
        onUnlock: () -> Unit,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "密码库已锁定",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface,
            )
            Text(
                text = "请先输入主密码解锁，以选择要填充的条目",
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurface,
            )
            TextField(
                value = masterPassword,
                onValueChange = onMasterPasswordChange,
                label = "主密码",
                visualTransformation =
                    if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onUnlock,
                    enabled = !unlockLoading,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (unlockLoading) "解锁中…" else "解锁并继续")
                }
                TextButton(
                    text = if (showPassword) "隐藏" else "显示",
                    onClick = onToggleShow,
                )
            }
        }
    }

    private fun loadEntriesAndRespond(
        searchInfo: AutofillSearchInfo,
        parseResult: StructureParser.Result,
        autofillComponent: AutofillComponent,
    ) {
        lifecycleScope.launch {
            try {
                val config = AutofillBridge.getConfig()
                if (config == null) {
                    cancelAndFinish()
                    return@launch
                }
                // 第三道防线：独立断言库已解锁，避免仅依赖 provider 单层校验；
                // 即便 provider 的 isLibraryUnlocked 检查被误改，Picker 也不会在锁定时
                // 构建/返回含条目明文的 FillResponse。
                if (!TokenViewModel
                        .getSharedInstance(applicationContext)
                        .libraryViewModel.isLibraryUnlocked.value
                ) {
                    cancelAndFinish()
                    return@launch
                }
                // 选择界面展示库内全部可填条目，交由系统填充选择器让用户挑选
                val result =
                    withTimeoutOrNull(config.queryTimeoutMillis) {
                        config.entryProvider.search(searchInfo.copy(manualSelection = true))
                    } ?: AutofillQueryResult.NotFound

                if (result is AutofillQueryResult.Found && result.entries.isNotEmpty()) {
                    val response =
                        AutofillHelper.buildResponse(
                            context = this@AutofillPickerActivity,
                            entries = result.entries,
                            parseResult = parseResult,
                            autofillComponent = autofillComponent,
                            preferences = config.preferences,
                            uiTarget = config.uiTarget,
                            appIconRes = config.appIconRes,
                        )
                    if (response != null) {
                        setResult(
                            Activity.RESULT_OK,
                            Intent().putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, response),
                        )
                    } else {
                        setResult(Activity.RESULT_CANCELED)
                    }
                } else {
                    setResult(Activity.RESULT_CANCELED)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading autofill entries", e)
                setResult(Activity.RESULT_CANCELED)
            }
            finish()
        }
    }

    private fun cancelAndFinish() {
        setResult(Activity.RESULT_CANCELED)
        finish()
    }
}
