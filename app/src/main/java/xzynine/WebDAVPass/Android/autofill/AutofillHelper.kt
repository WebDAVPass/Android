package xzynine.WebDAVPass.Android.autofill

import android.app.PendingIntent
import android.app.assist.AssistStructure
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.autofill.Dataset
import android.service.autofill.Field
import android.service.autofill.FillResponse
import android.service.autofill.Presentations
import android.util.Log
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import xzynine.WebDAVPass.Android.R
import xzynine.WebDAVPass.Android.model.RegisterInfo
import xzynine.WebDAVPass.Android.model.SearchInfo

@RequiresApi(api = Build.VERSION_CODES.O)
object AutofillHelper {
    private const val TAG = "AutofillHelper"

    private const val KEY_PENDING_INTENT_BUNDLE = "xzynine.WebDAVPass.Android.extra.BUNDLE"
    private const val KEY_SPECIAL_MODE = "xzynine.WebDAVPass.Android.extra.SPECIAL_MODE"
    private const val KEY_SEARCH_INFO = "xzynine.WebDAVPass.Android.extra.SEARCH_INFO"
    private const val KEY_REGISTER_INFO = "xzynine.WebDAVPass.Android.extra.REGISTER_INFO"
    private const val KEY_BASE_STRUCTURE = "xzynine.WebDAVPass.Android.autofill.BASE_STRUCTURE"
    private const val KEY_INLINE_SUGGESTIONS_REQUEST = "xzynine.WebDAVPass.Android.autofill.INLINE_SUGGESTIONS_REQUEST"

    fun getSpecialModeFromBundle(bundle: Bundle): SpecialMode? =
        runCatching {
            bundle.getString(KEY_SPECIAL_MODE)?.let { name ->
                SpecialMode.valueOf(name)
            }
        }.getOrNull()

    fun getSearchInfoFromBundle(bundle: Bundle): SearchInfo? {
        @Suppress("DEPRECATION")
        return bundle.getParcelable(KEY_SEARCH_INFO)
    }

    fun getRegisterInfoFromBundle(bundle: Bundle): RegisterInfo? {
        @Suppress("DEPRECATION")
        return bundle.getParcelable(KEY_REGISTER_INFO)
    }

