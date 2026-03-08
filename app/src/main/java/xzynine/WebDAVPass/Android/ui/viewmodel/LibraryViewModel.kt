package xzynine.WebDAVPass.Android.ui.ViewModel

import android.content.Context
import xzylib.base.util.Logger
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import xzynine.WebDAVPass.Android.data.LibraryContext
import xzynine.WebDAVPass.Android.data.LibraryContextStore
import xzynine.WebDAVPass.Android.data.LibrarySourceType
import xzynine.WebDAVPass.Android.data.KdbxTokenRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * 库视图模型
 *
 * 负责管理库上下文的加载、选择、切换和历史记录。
 */
class LibraryViewModel(private val context: Context) : ViewModel() {

    companion object {
        private const val SYNC_STATUS_IDLE = "idle"
        private const val SYNC_STATUS_SYNCING = "syncing"
        private const val SYNC_STATUS_SUCCESS = "success"
        private const val SYNC_STATUS_MERGED = "merged"
        private const val SYNC_STATUS_CONFLICT = "conflict"
        private const val SYNC_STATUS_FAILED = "failed"
        private const val SYNC_LOG_TAG = "同步"
    }

    private val libraryContextStore: LibraryContextStore = LibraryContextStore(context)

    private val _libraryHistory = MutableStateFlow<List<LibraryContext>>(emptyList())
    val libraryHistory: StateFlow<List<LibraryContext>> = _libraryHistory.asStateFlow()

    private val _currentLibrary = MutableStateFlow<LibraryContext?>(null)
    val currentLibrary: StateFlow<LibraryContext?> = _currentLibrary.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLibraryUnlocked = MutableStateFlow(false)
    val isLibraryUnlocked: StateFlow<Boolean> = _isLibraryUnlocked.asStateFlow()

    private var currentLibraryMasterPassword: String = ""
    private var isCurrentLibraryMasterPasswordManualVerified: Boolean = false
    private var lastUnlockErrorMessage: String? = null

    init {
        refreshLibraryHistory()
    }

    /**
     * 刷新库历史与当前库状态
     */
    private fun refreshLibraryHistory() {
        val history = libraryContextStore.getHistory()
        val sortedHistory = history.sortedByDescending { it.lastUsedAt }
        _libraryHistory.value = sortedHistory

        val currentId = libraryContextStore.getCurrentLibraryId()
        _currentLibrary.value = if (currentId != null) {
            sortedHistory.firstOrNull { it.id == currentId }
        } else {
            null
        }
    }

    /**
     * 持久化当前库元数据，不重置已解锁状态。
     */
    private fun persistCurrentLibraryMetadata(updated: LibraryContext) {
        val persisted = libraryContextStore.updateHistoryItem(updated)
            ?: libraryContextStore.upsertAndSelect(updated)
        _currentLibrary.value = persisted
        _libraryHistory.value = libraryContextStore.getHistory().sortedByDescending { it.lastUsedAt }
    }

    /**
     * 添加或更新历史库并选中
     */
    fun upsertAndSelectLibrary(libraryContext: LibraryContext) {
        val updated = libraryContextStore.upsertAndSelect(libraryContext)
        _currentLibrary.value = updated
        resetUnlockState()
        refreshLibraryHistory()
    }

    /**
     * 按ID选中历史库
     */
    fun selectLibraryById(libraryId: String) {
        val selected = libraryContextStore.selectById(libraryId)
        _currentLibrary.value = selected
        resetUnlockState()
        refreshLibraryHistory()
    }

    /**
     * 清空当前库选择
     */
    fun clearCurrentLibrarySelection() {
        libraryContextStore.clearCurrentSelection()
        _currentLibrary.value = null
        resetUnlockState()
        refreshLibraryHistory()
    }

    /**
     * 打开并选中库上下文
     */
    fun openLibraryContext(libraryContext: LibraryContext) {
        upsertAndSelectLibrary(libraryContext)
    }

