package xzynine.WebDAVPass.Android.ui.ViewModel

import android.content.Context
import xzylib.base.util.Logger
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import xzynine.WebDAVPass.Android.data.LibraryContext
import xzynine.WebDAVPass.Android.data.LibraryContextStore
import xzynine.WebDAVPass.Android.data.LibrarySourceType
import xzynine.WebDAVPass.Android.data.KdbxTokenRepository
import xzynine.WebDAVPass.Android.data.DatabaseManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

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

    /**
     * 正在进行的「缓存丢失后异步重建」任务（若有）。
     * 原子引用保证多线程并发调用 getMasterPasswordInternal 时只启动一次 IO 协程，
     * 避免重复跑 KDF（Argon2 64MB+ 代价高）。
     */
    private val cacheRebuildJobRef: AtomicReference<Job?> = AtomicReference(null)

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
     * 超时锁定：仅重置解锁状态（清空内存密码与数据库缓存），保留当前库选择。
     * 回到前台超时后调用，解锁页仍显示当前库。
     */
    fun lockCurrentLibrary() {
        resetUnlockState()
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
        isManualUnlock: Boolean = true,
        keyFileData: ByteArray? = null
    ): Boolean {
        val localPath = _currentLibrary.value?.localPath ?: return false
        val ok = withContext(Dispatchers.IO) {
            repository.validatePassword(localPath, masterPassword, keyFileData)
        }
        if (!ok) {
            lastUnlockErrorMessage = resolveUnlockFailureMessage(localPath, repository)
            _isLibraryUnlocked.value = false
            return false
        }

        // 解锁成功后登记密钥文件，供后续重新加密保存时复用。
        // 注意：仓库层 validatePassword 内部已根据 effectiveKeyFileData 登记过一次；
        // 当调用方明确传入 keyFileData（手动解锁路径）时覆盖为调用方的值，
        // 否则（生物识别自动解锁 keyFileData==null）保留仓库层登记值，
        // 避免刚登记的密钥文件被立即置 null 导致后续写操作静默丢失密钥文件保护。
        if (keyFileData != null) {
            DatabaseManager.setKeyFileData(keyFileData)
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
        updateManualTimestamp: Boolean = false,
        keyFileData: ByteArray? = null
    ): Boolean {
        val localPath = _currentLibrary.value?.localPath ?: return false
        val ok = withContext(Dispatchers.IO) {
            repository.validatePassword(localPath, masterPassword, keyFileData)
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
        // 取消可能正在进行的缓存重建，避免切库后仍为旧库跑 KDF 或完成后污染状态
        cacheRebuildJobRef.getAndSet(null)?.cancel()
        currentLibraryMasterPassword = ""
        isCurrentLibraryMasterPasswordManualVerified = false
        _isLibraryUnlocked.value = false
        // 切换或清除库时关闭缓存的数据库实例，释放内存中的解密数据
        DatabaseManager.close()
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
     *
     * 若 UI 仍显示"已解锁"但数据库缓存已失效（合并/改密失败路径调用了
     * [DatabaseManager.invalidateCacheKeepKeyFile]），会立刻在 **IO 后台协程**
     * 调度一次缓存重建，避免在调用方线程（尤其 Main 线程）上同步执行
     * Argon2/AES-KDF 造成数秒卡顿甚至 ANR。
     *
     * 本函数本身**不挂起、不阻塞**：立即返回内存中的主密码给调用方。
     * 即使后台重建尚未完成，后续 `withDatabase` 在 IO 上下文内仍会自行
     * 打开磁盘文件完成操作，只是暂时没有缓存命中。
     */
    internal fun getMasterPasswordInternal(): String {
        scheduleCacheRebuildIfNeeded()
        return currentLibraryMasterPassword
    }

    /**
     * 当缓存已失效但凭据仍在时，在后台 IO 协程中重建缓存。
     *
     * - 并发安全：[cacheRebuildJobRef] 原子引用保证只启动一个重建协程。
     * - 非阻塞：调用后立即返回，不会在调用方线程上跑 KDF。
     * - 失败回落：重建失败则把 UI 切回锁定态，但仍保留密钥文件凭据。
     *
     * 可在已知缓存即将失效的时机（如合并/改密失败后）主动调用，
     * 作为「惰性异步预重建」——多数情况下用户下一次操作前缓存已就绪。
     */
    fun scheduleCacheRebuildIfNeeded() {
        if (!_isLibraryUnlocked.value || DatabaseManager.isOpen()) return
        val localPath = _currentLibrary.value?.localPath
        if (localPath.isNullOrBlank() || currentLibraryMasterPassword.isBlank()) return

        // 尝试原子地占用「重建槽位」：已有 Job 在跑就跳过，避免重复 KDF。
        val existing = cacheRebuildJobRef.get()
        if (existing != null && existing.isActive) return
        val newJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val rebuilt = runCatching {
                    // validatePassword 内部会打开数据库并存入 DatabaseManager 缓存
                    KdbxTokenRepository(context).validatePassword(
                        localPath = localPath,
                        masterPassword = currentLibraryMasterPassword,
                        keyFileData = null  // 已由 invalidateCacheKeepKeyFile 保留在 DatabaseManager 中
                    )
                }.getOrDefault(false)
                if (!rebuilt) {
                    // 重建失败（凭据失配/文件损坏/权限失效等）：切回锁定态
                    // 切 StateFlow 须在 Main 线程；此处无 UI 副作用也可直接赋值，
                    // 但保持一致用 Main dispatcher 更安全。
                    withContext(Dispatchers.Main.immediate) {
                        Logger.w(
                            "解锁状态",
                            "数据库缓存丢失且后台重建失败，回落至锁定态: path=$localPath"
                        )
                        resetUnlockStateKeepKeyFile()
                    }
                } else {
                    Logger.d("解锁状态", "数据库缓存后台重建完成: path=$localPath")
                }
            } finally {
                // Job 结束（无论成功/失败/取消）后清空引用，下次缓存失效时可再次调度
                cacheRebuildJobRef.compareAndSet(cacheRebuildJobRef.get(), null)
            }
        }
        if (!cacheRebuildJobRef.compareAndSet(existing, newJob)) {
            // CAS 失败：另一线程刚完成 compareAndSet，取消我们刚创建的 Job
            newJob.cancel()
        }
    }

    /**
     * 重置解锁状态，但保留 [DatabaseManager] 的密钥文件凭据。
     *
     * 用于"缓存已丢失且静默重开失败"的回落路径：需要锁定 UI，但密钥文件凭据
     * 仍然有效（下次手动解锁时不要求用户重新选密钥文件），避免问题 #1 叠加。
     */
    private fun resetUnlockStateKeepKeyFile() {
        // 取消可能正在进行的缓存重建，避免其结束后把已锁定的 UI 又改成已解锁
        cacheRebuildJobRef.getAndSet(null)?.cancel()
        currentLibraryMasterPassword = ""
        isCurrentLibraryMasterPasswordManualVerified = false
        _isLibraryUnlocked.value = false
        // 不调用 DatabaseManager.close()，保留 keyFileData，
        // 下次用户手动输入密码解锁时仍可复用已登记的密钥文件。
    }

    /**
     * 数据库设置变更（如修改主密码）后更新内存中的主密码。
     */
    internal fun updateMasterPasswordInternal(newMasterPassword: String) {
        if (newMasterPassword.isNotBlank()) {
            currentLibraryMasterPassword = newMasterPassword
        }
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
