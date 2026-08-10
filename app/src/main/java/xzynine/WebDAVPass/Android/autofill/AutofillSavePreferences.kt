package xzynine.WebDAVPass.Android.autofill

import android.content.Context
import kotlinx.coroutines.runBlocking
import xzynine.WebDAVPass.Android.data.AppDatabaseHolder
import xzynine.WebDAVPass.Android.data.AppSetting

/**
 * 自动填充「提示保存」偏好（持久化于 app_settings）。
 *
 * 由 [KeeAutofillService] 在连接时读取，设置页可切换。
 */
object AutofillSavePreferences {

    private const val KEY_ASK_TO_SAVE = "autofill_ask_to_save"

    /** 是否在表单提交后提示保存（内存缓存，服务连接时加载）。 */
    @Volatile
    var askToSaveData: Boolean = false

    /**
     * 从本地设置加载偏好（服务 onConnected 时调用）。
     */
    fun load(context: Context) {
        runCatching {
            runBlocking {
                AppDatabaseHolder.getInstance(context)
                    .appSettingsDao()
                    .getValue(KEY_ASK_TO_SAVE)
                    ?.value
            }
        }.getOrNull()?.let { value ->
            askToSaveData = value == "true"
        }
    }

    /**
     * 更新偏好并持久化。
     */
    fun setAskToSaveData(context: Context, enabled: Boolean) {
        askToSaveData = enabled
        runCatching {
            runBlocking {
                AppDatabaseHolder.getInstance(context)
                    .appSettingsDao()
                    .put(AppSetting(KEY_ASK_TO_SAVE, enabled.toString()))
            }
        }
    }
}
