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
 *  - 条目模型由 KeePassDX EntryInfo 改为本库 AutofillEntry
 *  - 数据源/UI 跳转由宿主桥接接口提供（AutofillUiTarget / AutofillPreferences）
 *  - 保留并合并 app 侧的 API 35 (VanillaIceCream) Presentations/Field 兼容写法
 *  - 去除 Magikeyboard / 剪贴板通知 / IconImage 等业务依赖
 */

package xzynine.WebDAVPass.Autofill.core

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.assist.AssistStructure
import android.content.Context
import android.content.Intent
import android.graphics.BlendMode
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.service.autofill.Dataset
import android.service.autofill.Field
import android.service.autofill.FillResponse
import android.service.autofill.InlinePresentation
import android.service.autofill.Presentations
import android.util.Log
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import android.widget.inline.InlinePresentationSpec
import androidx.annotation.RequiresApi
import androidx.autofill.inline.UiVersions
import androidx.autofill.inline.v1.InlineSuggestionUi
import androidx.core.os.BundleCompat
import kotlinx.coroutines.withTimeoutOrNull
import xzynine.WebDAVPass.Autofill.R
import xzynine.WebDAVPass.Autofill.bridge.AutofillPreferences
import xzynine.WebDAVPass.Autofill.bridge.AutofillUiTarget
import xzynine.WebDAVPass.Autofill.model.AutofillEntry
import xzynine.WebDAVPass.Autofill.model.AutofillQueryResult
import xzynine.WebDAVPass.Autofill.model.AutofillRegisterInfo
import xzynine.WebDAVPass.Autofill.model.AutofillSearchInfo
import java.util.Random
import kotlin.math.min

/**
 * 自动填充响应构建工具：负责数据集（Dataset）、内联建议（Inline Suggestions）、
 * 选择/注册界面的 PendingIntent 以及认证返回 Intent 的构造。
 */
@RequiresApi(api = Build.VERSION_CODES.O)
object AutofillHelper {
    private const val TAG = "AutofillHelper"

    private const val EXTRA_BASE_STRUCTURE = "xzynine.WebDAVPass.Autofill.BASE_STRUCTURE"
    private const val EXTRA_INLINE_SUGGESTIONS_REQUEST = "xzynine.WebDAVPass.Autofill.INLINE_SUGGESTIONS_REQUEST"
    private const val EXTRA_SPECIAL_MODE = "xzynine.WebDAVPass.Autofill.SPECIAL_MODE"
    private const val EXTRA_SEARCH_INFO = "xzynine.WebDAVPass.Autofill.SEARCH_INFO"
    private const val EXTRA_PARSE_RESULT = "xzynine.WebDAVPass.Autofill.PARSE_RESULT"
    private const val EXTRA_REGISTER_INFO = "xzynine.WebDAVPass.Autofill.REGISTER_INFO"

    /** 选择 / 注册模式标识（经 PendingIntent 传递给宿主界面）。 */
    const val MODE_SELECTION = "SELECTION"
    const val MODE_REGISTRATION = "REGISTRATION"

    private fun randomRequestCode(): Int = Random().nextInt(0xFFFF)

    // region AutofillComponent 在 Intent / Bundle 间的传递

