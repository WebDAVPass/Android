/*
 * Copyright 2019 Jeremy Jamet / Kunzisoft.
 *
 * This file is part of KeePassDX.
 *
 *  KeePassDX is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  KeePassDX is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with KeePassDX.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Modified for WebDAVPass:
 *  - 包名调整至 xzynine.WebDAVPass.Autofill.core
 *  - 全部数据/UI/偏好/日志经 AutofillBridge 桥接接口获取，剥离 KeePassDX 业务层
 *    （DatabaseTaskProvider / ContextualDatabase / MagikeyboardService /
 *     ClipboardEntryNotificationService / SearchHelper / PreferencesUtil / TypeMode / joda-time）
 *  - 检索走 suspend + 超时保护，保证 onFillRequest 回调恰好一次
 *  - 保留 API 35 Presentations/Field 兼容、内联建议、手动选择、黑名单、信用卡填充
 */

package xzynine.WebDAVPass.Autofill.core

import android.content.Context
import android.content.Intent
import android.graphics.BlendMode
import android.graphics.drawable.Icon
import android.os.Build
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.autofill.AutofillService
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.InlinePresentation
import android.service.autofill.Presentations
import android.service.autofill.SaveCallback
import android.service.autofill.SaveInfo
import android.service.autofill.SaveRequest
import android.util.Log
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.widget.RemoteViews
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.autofill.inline.UiVersions
import androidx.autofill.inline.v1.InlineSuggestionUi
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import xzynine.WebDAVPass.Autofill.AutofillBridge
import xzynine.WebDAVPass.Autofill.R
import xzynine.WebDAVPass.Autofill.model.AutofillQueryResult
import xzynine.WebDAVPass.Autofill.model.AutofillRegisterInfo
import xzynine.WebDAVPass.Autofill.model.AutofillSearchInfo

@RequiresApi(api = Build.VERSION_CODES.O)
class KeeAutofillService : AutofillService() {
    // 填充检索在主线程回调，但数据源查询走 IO；统一用带 SupervisorJob 的作用域，
    // 在 onDestroy 时取消，避免泄露。
    private val fillScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onConnected() {
        Log.d(TAG, "onConnected")
    }

    override fun onDisconnected() {
        Log.d(TAG, "onDisconnected")
    }

    /**
     * 当请求的应用/网站命中自动填充黑名单时，除 onFailure 外额外弹 toast 提示用户，
     * 避免仅静默日志导致用户不知为何未触发填充。
     */
    private fun showBlockedToast(
        applicationId: String?,
        webDomain: String?,
    ) {
        val target = applicationId ?: webDomain ?: "该应用"
        Handler(Looper.getMainLooper()).post {
            Toast
                .makeText(
                    this,
                    "「$target」在自动填充黑名单中，已跳过填充",
                    Toast.LENGTH_SHORT,
                ).show()
        }
    }

