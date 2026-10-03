package xzynine.WebDAVPass.Android.ui.component

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import github.xzynine.webdav.LocalNetworkPermission
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xzylib.base.util.ToastUtils

/**
 * 用户拒绝「本地网络」权限时的提示文案。
 */
private const val LOCAL_NETWORK_DENIED_TIP: String =
    "需要「本地网络」权限才能访问局域网中的 WebDAV 服务器"

/**
 * Android 17（SDK 37）本地网络权限门。
 *
 * 自 Android 17 起，访问局域网地址默认被屏蔽，必须获得 ACCESS_LOCAL_NETWORK 权限；
 * 未授予时连接只会超时而没有明确错误，因此所有访问局域网 WebDAV 服务器的入口
 * 都应先经过本门：不需要权限时直接放行，需要时弹出系统权限框并挂起等待结果。
 *
 * 判定逻辑（[LocalNetworkPermission]）由 WebDAV 子模块提供，本类只负责界面侧的
 * 权限请求与结果等待。
 */
class LocalNetworkPermissionGate internal constructor(
    private val context: Context,
    private val requestPermission: suspend (String) -> Boolean,
) {
    /**
     * 确保可以访问给定地址。
     *
     * @param url 待访问的远端地址；非局域网地址或已授权时立即返回 true
     * @param showDeniedTip 被拒绝时是否弹出提示
     * @return true 表示可以继续访问
     */
    suspend fun ensure(
        url: String?,
        showDeniedTip: Boolean = true,
    ): Boolean {
        if (!LocalNetworkPermission.needsRequest(context, url)) return true
        val granted = requestPermission(LocalNetworkPermission.PERMISSION)
        if (!granted && showDeniedTip) {
            withContext(Dispatchers.Main) {
                ToastUtils.showShortToast(context, LOCAL_NETWORK_DENIED_TIP)
            }
        }
        return granted
    }
}

/**
 * 在当前组合内创建并记住本地网络权限门。
 */
@Composable
fun rememberLocalNetworkPermissionGate(): LocalNetworkPermissionGate {
    val context = LocalContext.current
    val pending = remember { mutableStateOf<CompletableDeferred<Boolean>?>(null) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            pending.value?.complete(granted)
            pending.value = null
        }
    return remember(context) {
        LocalNetworkPermissionGate(context) { permission ->
            val deferred = CompletableDeferred<Boolean>()
            pending.value = deferred
            launcher.launch(permission)
            deferred.await()
        }
    }
}
