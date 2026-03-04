package xzynine.WebDAVPass.Android.ui.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.MiuixPopupHost
import xzynine.WebDAVPass.Android.theme.AppTheme
import xzynine.WebDAVPass.Android.theme.SetupSystemBars
import xzynine.WebDAVPass.Android.ui.MainActivity
import xzynine.WebDAVPass.Android.ui.Screen.PasswordEntryDetailScreen
import xzynine.WebDAVPass.Android.ui.ViewModel.TokenViewModel
import xzynine.WebDAVPass.Android.ui.utils.NavigationEventDispatcherProvider

class PasswordEntryDetailActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_ENTRY_ID = "extra_entry_id"

        fun createIntent(context: Context, entryId: Long): Intent {
            return Intent(context, PasswordEntryDetailActivity::class.java)
                .putExtra(EXTRA_ENTRY_ID, entryId)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val entryId = intent.getLongExtra(EXTRA_ENTRY_ID, -1L)

        setContent {
            val tokenViewModel: TokenViewModel = remember(application) {
                TokenViewModel.getSharedInstance(application)
            }

            val isLibraryUnlocked by tokenViewModel.isLibraryUnlocked.collectAsState(false)
            val currentLibrary by tokenViewModel.currentLibrary.collectAsState(null)

            LaunchedEffect(isLibraryUnlocked, currentLibrary, entryId) {
                if (!isLibraryUnlocked || currentLibrary == null || entryId < 0) {
                    startActivity(
                        Intent(this@PasswordEntryDetailActivity, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        }
                    )
                    finish()
                }
            }

            NavigationEventDispatcherProvider {
                AppTheme {
                    SetupSystemBars()
                    Box(modifier = Modifier.fillMaxSize()) {
                        PasswordEntryDetailScreen(
                            tokenViewModel = tokenViewModel,
                            entryId = entryId
                        )
                        MiuixPopupHost()
                    }
                }
            }
        }
    }
}
