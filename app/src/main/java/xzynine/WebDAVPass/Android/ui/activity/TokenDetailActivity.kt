package xzynine.WebDAVPass.Android.ui.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import xzynine.WebDAVPass.Android.theme.AppTheme
import xzynine.WebDAVPass.Android.ui.Screen.TokenListScreen
import xzynine.WebDAVPass.Android.ui.ViewModel.TokenViewModel
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.MiuixPopupHost

class TokenDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.setContent {
            val context = LocalContext.current
            val tokenViewModel: TokenViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        return TokenViewModel(context.applicationContext) as T
                    }
                }
            )
            
            // 配置 NavigationEventDispatcher，使 miuix 弹窗组件能够正常工作
            val navigationEventDispatcher = remember { NavigationEventDispatcher() }
            val navigationEventDispatcherOwner = object : NavigationEventDispatcherOwner {
                override val navigationEventDispatcher: NavigationEventDispatcher
                    get() = navigationEventDispatcher
            }
            
            CompositionLocalProvider(
                LocalNavigationEventDispatcherOwner provides navigationEventDispatcherOwner
            ) {
                AppTheme {
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
