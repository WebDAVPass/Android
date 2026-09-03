/*
 * Copyright 2026 WebDAVPass.
 *
 * 本文件属于 WebDAVPass for Android（GPLv3）。
 * 宿主侧自动填充偏好设置：持久化于 app_settings，内存缓存供填充服务同步读取。
 *
 * 沿用旧 AutofillSavePreferences 的 @Volatile 缓存 + 异步落库模式，并扩展内联建议 /
 * 手动选择 / 主开关 / 黑名单等键。
 */

package xzynine.WebDAVPass.Android.autofillbridge

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xzynine.WebDAVPass.Android.data.AppDatabaseHolder
import xzynine.WebDAVPass.Android.data.AppSetting
import xzynine.WebDAVPass.Autofill.bridge.AutofillPreferences

object AppAutofillPreferences : AutofillPreferences {
    private const val KEY_ENABLED = "autofill_enabled"
    private const val KEY_INLINE = "autofill_inline"
    private const val KEY_MANUAL = "autofill_manual"
    private const val KEY_ASK = "autofill_ask_to_save"
    private const val KEY_APP_BLOCK = "autofill_app_blocklist"
    private const val KEY_WEB_BLOCK = "autofill_web_blocklist"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var enabled: Boolean = true

    @Volatile private var inline: Boolean = true

    @Volatile private var manual: Boolean = true

    @Volatile override var askToSaveData: Boolean = true

    @Volatile private var appBlock: Set<String> = emptySet()

    @Volatile private var webBlock: Set<String> = emptySet()

    /**
     * 从 app_settings 加载全部偏好（suspend，加载完成才返回）。
     * 设置页进入时使用：调用方（如 LaunchedEffect）挂起等待，避免读到默认缓存值。
     */
    suspend fun load(context: Context) {
        withContext(Dispatchers.IO) {
            runCatching {
                val dao = AppDatabaseHolder.getInstance(context).appSettingsDao()
                enabled = dao.getValue(KEY_ENABLED)?.value?.toBooleanStrictOrNull() ?: true
                inline = dao.getValue(KEY_INLINE)?.value?.toBooleanStrictOrNull() ?: true
                manual = dao.getValue(KEY_MANUAL)?.value?.toBooleanStrictOrNull() ?: true
                askToSaveData = dao.getValue(KEY_ASK)?.value?.toBooleanStrictOrNull() ?: true
                appBlock = parseSet(dao.getValue(KEY_APP_BLOCK)?.value)
                webBlock = parseSet(dao.getValue(KEY_WEB_BLOCK)?.value)
            }
        }
    }

    /** 非阻塞加载（Application.onCreate 使用，不阻塞主线程冷启动）。 */
    fun loadAsync(context: Context) {
        scope.launch { load(context) }
    }

    fun setEnabled(
        context: Context,
        value: Boolean,
    ) {
        enabled = value
        persist(context, KEY_ENABLED, value)
    }

    fun setInlineEnabled(
        context: Context,
        value: Boolean,
    ) {
        inline = value
        persist(context, KEY_INLINE, value)
    }

    fun setManualSelectionEnabled(
        context: Context,
        value: Boolean,
    ) {
        manual = value
        persist(context, KEY_MANUAL, value)
    }

    fun setAskToSaveData(
        context: Context,
        value: Boolean,
    ) {
        askToSaveData = value
        persist(context, KEY_ASK, value)
    }

    fun setApplicationIdBlocklist(
        context: Context,
        set: Set<String>,
    ) {
        appBlock = set
        persist(context, KEY_APP_BLOCK, set.joinToString(","))
    }

    fun setWebDomainBlocklist(
        context: Context,
        set: Set<String>,
    ) {
        webBlock = set
        persist(context, KEY_WEB_BLOCK, set.joinToString(","))
    }

    private fun persist(
        context: Context,
        key: String,
        value: Boolean,
    ) = persist(context, key, value.toString())

    private fun persist(
        context: Context,
        key: String,
        value: String,
    ) {
        scope.launch {
            runCatching {
                AppDatabaseHolder.getInstance(context).appSettingsDao().put(AppSetting(key, value))
            }
        }
    }

    private fun parseSet(raw: String?): Set<String> =
        raw
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            ?: emptySet()

    override val autofillSuggestionsEnabled: Boolean
        get() = enabled

    override val inlineSuggestionsEnabled: Boolean
        get() = inline

    override val manualSelectionEnabled: Boolean
        get() = manual

    override val applicationIdBlocklist: Set<String>
        get() = appBlock

    override val webDomainBlocklist: Set<String>
        get() = webBlock
}
