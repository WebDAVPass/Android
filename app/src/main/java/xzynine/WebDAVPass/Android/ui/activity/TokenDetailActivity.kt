package xzynine.WebDAVPass.Android.ui.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import xzynine.WebDAVPass.Android.theme.AppTheme
import xzynine.WebDAVPass.Android.theme.SetupSystemBars
import xzynine.WebDAVPass.Android.ui.MainActivity
import xzynine.WebDAVPass.Android.ui.Screen.TokenListScreen
import xzynine.WebDAVPass.Android.ui.ViewModel.TokenViewModel
import xzynine.WebDAVPass.Android.ui.utils.NavigationEventDispatcherProvider
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.MiuixPopupHost

class TokenDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.setContent {
            val tokenViewModel: TokenViewModel = remember(application) {
                TokenViewModel.getSharedInstance(application)
            }
            val isLibraryUnlocked by tokenViewModel.isLibraryUnlocked.collectAsState(false)
            val currentLibrary by tokenViewModel.currentLibrary.collectAsState(null)

            /**
             * 未解锁兜底：若详情页被直接拉起但当前库未解锁，
             * 则回到主活动并交由欢迎流重新绑定/解锁。
             */
            LaunchedEffect(isLibraryUnlocked, currentLibrary) {
                if (!isLibraryUnlocked || currentLibrary == null) {
                    startActivity(
                        Intent(this@TokenDetailActivity, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        }
                    )
                    finish()
                }
            }
            
            NavigationEventDispatcherProvider {
                AppTheme {
                    // 设置系统栏外观
                    SetupSystemBars()
                    // 使用 Box 包裹，并在外部放置 MiuixPopupHost
                    Box(modifier = Modifier.fillMaxSize()) {
                        TokenListScreen(tokenViewModel = tokenViewModel)
                        // MiuixPopupHost 作为弹窗宿主
                        MiuixPopupHost()
                    }
                }
            }
        }
    }
}
