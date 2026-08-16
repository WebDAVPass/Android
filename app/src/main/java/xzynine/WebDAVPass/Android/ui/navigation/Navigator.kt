package xzynine.WebDAVPass.Android.ui.navigation

import androidx.compose.runtime.staticCompositionLocalOf
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.nav.core.NavKey

/** 用整个栈替换当前导航栈（等价原 Navigator.replaceAll 语义）。 */
fun NavBackStack.replaceAll(keys: List<NavKey>) {
    if (keys.isEmpty()) {
        return
    }
    clear()
    addAll(keys)
}

/** 入栈（等价原 Navigator.push 语义）。 */
fun NavBackStack.push(key: NavKey) {
    add(key)
}

/** 出栈（等价原 Navigator.pop 语义：不保留根，栈底也允许弹出）。 */
fun NavBackStack.pop() {
    removeLastOrNull()
}

val LocalNavigator =
    staticCompositionLocalOf<NavBackStack> {
        error("LocalNavigator not provided")
    }
