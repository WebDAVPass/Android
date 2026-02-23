package xzynine.WebDAVPass.Android.service

import android.app.PendingIntent
import android.app.assist.AssistStructure
import android.content.Intent
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import android.widget.RemoteViews
import xzynine.WebDAVPass.Android.R
import xzynine.WebDAVPass.Android.ui.AutofillPickerActivity

class TwoFasAutofillService : AutofillService() {
    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback
    ) {
        val structure = request.fillContexts.lastOrNull()?.structure
        if (structure == null) {
            callback.onSuccess(null)
            return
        }

        val autofillId = findFocusedAutofillId(structure)
        if (autofillId == null) {
            callback.onSuccess(null)
            return
        }

        val targetPackage = structure.activityComponent?.packageName.orEmpty()

        val intent = Intent(this, AutofillPickerActivity::class.java).apply {
            putExtra(AutofillPickerActivity.EXTRA_AUTOFILL_ID, autofillId)
            putExtra(AutofillPickerActivity.EXTRA_PACKAGE_NAME, targetPackage)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val presentation = RemoteViews(packageName, R.layout.autofill_presentation).apply {
            setTextViewText(
                R.id.autofill_presentation_text,
                "选择 ${getString(R.string.app_name)}"
            )
        }

        val response = FillResponse.Builder()
            .setAuthentication(arrayOf(autofillId), pendingIntent.intentSender, presentation)
            .build()

        callback.onSuccess(response)
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        callback.onSuccess()
    }

    private fun findFocusedAutofillId(structure: AssistStructure): android.view.autofill.AutofillId? {
        val windowNodeCount = structure.windowNodeCount
        for (index in 0 until windowNodeCount) {
            val rootViewNode = structure.getWindowNodeAt(index).rootViewNode
            val focusedId = findFocusedAutofillId(rootViewNode)
            if (focusedId != null) {
                return focusedId
            }
        }
        return null
    }

    private fun findFocusedAutofillId(node: AssistStructure.ViewNode): android.view.autofill.AutofillId? {
        if (node.isFocused && node.autofillId != null) {
            return node.autofillId
        }
        val childCount = node.childCount
        for (index in 0 until childCount) {
            val child = node.getChildAt(index)
            val focusedId = findFocusedAutofillId(child)
            if (focusedId != null) {
                return focusedId
            }
        }
        return null
    }
}
