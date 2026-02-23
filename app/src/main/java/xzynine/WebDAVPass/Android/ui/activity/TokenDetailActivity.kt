package xzynine.WebDAVPass.Android.ui.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import xzynine.WebDAVPass.Android.theme.AppTheme
import xzynine.WebDAVPass.Android.ui.Screen.TokenListScreen
import xzynine.WebDAVPass.Android.ui.ViewModel.TokenViewModel

class TokenDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.setContent {
            val context = LocalContext.current
            val appContext = context.applicationContext
            val tokenViewModel = remember { TokenViewModel(appContext) }
            AppTheme {
                TokenListScreen(tokenViewModel = tokenViewModel)
            }
        }
    }
}
