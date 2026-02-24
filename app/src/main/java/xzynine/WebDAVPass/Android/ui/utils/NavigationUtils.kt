package xzynine.WebDAVPass.Android.ui.utils

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.compose.runtime.Composable

/**
 * 导航事件分发器提供者
 */
@Composable
fun NavigationEventDispatcherProvider(content: @Composable () -> Unit) {
    val navigationEventDispatcher = remember { NavigationEventDispatcher() }
    val navigationEventDispatcherOwner = object : NavigationEventDispatcherOwner {
        override val navigationEventDispatcher: NavigationEventDispatcher
            get() = navigationEventDispatcher
    }
    CompositionLocalProvider(
        LocalNavigationEventDispatcherOwner provides navigationEventDispatcherOwner
    ) {
        content()
    }
}
