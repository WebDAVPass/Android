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
 *  - 信用卡过期时间由 joda-time DateTime 改为 epoch millis（避免库依赖 joda-time）
 *  - 去除 KeePassDX 业务无关引用
 */

package xzynine.WebDAVPass.Autofill.core

import android.app.assist.AssistStructure
import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import android.text.InputType
import android.util.Log
import android.view.View
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import androidx.annotation.RequiresApi
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * 解析 AssistStructure，推断用户名 / 密码 / 信用卡 / OTP 字段。
 * 引擎逻辑与 KeePassDX 保持一致（三策略：autofillHint → htmlAttribute → androidInputType）。
 */
@RequiresApi(api = Build.VERSION_CODES.O)
class StructureParser(
    private val structure: AssistStructure,
    private val webViewDenied: Boolean = false,
) {
    private var result: Result? = null
    private var usernameIdCandidate: AutofillId? = null
    private var usernameValueCandidate: AutofillValue? = null

    fun parseOrNull(saveValue: Boolean = false): Result? {
        val result = parse(saveValue)
        if (result != null && result.isValid()) {
            return result
        }
        return null
    }

    fun parse(saveValue: Boolean): Result? {
        try {
            result =
                Result().apply {
                    allowSaveValues = saveValue
                    usernameIdCandidate = null
                    usernameValueCandidate = null
                    mainLoop@ for (i in 0 until structure.windowNodeCount) {
                        val windowNode = structure.getWindowNodeAt(i)
                        val windowAppId = windowNode.title.toString().split("/")[0]
                        Log.d(TAG, "Autofill applicationId: $windowAppId")

                        // 弹窗窗口（PopupWindow:xxx）不是真实应用包名：跳过其中的字段解析，
                        // 且不得覆盖 applicationId，否则会把弹窗名误当作包名，
                        // 导致被「黑名单」误拦截（即便黑名单为空）。
                        if (windowAppId?.contains(APPLICATION_ID_POPUP_WINDOW) == true) {
                            continue
                        }
                        if (applicationId == null) {
                            applicationId = windowAppId
                        }
                        if (parseViewNode(windowNode.rootViewNode)) {
                            break@mainLoop
                        }
                    }
                    // 若未显式找到 username 字段，则把候选字段（通常为密码框前的文本输入框）作为 username。
                    // 不再要求必须存在密码框，以兼容仅含账号/用户名、无密码框的分步登录页（如部分应用）。
                    if (usernameId == null && usernameIdCandidate != null) {
                        usernameId = usernameIdCandidate
                        usernameValue = usernameValueCandidate
                    }
                }
            return result
        } catch (e: Exception) {
            Log.e(TAG, "Autofill error", e)
            return null
        }
    }

    private fun parseViewNode(node: AssistStructure.ViewNode): Boolean {
        // WebView 过滤
        if (node.className?.contains("webview", ignoreCase = true) == true) {
            result?.isWebView = true
            if (webViewDenied) {
                Log.w(TAG, "Blocking webview Autofill for ${node.className}")
                return false
            } else {
                Log.d(TAG, "Enabling webview Autofill for ${node.className}")
            }
        }

        node.webDomain?.let { webDomain ->
            if (webDomain.isNotEmpty()) {
                result?.webDomain = webDomain
                Log.d(TAG, "Autofill domain: $webDomain")
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            node.webScheme?.let { webScheme ->
                if (webScheme.isNotEmpty()) {
                    result?.webScheme = webScheme
                    Log.d(TAG, "Autofill scheme: $webScheme")
                }
            }
        }
        val domainNotEmpty = result?.webDomain?.isNotEmpty() == true

        var returnValue = false
        // 仅解析可见节点
        if (node.visibility == View.VISIBLE) {
            if (node.autofillId != null) {
                val hints = node.autofillHints
                if (!hints.isNullOrEmpty()) {
                    // 带 hint 但未被识别时，回落到 html/inputType 兜底，
                    // 避免"带了一个不认识的 hint 反而漏识别 inputType"导致表单识别失败
                    if (!parseNodeByAutofillHint(node)) {
                        if (parseNodeByHtmlAttributes(node)) {
                            returnValue = true
                        } else if (parseNodeByAndroidInput(node)) {
                            returnValue = true
                        }
                    } else {
                        returnValue = true
                    }
                } else if (parseNodeByHtmlAttributes(node)) {
                    returnValue = true
                } else if (parseNodeByAndroidInput(node)) {
                    returnValue = true
                }
            }
            // 域名非空时命中即返回（优化）
            if (domainNotEmpty && returnValue) {
                return true
            }
            for (i in 0 until node.childCount) {
                if (parseViewNode(node.getChildAt(i))) {
                    returnValue = true
                }
                if (domainNotEmpty && returnValue) {
                    return true
                }
            }
        }
        return returnValue
    }

    private fun parseNodeByAutofillHint(node: AssistStructure.ViewNode): Boolean {
        val autofillId = node.autofillId
        var recognized = false
        node.autofillHints?.forEach {
            when {
                it.contains("2faAppOTPCode", true) ||
                    it.contains("one-time-code", true) ||
                    it.contains("one-time-password", true) -> {
                    Log.d(TAG, "Autofill OTP token")
                    result?.otpTokenId = autofillId
                    result?.otpTokenValue = node.autofillValue
                    recognized = true
                }
                it.contains(View.AUTOFILL_HINT_USERNAME, true) ||
                    it.contains(View.AUTOFILL_HINT_EMAIL_ADDRESS, true) ||
                    it.contains("email", true) ||
                    it.contains("login", true) -> {
                    if (result?.passwordId == null) {
                        result?.usernameId = autofillId
                        result?.usernameValue = node.autofillValue
                        Log.d(TAG, "Autofill username hint if no password")
                    } else {
                        usernameIdCandidate = autofillId
                        usernameValueCandidate = node.autofillValue
                        Log.d(TAG, "Autofill username hint if password")
                    }
                    recognized = true
                }
                it.contains(View.AUTOFILL_HINT_PHONE, true) -> {
                    if (usernameIdCandidate == null) {
                        usernameIdCandidate = autofillId
                        usernameValueCandidate = node.autofillValue
                        Log.d(TAG, "Autofill phone")
                    }
                    recognized = true
                }
                it.contains(View.AUTOFILL_HINT_PASSWORD, true) -> {
                    if (result?.passwordId != null && usernameIdCandidate != null) {
                        result?.usernameId = usernameIdCandidate
                        result?.usernameValue = usernameValueCandidate
                    }
                    result?.passwordId = autofillId
                    result?.passwordValue = node.autofillValue
                    Log.d(TAG, "Autofill password hint")
                    recognized = true
                }
                it.equals("cc-name", true) -> {
                    Log.d(TAG, "Autofill credit card name hint")
                    result?.creditCardHolderId = autofillId
                    result?.creditCardHolder = node.autofillValue
                    recognized = true
                }
                it.contains(View.AUTOFILL_HINT_CREDIT_CARD_NUMBER, true) ||
                    it.equals("cc-number", true) -> {
                    Log.d(TAG, "Autofill credit card number hint")
                    result?.creditCardNumberId = autofillId
                    result?.creditCardNumber = node.autofillValue
                    recognized = true
                }
                it.equals("cc-exp", true) -> {
                    Log.d(TAG, "Autofill credit card expiration date hint")
                    result?.creditCardExpirationDateId = autofillId
                    node.autofillValue?.let { value ->
                        if (value.isText && value.textValue.length == 7) {
                            value.textValue.let { date ->
                                try {
                                    val yy = date.substring(2, 4).toInt()
                                    val mm = date.substring(5, 7).toInt()
                                    result?.creditCardExpirationValueMillis =
                                        LocalDate
                                            .of(2000 + yy, mm, 1)
                                            .atStartOfDay(ZoneId.systemDefault())
                                            .toInstant()
                                            .toEpochMilli()
                                } catch (e: Exception) {
                                    Log.e(TAG, "Unable to retrieve expiration", e)
                                }
                            }
                        }
                    }
                    recognized = true
                }
                it.contains(View.AUTOFILL_HINT_CREDIT_CARD_EXPIRATION_DATE, true) -> {
                    Log.d(TAG, "Autofill credit card expiration date hint")
                    result?.creditCardExpirationDateId = autofillId
                    node.autofillValue?.let { value ->
                        if (value.isDate) {
                            result?.creditCardExpirationValueMillis = value.dateValue
                        }
                    }
                    recognized = true
                }
                it.contains(View.AUTOFILL_HINT_CREDIT_CARD_EXPIRATION_YEAR, true) ||
                    it.equals("cc-exp-year", true) -> {
                    Log.d(TAG, "Autofill credit card expiration year hint")
                    result?.creditCardExpirationYearId = autofillId
                    if (node.autofillOptions != null) {
                        result?.creditCardExpirationYearOptions = node.autofillOptions
                    }
                    node.autofillValue?.let { value ->
                        var year = 0
                        try {
                            if (value.isText) {
                                year = value.textValue.toString().toInt()
                            }
                            if (value.isList) {
                                year =
                                    node.autofillOptions
                                        ?.get(value.listValue)
                                        .toString()
                                        .toInt()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Unable to retrieve expiration year", e)
                        }
                        result?.creditCardExpirationYearValue = year % 100
                    }
                    recognized = true
                }
                it.contains(View.AUTOFILL_HINT_CREDIT_CARD_EXPIRATION_MONTH, true) ||
                    it.equals("cc-exp-month", true) -> {
                    Log.d(TAG, "Autofill credit card expiration month hint")
                    result?.creditCardExpirationMonthId = autofillId
                    if (node.autofillOptions != null) {
                        result?.creditCardExpirationMonthOptions = node.autofillOptions
                    }
                    node.autofillValue?.let { value ->
                        var month = 0
                        try {
                            if (value.isText) {
                                month = value.textValue.toString().toInt()
                            }
                            if (value.isList) {
                                month = value.listValue + 1
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Unable to retrieve expiration month", e)
                        }
                        result?.creditCardExpirationMonthValue = month
                    }
                    recognized = true
                }
                it.contains(View.AUTOFILL_HINT_CREDIT_CARD_EXPIRATION_DAY, true) ||
                    it.equals("cc-exp-day", true) -> {
                    Log.d(TAG, "Autofill credit card expiration day hint")
                    result?.creditCardExpirationDayId = autofillId
                    if (node.autofillOptions != null) {
                        result?.creditCardExpirationDayOptions = node.autofillOptions
                    }
                    node.autofillValue?.let { value ->
                        var day = 0
                        try {
                            if (value.isText) {
                                day = value.textValue.toString().toInt()
                            }
                            if (value.isList) {
                                day =
                                    node.autofillOptions
                                        ?.get(value.listValue)
                                        .toString()
                                        .toInt()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Unable to retrieve expiration day", e)
                        }
                        result?.creditCardExpirationDayValue = day
                    }
                    recognized = true
                }
                it.contains(View.AUTOFILL_HINT_CREDIT_CARD_SECURITY_CODE, true) ||
                    it.contains("cc-csc", true) -> {
                    Log.d(TAG, "Autofill card security code hint")
                    result?.cardVerificationValueId = autofillId
                    result?.cardVerificationValue = node.autofillValue
                    recognized = true
                }
                it.equals("off", true) ||
                    it.equals("on", true) -> {
                    Log.d(TAG, "Autofill web hint")
                    return parseNodeByHtmlAttributes(node)
                }
                else -> Log.d(TAG, "Autofill unsupported hint $it")
            }
        }
        return recognized
    }

    private fun parseNodeByHtmlAttributes(node: AssistStructure.ViewNode): Boolean {
        val autofillId = node.autofillId
        val nodHtml = node.htmlInfo
        when (nodHtml?.tag?.lowercase(Locale.ENGLISH)) {
            "input" -> {
                nodHtml.attributes?.forEach { pairAttribute ->
                    when (pairAttribute.first.lowercase(Locale.ENGLISH)) {
                        "id", "name" -> {
                            when (pairAttribute.second.lowercase(Locale.ENGLISH)) {
                                "2fa",
                                "2fpin",
                                "app_otp",
                                "app_totp",
                                "auth",
                                "challenge",
                                "code",
                                "idvpin",
                                "mfa",
                                "mfacode",
                                "otp",
                                "otpcode",
                                "token",
                                "totp",
                                "totppin",
                                "two-factor",
                                "twofa",
                                "twofactor",
                                "verification_pin",
                                -> {
                                    result?.otpTokenId = autofillId
                                    result?.otpTokenValue = node.autofillValue
                                    Log.d(TAG, "Autofill OTP token web id: ${node.htmlInfo?.tag} ${node.htmlInfo?.attributes}")
                                    return true
                                }
                            }
                        }
                        "type" -> {
                            when (pairAttribute.second.lowercase(Locale.ENGLISH)) {
                                "tel", "email" -> {
                                    if (result?.passwordId == null) {
                                        result?.usernameId = autofillId
                                        result?.usernameValue = node.autofillValue
                                        Log.d(TAG, "Autofill username web type: ${node.htmlInfo?.tag} ${node.htmlInfo?.attributes}")
                                    }
                                }
                                "text" -> {
                                    if (result?.passwordId == null) {
                                        usernameIdCandidate = autofillId
                                        usernameValueCandidate = node.autofillValue
                                        Log.d(TAG, "Autofill username candidate web type: ${node.htmlInfo?.tag} ${node.htmlInfo?.attributes}")
                                    }
                                }
                                "password" -> {
                                    result?.passwordId = autofillId
                                    result?.passwordValue = node.autofillValue
                                    Log.d(TAG, "Autofill password web type: ${node.htmlInfo?.tag} ${node.htmlInfo?.attributes}")
                                    return true
                                }
                            }
                        }
                    }
                }
            }
        }
        return false
    }

    private fun inputIsVariationType(
        inputType: Int,
        vararg type: Int,
    ): Boolean {
        type.forEach {
            if (inputType and InputType.TYPE_MASK_VARIATION == it) {
                return true
            }
        }
        return false
    }

    private fun showHexInputType(inputType: Int): String = "0x${"%08x".format(inputType)}"

    private fun manageTypeText(
        node: AssistStructure.ViewNode,
        autofillId: AutofillId?,
        inputType: Int,
    ): Boolean {
        when {
            inputIsVariationType(
                inputType,
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            ) -> {
                if (result?.passwordId == null) {
                    result?.usernameId = autofillId
                    result?.usernameValue = node.autofillValue
                    Log.d(TAG, "Autofill username android text type: ${showHexInputType(inputType)}")
                }
            }
            inputIsVariationType(
                inputType,
                InputType.TYPE_TEXT_VARIATION_NORMAL,
                InputType.TYPE_TEXT_VARIATION_PERSON_NAME,
                InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT,
            ) -> {
                if (result?.passwordId == null) {
                    usernameIdCandidate = autofillId
                    usernameValueCandidate = node.autofillValue
                    Log.d(TAG, "Autofill username candidate android text type: ${showHexInputType(inputType)}")
                }
            }
            inputIsVariationType(
                inputType,
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            ) -> {
                if (result?.passwordId == null &&
                    usernameIdCandidate == null &&
                    usernameValueCandidate == null
                ) {
                    usernameIdCandidate = autofillId
                    usernameValueCandidate = node.autofillValue
                    Log.d(TAG, "Autofill visible password android text type (as username): ${showHexInputType(inputType)}")
                } else if (result?.passwordId == null && result?.passwordValue == null) {
                    result?.passwordId = autofillId
                    result?.passwordValue = node.autofillValue
                    Log.d(TAG, "Autofill visible password android text type (as password): ${showHexInputType(inputType)}")
                }
            }
            inputIsVariationType(
                inputType,
                InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            ) -> {
                result?.passwordId = autofillId
                result?.passwordValue = node.autofillValue
                Log.d(TAG, "Autofill password android text type: ${showHexInputType(inputType)}")
                return true
            }
            inputIsVariationType(
                inputType,
                InputType.TYPE_TEXT_VARIATION_EMAIL_SUBJECT,
                InputType.TYPE_TEXT_VARIATION_FILTER,
                InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE,
                InputType.TYPE_TEXT_VARIATION_PHONETIC,
                InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS,
                InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE,
                InputType.TYPE_TEXT_VARIATION_URI,
            ) -> {
                Log.d(TAG, "Autofill not used android text type: ${showHexInputType(inputType)}")
            }
            else -> {
                Log.d(TAG, "Autofill unknown android text type: ${showHexInputType(inputType)}")
                // 未知文本变体（如网页纯文本框 variation==0）也当作用户名候选，避免漏识别
                if (result?.passwordId == null) {
                    usernameIdCandidate = autofillId
                    usernameValueCandidate = node.autofillValue
                    Log.d(TAG, "Autofill username candidate (unknown text type): ${showHexInputType(inputType)}")
                }
            }
        }
        return false
    }

    private fun manageTypeNumber(
        node: AssistStructure.ViewNode,
        autofillId: AutofillId?,
        inputType: Int,
    ): Boolean {
        when {
            inputIsVariationType(
                inputType,
                InputType.TYPE_NUMBER_VARIATION_NORMAL,
            ) -> {
                if (usernameIdCandidate == null) {
                    usernameIdCandidate = autofillId
                    usernameValueCandidate = node.autofillValue
                    Log.d(TAG, "Autofill username candidate android number type: ${showHexInputType(inputType)}")
                }
            }
            inputIsVariationType(
                inputType,
                InputType.TYPE_NUMBER_VARIATION_PASSWORD,
            ) -> {
                result?.passwordId = autofillId
                result?.passwordValue = node.autofillValue
                Log.d(TAG, "Autofill password android number type: ${showHexInputType(inputType)}")
                return true
            }
            else -> {
                Log.d(TAG, "Autofill unknown android number type: ${showHexInputType(inputType)}")
            }
        }
        return false
    }

    private fun manageTypeNull(
        node: AssistStructure.ViewNode,
        autofillId: AutofillId?,
        inputType: Int,
    ): Boolean {
        // WebView 内文本输入框 className 非 EditText，但其 inputType 常为 TYPE_NULL；
        // 在 WebView 上下文中也当作用户名候选，避免漏识别
        val isEditableText = node.className == "android.widget.EditText" || result?.isWebView == true
        if (isEditableText) {
            Log.d(TAG, "Autofill null android input type class: ${showHexInputType(inputType)}, editable node")
            if (result?.passwordId == null) {
                usernameIdCandidate = autofillId
                usernameValueCandidate = node.autofillValue
            }
        }
        return false
    }

    private fun parseNodeByAndroidInput(node: AssistStructure.ViewNode): Boolean {
        val autofillId = node.autofillId
        val inputType = node.inputType
        when (inputType and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_TEXT -> return manageTypeText(node, autofillId, inputType)
            InputType.TYPE_CLASS_NUMBER -> return manageTypeNumber(node, autofillId, inputType)
            InputType.TYPE_NULL -> return manageTypeNull(node, autofillId, inputType)
        }
        return false
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    class Result : Parcelable {
        var isWebView: Boolean = false
        var applicationId: String? = null
        var webDomain: String? = null
            set(value) {
                if (field == null) {
                    field = value
                }
            }

        var webScheme: String? = null
            set(value) {
                if (field == null) {
                    field = value
                }
            }

        // 信用卡过期年月日的下拉选项
        var creditCardExpirationYearOptions: Array<CharSequence>? = null
        var creditCardExpirationMonthOptions: Array<CharSequence>? = null
        var creditCardExpirationDayOptions: Array<CharSequence>? = null

        var usernameId: AutofillId? = null
        var passwordId: AutofillId? = null
        var creditCardHolderId: AutofillId? = null
        var creditCardNumberId: AutofillId? = null
        var creditCardExpirationDateId: AutofillId? = null
        var creditCardExpirationYearId: AutofillId? = null
        var creditCardExpirationMonthId: AutofillId? = null
        var creditCardExpirationDayId: AutofillId? = null
        var cardVerificationValueId: AutofillId? = null
        var otpTokenId: AutofillId? = null

        fun isValid(): Boolean = usernameId != null || passwordId != null || creditCardNumberId != null || otpTokenId != null

        fun allAutofillIds(): Array<AutofillId> {
            val all = mutableListOf<AutofillId>()
            usernameId?.let { all.add(it) }
            passwordId?.let { all.add(it) }
            creditCardHolderId?.let { all.add(it) }
            creditCardNumberId?.let { all.add(it) }
            cardVerificationValueId?.let { all.add(it) }
            otpTokenId?.let { all.add(it) }
            return all.toTypedArray()
        }

        // 仅注册模式可写
        var allowSaveValues = false

        var usernameValue: AutofillValue? = null
            set(value) {
                if (allowSaveValues) field = value
            }

        var passwordValue: AutofillValue? = null
            set(value) {
                if (allowSaveValues) field = value
            }

        var creditCardHolder: AutofillValue? = null
            set(value) {
                if (allowSaveValues) field = value
            }

        var creditCardNumber: AutofillValue? = null
            set(value) {
                if (allowSaveValues) field = value
            }

        // 信用卡过期时间（epoch millis，避免 joda-time 依赖）
        var creditCardExpirationValueMillis: Long? = null
            set(value) {
                if (allowSaveValues) field = value
            }

        var creditCardExpirationYearValue = 0
            set(value) {
                if (allowSaveValues) field = value
            }

        var creditCardExpirationMonthValue = 0
            set(value) {
                if (allowSaveValues) field = value
            }

        var creditCardExpirationDayValue = 0
            set(value) {
                if (allowSaveValues) field = value
            }

        var cardVerificationValue: AutofillValue? = null
            set(value) {
                if (allowSaveValues) field = value
            }

        var otpTokenValue: AutofillValue? = null
            set(value) {
                if (allowSaveValues) field = value
            }

        // region Parcelable：避免在选择 Activity 中重新解析 AssistStructure（MIUI 在 Activity 进程重读结构会抛 SecurityException）
        constructor()

        constructor(parcel: Parcel) {
            // 先开启可写，使下方 value 的自定义 setter 能正确存储反序列化得到的值
            allowSaveValues = true
            isWebView = parcel.readByte() != 0.toByte()
            applicationId = parcel.readString()
            webDomain = parcel.readString()
            webScheme = parcel.readString()
            // 信用卡过期选项数组仅用于信用卡选择分支，选择/保存流程不依赖，重建时置空
            creditCardExpirationYearOptions = null
            creditCardExpirationMonthOptions = null
            creditCardExpirationDayOptions = null
            usernameId = parcel.readParcelable(AutofillId::class.java.classLoader)
            passwordId = parcel.readParcelable(AutofillId::class.java.classLoader)
            creditCardHolderId = parcel.readParcelable(AutofillId::class.java.classLoader)
            creditCardNumberId = parcel.readParcelable(AutofillId::class.java.classLoader)
            creditCardExpirationDateId = parcel.readParcelable(AutofillId::class.java.classLoader)
            creditCardExpirationYearId = parcel.readParcelable(AutofillId::class.java.classLoader)
            creditCardExpirationMonthId = parcel.readParcelable(AutofillId::class.java.classLoader)
            creditCardExpirationDayId = parcel.readParcelable(AutofillId::class.java.classLoader)
            cardVerificationValueId = parcel.readParcelable(AutofillId::class.java.classLoader)
            otpTokenId = parcel.readParcelable(AutofillId::class.java.classLoader)
            usernameValue = parcel.readParcelable(AutofillValue::class.java.classLoader)
            passwordValue = parcel.readParcelable(AutofillValue::class.java.classLoader)
            creditCardHolder = parcel.readParcelable(AutofillValue::class.java.classLoader)
            creditCardNumber = parcel.readParcelable(AutofillValue::class.java.classLoader)
            creditCardExpirationValueMillis = parcel.readValue(Long::class.java.classLoader) as? Long
            creditCardExpirationYearValue = parcel.readInt()
            creditCardExpirationMonthValue = parcel.readInt()
            creditCardExpirationDayValue = parcel.readInt()
            cardVerificationValue = parcel.readParcelable(AutofillValue::class.java.classLoader)
            otpTokenValue = parcel.readParcelable(AutofillValue::class.java.classLoader)
        }

        override fun writeToParcel(
            parcel: Parcel,
            flags: Int,
        ) {
            parcel.writeByte(if (isWebView) 1 else 0)
            parcel.writeString(applicationId)
            parcel.writeString(webDomain)
            parcel.writeString(webScheme)
            // 信用卡过期选项数组不写入 Parcel（重建为 null，见读取端）
            parcel.writeParcelable(usernameId, flags)
            parcel.writeParcelable(passwordId, flags)
            parcel.writeParcelable(creditCardHolderId, flags)
            parcel.writeParcelable(creditCardNumberId, flags)
            parcel.writeParcelable(creditCardExpirationDateId, flags)
            parcel.writeParcelable(creditCardExpirationYearId, flags)
            parcel.writeParcelable(creditCardExpirationMonthId, flags)
            parcel.writeParcelable(creditCardExpirationDayId, flags)
            parcel.writeParcelable(cardVerificationValueId, flags)
            parcel.writeParcelable(otpTokenId, flags)
            parcel.writeParcelable(usernameValue, flags)
            parcel.writeParcelable(passwordValue, flags)
            parcel.writeParcelable(creditCardHolder, flags)
            parcel.writeParcelable(creditCardNumber, flags)
            parcel.writeValue(creditCardExpirationValueMillis)
            parcel.writeInt(creditCardExpirationYearValue)
            parcel.writeInt(creditCardExpirationMonthValue)
            parcel.writeInt(creditCardExpirationDayValue)
            parcel.writeParcelable(cardVerificationValue, flags)
            parcel.writeParcelable(otpTokenValue, flags)
        }

        override fun describeContents(): Int = 0

        companion object CREATOR : Parcelable.Creator<Result> {
            override fun createFromParcel(parcel: Parcel): Result = Result(parcel)

            override fun newArray(size: Int): Array<Result?> = arrayOfNulls(size)
        }
        // endregion
    }

    companion object {
        private val TAG = StructureParser::class.java.name

        const val APPLICATION_ID_POPUP_WINDOW = "PopupWindow:"
    }
}
