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
 */
package xzynine.WebDAVPass.Android.autofill

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.CancellationSignal
import android.provider.Settings
import android.service.autofill.AutofillService
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveInfo
import android.service.autofill.SaveRequest
import android.util.Log
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import xzynine.WebDAVPass.Android.R
import xzynine.WebDAVPass.Android.model.RegisterInfo
import xzynine.WebDAVPass.Android.model.SearchInfo


@RequiresApi(api = Build.VERSION_CODES.O)
class KeeAutofillService : AutofillService() {

    private var applicationIdBlocklist: Set<String> = emptySet()
    private var webDomainBlocklist: Set<String> = emptySet()
    private var askToSaveData: Boolean = false

    override fun onConnected() {
        Log.d(TAG, "onConnected")
        getPreferences()
    }

    override fun onDisconnected() {
        Log.d(TAG, "onDisconnected")
    }

    private fun getPreferences() {
        AutofillSavePreferences.load(this)
        askToSaveData = AutofillSavePreferences.askToSaveData
    }

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        cancellationSignal.setOnCancelListener { Log.w(TAG, "Cancel autofill.") }

        if (request.flags and FillRequest.FLAG_COMPATIBILITY_MODE_REQUEST != 0) {
            Log.d(TAG, "Autofill requested in compatibility mode")
        } else {
            Log.d(TAG, "Autofill requested in native mode")
        }

        val latestStructure = request.fillContexts.last().structure
        StructureParser(latestStructure).parse(saveValue = false)?.let { parseResult ->

            val searchInfo = SearchInfo().apply {
                applicationId = parseResult.applicationId
                webScheme = parseResult.webScheme
                webDomain = parseResult.webDomain
            }

            if (autofillAllowedFor(
                    applicationId = parseResult.applicationId,
                    applicationIdBlocklist = applicationIdBlocklist,
                    webDomain = parseResult.webDomain,
                    webDomainBlocklist = webDomainBlocklist)
                ) {

                if (parseResult.isValid()) {
                    val inlineSuggestionsRequest =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            CompatInlineSuggestionsRequest.fromFillRequest(request)
                        } else {
                            null
                        }
                    val autofillComponent = AutofillComponent(
                        latestStructure,
                        inlineSuggestionsRequest
                    )
                    
                    showUIForEntrySelection(
                        parseResult,
                        searchInfo, autofillComponent, callback
                    )
                }
            }
        }
    }

    private fun showUIForEntrySelection(
        parseResult: StructureParser.Result,
        searchInfo: SearchInfo,
        autofillComponent: AutofillComponent,
        callback: FillCallback
    ) {
        var success = false
        parseResult.allAutofillIds().let { autofillIds ->
            if (autofillIds.isNotEmpty()) {
                AutofillHelper.getPendingIntentForSelection(
                    this,
                    searchInfo,
                    autofillComponent
                )?.intentSender?.let { intentSender ->
                    val responseBuilder = FillResponse.Builder()
                    val remoteViewsUnlock: RemoteViews = if (!parseResult.webDomain.isNullOrEmpty()) {
                        RemoteViews(
                            packageName,
                            R.layout.item_autofill_unlock_web_domain
                        ).apply {
                            setTextViewText(
                                R.id.autofill_web_domain_text,
                                parseResult.webDomain
                            )
                        }
                    } else if (!parseResult.applicationId.isNullOrEmpty()) {
                        RemoteViews(packageName, R.layout.item_autofill_unlock_app_id).apply {
                            setTextViewText(
                                R.id.autofill_app_id_text,
                                parseResult.applicationId
                            )
                        }
                    } else {
                        RemoteViews(packageName, R.layout.item_autofill_unlock)
                    }

                    if (askToSaveData) {
                        var types: Int = SaveInfo.SAVE_DATA_TYPE_GENERIC
                        val requiredIds = ArrayList<AutofillId>()

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

                    @Suppress("DEPRECATION")
                    responseBuilder.setAuthentication(
                        autofillIds,
                        intentSender,
                        remoteViewsUnlock
                    )
                    success = true
                    callback.onSuccess(responseBuilder.build())
                }
            }
        }
        if (!success)
            callback.onFailure("Unable to get Autofill ids for UI selection")
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        // 功能关闭或系统版本不支持时静默接受，避免每次表单提交都提示保存失败
        if (!askToSaveData || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            callback.onSuccess()
            return
        }
        var success = false
        val latestStructure = request.fillContexts.last().structure
        StructureParser(latestStructure).parse(saveValue = true)?.let { parseResult ->
            if (parseResult.isValid() && autofillAllowedFor(
                    applicationId = parseResult.applicationId,
                    applicationIdBlocklist = applicationIdBlocklist,
                    webDomain = parseResult.webDomain,
                    webDomainBlocklist = webDomainBlocklist)
                ) {
                Log.d(TAG, "autofill onSaveRequest password")

                val searchInfo = SearchInfo().apply {
                    applicationId = parseResult.applicationId
                    webScheme = parseResult.webScheme
                    webDomain = parseResult.webDomain
                }
                val registerInfo = RegisterInfo(
                    searchInfo = searchInfo,
                    username = parseResult.usernameValue?.textValue?.toString(),
                    password = parseResult.passwordValue?.textValue?.toString()
                )

                // 拉起注册界面：展示表单值并选择目标分组后创建条目
                AutofillHelper.getPendingIntentForRegistration(
                    this,
                    registerInfo
                )?.intentSender?.let { intentSender ->
                    success = true
                    callback.onSuccess(intentSender)
                }
            }
        }
        if (!success) {
            callback.onFailure("Saving form values is not allowed")
        }
    }

    companion object {
        private val TAG = KeeAutofillService::class.java.name

        fun autofillAllowedFor(applicationId: String?,
                               webDomain: String?,
                               context: Context
        ): Boolean {
            return autofillAllowedFor(
                applicationId = applicationId,
                applicationIdBlocklist = emptySet(),
                webDomain = webDomain,
                webDomainBlocklist = emptySet())
        }

        fun autofillAllowedFor(applicationId: String?,
                               applicationIdBlocklist: Set<String>?,
                               webDomain: String?,
                               webDomainBlocklist: Set<String>?
        ): Boolean {
            return autofillAllowedFor(applicationId, applicationIdBlocklist)
                    && applicationId?.contains(APPLICATION_ID_POPUP_WINDOW) != true
                    && autofillAllowedFor(webDomain, webDomainBlocklist)
        }

        fun autofillAllowedFor(element: String?, blockList: Set<String>?): Boolean {
            element?.let { elementNotNull ->
                if (blockList?.any { appIdBlocked ->
                            elementNotNull.contains(appIdBlocked)
                        } == true
                ) {
                    Log.d(TAG, "Autofill not allowed for $elementNotNull")
                    return false
                }
            }
            return true
        }

        const val APPLICATION_ID_POPUP_WINDOW = "PopupWindow:"

        fun Context.isKeeAutofillActivated(): Boolean {
            val activated = ContextCompat.getSystemService(
                this,
                AutofillManager::class.java
            )?.hasEnabledAutofillServices() == true
            return activated
        }

        fun Context.showAutofillDeviceSettings() {
            try {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply {
                        data = "package:${KeeAutofillService::class.java.canonicalName}".toUri()
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Unable to choose the autofill service", e)
            }
        }
    }
}
