package xzynine.WebDAVPass.Android.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 库上下文存储
 */
class LibraryContextStore(private val context: Context) {
    private val gson = Gson()
    private val preferences by lazy {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 读取历史库列表
     */
    fun getHistory(): List<LibraryContext> {
        val raw = preferences.getString(KEY_HISTORY, null) ?: return emptyList()
        return runCatching {
            val type = object : TypeToken<List<LibraryContext>>() {}.type
            (gson.fromJson<List<LibraryContext>>(raw, type) ?: emptyList())
                .map { normalizeContext(it) }
        }.getOrElse {
            emptyList()
        }
    }

    /**
     * 标准化历史项。
     *
     * 说明：旧版本历史记录不存在同步字段时，Gson 反序列化会使用默认零值。
     * 这里统一修正为预期行为，避免云端库被误判为关闭自动同步。
     */
    private fun normalizeContext(item: LibraryContext): LibraryContext {
        val hasSyncMetadata = item.lastSyncAt != null
                || item.lastRemoteModifiedAt != null
                || !item.lastSyncStatus.isNullOrBlank()
                || !item.lastSyncError.isNullOrBlank()

        val normalizedAutoSync = when (item.sourceType) {
            LibrarySourceType.CLOUD -> {
                if (hasSyncMetadata) item.autoSyncEnabled else true
            }

            LibrarySourceType.LOCAL -> false
        }

        return item.copy(autoSyncEnabled = normalizedAutoSync)
    }

    /**
     * 保存历史库列表
     */
    private fun saveHistory(history: List<LibraryContext>) {
        preferences.edit().putString(KEY_HISTORY, gson.toJson(history)).apply()
    }

    /**
     * 获取当前选中的库
     */
    fun getCurrentLibrary(): LibraryContext? {
        val currentId = preferences.getString(KEY_CURRENT_ID, null) ?: return null
        return getHistory().firstOrNull { it.id == currentId }
    }

    /**
     * 写入并选中库
     */
    fun upsertAndSelect(context: LibraryContext): LibraryContext {
        val history = getHistory().toMutableList()
        val updated = context.copy(lastUsedAt = System.currentTimeMillis())
        val existingIndex = history.indexOfFirst { it.id == updated.id || it.localPath == updated.localPath }
        if (existingIndex >= 0) {
            history[existingIndex] = updated
        } else {
            history.add(updated)
        }

        val sorted = history.sortedByDescending { it.lastUsedAt }
        saveHistory(sorted)
        preferences.edit().putString(KEY_CURRENT_ID, updated.id).apply()
        return updated
    }

    /**
     * 选中历史库
     */
    fun selectById(id: String): LibraryContext? {
        val item = getHistory().firstOrNull { it.id == id } ?: return null
        preferences.edit().putString(KEY_CURRENT_ID, item.id).apply()
        return item
    }

    /**
     * 仅更新历史项内容，不改变当前选中状态。
     *
     * @return 更新后的历史项；未命中时返回 null。
     */
    fun updateHistoryItem(item: LibraryContext): LibraryContext? {
        val history = getHistory().toMutableList()
        val index = history.indexOfFirst { it.id == item.id }
        if (index < 0) {
            return null
        }

        val merged = item.copy(lastUsedAt = history[index].lastUsedAt)
        history[index] = merged
        saveHistory(history.sortedByDescending { it.lastUsedAt })
        return merged
    }

    /**
     * 按ID移除历史库。
     *
     * @return 若成功移除返回 true。
     */
    fun removeHistoryById(id: String): Boolean {
        return removeHistoryByIds(setOf(id)) > 0
    }

    /**
     * 批量移除历史库。
     *
     * 说明：
     * - 仅移除应用内历史记录；
     * - 若当前选中库被移除，会同步清理 `KEY_CURRENT_ID`。
     *
     * @return 实际移除数量。
     */
    fun removeHistoryByIds(ids: Set<String>): Int {
        if (ids.isEmpty()) {
            return 0
        }

        val history = getHistory()
        val removedItems = history.filter { ids.contains(it.id) }
        val filtered = history.filterNot { ids.contains(it.id) }
        val removedCount = history.size - filtered.size
        if (removedCount <= 0) {
            return 0
        }

        saveHistory(filtered)
        val currentId = preferences.getString(KEY_CURRENT_ID, null)
        if (!currentId.isNullOrBlank() && ids.contains(currentId)) {
            preferences.edit().remove(KEY_CURRENT_ID).apply()
        }

        // 清理已不再被历史引用的 Uri 权限。
        releaseObsoleteUriPermissions(removedItems, filtered)
        return removedCount
    }

    /**
     * 释放被移除历史项的 Uri 权限（若仍被其他历史项引用则保留）。
     */
    private fun releaseObsoleteUriPermissions(removedItems: List<LibraryContext>, remainedItems: List<LibraryContext>) {
        val remainedLocalPaths = remainedItems.map { it.localPath }.toSet()
        val targetUris = removedItems
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
        preferences.edit().remove(KEY_CURRENT_ID).apply()
    }

    companion object {
        private const val PREF_NAME = "library_context_pref"
        private const val KEY_HISTORY = "history"
        private const val KEY_CURRENT_ID = "current_id"
    }
}
