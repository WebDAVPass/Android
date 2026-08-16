package xzynine.WebDAVPass.Android.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import xzylib.base.util.Logger

/**
 * 库上下文存储（数据库实现）
 *
 * 说明：
 * - 数据持久化在 Room 数据库（library_contexts + app_settings），不再使用 SharedPreferences；
 * - 首次访问时同步预热内存缓存，并执行一次性的旧 SharedPreferences 数据迁移；
 * - 读取走内存缓存，写入同步更新缓存并异步落库，对外保持同步 API。
 */
class LibraryContextStore(
    private val context: Context,
) {
    private val gson = Gson()
    private val database = AppDatabaseHolder.getInstance(context)

    /**
     * 异步落库协程作用域（与应用同生命周期，随单例 ViewModel 存活）。
     * 安装 [CoroutineExceptionHandler] 以捕获 DAO 写入异常，避免异常传播到未捕获处理器导致崩溃。
     */
    private val ioScope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.IO +
                CoroutineExceptionHandler { _, throwable ->
                    Logger.e("LibraryContextStore", "异步落库失败", throwable)
                },
        )

    private val lock = Any()

    @Volatile
    private var loaded = false

    @Volatile
    private var history = mutableListOf<LibraryContext>()

    @Volatile
    private var currentId: String? = null

    /**
     * 确保缓存已加载。
     *
     * 说明：首次访问时同步阻塞完成迁移与数据库加载（数据量小，毫秒级），
     * 之后所有读取均命中内存缓存。
     */
    private fun ensureLoaded() {
        if (loaded) {
            return
        }
        synchronized(lock) {
            if (loaded) {
                return
            }
            runBlocking(Dispatchers.IO) {
                runOneTimeMigrationIfNeeded()
                loadFromDatabase()
            }
            loaded = true
        }
    }

    /**
     * 应用启动预热：在 IO 协程中提前完成迁移与数据库加载，
     * 使首次用户操作前的缓存已就绪，避免 UI 线程触发 [ensureLoaded] 的同步阻塞。
     */
    fun warmUp() {
        ioScope.launch { ensureLoaded() }
    }

    /**
     * 一次性迁移：旧 SharedPreferences 数据导入数据库，并加密存量明文密码。
     *
     * 说明：
     * - 仅旧版本遗留数据需要迁移，成功导入后清除 SharedPreferences；
     * - 迁移失败时兜底为空库继续启动，不影响正常使用。
     */
    private suspend fun runOneTimeMigrationIfNeeded() {
        runCatching {
            val preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val spHistoryRaw = preferences.getString(KEY_HISTORY, null)
            val spCurrentId = preferences.getString(KEY_CURRENT_ID, null)

            if (!spHistoryRaw.isNullOrBlank()) {
                val spHistory =
                    runCatching {
                        val type = object : TypeToken<List<LibraryContext>>() {}.type
                        (gson.fromJson<List<LibraryContext>>(spHistoryRaw, type) ?: emptyList())
                            .map { normalizeContext(it) }
                    }.getOrDefault(emptyList())

                // 库表为空时导入历史库（凭据加密落库）
                if (spHistory.isNotEmpty() && database.libraryContextDao().getAllOnce().isEmpty()) {
                    database.libraryContextDao().upsertAll(spHistory.map { it.toEntity() })
                }

                // 播种 WebDAV 账号表：历史云端库按账号去重
                seedWebDavConfigsFromHistory(spHistory)
            }

            // 存量明文密码统一转为密文
            encryptLegacyWebDavConfigPasswords()

            // 当前选中库 ID 迁移
            if (!spCurrentId.isNullOrBlank() && database.appSettingsDao().getValue(KEY_CURRENT_ID) == null) {
                database.appSettingsDao().put(AppSetting(KEY_CURRENT_ID, spCurrentId))
            }

            // 迁移完成，清除旧存储
            preferences
                .edit()
                .remove(KEY_HISTORY)
                .remove(KEY_CURRENT_ID)
                .apply()
        }
    }

    /**
     * 从历史云端库去重播种 WebDAV 账号表。
     *
     * 说明：按 (服务器根地址, 用户名, 密码) 去重；已存在的账号仅在新密码不同时更新。
     */
    private suspend fun seedWebDavConfigsFromHistory(libraries: List<LibraryContext>) {
        val accounts =
            libraries
                .filter { it.sourceType == LibrarySourceType.CLOUD }
                .filter { !it.remoteBaseUrl.isNullOrBlank() && !it.username.isNullOrBlank() && !it.password.isNullOrBlank() }
                .distinctBy { Triple(it.remoteBaseUrl, it.username, it.password) }

        if (accounts.isEmpty()) {
            return
        }

        val existing = database.webDavConfigDao().getAllOnce()
        val lastSortNumber = database.webDavConfigDao().getLastSortNumber() ?: 0
        var sortCursor = lastSortNumber

        accounts.forEach { library ->
            val baseUrl = normalizeBaseUrl(library.remoteBaseUrl!!)
            val match =
                existing.firstOrNull {
                    normalizeBaseUrl(it.url) == baseUrl && it.username == library.username
                }
            if (match != null) {
                val currentPlain = WebDavPasswordCipher.decrypt(match.password)
                if (currentPlain == null || currentPlain != library.password) {
                    database.webDavConfigDao().update(
                        match.copy(password = WebDavPasswordCipher.encrypt(library.password!!)),
                    )
                }
            } else {
                sortCursor += 1
                database.webDavConfigDao().insert(
                    WebDavConfig(
                        id = 0,
                        name = library.displayName,
                        url = baseUrl,
                        directory = null,
                        username = library.username!!,
                        password = WebDavPasswordCipher.encrypt(library.password!!),
                        sortNumber = sortCursor,
                    ),
                )
            }
        }
    }

    /**
     * 将 webdav_configs 中存量明文密码转为密文。
     */
    private suspend fun encryptLegacyWebDavConfigPasswords() {
        database.webDavConfigDao().getAllOnce().forEach { config ->
            if (!config.password.startsWith(WebDavPasswordCipher.PREFIX)) {
                database.webDavConfigDao().update(
                    config.copy(password = WebDavPasswordCipher.encrypt(config.password)),
                )
            }
        }
    }

    /**
     * 保证服务器根地址以 "/" 结尾
     */
    private fun normalizeBaseUrl(raw: String): String = if (raw.endsWith("/")) raw else "$raw/"

    /**
     * 从数据库加载缓存
     */
    private suspend fun loadFromDatabase() {
        val entities = database.libraryContextDao().getAllOnce()
        history =
            entities
                .map { it.toLibraryContext() }
                .sortedByDescending { it.lastUsedAt }
                .toMutableList()
        currentId = database.appSettingsDao().getValue(KEY_CURRENT_ID)?.value
    }

    /**
     * 读取历史库列表
     */
    fun getHistory(): List<LibraryContext> {
        ensureLoaded()
        return history.toList()
    }

    /**
     * 获取当前选中的库ID
     */
    fun getCurrentLibraryId(): String? {
        ensureLoaded()
        return currentId
    }

    /**
     * 获取当前选中的库
     */
    fun getCurrentLibrary(): LibraryContext? {
        ensureLoaded()
        return history.firstOrNull { it.id == currentId }
    }

    /**
     * 写入并选中库
     */
    fun upsertAndSelect(libraryContext: LibraryContext): LibraryContext {
        ensureLoaded()
        val updated = libraryContext.copy(lastUsedAt = System.currentTimeMillis())
        synchronized(lock) {
            val existingIndex = history.indexOfFirst { it.id == updated.id || it.localPath == updated.localPath }
            if (existingIndex >= 0) {
                history[existingIndex] = updated
            } else {
                history.add(updated)
            }
            history.sortByDescending { it.lastUsedAt }
            currentId = updated.id
        }
        ioScope.launch {
            database.libraryContextDao().upsert(updated.toEntity())
            database.appSettingsDao().put(AppSetting(KEY_CURRENT_ID, updated.id))
        }
        return updated
    }

    /**
     * 选中历史库
     */
    fun selectById(id: String): LibraryContext? {
        ensureLoaded()
        val item: LibraryContext
        synchronized(lock) {
            item = history.firstOrNull { it.id == id } ?: return null
            currentId = item.id
        }
        ioScope.launch {
            database.appSettingsDao().put(AppSetting(KEY_CURRENT_ID, item.id))
        }
        return item
    }

    /**
     * 仅更新历史项内容，不改变当前选中状态。
     *
     * @return 更新后的历史项；未命中时返回 null。
     */
    fun updateHistoryItem(item: LibraryContext): LibraryContext? {
        ensureLoaded()
        val merged: LibraryContext
        synchronized(lock) {
            val index = history.indexOfFirst { it.id == item.id }
            if (index < 0) {
                return null
            }
            merged = item.copy(lastUsedAt = history[index].lastUsedAt)
            history[index] = merged
            history.sortByDescending { it.lastUsedAt }
        }
        ioScope.launch {
            database.libraryContextDao().upsert(merged.toEntity())
        }
        return merged
    }

    /**
     * 按ID移除历史库。
     *
     * @return 若成功移除返回 true。
     */
    fun removeHistoryById(id: String): Boolean = removeHistoryByIds(setOf(id)) > 0

    /**
     * 批量移除历史库。
     *
     * 说明：
     * - 仅移除应用内历史记录；
     * - 若当前选中库被移除，会同步清理 current_id；
     * - 释放不再被引用的 Uri 权限。
     *
     * @return 实际移除数量。
     */
    fun removeHistoryByIds(ids: Set<String>): Int {
        if (ids.isEmpty()) {
            return 0
        }
        ensureLoaded()

        val removedItems: List<LibraryContext>
        val filtered: List<LibraryContext>
        val removedCurrentId: Boolean
        synchronized(lock) {
            removedItems = history.filter { ids.contains(it.id) }
            filtered = history.filterNot { ids.contains(it.id) }
            val removedCount = history.size - filtered.size
            if (removedCount <= 0) {
                return 0
            }
            history = filtered.toMutableList()
            removedCurrentId = !currentId.isNullOrBlank() && ids.contains(currentId)
            if (removedCurrentId) {
                currentId = null
            }
        }
        ioScope.launch {
            database.libraryContextDao().deleteByIds(ids)
        }
        if (removedCurrentId) {
            ioScope.launch {
                database.appSettingsDao().delete(KEY_CURRENT_ID)
            }
        }

        // 清理已不再被历史引用的 Uri 权限。
        releaseObsoleteUriPermissions(removedItems, filtered)
        return history.size - filtered.size
    }

    /**
     * 释放被移除历史项的 Uri 权限（若仍被其他历史项引用则保留）。
     */
    private fun releaseObsoleteUriPermissions(
        removedItems: List<LibraryContext>,
        remainedItems: List<LibraryContext>,
    ) {
        val remainedLocalPaths = remainedItems.map { it.localPath }.toSet()
        val targetUris =
            removedItems
                .mapNotNull { item -> toContentUri(item.localPath) }
                .filter { uri -> !remainedLocalPaths.contains(uri.toString()) }
                .distinctBy { it.toString() }

        if (targetUris.isEmpty()) {
            return
        }

        val resolver = context.contentResolver
        targetUris.forEach { uri ->
            runCatching {
                resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            runCatching {
                resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }

    /**
     * 将本地路径解析为 Content Uri。
     */
    private fun toContentUri(localPath: String): Uri? {
        val parsed = runCatching { Uri.parse(localPath) }.getOrNull() ?: return null
        return if (parsed.scheme.equals("content", ignoreCase = true)) parsed else null
    }

    /**
     * 清空当前选中库
     */
    fun clearCurrentSelection() {
        ensureLoaded()
        synchronized(lock) {
            currentId = null
        }
        ioScope.launch {
            database.appSettingsDao().delete(KEY_CURRENT_ID)
        }
    }

    /**
     * 标准化历史项。
     *
     * 说明：旧版本历史记录不存在同步字段时，Gson 反序列化会使用默认零值。
     * 这里统一修正为预期行为，避免云端库被误判为关闭自动同步（仅迁移时使用）。
     */
    private fun normalizeContext(item: LibraryContext): LibraryContext {
        val hasSyncMetadata =
            item.lastSyncAt != null ||
                item.lastRemoteModifiedAt != null ||
                !item.lastSyncStatus.isNullOrBlank() ||
                !item.lastSyncError.isNullOrBlank()

        val normalizedAutoSync =
            when (item.sourceType) {
                LibrarySourceType.CLOUD -> {
                    if (hasSyncMetadata) item.autoSyncEnabled else true
                }

                LibrarySourceType.LOCAL -> false
            }

        val normalizedAuthMode =
            when (item.autoUnlockAuthMode) {
                0, 1, 2 -> item.autoUnlockAuthMode
                else -> 0
            }

        // 兼容旧版本：缺省视为启用48小时手动主密码策略。
        val normalizedForceManualUnlock = item.forceManualUnlockEvery48Hours ?: true

        return item.copy(
            autoSyncEnabled = normalizedAutoSync,
            autoUnlockAuthMode = normalizedAuthMode,
            forceManualUnlockEvery48Hours = normalizedForceManualUnlock,
            autoUnlockInvalidated = item.autoUnlockInvalidated,
        )
    }

    companion object {
        private const val PREF_NAME = "library_context_pref"
        private const val KEY_HISTORY = "history"
        private const val KEY_CURRENT_ID = "current_id"
    }
}
