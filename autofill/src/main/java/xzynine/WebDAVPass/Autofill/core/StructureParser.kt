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
                        applicationId = windowNode.title.toString().split("/")[0]
                        Log.d(TAG, "Autofill applicationId: $applicationId")

                        if (applicationId?.contains(APPLICATION_ID_POPUP_WINDOW) == false) {
                            if (parseViewNode(windowNode.rootViewNode)) {
                                break@mainLoop
                            }
                        }
                    }
                    // 若未显式找到 username 字段，则把密码前的候选字段作为 username
                    if (usernameId == null && passwordId != null && usernameIdCandidate != null) {
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
                    if (parseNodeByAutofillHint(node)) {
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
        node.autofillHints?.forEach {
            when {
                it.contains("2faAppOTPCode", true) ||
                    it.contains("one-time-code", true) ||
                    it.contains("one-time-password", true) -> {
                    Log.d(TAG, "Autofill OTP token")
                    result?.otpTokenId = autofillId
                    result?.otpTokenValue = node.autofillValue
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
                }
                it.contains(View.AUTOFILL_HINT_PHONE, true) -> {
                    if (usernameIdCandidate == null) {
                        usernameIdCandidate = autofillId
                        usernameValueCandidate = node.autofillValue
                        Log.d(TAG, "Autofill phone")
                    }
                }
                it.contains(View.AUTOFILL_HINT_PASSWORD, true) -> {
                    if (result?.passwordId != null && usernameIdCandidate != null) {
                        result?.usernameId = usernameIdCandidate
                        result?.usernameValue = usernameValueCandidate
                    }
                    result?.passwordId = autofillId
                    result?.passwordValue = node.autofillValue
                    Log.d(TAG, "Autofill password hint")
                }
                it.equals("cc-name", true) -> {
                    Log.d(TAG, "Autofill credit card name hint")
                    result?.creditCardHolderId = autofillId
                    result?.creditCardHolder = node.autofillValue
                }
                it.contains(View.AUTOFILL_HINT_CREDIT_CARD_NUMBER, true) ||
                    it.equals("cc-number", true) -> {
                    Log.d(TAG, "Autofill credit card number hint")
                    result?.creditCardNumberId = autofillId
                    result?.creditCardNumber = node.autofillValue
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
                }
                it.contains(View.AUTOFILL_HINT_CREDIT_CARD_EXPIRATION_DATE, true) -> {
                    Log.d(TAG, "Autofill credit card expiration date hint")
                    result?.creditCardExpirationDateId = autofillId
                    node.autofillValue?.let { value ->
                        if (value.isDate) {
                            result?.creditCardExpirationValueMillis = value.dateValue
                        }
                    }
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
                }
                it.contains(View.AUTOFILL_HINT_CREDIT_CARD_SECURITY_CODE, true) ||
                    it.contains("cc-csc", true) -> {
                    Log.d(TAG, "Autofill card security code hint")
                    result?.cardVerificationValueId = autofillId
                    result?.cardVerificationValue = node.autofillValue
                }
                it.equals("off", true) ||
                    it.equals("on", true) -> {
                    Log.d(TAG, "Autofill web hint")
                    return parseNodeByHtmlAttributes(node)
                }
                else -> Log.d(TAG, "Autofill unsupported hint $it")
            }
        }
        return false
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
        if (node.className == "android.widget.EditText") {
            Log.d(TAG, "Autofill null android input type class: ${showHexInputType(inputType)}, get the EditText node class name!")
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
    class Result {
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

        fun isValid(): Boolean = passwordId != null || creditCardNumberId != null || otpTokenId != null

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
    }

    companion object {
        private val TAG = StructureParser::class.java.name

        const val APPLICATION_ID_POPUP_WINDOW = "PopupWindow:"
    }
}
