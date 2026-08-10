package xzynine.WebDAVPass.Android.ui.ViewModel

import android.content.Context
import android.content.Intent
import android.net.Uri
import xzylib.base.util.Logger
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import xzynine.WebDAVPass.Android.data.DatabaseManager
import xzynine.WebDAVPass.Android.data.EntryHistoryInfo
import xzynine.WebDAVPass.Android.data.GroupNodeInfo
import xzynine.WebDAVPass.Android.data.LibraryContext
import xzynine.WebDAVPass.Android.data.KdbxTokenRepository
import xzynine.WebDAVPass.Android.data.OtpToken
import xzynine.WebDAVPass.Android.data.PasswordEntryEditDraft
import xzynine.WebDAVPass.Android.data.PasswordGroupEditDraft
import xzynine.WebDAVPass.Android.data.TokenCode
import xzynine.WebDAVPass.Android.util.UniqueIdGenerator
import xzynine.WebDAVPass.Android.util.TokenCodeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.crypto.Cipher

/**
 * 令牌视图模型
 *
 * 持有令牌相关状态（OTP 列表、验证码快照等），并协调跨模块操作（解锁后刷新、写入后备份等）。
 */
class TokenViewModel(private val context: Context) : ViewModel() {

    companion object {
        private const val UNLOCK_LOAD_RETRY_COUNT = 3
        private const val UNLOCK_LOAD_RETRY_DELAY_MS = 250L
        private const val SYNC_LOG_TAG = "同步"

        @Volatile
        private var SHARED_VIEW_MODEL: TokenViewModel? = null

        /**
         * 获取跨 Activity 共享的令牌视图模型实例
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

    private val kdbxTokenRepository: KdbxTokenRepository = KdbxTokenRepository(context)
    private val tokenCodeUtil: TokenCodeUtil = TokenCodeUtil()
    private var tokenRefreshJob: Job? = null

    val libraryViewModel: LibraryViewModel = LibraryViewModel(context)
    val autoUnlockViewModel: AutoUnlockViewModel = AutoUnlockViewModel(context)
    val cloudSyncViewModel: CloudSyncViewModel = CloudSyncViewModel(context)
    val webDavConfigViewModel: WebDavConfigViewModel = WebDavConfigViewModel(context)
    val passwordViewModel: PasswordViewModel = PasswordViewModel(context)

    private val _tokens = MutableStateFlow<List<OtpToken>>(emptyList())
    val tokens: StateFlow<List<OtpToken>> = _tokens.asStateFlow()

    private val _tokenCodes = mutableMapOf<Long, MutableStateFlow<TokenCode?>>()
    private val _tokenCodeSnapshot = MutableStateFlow<Map<Long, TokenCode?>>(emptyMap())
    val tokenCodeSnapshot: StateFlow<Map<Long, TokenCode?>> = _tokenCodeSnapshot.asStateFlow()

    private val _currentTimeMillis = MutableStateFlow(System.currentTimeMillis())
    val currentTimeMillis: StateFlow<Long> = _currentTimeMillis.asStateFlow()

    init {
        startTokenRefreshTimer()
    }

    /**
     * 添加或更新历史库并选中
     */
    fun upsertAndSelectLibrary(libraryContext: LibraryContext) {
        libraryViewModel.upsertAndSelectLibrary(libraryContext)
        _tokens.value = emptyList()
        _tokenCodes.clear()
        publishTokenCodeSnapshot()
        passwordViewModel.resetAllState()
    }

    /**
     * 按ID选中历史库
     */
    fun selectLibraryById(libraryId: String) {
        libraryViewModel.selectLibraryById(libraryId)
        _tokens.value = emptyList()
        _tokenCodes.clear()
        publishTokenCodeSnapshot()
        passwordViewModel.resetAllState()
    }

    /**
     * 清空当前库选择
     */
    fun clearCurrentLibrarySelection() {
        libraryViewModel.clearCurrentLibrarySelection()
        _tokens.value = emptyList()
        _tokenCodes.clear()
        publishTokenCodeSnapshot()
        passwordViewModel.resetAllState()
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
    fun removeLibraryHistoryByIds(libraryIds: Collection<String>): Int {
        return libraryViewModel.removeLibraryHistoryByIds(libraryIds) { id ->
            autoUnlockViewModel.deleteKey(id)
        }
    }

    /**
     * 解锁当前库
     */
    suspend fun unlockCurrentLibrary(
        masterPassword: String,
        isManualUnlock: Boolean = true,
        keyFileData: ByteArray? = null
    ): Boolean {
        val ok = libraryViewModel.unlockCurrentLibrary(kdbxTokenRepository, masterPassword, isManualUnlock, keyFileData)
        if (!ok) {
            return false
        }

        repeat(UNLOCK_LOAD_RETRY_COUNT) { attemptIndex ->
            val loaded = loadTokensInternal()
            if (loaded) {
                if (libraryViewModel.shouldAutoSyncCurrentLibrary()) {
                    Logger.d(SYNC_LOG_TAG, "解锁成功，触发自动恢复")
                    cloudSyncViewModel.autoRestoreTokens(libraryViewModel, kdbxTokenRepository) {
                        loadTokensInternal()
                    }
                }
                return true
            }

            if (attemptIndex < UNLOCK_LOAD_RETRY_COUNT - 1) {
                delay(UNLOCK_LOAD_RETRY_DELAY_MS)
            }
        }

        return false
    }

    /**
     * 仅校验当前库主密码，不进入解锁态。
     */
    suspend fun verifyCurrentLibraryPassword(
        masterPassword: String,
        updateManualTimestamp: Boolean = false,
        keyFileData: ByteArray? = null
    ): Boolean {
        return libraryViewModel.verifyCurrentLibraryPassword(
            kdbxTokenRepository,
            masterPassword,
            updateManualTimestamp,
            keyFileData
        )
    }

    /**
     * 凭据自动解锁成功后的策略收尾。
     */
    fun applyPostCredentialUnlockPolicy(library: LibraryContext): Boolean {
        return autoUnlockViewModel.applyPostCredentialUnlockPolicy(
            library,
            { libraryViewModel.resolveLibrarySnapshot(it) }
        ) {
            autoUnlockViewModel.invalidateAutoUnlock(it) { lib ->
                libraryViewModel.persistLibraryMetadata(lib)
            }
        }
    }

    /**
     * 使用生物识别解密并解锁库。
     */
    suspend fun unlockWithBiometric(library: LibraryContext, cipher: Cipher): Boolean {
        return autoUnlockViewModel.unlockWithBiometric(library, cipher, kdbxTokenRepository) { password, isManual ->
            unlockCurrentLibrary(password, isManual)
        }
    }

    /**
     * 将 Uri 指向的 kdbx 文件登记为本地库
     */
    suspend fun persistKdbxFromUri(uri: Uri): String? {
        return runCatching {
            takePersistableUriPermission(uri)
            uri.toString()
        }.getOrNull()
    }

    /**
     * 通过系统 CreateDocument 创建本地 kdbx 文件并就地使用。
     */
    suspend fun createLocalKdbx(uri: Uri, masterPassword: String, keyFileData: ByteArray? = null): String? {
        return runCatching {
            val kdbxBytes = kdbxTokenRepository.createDatabaseBytes(masterPassword, keyFileData)
            context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                out.write(kdbxBytes)
            } ?: return null

            takePersistableUriPermission(uri)
            uri.toString()
        }.getOrNull()
    }

    fun createEmptyKdbxBytes(masterPassword: String, keyFileData: ByteArray? = null): ByteArray {
        return kdbxTokenRepository.createDatabaseBytes(masterPassword, keyFileData)
    }

    /**
     * 同步加载当前库令牌。
     */
    private suspend fun loadTokensInternal(): Boolean {
        libraryViewModel.setLoading(true)
        val currentLocalPath = libraryViewModel.currentLibrary.value?.localPath
        val masterPassword = libraryViewModel.getMasterPasswordInternal()
        return try {
            val localPath = currentLocalPath
            if (localPath.isNullOrBlank()) {
                _tokens.value = emptyList()
                _tokenCodes.clear()
                return false
            }

            // 使用 Flow 版本加载，底层复用 DatabaseManager 中已缓存的数据库实例
            val loadedTokens = mutableListOf<OtpToken>()
            withContext(Dispatchers.IO) {
                kdbxTokenRepository.loadTokensFlow(localPath, masterPassword)
                    .collect { token -> loadedTokens.add(token) }
            }
            _tokens.value = loadedTokens

            loadedTokens.forEach {
                if (!_tokenCodes.containsKey(it.id)) {
                    _tokenCodes[it.id] = MutableStateFlow(tokenCodeUtil.generateTokenCode(it))
                }
            }
            _tokenCodes.keys.retainAll(loadedTokens.map { it.id }.toSet())
            publishTokenCodeSnapshot()

            passwordViewModel.updateAccessProvider(
                isLibraryUnlocked = true,
                localPath = localPath,
                masterPassword = masterPassword
            )
            passwordViewModel.reloadInitialPasswordData()
            true
        } catch (ex: Exception) {
            _tokens.value = emptyList()
            passwordViewModel.clearAll(resetTotalCount = true)
            _tokenCodes.clear()
            publishTokenCodeSnapshot()
            Logger.e(SYNC_LOG_TAG, "加载当前库失败: ${ex.message}", ex)
            false
        } finally {
            libraryViewModel.setLoading(false)
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
     * 按稳定 ID 读取单条密码详情。
     */
    suspend fun loadPasswordEntryDetail(entryId: Long): xzynine.WebDAVPass.Android.data.PasswordEntry? {
        return passwordViewModel.loadPasswordEntryDetail(
            entryId,
            libraryViewModel.isLibraryUnlocked.value,
            libraryViewModel.currentLibrary.value?.localPath,
            libraryViewModel.getMasterPasswordInternal()
        )
    }

    /**
     * 按稳定 ID 读取条目编辑草稿。
     */
    suspend fun loadPasswordEntryDraft(entryId: Long): PasswordEntryEditDraft? {
        return passwordViewModel.loadPasswordEntryDraft(
            entryId,
            libraryViewModel.isLibraryUnlocked.value,
            libraryViewModel.currentLibrary.value?.localPath,
            libraryViewModel.getMasterPasswordInternal()
        )
    }

    /**
     * 读取条目的历史版本摘要列表。
     */
    suspend fun loadEntryHistory(entryId: Long): List<EntryHistoryInfo> {
        return passwordViewModel.loadEntryHistory(
            entryId,
            libraryViewModel.isLibraryUnlocked.value,
            libraryViewModel.currentLibrary.value?.localPath,
            libraryViewModel.getMasterPasswordInternal()
        )
    }

    /**
     * 将指定历史版本恢复为条目当前内容。
     */
    suspend fun restoreEntryFromHistory(entryId: Long, historyIndex: Int): Boolean {
        val restored = passwordViewModel.restoreEntryFromHistory(
            entryId,
            historyIndex,
            libraryViewModel.isLibraryUnlocked.value,
            libraryViewModel.currentLibrary.value?.localPath,
            libraryViewModel.getMasterPasswordInternal()
        )
        if (restored) {
            onPasswordWriteSuccess()
        }
        return restored
    }

    /**
     * 按稳定 ID 读取分组编辑草稿。
     */
    suspend fun loadPasswordGroupDraft(groupId: Long): PasswordGroupEditDraft? {
        return passwordViewModel.loadPasswordGroupDraft(
            groupId,
            libraryViewModel.isLibraryUnlocked.value,
            libraryViewModel.currentLibrary.value?.localPath,
            libraryViewModel.getMasterPasswordInternal()
        )
    }

    /**
     * 新建密码条目并返回稳定 ID。
     */
    suspend fun createPasswordEntry(draft: PasswordEntryEditDraft): Long? {
        val createdId = passwordViewModel.createPasswordEntry(
            draft,
            libraryViewModel.isLibraryUnlocked.value,
            libraryViewModel.currentLibrary.value?.localPath,
            libraryViewModel.getMasterPasswordInternal()
        )
        if (createdId != null) {
            onPasswordWriteSuccess()
        }
        return createdId
    }

    /**
     * 更新密码条目。
     */
    suspend fun updatePasswordEntry(draft: PasswordEntryEditDraft): Boolean {
        val updated = passwordViewModel.updatePasswordEntry(
            draft,
            libraryViewModel.isLibraryUnlocked.value,
            libraryViewModel.currentLibrary.value?.localPath,
            libraryViewModel.getMasterPasswordInternal()
        )
        if (updated) {
            onPasswordWriteSuccess()
        }
        return updated
    }

    /**
     * 读取条目附件的字节内容。
     */
    suspend fun getEntryAttachmentBytes(entryId: Long, name: String): ByteArray? {
        val localPath = libraryViewModel.currentLibrary.value?.localPath ?: return null
        val masterPassword = libraryViewModel.getMasterPasswordInternal() ?: return null
        return withContext(Dispatchers.IO) {
            kdbxTokenRepository.getEntryAttachmentBytes(localPath, masterPassword, entryId, name)
        }
    }

    /**
     * 将条目附件以增量方式拷贝到指定输出流（供保存到本地文件，避免第二份全量内存拷贝）。
     */
    suspend fun copyEntryAttachmentTo(entryId: Long, name: String, output: java.io.OutputStream): Boolean {
        val localPath = libraryViewModel.currentLibrary.value?.localPath ?: return false
        val masterPassword = libraryViewModel.getMasterPasswordInternal() ?: return false
        return withContext(Dispatchers.IO) {
            kdbxTokenRepository.copyEntryAttachmentTo(localPath, masterPassword, entryId, name, output)
        }
    }

    /**
     * 删除密码条目（进入回收站）。
     */
    suspend fun deletePasswordEntry(entryId: Long): Boolean {
        val deleted = passwordViewModel.deletePasswordEntry(
            entryId,
            libraryViewModel.isLibraryUnlocked.value,
            libraryViewModel.currentLibrary.value?.localPath,
            libraryViewModel.getMasterPasswordInternal()
        )
        if (deleted) {
            onPasswordWriteSuccess()
        }
        return deleted
    }

    /**
     * 新建密码分组并返回稳定 ID。
     */
    suspend fun createPasswordGroup(draft: PasswordGroupEditDraft): Long? {
        val createdId = passwordViewModel.createPasswordGroup(
            draft,
            libraryViewModel.isLibraryUnlocked.value,
            libraryViewModel.currentLibrary.value?.localPath,
            libraryViewModel.getMasterPasswordInternal()
        )
        if (createdId != null) {
            onPasswordWriteSuccess()
        }
        return createdId
    }

    /**
     * 更新密码分组。
     */
    suspend fun updatePasswordGroup(draft: PasswordGroupEditDraft): Boolean {
        val updated = passwordViewModel.updatePasswordGroup(
            draft,
            libraryViewModel.isLibraryUnlocked.value,
            libraryViewModel.currentLibrary.value?.localPath,
            libraryViewModel.getMasterPasswordInternal()
        )
        if (updated) {
            onPasswordWriteSuccess()
        }
        return updated
    }

    /**
     * 删除密码分组（进入回收站）。
     */
    suspend fun deletePasswordGroup(groupId: Long): Boolean {
        val deleted = passwordViewModel.deletePasswordGroup(
            groupId,
            libraryViewModel.isLibraryUnlocked.value,
            libraryViewModel.currentLibrary.value?.localPath,
            libraryViewModel.getMasterPasswordInternal()
        )
        if (deleted) {
            onPasswordWriteSuccess()
        }
        return deleted
    }

    /**
     * 从回收站批量恢复条目。
     */
    suspend fun restoreRecentDeletedPasswordEntries(entryIds: List<Long>): Int {
        val restored = passwordViewModel.restoreRecentDeletedPasswordEntries(
            entryIds,
            libraryViewModel.isLibraryUnlocked.value,
            libraryViewModel.currentLibrary.value?.localPath,
            libraryViewModel.getMasterPasswordInternal()
        )
        if (restored > 0) {
            onPasswordWriteSuccess()
        }
        return restored
    }

    /**
     * 从回收站批量永久删除条目。
     */
    suspend fun permanentlyDeleteRecentDeletedPasswordEntries(entryIds: List<Long>): Int {
        val deleted = passwordViewModel.permanentlyDeleteRecentDeletedPasswordEntries(
            entryIds,
            libraryViewModel.isLibraryUnlocked.value,
            libraryViewModel.currentLibrary.value?.localPath,
            libraryViewModel.getMasterPasswordInternal()
        )
        if (deleted > 0) {
            onPasswordWriteSuccess()
        }
        return deleted
    }

    /**
     * 加载全部分组树（不含回收站），用于移动/复制的目标分组选择。
     */
    suspend fun loadAllPasswordGroups(): List<GroupNodeInfo> {
        return passwordViewModel.loadAllPasswordGroups(
            libraryViewModel.isLibraryUnlocked.value,
            libraryViewModel.currentLibrary.value?.localPath,
            libraryViewModel.getMasterPasswordInternal()
        )
    }

    /**
     * 批量移动条目/分组到目标分组。
     */
    suspend fun movePasswordTargets(entryIds: List<Long>, groupIds: List<Long>, targetGroupId: Long?): Int {
        val moved = passwordViewModel.movePasswordTargets(
            entryIds,
            groupIds,
            targetGroupId,
            libraryViewModel.isLibraryUnlocked.value,
            libraryViewModel.currentLibrary.value?.localPath,
            libraryViewModel.getMasterPasswordInternal()
        )
        if (moved > 0) {
            onPasswordWriteSuccess()
        }
        return moved
    }

    /**
     * 批量复制条目/分组到目标分组。
     */
    suspend fun copyPasswordTargets(entryIds: List<Long>, groupIds: List<Long>, targetGroupId: Long?): Int {
        val copied = passwordViewModel.copyPasswordTargets(
            entryIds,
            groupIds,
            targetGroupId,
            libraryViewModel.isLibraryUnlocked.value,
            libraryViewModel.currentLibrary.value?.localPath,
            libraryViewModel.getMasterPasswordInternal()
        )
        if (copied > 0) {
            onPasswordWriteSuccess()
        }
        return copied
    }

    /**
     * 写入成功后的统一刷新链路。
     */
    private fun onPasswordWriteSuccess() {
        viewModelScope.launch {
            loadTokensInternal()
        }
        passwordViewModel.refreshPasswordEntries()
        passwordViewModel.refreshRecentDeletedCount()
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
                delay(1000)
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
     * 发布当前令牌验证码快照
     */
    private fun publishTokenCodeSnapshot() {
        _tokenCodeSnapshot.value = _tokenCodes.mapValues { it.value.value }
    }

    /**
     * 添加新令牌
     */
    suspend fun addToken(token: OtpToken): Boolean {
        val localPath = libraryViewModel.currentLibrary.value?.localPath ?: return false
        val masterPassword = libraryViewModel.getMasterPasswordInternal()

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
                masterPassword,
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
            kdbxTokenRepository.addToken(localPath, masterPassword, tokenWithId)
        }
        if (added) {
            viewModelScope.launch {
                loadTokensInternal()
            }
            backupTokens()
        }
        return added
    }

    /**
     * 判断令牌是否重复
     */
    suspend fun isTokenDuplicate(secret: String, algorithm: String, digits: Int, period: Int): Boolean {
        val localPath = libraryViewModel.currentLibrary.value?.localPath ?: return false
        val masterPassword = libraryViewModel.getMasterPasswordInternal()
        return withContext(Dispatchers.IO) {
            kdbxTokenRepository.isDuplicate(
                localPath,
                masterPassword,
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
            val localPath = libraryViewModel.currentLibrary.value?.localPath ?: return@launch
            val masterPassword = libraryViewModel.getMasterPasswordInternal()
            val deleted = withContext(Dispatchers.IO) {
                kdbxTokenRepository.deleteToken(localPath, masterPassword, tokenId)
            }
            if (deleted) {
                _tokenCodes.remove(tokenId)
                publishTokenCodeSnapshot()
                viewModelScope.launch {
                    loadTokensInternal()
                }
                passwordViewModel.refreshRecentDeletedCount()
                backupTokens()
            }
        }
    }

    /**
     * 更新令牌
     */
    fun updateToken(token: OtpToken) {
        viewModelScope.launch {
            val localPath = libraryViewModel.currentLibrary.value?.localPath ?: return@launch
            val masterPassword = libraryViewModel.getMasterPasswordInternal()
            val updated = withContext(Dispatchers.IO) {
                kdbxTokenRepository.updateToken(localPath, masterPassword, token)
            }
            if (updated) {
                _tokenCodes[token.id]?.value = tokenCodeUtil.generateTokenCode(token)
                publishTokenCodeSnapshot()
                viewModelScope.launch {
                    loadTokensInternal()
                }
                backupTokens()
            }
        }
    }

    /**
     * 递增HOTP计数器
     */
    fun incrementCounter(tokenId: Long) {
        viewModelScope.launch {
            val localPath = libraryViewModel.currentLibrary.value?.localPath ?: return@launch
            val masterPassword = libraryViewModel.getMasterPasswordInternal()
            val incremented = withContext(Dispatchers.IO) {
                kdbxTokenRepository.incrementCounter(localPath, masterPassword, tokenId)
            }
            if (incremented) {
                viewModelScope.launch {
                    loadTokensInternal()
                }
            }
        }
    }

    /**
     * 手动恢复令牌
     */
    fun manualRestoreTokens() {
        cloudSyncViewModel.manualRestoreTokens(libraryViewModel, kdbxTokenRepository) {
            loadTokensInternal()
        }
    }

    /**
     * 备份令牌
     */
    fun backupTokens(force: Boolean = false) {
        cloudSyncViewModel.backupTokens(
            libraryViewModel,
            kdbxTokenRepository,
            libraryViewModel.getMasterPasswordInternal(),
            force
        )
    }

    /**
     * ViewModel 销毁时关闭缓存的数据库实例，释放内存中的解密数据。
     *
     * 说明：TokenViewModel 以共享单例形式存在，此方法在最后一个持有者销毁时被调用。
     */
    override fun onCleared() {
        super.onCleared()
        DatabaseManager.close()
    }

    /**
     * 申请并持久化 Uri 读写权限。
     */
    private fun takePersistableUriPermission(uri: Uri) {
        if (!uri.scheme.equals("content", ignoreCase = true)) {
            return
        }

        val resolver = context.contentResolver
        runCatching {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
    }
}
