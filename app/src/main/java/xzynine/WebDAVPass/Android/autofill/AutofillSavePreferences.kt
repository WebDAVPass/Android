package xzynine.WebDAVPass.Android.autofill

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import xzynine.WebDAVPass.Android.data.AppDatabaseHolder
import xzynine.WebDAVPass.Android.data.AppSetting

/**
 * 自动填充「提示保存」偏好（持久化于 app_settings）。
 *
 * 由 [KeeAutofillService] 在连接时读取，设置页可切换。
 */
object AutofillSavePreferences {
    private const val KEY_ASK_TO_SAVE = "autofill_ask_to_save"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 是否在表单提交后提示保存（内存缓存，服务连接时异步加载，@Volatile 保证可见性）。 */
    @Volatile
    var askToSaveData: Boolean = false

    /**
     * 从本地设置异步加载偏好（服务 onConnected 时调用）。
     *
     * 不使用 runBlocking 以避免在服务主线程阻塞等待 Room；调用方在 Fill/Save 请求时
     * 直接读取 [askToSaveData]，加载完成前回落到默认值（false，不提示保存，安全）。
     */
    fun load(context: Context) {
        scope.launch {
            runCatching {
                AppDatabaseHolder
                    .getInstance(context)
                    .appSettingsDao()
                    .getValue(KEY_ASK_TO_SAVE)
                    ?.value
            }.getOrNull()?.let { value ->
                askToSaveData = value == "true"
            }
        }
    }

    /**
     * 更新偏好并异步持久化（先即时写入 @Volatile 字段，再后台落库）。
     */
    fun setAskToSaveData(
        context: Context,
        enabled: Boolean,
    ) {
        askToSaveData = enabled
        scope.launch {
            runCatching {
                AppDatabaseHolder
                    .getInstance(context)
                    .appSettingsDao()
                    .put(AppSetting(KEY_ASK_TO_SAVE, enabled.toString()))
            }
        }
    }
}
