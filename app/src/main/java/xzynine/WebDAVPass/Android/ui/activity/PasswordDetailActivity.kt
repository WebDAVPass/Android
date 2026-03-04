package xzynine.WebDAVPass.Android.ui.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import xzynine.WebDAVPass.Android.theme.AppTheme
import xzynine.WebDAVPass.Android.theme.SetupSystemBars
import xzynine.WebDAVPass.Android.ui.MainActivity
import xzynine.WebDAVPass.Android.ui.activity.PasswordEntryDetailActivity.Companion.createIntent
import xzynine.WebDAVPass.Android.ui.Screen.PasswordListScreen
import xzynine.WebDAVPass.Android.ui.ViewModel.PasswordListMode
import xzynine.WebDAVPass.Android.ui.ViewModel.TokenViewModel
import xzynine.WebDAVPass.Android.ui.utils.NavigationEventDispatcherProvider
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.MiuixPopupHost

/**
 * 非双因素键值详情页面
 */
class PasswordDetailActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_PASSWORD_LIST_MODE = "extra_password_list_mode"

        fun createIntent(context: Context, listMode: PasswordListMode): Intent {
            return Intent(context, PasswordDetailActivity::class.java)
                .putExtra(EXTRA_PASSWORD_LIST_MODE, listMode.name)
        }

        private fun resolveListMode(rawMode: String?): PasswordListMode {
            return when (rawMode) {
                PasswordListMode.RECENT_DELETED.name -> PasswordListMode.RECENT_DELETED
                else -> PasswordListMode.ALL_PASSWORDS
            }
        }
    }

    private var openedListMode: PasswordListMode = PasswordListMode.ALL_PASSWORDS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        openedListMode = resolveListMode(intent.getStringExtra(EXTRA_PASSWORD_LIST_MODE))
        TokenViewModel.getSharedInstance(application).apply {
            setPasswordListMode(openedListMode, refreshNow = true)
            refreshRecentDeletedCount()
        }
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
                        Intent(this@PasswordDetailActivity, MainActivity::class.java).apply {
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
                        PasswordListScreen(
                            tokenViewModel = tokenViewModel,
                            title = if (openedListMode == PasswordListMode.RECENT_DELETED) "最近删除" else "全部密码",
                            emptyStateText = if (openedListMode == PasswordListMode.RECENT_DELETED) "暂无最近删除条目" else "暂无条目",
                            emptySearchStateText = "无匹配条目",
                            enableGroupNavigation = openedListMode == PasswordListMode.ALL_PASSWORDS,
                            onEntryClick = { entryId ->
                                startActivity(createIntent(this@PasswordDetailActivity, entryId))
                            }
                        )
                        MiuixPopupHost()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        TokenViewModel.getSharedInstance(application).apply {
            resetPasswordGroupStackOnly()
            if (openedListMode == PasswordListMode.RECENT_DELETED) {
                setPasswordListMode(PasswordListMode.ALL_PASSWORDS, refreshNow = true)
            }
        }
        super.onDestroy()
    }
}