    /**
     * 保存当前库的云端绑定信息。
     */
    fun bindCurrentLibraryToCloud(boundContext: LibraryContext): Boolean {
        val current = _currentLibrary.value ?: return false
        val remoteBaseUrl = boundContext.remoteBaseUrl?.takeIf { it.isNotBlank() } ?: return false
        val remoteFilePath = boundContext.remoteFilePath?.takeIf { it.isNotBlank() } ?: return false
        val username = boundContext.username?.takeIf { it.isNotBlank() } ?: return false
        val password = boundContext.password?.takeIf { it.isNotBlank() } ?: return false

        val updated = current.copy(
            sourceType = LibrarySourceType.CLOUD,
            remoteBaseUrl = remoteBaseUrl,
            remoteFilePath = remoteFilePath,
            username = username,
            password = password,
            autoSyncEnabled = true,
            lastSyncStatus = current.lastSyncStatus ?: SYNC_STATUS_IDLE,
            lastSyncError = null
        )
        persistCurrentLibraryMetadata(updated)
        return true
    }

    /**
     * 切换已存在库
     */
    fun switchLibrary(libraryId: String) {
        selectLibraryById(libraryId)
    }

    /**
     * 按ID移除单个历史库。
     */
    fun removeLibraryHistoryById(libraryId: String): Boolean {
        return removeLibraryHistoryByIds(listOf(libraryId)) > 0
    }

    /**
     * 批量移除历史库。
     */
    fun removeLibraryHistoryByIds(libraryIds: Collection<String>, onDeleteKey: (String) -> Unit = {}): Int {
        val targetIds = libraryIds.filter { it.isNotBlank() }.toSet()
        if (targetIds.isEmpty()) {
            return 0
        }

        targetIds.forEach { targetId ->
            onDeleteKey(targetId)
        }

        val currentId = _currentLibrary.value?.id
        val removedCount = libraryContextStore.removeHistoryByIds(targetIds)
        if (removedCount <= 0) {
            return 0
        }

        if (!currentId.isNullOrBlank() && targetIds.contains(currentId)) {
            clearCurrentLibrarySelection()
        } else {
            refreshLibraryHistory()
        }
        return removedCount
    }

    /**
     * 解锁当前库
     */
    suspend fun unlockCurrentLibrary(
        repository: KdbxTokenRepository,
        masterPassword: String,
        isManualUnlock: Boolean = true
    ): Boolean {
        val localPath = _currentLibrary.value?.localPath ?: return false
        val ok = withContext(Dispatchers.IO) {
            repository.validatePassword(localPath, masterPassword)
        }
        if (!ok) {
            lastUnlockErrorMessage = resolveUnlockFailureMessage(localPath, repository)
            _isLibraryUnlocked.value = false
            return false
        }

        lastUnlockErrorMessage = null
        currentLibraryMasterPassword = masterPassword
        isCurrentLibraryMasterPasswordManualVerified = isManualUnlock

        if (isManualUnlock) {
            val current = _currentLibrary.value
            if (current != null) {
                persistCurrentLibraryMetadata(
                    current.copy(lastManualMasterUnlockAt = System.currentTimeMillis())
                )
            }
        }

        _isLibraryUnlocked.value = true
        return true
    }

    /**
     * 仅校验当前库主密码，不进入解锁态。
     */
    suspend fun verifyCurrentLibraryPassword(
        repository: KdbxTokenRepository,
        masterPassword: String,
        updateManualTimestamp: Boolean = false
    ): Boolean {
        val localPath = _currentLibrary.value?.localPath ?: return false
        val ok = withContext(Dispatchers.IO) {
            repository.validatePassword(localPath, masterPassword)
        }
        if (!ok) {
            lastUnlockErrorMessage = resolveUnlockFailureMessage(localPath, repository)
            return false
        }

        lastUnlockErrorMessage = null
        if (updateManualTimestamp) {
            val current = _currentLibrary.value
            if (current != null) {
                persistCurrentLibraryMetadata(
                    current.copy(lastManualMasterUnlockAt = System.currentTimeMillis())
                )
            }
        }
        return true
    }

    /**
     * 重置解锁状态
     */
    private fun resetUnlockState() {
        currentLibraryMasterPassword = ""
        isCurrentLibraryMasterPasswordManualVerified = false
        _isLibraryUnlocked.value = false
    }

    /**
     * 获取当前库的主密码
     */
    fun getCurrentLibraryMasterPassword(): String? {
        return currentLibraryMasterPassword.takeIf {
            it.isNotBlank() && isCurrentLibraryMasterPasswordManualVerified
        }
    }

