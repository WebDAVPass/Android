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
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 展示条目时防截屏
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
        loadEntriesAndRespond(searchInfo, parseResult, autofillComponent)
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
