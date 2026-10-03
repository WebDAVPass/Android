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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import xzynine.WebDAVPass.Android.data.AppDatabaseHolder
import xzynine.WebDAVPass.Android.data.AppSetting
import xzynine.WebDAVPass.Autofill.bridge.AutofillPreferences

/**
 * 自动填充偏好的宿主实现：内存缓存 + app_settings 持久化。
 *
 * 写入经 [SerialKeyWriter] 按键串行落库，保证「设置顺序 == 落库顺序」；
 * 读取走内存缓存，由 [ensureLoaded] 保证冷启动期间不返回默认值。
 */
object AppAutofillPreferences : AutofillPreferences {
    private const val KEY_ENABLED = "autofill_enabled"
    private const val KEY_INLINE = "autofill_inline"
    private const val KEY_MANUAL = "autofill_manual"
    private const val KEY_ASK = "autofill_ask_to_save"
    private const val KEY_APP_BLOCK = "autofill_app_blocklist"
    private const val KEY_WEB_BLOCK = "autofill_web_blocklist"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/** 偏好落库写入器：同一键串行，保证「调用顺序 == 落库顺序」。 */
    private val writer = SerialKeyWriter(scope)

    @Volatile private var enabled: Boolean = true

    @Volatile private var inline: Boolean = true

    @Volatile private var manual: Boolean = true

    @Volatile override var askToSaveData: Boolean = true

    @Volatile private var appBlock: Set<String> = emptySet()

    @Volatile private var webBlock: Set<String> = emptySet()

    /** 偏好是否已加载完成；未就绪时服务侧应同步加载，避免冷启动窗口期以默认全开状态应答。 */
    @Volatile var isReady: Boolean = false
        private set

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
        isReady = true
    }

    /** 非阻塞加载（Application.onCreate 使用，不阻塞主线程冷启动）。 */
    fun loadAsync(context: Context) {
        scope.launch { load(context) }
    }

    /**
     * 阻塞加载：若偏好尚未就绪，在调用线程同步加载（仅首次触发一次 Room 读）。
     * 自动填充服务在 binder 线程调用，消除冷启动窗口期"默认全开"的隐私风险；
     * 加载完成后后续调用直接走内存缓存。
     */
    override fun ensureLoaded(context: Context) {
        if (!isReady) {
            runBlocking { load(context) }
        }
    }

    /** 设置自动填充主开关（关闭后仅展示选择/解锁界面）。 */
    fun setEnabled(
        context: Context,
        value: Boolean,
    ) {
        enabled = value
        persist(context, KEY_ENABLED, value)
    }

    /** 设置是否在兼容键盘上显示内联建议。 */
    fun setInlineEnabled(
        context: Context,
        value: Boolean,
    ) {
        inline = value
        persist(context, KEY_INLINE, value)
    }

    /** 设置是否在候选列表中提供「手动选择」入口。 */
    fun setManualSelectionEnabled(
        context: Context,
        value: Boolean,
    ) {
        manual = value
        persist(context, KEY_MANUAL, value)
    }

    /** 设置表单提交后是否提示保存。 */
    fun setAskToSaveData(
        context: Context,
        value: Boolean,
    ) {
        askToSaveData = value
        persist(context, KEY_ASK, value)
    }

    /** 设置应用黑名单（元素只做 trim，大小写在匹配侧统一忽略）。 */
    fun setApplicationIdBlocklist(
        context: Context,
        set: Set<String>,
    ) {
        appBlock = set
        persist(context, KEY_APP_BLOCK, set.joinToString(","))
    }

    /** 设置网站黑名单（元素只做 trim，大小写在匹配侧统一忽略）。 */
    fun setWebDomainBlocklist(
        context: Context,
        set: Set<String>,
    ) {
        webBlock = set
        persist(context, KEY_WEB_BLOCK, set.joinToString(","))
    }

    /** 布尔偏好的落库入口（统一转为字符串存储）。 */
    private fun persist(
        context: Context,
        key: String,
        value: Boolean,
    ) = persist(context, key, value.toString())

    /** 入队一次偏好写入；同键串行，落库顺序与调用顺序一致。 */
    private fun persist(
        context: Context,
        key: String,
        value: String,
    ) {
        // 入队而非各自起协程：同一键的连续更新必须按调用顺序落库，
        // 否则较旧的写入可能后到并覆盖新值（REPLACE 策略），重启后读回过期设置。
        // 取 applicationContext，避免在队列中持有 Activity 等短生命周期 Context。
        val appContext = context.applicationContext
        writer.enqueue(key) { runCatching { doPersist(appContext, key, value) } }
    }

    /** 真正写入 app_settings（仅在同键前序写入完成后调用）。 */
    private suspend fun doPersist(
        context: Context,
        key: String,
        value: String,
    ) {
        AppDatabaseHolder.getInstance(context).appSettingsDao().put(AppSetting(key, value))
    }

    /** 解析逗号分隔的集合型偏好：去空白、去空项。 */
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
