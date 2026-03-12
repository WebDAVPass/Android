package xzynine.WebDAVPass.Android.autofill

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.autofill.AutofillManager
import androidx.appcompat.app.AppCompatActivity
import xzynine.WebDAVPass.Android.R

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

        val response = AutofillHelper.buildFillResponse(
            context = this,
            entries = emptyList(),
            parseResult = parseResult,
            autofillComponent = autofillComponent
        )
        
        if (response != null) {
            val replyIntent = Intent().putExtra(
                AutofillManager.EXTRA_AUTHENTICATION_RESULT,
                response
            )
            setResult(Activity.RESULT_OK, replyIntent)
        } else {
            setResult(Activity.RESULT_CANCELED)
        }
        finish()
    }

    private fun cancelAndFinish() {
        setResult(Activity.RESULT_CANCELED)
        finish()
    }
}
