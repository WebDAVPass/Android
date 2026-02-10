package xzynine.WebDAVPass.Android.ui

import android.content.Intent
import android.os.Bundle
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import android.app.Activity
import android.service.autofill.Dataset
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import xzynine.WebDAVPass.Android.R
import xzynine.WebDAVPass.Android.data.OtpToken
import xzynine.WebDAVPass.Android.theme.AppTheme
import xzynine.WebDAVPass.Android.ui.Screen.TokenItem
import xzynine.WebDAVPass.Android.ui.ViewModel.TokenViewModel
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text

class AutofillPickerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val autofillId = intent.getParcelableExtra<AutofillId>(EXTRA_AUTOFILL_ID)
        if (autofillId == null) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        val targetPackage = intent.getStringExtra(EXTRA_PACKAGE_NAME).orEmpty()

        setContent {
            AppTheme {
                AutofillPickerScreen(
                    autofillId = autofillId,
                    targetPackage = targetPackage
                )
            }
        }
    }

    companion object {
        const val EXTRA_AUTOFILL_ID = "github.xzynine.two_fas.EXTRA_AUTOFILL_ID"
        const val EXTRA_PACKAGE_NAME = "github.xzynine.two_fas.EXTRA_PACKAGE_NAME"
    }
}

@Composable
private fun AutofillPickerScreen(autofillId: AutofillId, targetPackage: String) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val tokenViewModel = remember { TokenViewModel(appContext) }
    val tokens by tokenViewModel.tokens.collectAsState(emptyList())

    val likelyMatches = remember(tokens, targetPackage) {
        if (targetPackage.isBlank()) {
            emptyList()
        } else {
            tokens.filter { isLikelyMatch(targetPackage, it) }
        }
    }
    val likelyMatchIds = remember(likelyMatches) { likelyMatches.map { it.id }.toSet() }
    val otherTokens = remember(tokens, likelyMatchIds) { tokens.filter { it.id !in likelyMatchIds } }

    if (tokens.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "暂无令牌")
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (likelyMatches.isNotEmpty()) {
                item {
                    Text(
                        text = "可能匹配",
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                items(likelyMatches) { token ->
                    TokenItem(
                        token = token,
                        tokenViewModel = tokenViewModel,
                        onLongClick = {},
                        onTokenClick = { code ->
                            finishAutofill(context, autofillId, token, code)
                        }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 0.5.dp
                    )
                }
            }

            item {
                Text(
                    text = "全部结果",
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            items(otherTokens) { token ->
                TokenItem(
                    token = token,
                    tokenViewModel = tokenViewModel,
                    onLongClick = {},
                    onTokenClick = { code ->
                        finishAutofill(context, autofillId, token, code)
                    }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 0.5.dp
                )
            }
        }
    }
}

private fun finishAutofill(
    context: android.content.Context,
    autofillId: AutofillId,
    token: OtpToken,
    code: String
) {
    val presentationText = token.issuer ?: token.label
    val presentation = RemoteViews(context.packageName, R.layout.autofill_presentation).apply {
        setTextViewText(R.id.autofill_presentation_text, presentationText)
    }

    val dataset = Dataset.Builder(presentation)
        .setValue(autofillId, AutofillValue.forText(code), presentation)
        .build()

    val replyIntent = Intent().putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, dataset)
    val activity = context as? Activity
    activity?.setResult(Activity.RESULT_OK, replyIntent)
    activity?.finish()
}

private fun isLikelyMatch(targetPackage: String, token: OtpToken): Boolean {
    val normalizedPackage = normalizeForMatch(targetPackage)
    if (normalizedPackage.isBlank()) {
        return false
    }

    val candidates = listOfNotNull(token.issuer, token.label, token.description)
        .map { normalizeForMatch(it) }
        .filter { it.length >= 3 }

    return candidates.any { normalizedPackage.contains(it) }
}

private fun normalizeForMatch(value: String): String {
    return buildString {
        for (char in value.lowercase()) {
            if (char.isLetterOrDigit()) {
                append(char)
            }
        }
    }
}