    /**
     * 为「保存表单」创建拉起注册界面的 PendingIntent。
     */
    fun getPendingIntentForRegistration(
        context: Context,
        registerInfo: RegisterInfo,
    ): PendingIntent? =
        try {
            val tempBundle =
                Bundle().apply {
                    putString(KEY_SPECIAL_MODE, SpecialMode.REGISTRATION.name)
                    putParcelable(KEY_REGISTER_INFO, registerInfo)
                }
            val intent =
                Intent(context, AutofillPickerActivity::class.java).apply {
                    putExtra(KEY_PENDING_INTENT_BUNDLE, tempBundle)
                }
            // 注册意图在创建时已写入专用 Bundle，系统无需补充字段；
            // 使用 FLAG_IMMUTABLE 收紧权限，避免接收方修改未设置的 Intent 字段。
            val flags =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT
                } else {
                    PendingIntent.FLAG_CANCEL_CURRENT
                }
            PendingIntent.getActivity(context, (System.currentTimeMillis() and 0xFFFF).toInt(), intent, flags)
        } catch (e: RuntimeException) {
            Log.e(TAG, "Unable to create pending intent for registration", e)
            null
        }

    fun getAutofillComponentFromBundle(bundle: Bundle): AutofillComponent? {
        @Suppress("DEPRECATION")
        bundle.getParcelable<AssistStructure>(KEY_BASE_STRUCTURE)?.let { assistStructure ->
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                @Suppress("DEPRECATION")
                val inlineRequest =
                    bundle.getParcelable<android.view.inputmethod.InlineSuggestionsRequest>(
                        KEY_INLINE_SUGGESTIONS_REQUEST,
                    )
                AutofillComponent(
                    assistStructure,
                    inlineRequest?.let { CompatInlineSuggestionsRequest(it) },
                )
            } else {
                AutofillComponent(assistStructure, null)
            }
        }
        return null
    }

    fun getPendingIntentForSelection(
        context: Context,
        searchInfo: SearchInfo?,
        autofillComponent: AutofillComponent,
    ): PendingIntent? =
        try {
            val tempBundle =
                Bundle().apply {
                    putString(KEY_SPECIAL_MODE, SpecialMode.SELECTION.name)
                    searchInfo?.let {
                        putParcelable(KEY_SEARCH_INFO, it)
                    }
                    putParcelable(KEY_BASE_STRUCTURE, autofillComponent.assistStructure)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        autofillComponent.compatInlineSuggestionsRequest?.inlineSuggestionsRequest?.let { request ->
                            putParcelable(KEY_INLINE_SUGGESTIONS_REQUEST, request)
                        }
                    }
                }
            val intent =
                Intent(context, AutofillPickerActivity::class.java).apply {
                    putExtra(KEY_PENDING_INTENT_BUNDLE, tempBundle)
                }
            val flags =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_CANCEL_CURRENT
                } else {
                    PendingIntent.FLAG_CANCEL_CURRENT
                }
            PendingIntent.getActivity(context, (System.currentTimeMillis() and 0xFFFF).toInt(), intent, flags)
        } catch (e: RuntimeException) {
            Log.e(TAG, "Unable to create pending intent for selection", e)
            null
        }

    fun buildDatasetForEntry(
        context: Context,
        entry: AutofillEntryInfo,
        parseResult: StructureParser.Result,
    ): Dataset {
        val title =
            if (entry.title.isNotEmpty() && entry.username.isNotEmpty()) {
                "${entry.title} (${entry.username})"
            } else if (entry.title.isNotEmpty()) {
                entry.title
            } else {
                entry.username
            }

        val presentation =
            RemoteViews(context.packageName, R.layout.item_autofill_entry).apply {
                setTextViewText(R.id.autofill_entry_text, title)
            }

        val builder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                // API 35 起 RemoteViews 构造与 setValue 弃用，改用 Presentations + setField
                Dataset.Builder(
                    Presentations
                        .Builder()
                        .setMenuPresentation(presentation)
                        .build(),
                )
            } else {
                // API 29-34 无新 API 可用，只能保留弃用的 RemoteViews 构造
                Dataset.Builder(presentation)
            }
        builder.setId(entry.id.toString())

        parseResult.usernameId?.let { id ->
            builder.setValueCompat(id, AutofillValue.forText(entry.username))
        }
        parseResult.passwordId?.let { id ->
            builder.setValueCompat(id, AutofillValue.forText(entry.password))
        }
        parseResult.otpTokenId?.let { id ->
            entry.otpToken?.let { token ->
                builder.setValueCompat(id, AutofillValue.forText(token))
            }
        }

        return builder.build()
    }

    /**
     * API 35 起 [Dataset.Builder.setValue] 弃用，改用 [Dataset.Builder.setField] + [Field]，旧版本回退。
     */
    private fun Dataset.Builder.setValueCompat(
        id: AutofillId,
        value: AutofillValue,
    ): Dataset.Builder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            setField(id, Field.Builder().setValue(value).build())
        } else {
            // API 29-34 无替代 API，只能保留弃用的 setValue
            setValue(id, value)
        }

    fun buildFillResponse(
        context: Context,
        entries: List<AutofillEntryInfo>,
        parseResult: StructureParser.Result,
        autofillComponent: AutofillComponent?,
    ): FillResponse? {
        if (entries.isEmpty()) return null

        val builder = FillResponse.Builder()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            parseResult.webDomain?.let { domain ->
                val header =
                    RemoteViews(context.packageName, R.layout.item_autofill_web_domain).apply {
                        setTextViewText(R.id.autofill_web_domain_text, domain)
                    }
                builder.setHeader(header)
            } ?: parseResult.applicationId?.let { appId ->
                val header =
                    RemoteViews(context.packageName, R.layout.item_autofill_app_id).apply {
                        setTextViewText(R.id.autofill_app_id_text, appId)
                    }
                builder.setHeader(header)
            }
        }

        entries.forEach { entry ->
            try {
                builder.addDataset(buildDatasetForEntry(context, entry, parseResult))
            } catch (e: Exception) {
                Log.e(TAG, "Unable to add dataset for entry: ${entry.title}", e)
            }
        }

        return try {
            builder.build()
        } catch (e: Exception) {
            Log.e(TAG, "Unable to build fill response", e)
            null
        }
    }
}

data class AutofillEntryInfo(
    val id: Long,
    val title: String,
    val username: String = "",
    val password: String = "",
    val url: String = "",
    val otpToken: String? = null,
)

enum class SpecialMode {
    DEFAULT,
    SEARCH,
    SELECTION,
    REGISTRATION,
}