    fun Intent.addAutofillComponent(autofillComponent: AutofillComponent?): Intent {
        autofillComponent?.let {
            putExtra(EXTRA_BASE_STRUCTURE, autofillComponent.assistStructure)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                autofillComponent.compatInlineSuggestionsRequest?.let { req ->
                    putExtra(EXTRA_INLINE_SUGGESTIONS_REQUEST, req.inlineSuggestionsRequest)
                }
            }
        }
        return this
    }

    fun Intent.retrieveAutofillComponent(): AutofillComponent? {
        val structure =
            BundleCompat.getParcelable(
                extras ?: Bundle.EMPTY,
                EXTRA_BASE_STRUCTURE,
                AssistStructure::class.java,
            )
        structure ?: return null
        val compatInlineSuggestionsRequest =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                BundleCompat
                    .getParcelable(
                        extras ?: Bundle.EMPTY,
                        EXTRA_INLINE_SUGGESTIONS_REQUEST,
                        android.view.inputmethod.InlineSuggestionsRequest::class.java,
                    )?.let { CompatInlineSuggestionsRequest(it) }
            } else {
                null
            }
        return AutofillComponent(structure, compatInlineSuggestionsRequest)
    }

    fun Bundle.addAutofillComponent(autofillComponent: AutofillComponent?): Bundle {
        autofillComponent?.let {
            putParcelable(EXTRA_BASE_STRUCTURE, autofillComponent.assistStructure)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                autofillComponent.compatInlineSuggestionsRequest?.let { req ->
                    putParcelable(EXTRA_INLINE_SUGGESTIONS_REQUEST, req.inlineSuggestionsRequest)
                }
            }
        }
        return this
    }

    fun Bundle.retrieveAutofillComponent(): AutofillComponent? {
        val structure =
            BundleCompat.getParcelable(
                this,
                EXTRA_BASE_STRUCTURE,
                AssistStructure::class.java,
            )
        structure ?: return null
        val compatInlineSuggestionsRequest =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                BundleCompat
                    .getParcelable(
                        this,
                        EXTRA_INLINE_SUGGESTIONS_REQUEST,
                        android.view.inputmethod.InlineSuggestionsRequest::class.java,
                    )?.let { CompatInlineSuggestionsRequest(it) }
            } else {
                null
            }
        return AutofillComponent(structure, compatInlineSuggestionsRequest)
    }

    // endregion

    // region 宿主界面读取（selection / registration）

    fun getSpecialModeFromIntent(intent: Intent): String? = intent.getStringExtra(EXTRA_SPECIAL_MODE)

    fun getSearchInfoFromIntent(intent: Intent): AutofillSearchInfo? =
        BundleCompat.getParcelable(
            intent.extras ?: Bundle.EMPTY,
            EXTRA_SEARCH_INFO,
            AutofillSearchInfo::class.java,
        )

    fun getRegisterInfoFromIntent(intent: Intent): AutofillRegisterInfo? =
        BundleCompat.getParcelable(
            intent.extras ?: Bundle.EMPTY,
            EXTRA_REGISTER_INFO,
            AutofillRegisterInfo::class.java,
        )

    fun getAutofillComponentFromIntent(intent: Intent): AutofillComponent? = intent.retrieveAutofillComponent()

    fun getParseResultFromIntent(intent: Intent): StructureParser.Result? =
        BundleCompat.getParcelable(
            intent.extras ?: Bundle.EMPTY,
            EXTRA_PARSE_RESULT,
            StructureParser.Result::class.java,
        )

    // endregion

    // region PendingIntent 构造（指向宿主界面）

    /**
     * 为「选择条目」创建拉起宿主选择界面的 PendingIntent。
     * 该意图需系统回填 [AutofillManager.EXTRA_AUTHENTICATION_RESULT]，故使用 FLAG_MUTABLE。
     */
    fun getPendingIntentForSelection(
        context: Context,
        searchInfo: AutofillSearchInfo?,
        autofillComponent: AutofillComponent?,
        uiTarget: AutofillUiTarget,
        parseResult: StructureParser.Result? = null,
    ): PendingIntent? =
        try {
            val intent =
                Intent().apply {
                    component = uiTarget.selectionActivity()
                    putExtra(EXTRA_SPECIAL_MODE, MODE_SELECTION)
                    searchInfo?.let { putExtra(EXTRA_SEARCH_INFO, it) }
                    parseResult?.let { putExtra(EXTRA_PARSE_RESULT, it) }
                    addAutofillComponent(autofillComponent)
                }
            val flags =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_CANCEL_CURRENT
                } else {
                    PendingIntent.FLAG_CANCEL_CURRENT
                }
            PendingIntent.getActivity(context, randomRequestCode(), intent, flags)
        } catch (e: RuntimeException) {
            Log.e(TAG, "Unable to create pending intent for selection", e)
            null
        }

    private fun getPendingIntentForSelectionLaunch(
        context: Context,
        searchInfo: AutofillSearchInfo?,
        autofillComponent: AutofillComponent?,
        uiTarget: AutofillUiTarget,
        parseResult: StructureParser.Result? = null,
    ): PendingIntent? =
        try {
            val intent =
                Intent().apply {
                    component = uiTarget.selectionActivity()
                    putExtra(EXTRA_SPECIAL_MODE, MODE_SELECTION)
                    searchInfo?.let { putExtra(EXTRA_SEARCH_INFO, it) }
                    parseResult?.let { putExtra(EXTRA_PARSE_RESULT, it) }
                    addAutofillComponent(autofillComponent)
                }
            val flags =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_CANCEL_CURRENT
                } else {
                    PendingIntent.FLAG_CANCEL_CURRENT
                }
            PendingIntent.getActivity(context, randomRequestCode(), intent, flags)
        } catch (e: RuntimeException) {
            Log.e(TAG, "Unable to create pending intent for selection launch", e)
            null
        }

    /**
     * 为「保存表单」创建拉起宿主注册界面的 PendingIntent。
     * 创建时已确定字段，系统无需补充，故使用 FLAG_IMMUTABLE。
     */
    fun getPendingIntentForRegistration(
        context: Context,
        registerInfo: AutofillRegisterInfo,
        uiTarget: AutofillUiTarget,
    ): PendingIntent? =
        try {
            val intent =
                Intent().apply {
                    component = uiTarget.registrationActivity()
                    putExtra(EXTRA_SPECIAL_MODE, MODE_REGISTRATION)
                    putExtra(EXTRA_REGISTER_INFO, registerInfo)
                }
            val flags =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT
                } else {
                    PendingIntent.FLAG_CANCEL_CURRENT
                }
            PendingIntent.getActivity(context, randomRequestCode(), intent, flags)
        } catch (e: RuntimeException) {
            Log.e(TAG, "Unable to create pending intent for registration", e)
            null
        }

    // endregion

    // region 响应构建

    /**
     * 构建可直接返回的填充响应（命中条目时调用）。
     */
    fun buildResponse(
        context: Context,
        entries: List<AutofillEntry>,
        parseResult: StructureParser.Result,
        autofillComponent: AutofillComponent,
        preferences: AutofillPreferences,
        uiTarget: AutofillUiTarget,
        appIconRes: Int,
    ): FillResponse? {
        if (entries.isEmpty()) return null

        // 内联建议点击后会拉起选择界面，需要把搜索信息与已解析结果一并带过去，
        // 否则选择界面会因缺少 searchInfo 直接取消、或在 MIUI 上重读 AssistStructure 抛 SecurityException。
        val searchInfo =
            AutofillSearchInfo(
                applicationId = parseResult.applicationId,
                webDomain = parseResult.webDomain,
                webScheme = parseResult.webScheme,
                manualSelection = true,
            )

        val responseBuilder = FillResponse.Builder()

        // 头部：网站域名或应用包名
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            parseResult.webDomain?.let { webDomain ->
                responseBuilder.setHeader(
                    RemoteViews(context.packageName, R.layout.item_autofill_web_domain).apply {
                        setTextViewText(R.id.autofill_web_domain_text, webDomain)
                    },
                )
            } ?: parseResult.applicationId?.let { applicationId ->
                responseBuilder.setHeader(
                    RemoteViews(context.packageName, R.layout.item_autofill_app_id).apply {
                        setTextViewText(R.id.autofill_app_id_text, applicationId)
                    },
                )
            }
        }

        // 内联建议数量
        var numberInlineSuggestions = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            autofillComponent.compatInlineSuggestionsRequest
                ?.inlineSuggestionsRequest
                ?.let { req ->
                    numberInlineSuggestions = minOf(req.maxSuggestionCount, entries.size)
                    if (preferences.manualSelectionEnabled && entries.size >= req.maxSuggestionCount) {
                        --numberInlineSuggestions
                    }
                }
        }

        entries.forEachIndexed { _, entry ->
            try {
                var inlinePresentation: InlinePresentation? = null
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                    numberInlineSuggestions > 0 &&
                    autofillComponent.compatInlineSuggestionsRequest != null
                ) {
                    inlinePresentation =
                        buildInlinePresentationForEntry(
                            context,
                            autofillComponent.compatInlineSuggestionsRequest,
                            numberInlineSuggestions--,
                            entry,
                            uiTarget,
                            appIconRes,
                            searchInfo,
                            autofillComponent,
                            parseResult,
                        )
                }
                responseBuilder.addDataset(
                    buildDatasetForEntry(
                        context = context,
                        entry = entry,
                        struct = parseResult,
                        inlinePresentation = inlinePresentation,
                        appIconRes = appIconRes,
                        searchInfo = searchInfo,
                        autofillComponent = autofillComponent,
                    ),
                )
            } catch (e: Exception) {
                Log.e(TAG, "Unable to add dataset for entry: ${entry.title}", e)
            }
        }

        if (preferences.manualSelectionEnabled) {
            addManualSelectionDataset(context, parseResult, autofillComponent, responseBuilder, uiTarget, appIconRes)
        }

        return try {
            responseBuilder.build()
        } catch (e: Exception) {
            Log.e(TAG, "Unable to create Autofill response", e)
            null
        }
    }

    /**
     * 构造认证返回 Intent（宿主选择界面查询到条目后回传）。
     * 重新解析 AssistStructure，复用 [buildResponse]。
     */
    fun buildFillResponseIntent(
        context: Context,
        autofillComponent: AutofillComponent,
        entries: List<AutofillEntry>,
        preferences: AutofillPreferences,
        uiTarget: AutofillUiTarget,
        appIconRes: Int,
    ): Intent? {
        if (entries.isEmpty()) return null
        StructureParser(autofillComponent.assistStructure).parseOrNull()?.let { result ->
            val response =
                buildResponse(
                    context = context,
                    entries = entries,
                    parseResult = result,
                    autofillComponent = autofillComponent,
                    preferences = preferences,
                    uiTarget = uiTarget,
                    appIconRes = appIconRes,
                )
            return Intent().putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, response)
        }
        return null
    }

    /**
     * 便捷方法：宿主选择界面在协程中调用，内部完成检索 + 响应构建。
     * 检索复用桥接 [xzynine.WebDAVPass.Autofill.AutofillBridge]，超时按未命中处理。
     */
    suspend fun buildResponseForSelection(
        context: Context,
        autofillComponent: AutofillComponent,
        searchInfo: AutofillSearchInfo,
    ): Intent? {
        val config =
            xzynine.WebDAVPass.Autofill.AutofillBridge
                .getConfig() ?: run {
                Log.e(TAG, "AutofillBridge 未安装，无法构建选择响应")
                return null
            }
        val result =
            withTimeoutOrNull(config.queryTimeoutMillis) {
                config.entryProvider.search(searchInfo)
            } ?: AutofillQueryResult.NotFound
        if (result is AutofillQueryResult.Found) {
            return buildFillResponseIntent(
                context = context,
                autofillComponent = autofillComponent,
                entries = result.entries,
                preferences = config.preferences,
                uiTarget = config.uiTarget,
                appIconRes = config.appIconRes,
            )
        }
        return null
    }

    // endregion

    // region 数据集与内联建议

    private fun makeEntryTitle(entry: AutofillEntry): String =
        when {
            entry.title.isNotEmpty() && entry.username.isNotEmpty() -> "${entry.title} (${entry.username})"
            entry.title.isNotEmpty() -> entry.title
            entry.url.isNotEmpty() -> entry.url
            entry.username.isNotEmpty() -> entry.username
            else -> ""
        }

    private fun Dataset.Builder.addValueToDatasetBuilder(
        id: AutofillId,
        autofillValue: AutofillValue?,
    ): Dataset.Builder {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            // API 35 起 setValue 弃用，改用 Field
            setField(id, autofillValue?.let { Field.Builder().setValue(it).build() })
        } else {
            @Suppress("DEPRECATION")
            setValue(id, autofillValue)
        }
        Log.d(TAG, "Set Autofill value for id $id")
        return this
    }

    private fun buildDatasetForEntry(
        context: Context,
        entry: AutofillEntry,
        struct: StructureParser.Result,
        inlinePresentation: InlinePresentation?,
        appIconRes: Int,
        searchInfo: AutofillSearchInfo?,
        autofillComponent: AutofillComponent?,
    ): Dataset {
        val title = makeEntryTitle(entry)
        val remoteViews =
            RemoteViews(context.packageName, R.layout.item_autofill_entry).apply {
                setTextViewText(R.id.autofill_entry_text, title)
                if (appIconRes != 0) {
                    setImageViewResource(R.id.autofill_icon, appIconRes)
                }
            }

        val datasetBuilder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Dataset
                    .Builder(
                        Presentations
                            .Builder()
                            .apply {
                                inlinePresentation?.let { setInlinePresentation(it) }
                            }.setDialogPresentation(remoteViews)
                            .setMenuPresentation(remoteViews)
                            .build(),
                    )
            } else {
                @Suppress("DEPRECATION")
                Dataset.Builder(remoteViews).apply {
                    inlinePresentation?.let {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            setInlinePresentation(it)
                        }
                    }
                }
            }

        datasetBuilder.setId(entry.id.toString())

        struct.usernameId?.let { id ->
            datasetBuilder.addValueToDatasetBuilder(id, AutofillValue.forText(entry.username))
        }
        struct.passwordId?.let { id ->
            datasetBuilder.addValueToDatasetBuilder(id, AutofillValue.forText(entry.password))
        }

        // 信用卡
        val creditCard = entry.creditCard
        struct.creditCardHolderId?.let { id ->
            datasetBuilder.addValueToDatasetBuilder(id, AutofillValue.forText(creditCard?.holder))
        }
        struct.creditCardNumberId?.let { id ->
            datasetBuilder.addValueToDatasetBuilder(id, AutofillValue.forText(creditCard?.number))
        }
        struct.cardVerificationValueId?.let { id ->
            datasetBuilder.addValueToDatasetBuilder(id, AutofillValue.forText(creditCard?.cvv))
        }
        if (creditCard != null && creditCard.expiryYear != null && creditCard.expiryMonth != null) {
            val year = if (creditCard.expiryYear < 100) 2000 + creditCard.expiryYear else creditCard.expiryYear
            val month = creditCard.expiryMonth
            val day = creditCard.expiryDay ?: 1
            val millis =
                try {
                    java.time.LocalDate
                        .of(year, month, day)
                        .atStartOfDay(java.time.ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()
                } catch (e: Exception) {
                    Log.e(TAG, "Unable to build expiry millis", e)
                    0L
                }
            val yearString = year.toString()
            val monthString = month.toString().padStart(2, '0')
            val dayString = day.toString().padStart(2, '0')

            struct.creditCardExpirationDateId?.let {
                if (struct.isWebView) {
                    datasetBuilder.addValueToDatasetBuilder(
                        it,
                        AutofillValue.forText("$yearString-$monthString"),
                    )
                } else {
                    datasetBuilder.addValueToDatasetBuilder(it, AutofillValue.forDate(millis))
                }
            }
            struct.creditCardExpirationYearId?.let {
                var autofillValue: AutofillValue? = null
                struct.creditCardExpirationYearOptions?.let { options ->
                    var yearIndex = options.indexOf(yearString.substring(0, 2))
                    if (yearIndex == -1) yearIndex = options.indexOf(yearString)
                    if (yearIndex != -1) {
                        autofillValue = AutofillValue.forList(yearIndex)
                        datasetBuilder.addValueToDatasetBuilder(it, autofillValue)
                    }
                }
                if (autofillValue == null) {
                    datasetBuilder.addValueToDatasetBuilder(it, AutofillValue.forText(yearString))
                }
            }
            struct.creditCardExpirationMonthId?.let {
                if (struct.isWebView) {
                    datasetBuilder.addValueToDatasetBuilder(it, AutofillValue.forText(monthString))
                } else {
                    if (struct.creditCardExpirationMonthOptions != null) {
                        datasetBuilder.addValueToDatasetBuilder(it, AutofillValue.forList(month - 1))
                    } else {
                        datasetBuilder.addValueToDatasetBuilder(it, AutofillValue.forText(monthString))
                    }
                }
            }
            struct.creditCardExpirationDayId?.let {
                if (struct.isWebView) {
                    datasetBuilder.addValueToDatasetBuilder(it, AutofillValue.forText(dayString))
                } else {
                    if (struct.creditCardExpirationDayOptions != null) {
                        datasetBuilder.addValueToDatasetBuilder(it, AutofillValue.forList(day - 1))
                    } else {
                        datasetBuilder.addValueToDatasetBuilder(it, AutofillValue.forText(dayString))
                    }
                }
            }
        }

        // OTP
        struct.otpTokenId?.let { id ->
            entry.otpToken?.let { token ->
                datasetBuilder.addValueToDatasetBuilder(id, AutofillValue.forText(token))
            }
        }

        Log.d(TAG, "Autofill Dataset for entry '${entry.title}' created")
        return datasetBuilder.build()
    }

    @SuppressLint("RestrictedApi")
    @RequiresApi(Build.VERSION_CODES.R)
    private fun buildInlinePresentationForEntry(
        context: Context,
        compat: CompatInlineSuggestionsRequest,
        positionItem: Int,
        entry: AutofillEntry,
        uiTarget: AutofillUiTarget,
        appIconRes: Int,
        searchInfo: AutofillSearchInfo?,
        autofillComponent: AutofillComponent?,
        parseResult: StructureParser.Result?,
    ): InlinePresentation? {
        compat.inlineSuggestionsRequest?.let { req ->
            val specs = req.inlinePresentationSpecs
            val max = req.maxSuggestionCount
            if (positionItem <= max - 1) {
                val spec = specs[min(positionItem, specs.size - 1)]
                val imeStyle = spec.style
                if (!UiVersions.getVersions(imeStyle).contains(UiVersions.INLINE_UI_VERSION_1)) {
                    return null
                }
                val pendingIntent =
                    getPendingIntentForSelectionLaunch(
                        context,
                        searchInfo,
                        autofillComponent,
                        uiTarget,
                        parseResult,
                    ) ?: return null
                return InlinePresentation(
                    InlineSuggestionUi
                        .newContentBuilder(pendingIntent)
                        .apply {
                            setContentDescription(context.getString(R.string.autofill_sign_in_prompt))
                            setTitle(entry.title)
                            setSubtitle(entry.username)
                            setStartIcon(
                                Icon
                                    .createWithResource(
                                        context,
                                        if (appIconRes != 0) appIconRes else R.drawable.ic_autofill_app,
                                    ).apply { setTintBlendMode(BlendMode.DST) },
                            )
                        }.build()
                        .slice,
                    spec,
                    false,
                )
            }
        }
        return null
    }

    @RequiresApi(Build.VERSION_CODES.R)
    @SuppressLint("RestrictedApi")
    private fun buildInlinePresentationForManualSelection(
        context: Context,
        inlinePresentationSpec: InlinePresentationSpec,
        pendingIntent: PendingIntent,
        appIconRes: Int,
    ): InlinePresentation? {
        val imeStyle = inlinePresentationSpec.style
        if (!UiVersions.getVersions(imeStyle).contains(UiVersions.INLINE_UI_VERSION_1)) {
            return null
        }
        return InlinePresentation(
            InlineSuggestionUi
                .newContentBuilder(pendingIntent)
                .apply {
                    setContentDescription(context.getString(R.string.autofill_sign_in_prompt))
                    setTitle(context.getString(R.string.autofill_select_entry))
                    setStartIcon(
                        Icon
                            .createWithResource(
                                context,
                                if (appIconRes != 0) appIconRes else R.drawable.ic_autofill_app,
                            ).apply { setTintBlendMode(BlendMode.DST) },
                    )
                }.build()
                .slice,
            inlinePresentationSpec,
            false,
        )
    }

    private fun addManualSelectionDataset(
        context: Context,
        parseResult: StructureParser.Result,
        autofillComponent: AutofillComponent,
        responseBuilder: FillResponse.Builder,
        uiTarget: AutofillUiTarget,
        appIconRes: Int,
    ) {
        val searchInfo =
            AutofillSearchInfo(
                applicationId = parseResult.applicationId,
                webDomain = parseResult.webDomain,
                webScheme = parseResult.webScheme,
                manualSelection = true,
            )
        val view = RemoteViews(context.packageName, R.layout.item_autofill_select_entry)
        val pendingIntent =
            getPendingIntentForSelection(context, searchInfo, autofillComponent, uiTarget, parseResult)
                ?: return

        var inlinePresentation: InlinePresentation? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            autofillComponent.compatInlineSuggestionsRequest
                ?.inlineSuggestionsRequest
                ?.let { req ->
                    val specs = req.inlinePresentationSpecs
                    if (specs.isNotEmpty()) {
                        inlinePresentation =
                            buildInlinePresentationForManualSelection(context, specs[0], pendingIntent, appIconRes)
                    }
                }
        }

        val datasetBuilder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Dataset
                    .Builder(
                        Presentations
                            .Builder()
                            .apply {
                                inlinePresentation?.let { setInlinePresentation(it) }
                            }.setDialogPresentation(view)
                            .setMenuPresentation(view)
                            .build(),
                    )
            } else {
                @Suppress("DEPRECATION")
                Dataset.Builder(view).apply {
                    inlinePresentation?.let {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            setInlinePresentation(it)
                        }
                    }
                }
            }

        parseResult.allAutofillIds().forEach { id ->
            datasetBuilder.addValueToDatasetBuilder(id, null)
        }
        // 认证意图对所有字段相同，仅在循环外设置一次
        datasetBuilder.setAuthentication(pendingIntent.intentSender)
        responseBuilder.addDataset(datasetBuilder.build())
    }

    // endregion
}
