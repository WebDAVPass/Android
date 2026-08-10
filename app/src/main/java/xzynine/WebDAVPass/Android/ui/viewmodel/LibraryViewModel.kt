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
        private const val UNLOCK_STATE_LOG_TAG = "解锁状态"
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
     * 原子引用保证并发场景只启动一次 IO 协程，避免重复跑 KDF（Argon2 64MB+ 代价高）。
     */
    private val cacheRebuildJobRef: AtomicReference<Job?> = AtomicReference(null)

    init {
        refreshLibraryHistory()
        // 订阅 DatabaseManager 的缓存失效事件。
        // 触发时机：invalidateCacheKeepKeyFile（合并/改密失败路径）或 close（切库/锁定）。
        // 与"在 getMasterPasswordInternal 里与操作并发调度"不同，这里的调度发生在
        // 失败路径 onFailure 回调返回之前——此时用户还没有发起下一次操作，
        // 预重建与后续用户操作之间是「先重建、再使用」的串行关系，不会并发打开
        // 两个实例导致缓存 S0/磁盘 S1 的代次竞争。
        viewModelScope.launch {
            // DatabaseManager.cacheInvalidatedEvents 是 SharedFlow<Unit>，按本条 collect：
            // - 生命周期跟随 viewModelScope；
            // - tryEmit + DROP_OLDEST 保证发射永不阻塞 onFailure 同步回调。
            DatabaseManager.cacheInvalidatedEvents.collect {
                scheduleCacheRebuildIfNeeded()
            }
        }
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
     * 不做缓存重建调度——调度由 [DatabaseManager.cacheInvalidatedEvents] 订阅触发：
     * 失败路径 onFailure 回调返回前已同步 emit 事件，预重建在用户下一次操作之前启动，
     * 避免与用户操作并发打开两个实例造成的缓存过期问题。
     *
     * 即使后台重建尚未完成，调用方进入 `withDatabase` 未命中缓存也会自行
     * 打开磁盘文件 → 操作 → 关闭，功能正确；若写入成功会自动推进写入代次，
     * tryGet() 能识别尚未完成的「过期重建缓存」并丢弃。
     */
    internal fun getMasterPasswordInternal(): String {
        return currentLibraryMasterPassword
    }

    /**
     * 当缓存已失效但凭据仍在时，在后台 IO 协程中重建缓存。
     *
     * 触发来源：[DatabaseManager.cacheInvalidatedEvents] 的订阅方。
     *
     * - 并发安全：[cacheRebuildJobRef] 原子引用保证只启动一个重建协程。
     * - 非阻塞：调用后立即返回，不会在调用方线程上跑 KDF。
     * - 失败回落：重建失败则把 UI 切回锁定态，但仍保留密钥文件凭据。
     * - 写入代次校验：重建 KDF 阻塞期间若有写入（重试合并/用户手动编辑等），
     *   完成后立即丢弃基于旧磁盘状态的缓存实例，由后续 withDatabase 自行
     *   打开最新版本，杜绝「缓存 S0/磁盘 S1」造成写入静默回滚。
     */
    fun scheduleCacheRebuildIfNeeded() {
        if (!_isLibraryUnlocked.value || DatabaseManager.isOpen()) return
        val localPath = _currentLibrary.value?.localPath
        if (localPath.isNullOrBlank() || currentLibraryMasterPassword.isBlank()) return

        // 尝试原子地占用「重建槽位」：已有 Job 在跑就跳过，避免重复 KDF。
        val existing = cacheRebuildJobRef.get()
        if (existing != null && existing.isActive) return
        // lateinit：launch 返回前赋值尚未完成，lambda 体调度执行时赋值早已结束，安全读取。
        // 用 selfJob 而非 coroutineContext[Job] 规避 import/挂起上下文限制。
        lateinit var selfJob: Job
        val newJob = viewModelScope.launch(Dispatchers.IO) {
            // 快照重建开始时的写入代次：若代次不同说明 KDF 期间有并发写入。
            val startGeneration = DatabaseManager.currentSaveGeneration()
            try {
                val rebuilt = runCatching {
                    // validatePassword 内部会打开数据库并存入 DatabaseManager 缓存，
                    // store() 时会再快照一次 currentSaveGeneration 作为 storeGeneration。
                    KdbxTokenRepository(context).validatePassword(
                        localPath = localPath,
                        masterPassword = currentLibraryMasterPassword,
                        keyFileData = null  // 已由 invalidateCacheKeepKeyFile 保留在 DatabaseManager 中
                    )
                }.getOrDefault(false)
                if (!rebuilt) {
                    // 重建失败（凭据失配/文件损坏/权限失效等）：切回锁定态
                    withContext(Dispatchers.Main.immediate) {
                        Logger.w(
                            UNLOCK_STATE_LOG_TAG,
                            "数据库缓存丢失且后台重建失败，回落至锁定态: path=$localPath"
                        )
                        resetUnlockStateKeepKeyFile()
                    }
                    return@launch
                }

                // 重建成功后的代次一致性校验：
                // 阻塞型 KDF 期间（Argon2/AES 1-3s）若有另一个 withDatabase 非缓存路径
                // 成功写入磁盘，saveDatabase 会把代次推进；当前缓存是基于 KDF 之前的
                // 磁盘快照（S0），磁盘已是 S1 → 缓存已过期，必须丢弃避免后续 tryGet
                // 读到 S0，写操作时静默回滚掉并发修改。
                val currentAfterRebuild = DatabaseManager.currentSaveGeneration()
                if (currentAfterRebuild != startGeneration) {
                    Logger.w(
                        UNLOCK_STATE_LOG_TAG,
                        "重建期间检测到并发写入（start=$startGeneration, current=$currentAfterRebuild），" +
                            "丢弃过期重建缓存: path=$localPath"
                    )
                    // 丢弃旧缓存但保留密钥文件凭据——代次推进意味着已有新数据落盘，
                    // 不能让旧缓存污染后续读写。下次触发失效事件时会再次调度重建。
                    DatabaseManager.invalidateCacheKeepKeyFile()
                } else {
                    Logger.d(
                        UNLOCK_STATE_LOG_TAG,
                        "数据库缓存后台重建完成（代次一致 start=$startGeneration）: path=$localPath"
                    )
                }
            } finally {
                // 只在当前引用仍指向「本协程自己的 Job」时清空。
                // 之前写法 compareAndSet(cacheRebuildJobRef.get(), null)：
                // 本任务被切库 cancel、期间又调度了新任务时，会把新任务的引用误清空，
                // 下一次触发调度时将并发启动第二个 KDF。修复为与自身 Job 比较。
                cacheRebuildJobRef.compareAndSet(selfJob, null)
            }
        }
        selfJob = newJob
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
