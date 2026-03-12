package xzynine.WebDAVPass.Android.autofill

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.autofill.AutofillManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import xzynine.WebDAVPass.Android.R
import xzynine.WebDAVPass.Android.data.PasswordEntry
import xzynine.WebDAVPass.Android.data.RemainingValueType
import xzynine.WebDAVPass.Android.model.SearchInfo
import xzynine.WebDAVPass.Android.ui.ViewModel.TokenViewModel

class AutofillPickerActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "AutofillPickerActivity"
        private const val KEY_PENDING_INTENT_BUNDLE = "xzynine.WebDAVPass.Android.extra.BUNDLE"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_autofill_picker)

        val bundle = intent.getBundleExtra(KEY_PENDING_INTENT_BUNDLE)
        if (bundle == null) {
            Log.w(TAG, "No bundle provided")
            cancelAndFinish()
            return
        }

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
