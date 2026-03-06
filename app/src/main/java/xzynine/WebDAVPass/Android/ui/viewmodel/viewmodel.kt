package xzynine.WebDAVPass.Android.ui.ViewModel

import android.content.Context
import android.net.Uri
import xzylib.base.util.Logger
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import xzynine.WebDAVPass.Android.data.AppDatabase
import xzynine.WebDAVPass.Android.data.LibraryContext
import xzynine.WebDAVPass.Android.data.LibraryContextStore
import xzynine.WebDAVPass.Android.data.LibrarySourceType
import xzynine.WebDAVPass.Android.data.KdbxTokenRepository
import xzynine.WebDAVPass.Android.data.OtpToken
import xzynine.WebDAVPass.Android.data.PasswordEntry
import xzynine.WebDAVPass.Android.data.PasswordEntryEditDraft
import xzynine.WebDAVPass.Android.data.PasswordGroupEditDraft
import xzynine.WebDAVPass.Android.data.TokenCode
import xzynine.WebDAVPass.Android.data.WebDavConfig
import xzynine.WebDAVPass.webdav.WebDav
import xzynine.WebDAVPass.webdav.Authorization
import xzynine.WebDAVPass.Android.util.UniqueIdGenerator
import xzynine.WebDAVPass.Android.util.TokenCodeUtil
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext



/**
 * 令牌视图模型
 */
class TokenViewModel(private val context: Context) : ViewModel() {