    override fun onDestroy() {
        fillScope.cancel()
        super.onDestroy()
    }

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback,
    ) {
        val config = AutofillBridge.getConfig()
        if (config == null) {
            Log.e(TAG, "AutofillBridge 未安装，无法处理填充请求")
            callback.onFailure("Autofill bridge not installed")
            return
        }
        val logger = config.logger
        var fillJob: Job? = null
        cancellationSignal.setOnCancelListener {
            logger.w(TAG, "Cancel autofill.")
            // 系统取消请求时真正取消本次检索协程，避免对已过期 callback 继续构建响应
            fillJob?.cancel()
        }

        if (request.flags and FillRequest.FLAG_COMPATIBILITY_MODE_REQUEST != 0) {
            Log.d(TAG, "Autofill requested in compatibility mode")
        } else {
            Log.d(TAG, "Autofill requested in native mode")
        }

        val latestStructure = request.fillContexts.last().structure
        val parseResult = StructureParser(latestStructure).parse(saveValue = false)
        if (parseResult == null || !parseResult.isValid()) {
            callback.onFailure("表单无可填充字段")
            return
        }

        val searchInfo =
            AutofillSearchInfo(
                applicationId = parseResult.applicationId,
                webScheme = parseResult.webScheme,
                webDomain = parseResult.webDomain,
            )

        val prefs = config.preferences
        // 确保偏好已加载：避免冷启动窗口期以默认全开状态应答，忽略用户隐私设置
        prefs.ensureLoaded(applicationContext)
        if (!prefs.autofillSuggestionsEnabled) {
            // 主开关关闭：直接展示选择/解锁界面
            showUIForEntrySelection(parseResult, searchInfo, null, callback, config)
            return
        }
        if (!AutofillBlocklist.allowedFor(parseResult.applicationId, parseResult.webDomain, prefs)) {
            showBlockedToast(parseResult.applicationId, parseResult.webDomain)
            callback.onFailure("应用或网站在自动填充黑名单中")
            return
        }

        val inlineSuggestionsRequest =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && prefs.inlineSuggestionsEnabled) {
                CompatInlineSuggestionsRequest.fromFillRequest(request)
            } else {
                null
            }
        val autofillComponent = AutofillComponent(latestStructure, inlineSuggestionsRequest)

        fillJob =
            fillScope.launch {
                var responded = false
                try {
                    val result =
                        withTimeoutOrNull(config.queryTimeoutMillis) {
                            withContext(Dispatchers.IO) { config.entryProvider.search(searchInfo) }
                        } ?: AutofillQueryResult.NotFound

                    when (result) {
                        is AutofillQueryResult.Found -> {
                            val response =
                                AutofillHelper.buildResponse(
                                    context = this@KeeAutofillService,
                                    entries = result.entries,
                                    parseResult = parseResult,
                                    autofillComponent = autofillComponent,
                                    preferences = prefs,
                                    uiTarget = config.uiTarget,
                                    appIconRes = config.appIconRes,
                                )
                            if (response != null) {
                                callback.onSuccess(response)
                                responded = true
                            }
                        }
                        else -> { /* NotFound / Unavailable：展示选择/解锁界面 */ }
                    }
                    if (!responded) {
                        showUIForEntrySelection(parseResult, searchInfo, autofillComponent, callback, config)
                    }
                } catch (e: Exception) {
                    logger.e(TAG, "onFillRequest 处理失败，退回选择界面", e)
                    if (!responded) {
                        try {
                            showUIForEntrySelection(parseResult, searchInfo, autofillComponent, callback, config)
                        } catch (e2: Exception) {
                            callback.onFailure("自动填充失败")
                        }
                    }
                }
            }
    }

    private fun showUIForEntrySelection(
        parseResult: StructureParser.Result,
        searchInfo: AutofillSearchInfo,
        autofillComponent: AutofillComponent?,
        callback: FillCallback,
        config: xzynine.WebDAVPass.Autofill.AutofillConfig,
    ) {
        var success = false
        parseResult.allAutofillIds().let { autofillIds ->
            if (autofillIds.isNotEmpty()) {
                val intentSender =
                    AutofillHelper
                        .getPendingIntentForSelection(
                            this,
                            searchInfo,
                            autofillComponent,
                            config.uiTarget,
                            parseResult,
                        )?.intentSender
                if (intentSender != null) {
                    val responseBuilder = FillResponse.Builder()
                    val remoteViewsUnlock: RemoteViews =
                        if (!parseResult.webDomain.isNullOrEmpty()) {
                            RemoteViews(
                                packageName,
                                R.layout.item_autofill_unlock_web_domain,
                            ).apply {
                                setTextViewText(R.id.autofill_web_domain_text, parseResult.webDomain)
                            }
                        } else if (!parseResult.applicationId.isNullOrEmpty()) {
                            RemoteViews(packageName, R.layout.item_autofill_unlock_app_id).apply {
                                setTextViewText(R.id.autofill_app_id_text, parseResult.applicationId)
                            }
                        } else {
                            RemoteViews(packageName, R.layout.item_autofill_unlock)
                        }

                    // 提示保存
                    if (config.preferences.askToSaveData) {
                        var types: Int = SaveInfo.SAVE_DATA_TYPE_GENERIC
                        val requiredIds = mutableListOf<AutofillId>()
                        parseResult.passwordId?.let { passwordInfo ->
                            parseResult.usernameId?.let { usernameInfo ->
                                types = types or SaveInfo.SAVE_DATA_TYPE_USERNAME
                                requiredIds.add(usernameInfo)
                            }
                            types = types or SaveInfo.SAVE_DATA_TYPE_PASSWORD
                            requiredIds.add(passwordInfo)
                        }
                        if (requiredIds.isNotEmpty()) {
                            val builder = SaveInfo.Builder(types, requiredIds.toTypedArray())
                            responseBuilder.setSaveInfo(builder.build())
                        }
                    }

                    // 解锁提示的内联建议
                    var inlinePresentation: InlinePresentation? = null
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                        config.preferences.inlineSuggestionsEnabled
                    ) {
                        autofillComponent?.compatInlineSuggestionsRequest?.inlineSuggestionsRequest?.let { req ->
                            val specs = req.inlinePresentationSpecs
                            if (req.maxSuggestionCount > 0 && specs.isNotEmpty()) {
                                val spec = specs[0]
                                if (UiVersions.getVersions(spec.style).contains(UiVersions.INLINE_UI_VERSION_1)) {
                                    val pendingIntent =
                                        AutofillHelper.getPendingIntentForSelection(
                                            this,
                                            searchInfo,
                                            autofillComponent,
                                            config.uiTarget,
                                        ) ?: return@let
                                    inlinePresentation =
                                        InlinePresentation(
                                            InlineSuggestionUi
                                                .newContentBuilder(pendingIntent)
                                                .apply {
                                                    setContentDescription(getString(R.string.autofill_sign_in_prompt))
                                                    setTitle(getString(R.string.autofill_sign_in_prompt))
                                                    setStartIcon(
                                                        Icon
                                                            .createWithResource(
                                                                this@KeeAutofillService,
                                                                if (config.appIconRes != 0) config.appIconRes else R.drawable.ic_autofill_app,
                                                            ).apply { setTintBlendMode(BlendMode.DST) },
                                                    )
                                                }.build()
                                                .slice,
                                            spec,
                                            false,
                                        )
                                }
                            }
                        }
                    }

                    // 认证响应
                    when {
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                            try {
                                responseBuilder.setAuthentication(
                                    autofillIds,
                                    intentSender,
                                    Presentations
                                        .Builder()
                                        .apply {
                                            inlinePresentation?.let { setInlinePresentation(it) }
                                            setDialogPresentation(remoteViewsUnlock)
                                        }.build(),
                                )
                            } catch (e: Exception) {
                                config.logger.e(TAG, "Unable to use new setAuthentication method.", e)
                                @Suppress("DEPRECATION")
                                responseBuilder.setAuthentication(
                                    autofillIds,
                                    intentSender,
                                    remoteViewsUnlock,
                                    inlinePresentation,
                                )
                            }
                        }
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                            @Suppress("DEPRECATION")
                            responseBuilder.setAuthentication(
                                autofillIds,
                                intentSender,
                                remoteViewsUnlock,
                                inlinePresentation,
                            )
                        }
                        else -> {
                            @Suppress("DEPRECATION")
                            responseBuilder.setAuthentication(
                                autofillIds,
                                intentSender,
                                remoteViewsUnlock,
                            )
                        }
                    }

                    success = true
                    callback.onSuccess(responseBuilder.build())
                }
            }
        }
        if (!success) {
            callback.onFailure("Unable to get Autofill ids for UI selection")
        }
    }

    override fun onSaveRequest(
        request: SaveRequest,
        callback: SaveCallback,
    ) {
        val config = AutofillBridge.getConfig()
        if (config == null) {
            callback.onFailure("Autofill bridge not installed")
            return
        }
        val prefs = config.preferences
        // 确保偏好已加载：避免冷启动窗口期以默认全开状态应答，忽略用户隐私设置
        prefs.ensureLoaded(applicationContext)
        // 功能关闭时静默接受，避免每次表单提交都提示保存失败
        if (!prefs.askToSaveData) {
            callback.onSuccess()
            return
        }

        val latestStructure = request.fillContexts.last().structure
        val parseResult = StructureParser(latestStructure).parse(saveValue = true)
        if (parseResult == null || !parseResult.isValid()) {
            callback.onFailure("无法解析当前表单结构，暂不支持保存")
            return
        }
        if (!AutofillBlocklist.allowedFor(parseResult.applicationId, parseResult.webDomain, prefs)) {
            callback.onFailure("当前应用或网站已被加入黑名单，不允许保存表单")
            return
        }

        val passwordText = parseResult.passwordValue?.textValue?.toString()
        // 密码为空或纯空格时不拉起注册界面，避免保存无意义条目
        if (passwordText.isNullOrBlank()) {
            callback.onSuccess()
            return
        }

        val searchInfo =
            AutofillSearchInfo(
                applicationId = parseResult.applicationId,
                webScheme = parseResult.webScheme,
                webDomain = parseResult.webDomain,
            )
        val registerInfo =
            AutofillRegisterInfo(
                searchInfo = searchInfo,
                username = parseResult.usernameValue?.textValue?.toString(),
                password = passwordText,
            )

        val intentSender =
            AutofillHelper
                .getPendingIntentForRegistration(this, registerInfo, config.uiTarget)
                ?.intentSender
        if (intentSender != null) {
            callback.onSuccess(intentSender)
        } else {
            callback.onFailure("无法创建保存入口（PendingIntent 构建失败）")
        }
    }

    companion object {
        private val TAG = KeeAutofillService::class.java.name

        fun Context.isCredentialProviderActivated(): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat
                    .getSystemService(this, AutofillManager::class.java)
                    ?.hasEnabledAutofillServices() == true
            } else {
                false
            }

        fun Context.showAutofillDeviceSettings() {
            try {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply {
                        data = "package:${KeeAutofillService::class.java.canonicalName}".toUri()
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                )
            } catch (e: Exception) {
                Log.e(TAG, "Unable to choose the autofill service", e)
            }
        }
    }
}