    /**
     * 获取主密码（内部使用）
     */
    internal fun getMasterPasswordInternal(): String {
        return currentLibraryMasterPassword
    }

    /**
     * 判断当前库是否开启自动同步。
     */
    fun shouldAutoSyncCurrentLibrary(): Boolean {
        val current = _currentLibrary.value ?: return false
        if (current.sourceType != LibrarySourceType.CLOUD) {
            return false
        }
        return current.autoSyncEnabled
    }

    /**
     * 获取当前云端库上下文
     */
    fun getCurrentCloudLibrary(): LibraryContext? {
        val current = _currentLibrary.value ?: return null
        if (current.sourceType != LibrarySourceType.CLOUD) {
            return null
        }
        if (current.remoteFilePath.isNullOrBlank() || current.username.isNullOrBlank() || current.password.isNullOrBlank()) {
            return null
        }
        return current
    }

    /**
     * 更新云端同步状态并写回当前库。
     */
    fun updateCloudSyncState(
        status: String,
        errorMessage: String? = null,
        remoteModifiedAt: Long? = null,
        syncAt: Long? = null
    ) {
        val current = getCurrentCloudLibrary() ?: return
        Logger.d(
            SYNC_LOG_TAG,
            "更新同步状态: 状态=$status, 远端修改时间=$remoteModifiedAt, 同步时间=$syncAt, 错误=${errorMessage.orEmpty()}"
        )
        persistCurrentLibraryMetadata(
            current.copy(
                lastSyncStatus = status,
                lastSyncError = errorMessage,
                lastRemoteModifiedAt = remoteModifiedAt ?: current.lastRemoteModifiedAt,
                lastSyncAt = syncAt ?: when (status) {
                    SYNC_STATUS_SUCCESS, SYNC_STATUS_MERGED -> System.currentTimeMillis()
                    else -> current.lastSyncAt
                }
            )
        )
    }

    /**
     * 获取最近一次云端同步错误文案。
     */
    fun currentCloudSyncError(): String {
        return _currentLibrary.value?.lastSyncError?.takeIf { it.isNotBlank() } ?: "请检查网络、权限与文件状态"
    }

    /**
     * 按库ID获取最新快照
     */
    fun resolveLibrarySnapshot(libraryId: String): LibraryContext? {
        val current = _currentLibrary.value
        if (current?.id == libraryId) {
            return current
        }
        return _libraryHistory.value.firstOrNull { it.id == libraryId }
    }

    /**
     * 持久化库元数据
     */
    fun persistLibraryMetadata(library: LibraryContext) {
        persistCurrentLibraryMetadata(library)
    }

    /**
     * 获取解锁错误信息
     */
    fun getLastUnlockErrorMessage(): String? {
        return lastUnlockErrorMessage
    }

    /**
     * 归一化解锁失败文案。
     */
    private fun resolveUnlockFailureMessage(localPath: String, repository: KdbxTokenRepository): String {
        val raw = repository.getLastUnlockErrorMessage().orEmpty()
        val isUriPath = runCatching { android.net.Uri.parse(localPath) }
            .getOrNull()
            ?.scheme?.equals("content", ignoreCase = true) == true

        if (isUriPath && (
            raw.contains("SecurityException", ignoreCase = true) ||
            raw.contains("permission", ignoreCase = true) ||
            raw.contains("denied", ignoreCase = true) ||
            raw.contains("ACTION_OPEN_DOCUMENT", ignoreCase = true)
        )) {
            return "解锁失败：文件访问权限已失效，请重新选择数据库文件"
        }

        if (raw.contains("FileNotFoundException", ignoreCase = true) ||
            raw.contains("fileMissing", ignoreCase = true)
        ) {
            return "解锁失败：数据库文件不存在或不可访问"
        }

        return "解锁失败：主密码不正确或文件无效"
    }

    /**
     * 判断当前 ViewModel 协程作用域是否仍处于活跃状态。
     */
    fun isScopeActive(): Boolean {
        return viewModelScope.coroutineContext[Job]?.isActive == true
    }

    /**
     * 设置加载状态
     */
    fun setLoading(loading: Boolean) {
        _isLoading.value = loading
    }
}