    companion object {
        private const val UNLOCK_LOAD_RETRY_COUNT = 3
        private const val UNLOCK_LOAD_RETRY_DELAY_MS = 250L

        private const val SYNC_STATUS_IDLE = "idle"
        private const val SYNC_STATUS_SYNCING = "syncing"
        private const val SYNC_STATUS_SUCCESS = "success"
        private const val SYNC_STATUS_MERGED = "merged"
        private const val SYNC_STATUS_CONFLICT = "conflict"
        private const val SYNC_STATUS_FAILED = "failed"
        private const val SYNC_LOG_TAG = "同步"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        @Volatile
        private var SHARED_VIEW_MODEL: TokenViewModel? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "webdav_config_database"
                ).fallbackToDestructiveMigration(dropAllTables = true).build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * 获取跨 Activity 共享的令牌视图模型实例
         *
         * 说明：主页与动态令牌详情页需要共享同一份已解锁状态与令牌缓存，
         * 否则详情页会因新建 ViewModel 导致显示“暂无令牌”。
         */
        fun getSharedInstance(context: Context): TokenViewModel {
            return SHARED_VIEW_MODEL ?: synchronized(this) {
                val existing = SHARED_VIEW_MODEL
                if (existing != null && existing.isScopeActive()) {
                    existing.startTokenRefreshTimer()
                    return existing
                }

                TokenViewModel(context.applicationContext).also {
                    SHARED_VIEW_MODEL = it
                }
            }
        }
    }

    private val database: AppDatabase = getDatabase(context)
    private val libraryContextStore: LibraryContextStore = LibraryContextStore(context)
    private val kdbxTokenRepository: KdbxTokenRepository = KdbxTokenRepository()
    private var currentLibraryMasterPassword: String = ""
    private var lastUnlockErrorMessage: String? = null

    private val tokenCodeUtil: TokenCodeUtil = TokenCodeUtil()
    private var tokenRefreshJob: Job? = null
    private val cloudSyncMutex = Mutex()
    

    private val _tokens = MutableStateFlow<List<OtpToken>>(emptyList())
    val tokens: StateFlow<List<OtpToken>> = _tokens.asStateFlow()

    private val _libraryHistory = MutableStateFlow<List<LibraryContext>>(emptyList())
    val libraryHistory: StateFlow<List<LibraryContext>> = _libraryHistory.asStateFlow()

    private val _currentLibrary = MutableStateFlow<LibraryContext?>(null)
    val currentLibrary: StateFlow<LibraryContext?> = _currentLibrary.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLibraryUnlocked = MutableStateFlow(false)
    val isLibraryUnlocked: StateFlow<Boolean> = _isLibraryUnlocked.asStateFlow()

    private val _tokenCodes = mutableMapOf<Long, MutableStateFlow<TokenCode?>>()
    private val _tokenCodeSnapshot = MutableStateFlow<Map<Long, TokenCode?>>(emptyMap())
    val tokenCodeSnapshot: StateFlow<Map<Long, TokenCode?>> = _tokenCodeSnapshot.asStateFlow()

    private val _currentTimeMillis = MutableStateFlow(System.currentTimeMillis())
    val currentTimeMillis: StateFlow<Long> = _currentTimeMillis.asStateFlow()

    private val passwordListModeSubViewModel: PasswordListModeSubViewModel by lazy {
        PasswordListModeSubViewModel(
            repository = kdbxTokenRepository,
            scope = viewModelScope,
            accessProvider = {
                PasswordDataAccess(
                    isLibraryUnlocked = _isLibraryUnlocked.value,
                    localPath = _currentLibrary.value?.localPath,
                    masterPassword = currentLibraryMasterPassword
                )
            }
        )
    }

    private val passwordSubViewModel: PasswordPagingSubViewModel by lazy {
        PasswordPagingSubViewModel(
            repository = kdbxTokenRepository,
            scope = viewModelScope,
            accessProvider = {
                PasswordDataAccess(
                    isLibraryUnlocked = _isLibraryUnlocked.value,
                    localPath = _currentLibrary.value?.localPath,
                    masterPassword = currentLibraryMasterPassword
                )
            },
            listModeProvider = {
                passwordListModeSubViewModel.passwordListMode.value
            }
        )
    }

    val passwordEntries: StateFlow<List<PasswordEntry>>
        get() = passwordSubViewModel.passwordEntries

    val passwordTotalCount: StateFlow<Int>
        get() = passwordSubViewModel.passwordTotalCount

    val passwordListMode: StateFlow<PasswordListMode>
        get() = passwordListModeSubViewModel.passwordListMode

    val recentDeletedCount: StateFlow<Int>
        get() = passwordListModeSubViewModel.recentDeletedCount

    val passwordGroupStack: StateFlow<List<Long>>
        get() = passwordSubViewModel.passwordGroupStack

    val passwordIndexKeys: StateFlow<List<String>>
        get() = passwordSubViewModel.passwordIndexKeys

    val passwordHasMore: StateFlow<Boolean>
        get() = passwordSubViewModel.passwordHasMore

    // WebDAV配置相关
    private val _webDavConfigs = MutableStateFlow<List<WebDavConfig>>(emptyList())
    val webDavConfigs: StateFlow<List<WebDavConfig>> = _webDavConfigs.asStateFlow()
    
    // 备份相关状态
    private val _isBackupInProgress = MutableStateFlow(false)
    val isBackupInProgress: StateFlow<Boolean> = _isBackupInProgress.asStateFlow()
    
    private val _isRestoreInProgress = MutableStateFlow(false)
    val isRestoreInProgress: StateFlow<Boolean> = _isRestoreInProgress.asStateFlow()
    
    private val _backupStatus = MutableStateFlow("")
    val backupStatus: StateFlow<String> = _backupStatus.asStateFlow()
    
    private val _backupProgress = MutableStateFlow(0)
    val backupProgress: StateFlow<Int> = _backupProgress.asStateFlow()
    
    private val _restoreProgress = MutableStateFlow(0)
    val restoreProgress: StateFlow<Int> = _restoreProgress.asStateFlow()




    init {
        refreshLibraryHistory()
        loadWebDavConfigs()
        startTokenRefreshTimer()
    }

    /**
     * 刷新库历史与当前库状态
     */
    private fun refreshLibraryHistory() {
        _libraryHistory.value = libraryContextStore.getHistory().sortedByDescending { it.lastUsedAt }
        _currentLibrary.value = libraryContextStore.getCurrentLibrary()
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
     * 判断当前库是否开启自动同步。
     */
    private fun shouldAutoSyncCurrentLibrary(): Boolean {
        val current = _currentLibrary.value ?: return false
        if (current.sourceType != LibrarySourceType.CLOUD) {
            return false
        }
        return current.autoSyncEnabled
    }

    /**
     * 更新云端同步状态并写回当前库。
     */
    private fun updateCloudSyncState(
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
                    SYNC_STATUS_SUCCESS,
                    SYNC_STATUS_MERGED -> System.currentTimeMillis()

                    else -> current.lastSyncAt
                }
            )
        )
    }

    /**
     * 添加或更新历史库并选中
     */
    fun upsertAndSelectLibrary(libraryContext: LibraryContext) {
        val updated = libraryContextStore.upsertAndSelect(libraryContext)
        _currentLibrary.value = updated
        currentLibraryMasterPassword = ""
        _isLibraryUnlocked.value = false
        passwordListModeSubViewModel.resetAllState()
        _tokens.value = emptyList()
        viewModelScope.launch {
            passwordSubViewModel.clearAll(resetTotalCount = true)
        }
        _tokenCodes.clear()
        publishTokenCodeSnapshot()
        refreshLibraryHistory()
    }

    /**
     * 按ID选中历史库
     */
    fun selectLibraryById(libraryId: String) {
        val selected = libraryContextStore.selectById(libraryId)
        _currentLibrary.value = selected
        currentLibraryMasterPassword = ""
        _isLibraryUnlocked.value = false
        passwordListModeSubViewModel.resetAllState()
        _tokens.value = emptyList()
        viewModelScope.launch {
            passwordSubViewModel.clearAll(resetTotalCount = true)
        }
        _tokenCodes.clear()
        publishTokenCodeSnapshot()
        refreshLibraryHistory()
    }

    /**
     * 清空当前库选择
     */
    fun clearCurrentLibrarySelection() {
        libraryContextStore.clearCurrentSelection()
        _currentLibrary.value = null
        currentLibraryMasterPassword = ""
        _isLibraryUnlocked.value = false
        passwordListModeSubViewModel.resetAllState()
        _tokens.value = emptyList()
        viewModelScope.launch {
            passwordSubViewModel.clearAll(resetTotalCount = true)
        }
        _tokenCodes.clear()
        publishTokenCodeSnapshot()
        refreshLibraryHistory()
    }

    /**
     * 打开并选中库上下文
     */
    fun openLibraryContext(libraryContext: LibraryContext) {
        upsertAndSelectLibrary(libraryContext)
    }

    /**
     * 切换已存在库
     */
    fun switchLibrary(libraryId: String) {
        selectLibraryById(libraryId)
    }

    /**
     * 按ID移除单个历史库。
     *
     * 说明：
     * - 仅移除应用内历史记录，不删除本地或云端文件；
     * - 若移除的是当前选中库，会同步清理当前库状态。
     */
    fun removeLibraryHistoryById(libraryId: String): Boolean {
        return removeLibraryHistoryByIds(listOf(libraryId)) > 0
    }

    /**
     * 批量移除历史库。
     *
     * @return 实际移除数量。
     */
    fun removeLibraryHistoryByIds(libraryIds: Collection<String>): Int {
        val targetIds = libraryIds.filter { it.isNotBlank() }.toSet()
        if (targetIds.isEmpty()) {
            return 0
        }

        val currentId = _currentLibrary.value?.id
        val removedCount = libraryContextStore.removeHistoryByIds(targetIds)
        if (removedCount <= 0) {
            return 0
        }

        if (!currentId.isNullOrBlank() && targetIds.contains(currentId)) {
            // 复用现有清理逻辑，确保解锁状态、缓存、分组导航都被重置。
            clearCurrentLibrarySelection()
        } else {
            refreshLibraryHistory()
        }
        return removedCount
    }

    /**
     * 解锁当前库（主密码与WebDAV密码分离）
     */
    suspend fun unlockCurrentLibrary(masterPassword: String): Boolean {
        val localPath = _currentLibrary.value?.localPath ?: return false
        val ok = withContext(Dispatchers.IO) {
            kdbxTokenRepository.validatePassword(localPath, masterPassword)
        }
        if (!ok) {
            lastUnlockErrorMessage = "解锁失败：主密码不正确或文件无效"
            _isLibraryUnlocked.value = false
            return false
        }

        lastUnlockErrorMessage = null
        currentLibraryMasterPassword = masterPassword

        repeat(UNLOCK_LOAD_RETRY_COUNT) { attemptIndex ->
            val loaded = loadTokensInternal()
            if (loaded) {
                lastUnlockErrorMessage = null
                if (shouldAutoSyncCurrentLibrary()) {
                    Logger.d(SYNC_LOG_TAG, "解锁成功，触发自动恢复")
                    autoRestoreTokens()
                }
                return true
            }

            if (attemptIndex < UNLOCK_LOAD_RETRY_COUNT - 1) {
                delay(UNLOCK_LOAD_RETRY_DELAY_MS)
            }
        }

        lastUnlockErrorMessage = "加载失败：已重试${UNLOCK_LOAD_RETRY_COUNT}次，请重试"
        return false
    }

    fun getLastUnlockErrorMessage(): String? {
        return lastUnlockErrorMessage
    }

    /**
     * 将Uri指向的kdbx文件持久化到应用私有目录
     *
     * @return 持久化后的绝对路径，失败返回null
     */
    suspend fun persistKdbxFromUri(uri: Uri): String? {
        return runCatching {
            val libraryDir = File(context.filesDir, "libraries")
            if (!libraryDir.exists()) {
                libraryDir.mkdirs()
            }

            val fileName = buildString {
                append("import-")
                append(System.currentTimeMillis())
                append(".kdbx")
            }

            val localFile = File(libraryDir, fileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                localFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return null

            localFile.absolutePath
        }.getOrNull()
    }

    /**
     * 通过系统CreateDocument创建本地kdbx文件，并同步保存一份到应用私有目录
     *
     * @return 本地私有目录中的绝对路径，失败返回null
     */
    suspend fun createLocalKdbx(uri: Uri, masterPassword: String): String? {
        return runCatching {
            val kdbxBytes = kdbxTokenRepository.createDatabaseBytes(masterPassword)
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(kdbxBytes)
            } ?: return null

            val persistedPath = persistKdbxFromUri(uri) ?: return null
            persistedPath
        }.getOrNull()
    }

    fun createEmptyKdbxBytes(masterPassword: String): ByteArray {
        return kdbxTokenRepository.createDatabaseBytes(masterPassword)
    }

    /**
     * 加载所有WebDAV配置
     */
    private fun loadWebDavConfigs() {
        viewModelScope.launch {
            database.webDavConfigDao().getAll().collect {
                _webDavConfigs.value = it
            }
        }
    }

    /**
     * 刷新WebDAV配置列表，确保立即更新UI
     */
    private suspend fun refreshWebDavConfigList() {
        val configList = database.webDavConfigDao().getAllOnce()
        _webDavConfigs.value = configList
    }

    /**
     * 从当前库加载令牌
     */
    private fun loadTokens() {
        viewModelScope.launch {
            loadTokensInternal()
        }
    }

    /**
     * 同步加载当前库令牌。
     *
     * @return 加载成功返回 true；失败返回 false。
     */
    private suspend fun loadTokensInternal(): Boolean {
        _isLoading.value = true
        return try {
            val localPath = _currentLibrary.value?.localPath
            if (localPath.isNullOrBlank()) {
                _tokens.value = emptyList()
                _tokenCodes.clear()
                _isLibraryUnlocked.value = false
                return false
            }

            val loadedTokens = withContext(Dispatchers.IO) {
                kdbxTokenRepository.loadTokens(localPath, currentLibraryMasterPassword)
            }
            _tokens.value = loadedTokens

            loadedTokens.forEach {
                if (!_tokenCodes.containsKey(it.id)) {
                    _tokenCodes[it.id] = MutableStateFlow(tokenCodeUtil.generateTokenCode(it))
                }
            }
            _tokenCodes.keys.retainAll(loadedTokens.map { it.id }.toSet())
            publishTokenCodeSnapshot()

            _isLibraryUnlocked.value = true
            passwordSubViewModel.reloadInitialPasswordData()
            passwordListModeSubViewModel.refreshRecentDeletedCount()
            true
        } catch (ex: Exception) {
            _tokens.value = emptyList()
            passwordSubViewModel.clearAll(resetTotalCount = true)
            passwordListModeSubViewModel.clearRecentDeletedCount()
            _tokenCodes.clear()
            publishTokenCodeSnapshot()
            _isLibraryUnlocked.value = false
            lastUnlockErrorMessage = "加载失败：${ex.message ?: ex.javaClass.simpleName}"
            ex.printStackTrace()
            false
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * 获取指定令牌的代码
     */
    fun getTokenCode(tokenId: Long): StateFlow<TokenCode?> {
        if (!_tokenCodes.containsKey(tokenId)) {
            _tokenCodes[tokenId] = MutableStateFlow(null)
            publishTokenCodeSnapshot()
        }
        return _tokenCodes[tokenId]!!.asStateFlow()
    }

    /**
     * 刷新密码条目与键值列表。
     */
    fun refreshPasswordEntries(searchQuery: String = "") {
        passwordSubViewModel.refreshPasswordEntries(searchQuery)
    }

    /**
     * 设置密码列表模式。
     */
    fun setPasswordListMode(
        mode: PasswordListMode,
        refreshNow: Boolean = true,
        searchQuery: String = ""
    ) {
        passwordListModeSubViewModel.setPasswordListMode(mode)
        passwordSubViewModel.resetPasswordGroupStackOnly()
        if (refreshNow) {
            passwordSubViewModel.refreshPasswordEntries(searchQuery)
        }
    }

    /**
     * 刷新最近删除数量。
     */
    fun refreshRecentDeletedCount() {
        passwordListModeSubViewModel.refreshRecentDeletedCount()
    }

    /**
     * 触发密码列表加载下一页。
     */
    fun loadNextPasswordPage() {
        passwordSubViewModel.loadNextPage()
    }

    /**
     * 为索引跳转预加载到目标分组。
     */
    suspend fun ensurePasswordIndexLoaded(indexKey: String): Boolean {
        return passwordSubViewModel.ensureSectionLoaded(indexKey)
    }

    /**
     * 获取指定索引分组在 LazyColumn 中对应的标题项下标。
     */
    suspend fun getPasswordHeaderScrollIndex(indexKey: String): Int? {
        return passwordSubViewModel.getHeaderScrollIndex(indexKey)
    }

    /**
     * 进入密码分组。
     */
    fun openPasswordGroup(groupStableId: Long, searchQuery: String = "") {
        passwordSubViewModel.openPasswordGroup(groupStableId, searchQuery)
    }

    /**
     * 返回上一级密码分组。
     */
    fun navigateUpPasswordGroup(searchQuery: String = "") {
        passwordSubViewModel.navigateUpPasswordGroup(searchQuery)
    }

    /**
     * 重置密码分组导航到根分组。
     */
    fun resetPasswordGroupNavigation(searchQuery: String = "") {
        passwordSubViewModel.resetPasswordGroupNavigation(searchQuery)
    }

    /**
     * 仅重置密码分组栈，不触发刷新。
     */
    fun resetPasswordGroupStackOnly() {
        passwordSubViewModel.resetPasswordGroupStackOnly()
    }

    /**
     * 兼容旧调用名。
     */
    fun refreshRemainingKeyValues() {
        refreshPasswordEntries()
    }

    /**
     * 按稳定 ID 读取单条密码详情。
     *
     * 说明：
     * - 仅用于详情页按需拉取；
     * - 不依赖列表缓存，避免列表页为详情提前全量加载。
     */
    suspend fun loadPasswordEntryDetail(entryId: Long): PasswordEntry? {
        if (entryId < 0) {
            return null
        }

        val access = PasswordDataAccess(
            isLibraryUnlocked = _isLibraryUnlocked.value,
            localPath = _currentLibrary.value?.localPath,
            masterPassword = currentLibraryMasterPassword
        )
        if (!access.isReady()) {
            return null
        }

        val localPath = access.localPath ?: return null
        return withContext(Dispatchers.IO) {
            kdbxTokenRepository.loadPasswordEntryById(localPath, access.masterPassword, entryId)
        }
    }

    /**
     * 按稳定 ID 读取条目编辑草稿。
     */
    suspend fun loadPasswordEntryDraft(entryId: Long): PasswordEntryEditDraft? {
        if (entryId < 0) {
            return null
        }
        val access = PasswordDataAccess(
            isLibraryUnlocked = _isLibraryUnlocked.value,
            localPath = _currentLibrary.value?.localPath,
            masterPassword = currentLibraryMasterPassword
        )
        if (!access.isReady()) {
            return null
        }

        val localPath = access.localPath ?: return null
        return withContext(Dispatchers.IO) {
            kdbxTokenRepository.loadPasswordEntryDraft(localPath, access.masterPassword, entryId)
        }
    }

    /**
     * 按稳定 ID 读取分组编辑草稿。
     */
    suspend fun loadPasswordGroupDraft(groupId: Long): PasswordGroupEditDraft? {
        if (groupId >= 0) {
            return null
        }
        val access = PasswordDataAccess(
            isLibraryUnlocked = _isLibraryUnlocked.value,
            localPath = _currentLibrary.value?.localPath,
            masterPassword = currentLibraryMasterPassword
        )
        if (!access.isReady()) {
            return null
        }

        val localPath = access.localPath ?: return null
        return withContext(Dispatchers.IO) {
            kdbxTokenRepository.loadPasswordGroupDraft(localPath, access.masterPassword, groupId)
        }
    }

    /**
     * 新建密码条目并返回稳定 ID。
     */
    suspend fun createPasswordEntry(draft: PasswordEntryEditDraft): Long? {
        val access = PasswordDataAccess(
            isLibraryUnlocked = _isLibraryUnlocked.value,
            localPath = _currentLibrary.value?.localPath,
            masterPassword = currentLibraryMasterPassword
        )
        if (!access.isReady()) {
            return null
        }

        val localPath = access.localPath ?: return null
        val createdId = withContext(Dispatchers.IO) {
            kdbxTokenRepository.createPasswordEntry(localPath, access.masterPassword, draft)
        }
        if (createdId != null) {
            onPasswordWriteSuccess()
        }
        return createdId
    }

    /**
     * 更新密码条目。
     */
    suspend fun updatePasswordEntry(draft: PasswordEntryEditDraft): Boolean {
        val access = PasswordDataAccess(
            isLibraryUnlocked = _isLibraryUnlocked.value,
            localPath = _currentLibrary.value?.localPath,
            masterPassword = currentLibraryMasterPassword
        )
        if (!access.isReady()) {
            return false
        }

        val localPath = access.localPath ?: return false
        val updated = withContext(Dispatchers.IO) {
            kdbxTokenRepository.updatePasswordEntry(localPath, access.masterPassword, draft)
        }
        if (updated) {
            onPasswordWriteSuccess()
        }
        return updated
    }

    /**
     * 删除密码条目（进入回收站）。
     */
    suspend fun deletePasswordEntry(entryId: Long): Boolean {
        if (entryId < 0) {
            return false
        }
        val access = PasswordDataAccess(
            isLibraryUnlocked = _isLibraryUnlocked.value,
            localPath = _currentLibrary.value?.localPath,
            masterPassword = currentLibraryMasterPassword
        )
        if (!access.isReady()) {
            return false
        }

        val localPath = access.localPath ?: return false
        val deleted = withContext(Dispatchers.IO) {
            kdbxTokenRepository.deletePasswordEntry(localPath, access.masterPassword, entryId)
        }
        if (deleted) {
            onPasswordWriteSuccess()
        }
        return deleted
    }

    /**
     * 新建密码分组并返回稳定 ID。
     */
    suspend fun createPasswordGroup(draft: PasswordGroupEditDraft): Long? {
        val access = PasswordDataAccess(
            isLibraryUnlocked = _isLibraryUnlocked.value,
            localPath = _currentLibrary.value?.localPath,
            masterPassword = currentLibraryMasterPassword
        )
        if (!access.isReady()) {
            return null
        }

        val localPath = access.localPath ?: return null
        val createdId = withContext(Dispatchers.IO) {
            kdbxTokenRepository.createPasswordGroup(localPath, access.masterPassword, draft)
        }
        if (createdId != null) {
            onPasswordWriteSuccess()
        }
        return createdId
    }

    /**
     * 更新密码分组。
     */
    suspend fun updatePasswordGroup(draft: PasswordGroupEditDraft): Boolean {
        val access = PasswordDataAccess(
            isLibraryUnlocked = _isLibraryUnlocked.value,
            localPath = _currentLibrary.value?.localPath,
            masterPassword = currentLibraryMasterPassword
        )
        if (!access.isReady()) {
            return false
        }

        val localPath = access.localPath ?: return false
        val updated = withContext(Dispatchers.IO) {
            kdbxTokenRepository.updatePasswordGroup(localPath, access.masterPassword, draft)
        }
        if (updated) {
            onPasswordWriteSuccess()
        }
        return updated
    }

    /**
     * 删除密码分组（进入回收站）。
     */
    suspend fun deletePasswordGroup(groupId: Long): Boolean {
        if (groupId >= 0) {
            return false
        }
        val access = PasswordDataAccess(
            isLibraryUnlocked = _isLibraryUnlocked.value,
            localPath = _currentLibrary.value?.localPath,
            masterPassword = currentLibraryMasterPassword
        )
        if (!access.isReady()) {
            return false
        }

        val localPath = access.localPath ?: return false
        val deleted = withContext(Dispatchers.IO) {
            kdbxTokenRepository.deletePasswordGroup(localPath, access.masterPassword, groupId)
        }
        if (deleted) {
            onPasswordWriteSuccess()
        }
        return deleted
    }

    /**
     * 写入成功后的统一刷新链路。
     */
    private fun onPasswordWriteSuccess() {
        loadTokens()
        refreshPasswordEntries()
        refreshRecentDeletedCount()
        backupTokens()
    }

    /**
     * 启动令牌刷新定时器
     */
    private fun startTokenRefreshTimer() {
        if (tokenRefreshJob?.isActive == true) {
            return
        }

        tokenRefreshJob = viewModelScope.launch {
            while (true) {
                delay(1000) // 每秒刷新一次
                runCatching {
                    val now = System.currentTimeMillis()
                    _currentTimeMillis.value = now
                    refreshTokenCodes(now)
                }.onFailure {
                    it.printStackTrace()
                }
            }
        }
    }

    /**
     * 判断当前 ViewModel 协程作用域是否仍处于活跃状态。
     */
    private fun isScopeActive(): Boolean {
        return viewModelScope.coroutineContext[Job]?.isActive == true
    }

    /**
     * 刷新所有令牌代码
     */
    private fun refreshTokenCodes(currentTime: Long) {
        var hasChanged = false
        
        _tokens.value.forEach {
            val currentCode = _tokenCodes[it.id]?.value
            val newCode = tokenCodeUtil.generateTokenCode(it)

            // 当 currentCode 为 null 或需要刷新令牌时更新
            if (currentCode == null || newCode.shouldRefreshToken(currentTime)) {
                _tokenCodes[it.id]?.value = newCode
                hasChanged = true
            }
        }

        if (hasChanged) {
            publishTokenCodeSnapshot()
        }
    }

    /**
     * 发布当前令牌验证码快照，供列表级 UI 统一订阅。
     */
    private fun publishTokenCodeSnapshot() {
        _tokenCodeSnapshot.value = _tokenCodes.mapValues { it.value.value }
    }

    /**
     * 添加新令牌
     * @return 是否成功添加（如果密钥+算法+位数+周期已存在则返回false）
     */
    suspend fun addToken(token: OtpToken): Boolean {
        val localPath = _currentLibrary.value?.localPath ?: return false

        val tokenWithId = if (token.uniqueId.isBlank()) {
            token.copy(
                uniqueId = UniqueIdGenerator.generate(
                    token.secret,
                    token.algorithm,
                    token.digits,
                    token.period
                )
            )
        } else {
            token
        }

        val duplicate = withContext(Dispatchers.IO) {
            kdbxTokenRepository.isDuplicate(
                localPath,
                currentLibraryMasterPassword,
                tokenWithId.secret,
                tokenWithId.algorithm,
                tokenWithId.digits,
                tokenWithId.period
            )
        }
        if (duplicate) {
            return false
        }

        val added = withContext(Dispatchers.IO) {
            kdbxTokenRepository.addToken(localPath, currentLibraryMasterPassword, tokenWithId)
        }
        if (added) {
            loadTokens()
            backupTokens()
        }
        return added
    }

    /**
     * 判断令牌是否重复
     */
    suspend fun isTokenDuplicate(secret: String, algorithm: String, digits: Int, period: Int): Boolean {
        val localPath = _currentLibrary.value?.localPath ?: return false
        return withContext(Dispatchers.IO) {
            kdbxTokenRepository.isDuplicate(
                localPath,
                currentLibraryMasterPassword,
                secret,
                algorithm,
                digits,
                period
            )
        }
    }

    /**
     * 删除令牌
     */
    fun deleteToken(tokenId: Long) {
        viewModelScope.launch {
            val localPath = _currentLibrary.value?.localPath ?: return@launch
            val deleted = withContext(Dispatchers.IO) {
                kdbxTokenRepository.deleteToken(localPath, currentLibraryMasterPassword, tokenId)
            }
            if (deleted) {
                _tokenCodes.remove(tokenId)
                publishTokenCodeSnapshot()
                loadTokens()
                passwordListModeSubViewModel.refreshRecentDeletedCount()
                backupTokens()
            }
        }
    }

    /**
     * 更新令牌
     */
    fun updateToken(token: OtpToken) {
        viewModelScope.launch {
            val localPath = _currentLibrary.value?.localPath ?: return@launch
            val updated = withContext(Dispatchers.IO) {
                kdbxTokenRepository.updateToken(localPath, currentLibraryMasterPassword, token)
            }
            if (updated) {
                _tokenCodes[token.id]?.value = tokenCodeUtil.generateTokenCode(token)
                publishTokenCodeSnapshot()
                loadTokens()
                backupTokens()
            }
        }
    }

    /**
     * 递增HOTP计数器
     */
    fun incrementCounter(tokenId: Long) {
        viewModelScope.launch {
            val localPath = _currentLibrary.value?.localPath ?: return@launch
            val incremented = withContext(Dispatchers.IO) {
                kdbxTokenRepository.incrementCounter(localPath, currentLibraryMasterPassword, tokenId)
            }
            if (incremented) {
                loadTokens()
            }
        }
    }

    /**
     * 添加WebDAV配置
     * @param config WebDAV配置对象
     * @return 插入的配置ID
     */
    suspend fun addWebDavConfig(config: WebDavConfig): Long {
        // 设置排序号
        val lastSortNumber = database.webDavConfigDao().getLastSortNumber()
        config.sortNumber = (lastSortNumber ?: 0) + 1

        val id = database.webDavConfigDao().insert(config)
        refreshWebDavConfigList()
        return id
    }

    /**
     * 更新WebDAV配置
     * @param config WebDAV配置对象
     */
    fun updateWebDavConfig(config: WebDavConfig) {
        viewModelScope.launch {
            database.webDavConfigDao().update(config)
            refreshWebDavConfigList()
        }
    }

    /**
     * 删除WebDAV配置
     * @param config WebDAV配置对象
     */
    fun deleteWebDavConfig(config: WebDavConfig) {
        viewModelScope.launch {
            database.webDavConfigDao().delete(config)
            refreshWebDavConfigList()
        }
    }

    /**
     * 根据ID删除WebDAV配置
     * @param id 配置ID
     */
    fun deleteWebDavConfigById(id: Long) {
        viewModelScope.launch {
            database.webDavConfigDao().deleteById(id)
            refreshWebDavConfigList()
        }
    }

    /**
     * 根据ID获取WebDAV配置
     * @param id 配置ID
     */
    suspend fun getWebDavConfigById(id: Long): WebDavConfig? {
        return database.webDavConfigDao().getById(id)
    }

    /**
     * 获取第一个WebDAV配置
     */
    suspend fun getFirstWebDavConfig(): WebDavConfig? {
        val configs = database.webDavConfigDao().getAllOnce()
        return configs.firstOrNull()
    }
    
    /**
     * 获取当前云端库上下文
     */
    private fun getCurrentCloudLibrary(): LibraryContext? {
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
     * 从当前云端库下载到本地
     */
    private suspend fun downloadCurrentCloudLibrary(): Boolean {
        val current = getCurrentCloudLibrary() ?: return false
        Logger.d(
            SYNC_LOG_TAG,
            "开始下载云端库: 本地路径=${current.localPath}, 远端路径=${current.remoteFilePath.orEmpty()}"
        )
        return withContext(Dispatchers.IO) {
            runCatching {
                val remote = WebDav(current.remoteFilePath!!, Authorization(current.username!!, current.password!!))
                if (!remote.exists()) {
                    Logger.d(SYNC_LOG_TAG, "跳过下载，远端文件不存在")
                    return@runCatching false
                }

                val remoteInfo = remote.getWebDavFile()
                val remoteModified = remoteInfo?.lastModify?.takeIf { it > 0 }
                val bytes = remote.download()
                val localFile = File(current.localPath)
                localFile.parentFile?.let {
                    if (!it.exists()) {
                        it.mkdirs()
                    }
                }
                localFile.writeBytes(bytes)

                updateCloudSyncState(
                    status = SYNC_STATUS_SUCCESS,
                    errorMessage = null,
                    remoteModifiedAt = remoteModified,
                    syncAt = System.currentTimeMillis()
                )
                Logger.d(
                    SYNC_LOG_TAG,
                    "下载云端库成功: 远端修改时间=$remoteModified"
                )
                true
            }.onFailure {
                Logger.e(SYNC_LOG_TAG, "下载云端库失败: ${it.message}", it)
                updateCloudSyncState(
                    status = SYNC_STATUS_FAILED,
                    errorMessage = it.message ?: "下载失败"
                )
            }.getOrDefault(false)
        }
    }

    /**
     * 将当前本地库上传到云端
     */
    private suspend fun uploadCurrentCloudLibrary(): Boolean {
        val current = getCurrentCloudLibrary() ?: return false
        Logger.d(
            SYNC_LOG_TAG,
            "开始上传云端库: 本地路径=${current.localPath}, 远端路径=${current.remoteFilePath.orEmpty()}"
        )
        if (currentLibraryMasterPassword.isBlank()) {
            Logger.d(SYNC_LOG_TAG, "上传中止，主密码为空")
            updateCloudSyncState(
                status = SYNC_STATUS_FAILED,
                errorMessage = "未解锁数据库，无法执行云端同步"
            )
            return false
        }

        return withContext(Dispatchers.IO) {
            runCatching {
                val localFile = File(current.localPath)
                if (!localFile.exists()) {
                    return@runCatching false
                }

                val remote = WebDav(current.remoteFilePath!!, Authorization(current.username!!, current.password!!))
                val remoteInfo = remote.getWebDavFile()
                val remoteModified = remoteInfo?.lastModify?.takeIf { it > 0 }
                val remoteChangedAfterSync = remoteModified != null
                        && (current.lastRemoteModifiedAt == null || remoteModified > current.lastRemoteModifiedAt)
                val localChangedAfterSync = current.lastSyncAt?.let { localFile.lastModified() > it } ?: true
                Logger.d(
                    SYNC_LOG_TAG,
                    "上传前比较: 远端已变更=$remoteChangedAfterSync, 本地已变更=$localChangedAfterSync, 当前远端修改时间=$remoteModified, 已记录远端修改时间=${current.lastRemoteModifiedAt}, 上次同步时间=${current.lastSyncAt}"
                )

                if (remoteChangedAfterSync) {
                    Logger.d(SYNC_LOG_TAG, "检测到远端变更，进入下载/合并流程")
                    val remoteBytes = remote.download()
                    val merged = if (localChangedAfterSync) {
                        Logger.d(SYNC_LOG_TAG, "检测到本地也有变更，尝试自动合并")
                        kdbxTokenRepository.mergeRemoteDatabaseBytes(
                            localPath = current.localPath,
                            masterPassword = currentLibraryMasterPassword,
                            remoteBytes = remoteBytes
                        )
                    } else {
                        Logger.d(SYNC_LOG_TAG, "本地无变更，使用远端内容覆盖本地")
                        localFile.writeBytes(remoteBytes)
                        true
                    }

                    if (!merged) {
                        Logger.e(SYNC_LOG_TAG, "自动合并失败，标记为冲突")
                        updateCloudSyncState(
                            status = SYNC_STATUS_CONFLICT,
                            errorMessage = "自动合并失败，请先手动恢复后再同步",
                            remoteModifiedAt = remoteModified
                        )
                        return@runCatching false
                    }
                    Logger.d(SYNC_LOG_TAG, "自动合并成功")
                }

                remote.upload(localFile, "application/octet-stream")
                val refreshedRemoteModified = runCatching {
                    remote.getWebDavFile()?.lastModify
                }.getOrNull()?.takeIf { it > 0 } ?: remoteModified

                val finalStatus = if (remoteChangedAfterSync && localChangedAfterSync) {
                    SYNC_STATUS_MERGED
                } else {
                    SYNC_STATUS_SUCCESS
                }
                updateCloudSyncState(
                    status = finalStatus,
                    errorMessage = null,
                    remoteModifiedAt = refreshedRemoteModified,
                    syncAt = System.currentTimeMillis()
                )
                Logger.d(
                    SYNC_LOG_TAG,
                    "上传云端库成功: 最终状态=$finalStatus, 最新远端修改时间=$refreshedRemoteModified"
                )
                true
            }.onFailure {
                Logger.e(SYNC_LOG_TAG, "上传云端库失败: ${it.message}", it)
                updateCloudSyncState(
                    status = SYNC_STATUS_FAILED,
                    errorMessage = it.message ?: "上传失败"
                )
            }.getOrDefault(false)
        }
    }
    
    /**
     * 自动恢复令牌
     */
    private fun autoRestoreTokens() {
        viewModelScope.launch {
            cloudSyncMutex.withLock {
                try {
                    Logger.d(SYNC_LOG_TAG, "开始自动恢复")
                    if (!shouldAutoSyncCurrentLibrary()) {
                        Logger.d(SYNC_LOG_TAG, "自动恢复跳过：自动同步未开启")
                        _backupStatus.value = "当前云端库未启用自动同步"
                        return@withLock
                    }

                    _isRestoreInProgress.value = true
                    _restoreProgress.value = 0
                    _backupStatus.value = "正在尝试自动恢复..."
                    updateCloudSyncState(status = SYNC_STATUS_SYNCING)

                    val cloudLibrary = getCurrentCloudLibrary()
                    if (cloudLibrary != null) {
                        val success = downloadCurrentCloudLibrary()
                        Logger.d(SYNC_LOG_TAG, "自动恢复下载结果=$success")
                        if (success && _isLibraryUnlocked.value) {
                            loadTokensInternal()
                        }
                        _backupStatus.value = if (success) "云端库自动同步完成" else "云端库自动同步失败"
                        return@withLock
                    }

                    _backupStatus.value = "未绑定云端 .kdbx，已跳过自动恢复"
                } catch (ex: Exception) {
                    Logger.e(SYNC_LOG_TAG, "自动恢复失败: ${ex.message}", ex)
                    _backupStatus.value = "自动恢复失败：${ex.message}"
                    updateCloudSyncState(
                        status = SYNC_STATUS_FAILED,
                        errorMessage = ex.message ?: "自动恢复失败"
                    )
                } finally {
                    _isRestoreInProgress.value = false
                    _restoreProgress.value = 0
                }
            }
        }
    }
    
    /**
     * 手动恢复令牌
     */
    fun manualRestoreTokens() {
        viewModelScope.launch {
            cloudSyncMutex.withLock {
                try {
                    Logger.d(SYNC_LOG_TAG, "开始手动恢复")
                    _isRestoreInProgress.value = true
                    _restoreProgress.value = 0
                    _backupStatus.value = "正在手动恢复..."
                    updateCloudSyncState(status = SYNC_STATUS_SYNCING)

                    val cloudLibrary = getCurrentCloudLibrary()
                    if (cloudLibrary != null) {
                        val success = downloadCurrentCloudLibrary()
                        Logger.d(SYNC_LOG_TAG, "手动恢复下载结果=$success")
                        if (success && _isLibraryUnlocked.value) {
                            loadTokensInternal()
                        }
                        _backupStatus.value = if (success) "云端库恢复成功" else "云端库恢复失败"
                        return@withLock
                    }

                    _backupStatus.value = "未绑定云端 .kdbx，无法手动恢复"
                } catch (ex: Exception) {
                    Logger.e(SYNC_LOG_TAG, "手动恢复失败: ${ex.message}", ex)
                    _backupStatus.value = "手动恢复失败：${ex.message}"
                    updateCloudSyncState(
                        status = SYNC_STATUS_FAILED,
                        errorMessage = ex.message ?: "手动恢复失败"
                    )
                } finally {
                    _isRestoreInProgress.value = false
                    _restoreProgress.value = 0
                }
            }
        }
    }
    
    /**
     * 备份令牌
     */
    fun backupTokens(force: Boolean = false) {
        viewModelScope.launch {
            cloudSyncMutex.withLock {
                try {
                    Logger.d(SYNC_LOG_TAG, "开始执行备份同步: force=$force")
                    val cloudLibrary = getCurrentCloudLibrary()
                    if (cloudLibrary == null && !force) {
                        Logger.d(SYNC_LOG_TAG, "备份同步跳过：未绑定云端库且 force=false")
                        return@withLock
                    }

                    _isBackupInProgress.value = true
                    _backupStatus.value = "正在备份..."
                    _backupProgress.value = 0
                    updateCloudSyncState(status = SYNC_STATUS_SYNCING)

                    if (cloudLibrary != null) {
                        val success = uploadCurrentCloudLibrary()
                        Logger.d(SYNC_LOG_TAG, "备份上传结果=$success")
                        _backupStatus.value = if (success) {
                            when (_currentLibrary.value?.lastSyncStatus) {
                                SYNC_STATUS_MERGED -> "云端库自动合并并同步成功"
                                else -> "云端库同步成功"
                            }
                        } else {
                            "云端库同步失败"
                        }
                        return@withLock
                    }

                    _backupStatus.value = "未绑定云端 .kdbx，无法同步备份"
                } catch (ex: Exception) {
                    Logger.e(SYNC_LOG_TAG, "备份同步失败: ${ex.message}", ex)
                    _backupStatus.value = "备份失败：${ex.message}"
                    updateCloudSyncState(
                        status = SYNC_STATUS_FAILED,
                        errorMessage = ex.message ?: "备份失败"
                    )
                } finally {
                    _isBackupInProgress.value = false
                    _backupProgress.value = 0
                }
            }
        }
    }
}