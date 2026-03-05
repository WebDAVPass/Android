package xzynine.WebDAVPass.Android.data

import android.content.Context
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
            gson.fromJson<List<LibraryContext>>(raw, type) ?: emptyList()
        }.getOrElse {
            emptyList()
        }
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
        return removedCount
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
