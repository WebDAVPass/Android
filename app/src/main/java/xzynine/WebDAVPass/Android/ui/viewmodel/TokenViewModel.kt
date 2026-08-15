package xzynine.WebDAVPass.Android.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import xzylib.base.util.Logger
import xzylib.base.util.ToastUtils
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import xzynine.WebDAVPass.Android.data.AppDatabaseHolder
import xzynine.WebDAVPass.Android.data.AppSetting
import xzynine.WebDAVPass.Android.data.DatabaseManager
import xzynine.WebDAVPass.Android.data.DatabaseSettingsInfo
import xzynine.WebDAVPass.Android.data.EntryHistoryInfo
import xzynine.WebDAVPass.Android.data.DuplicateEntryInfo
import xzynine.WebDAVPass.Android.data.DuplicateGroupInfo
import xzynine.WebDAVPass.Android.data.GroupNodeInfo
import xzynine.WebDAVPass.Android.data.LibraryContext
import xzynine.WebDAVPass.Android.data.KdbxTokenRepository
import xzynine.WebDAVPass.Android.data.OtpToken
import xzynine.WebDAVPass.Android.data.PasswordEntryEditDraft
import xzynine.WebDAVPass.Android.data.PasswordGroupEditDraft
import xzynine.WebDAVPass.Android.data.SecurityIssuesInfo
import xzynine.WebDAVPass.Android.data.TokenCode
import xzynine.WebDAVPass.Android.util.UniqueIdGenerator
import xzynine.WebDAVPass.Android.util.TokenCodeUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xzynine.WebDAVPass.Android.data.PasswordEntry
import xzynine.WebDAVPass.Android.ui.ViewModel.AutoUnlockViewModel
import xzynine.WebDAVPass.Android.ui.ViewModel.CloudSyncViewModel
import xzynine.WebDAVPass.Android.ui.ViewModel.LibraryViewModel
import xzynine.WebDAVPass.Android.ui.ViewModel.PasswordViewModel
import xzynine.WebDAVPass.Android.ui.ViewModel.WebDavConfigViewModel
import java.io.OutputStream
import javax.crypto.Cipher
import kotlin.time.Duration.Companion.milliseconds

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
        private const val SETTING_KEY_LOCK_TIMEOUT_MINUTES = "lock_timeout_minutes"
        private const val SETTING_KEY_LOCK_ON_BACKGROUND = "lock_on_background"
        private const val DEFAULT_LOCK_TIMEOUT_MINUTES = 5
        /** 截屏防护临时关闭后的自动恢复时长 */
        private const val SECURE_RECOVERY_DELAY_MS = 5 * 60_000L

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
    private var secureRestoreJob: Job? = null

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

    /** 应用超时锁定分钟数（0 表示不锁定），持久化于 app_settings，默认 5 分钟。 */
    private val _lockTimeoutMinutes = MutableStateFlow(DEFAULT_LOCK_TIMEOUT_MINUTES)
    val lockTimeoutMinutes: StateFlow<Int> = _lockTimeoutMinutes.asStateFlow()

    /** 后台自动锁定开关（退到后台 30 秒后锁定），默认开启。 */
    private val _lockOnBackground = MutableStateFlow(true)
    val lockOnBackground: StateFlow<Boolean> = _lockOnBackground.asStateFlow()

    /**
     * 截屏防护开关（仅内存状态，默认开启）。
     * 关闭后 5 分钟自动恢复开启；进程被杀/清理后台后重建必然回到开启态，保证安全兜底。
     */
    private val _isSecureRecentsEnabled = MutableStateFlow(true)
    val isSecureRecentsEnabled: StateFlow<Boolean> = _isSecureRecentsEnabled.asStateFlow()

    init {
        startTokenRefreshTimer()
        // 监听锁定状态：库一旦锁定，立即清空内存中的令牌与密码条目数据，
        // 确保任何页面在未解锁状态下都无法读到已解密内容（如安全性检查页防泄漏）。
        viewModelScope.launch {
            libraryViewModel.isLibraryUnlocked.collect { unlocked ->
                if (!unlocked) {
                    _tokens.value = emptyList()
                    _tokenCodes.clear()
                    publishTokenCodeSnapshot()
                    passwordViewModel.clearAll(resetTotalCount = false)
                }
            }
        }
        viewModelScope.launch {
            runCatching {
                AppDatabaseHolder.getInstance(context)
                    .appSettingsDao()
                    .getValue(SETTING_KEY_LOCK_TIMEOUT_MINUTES)
                    ?.value
                    ?.toIntOrNull()
            }.getOrNull()?.takeIf { it >= 0 }?.let { _lockTimeoutMinutes.value = it }

            runCatching {
                AppDatabaseHolder.getInstance(context)
                    .appSettingsDao()
                    .getValue(SETTING_KEY_LOCK_ON_BACKGROUND)
                    ?.value
            }.getOrNull()?.let { value ->
                _lockOnBackground.value = value != "false"
            }
        }
    }

    /**
     * 设置应用超时锁定分钟数（0 表示不锁定）。
     */
    fun setLockTimeoutMinutes(minutes: Int) {
        val safe = minutes.coerceAtLeast(0)
        _lockTimeoutMinutes.value = safe
        viewModelScope.launch {
            runCatching {
                AppDatabaseHolder.getInstance(context)
                    .appSettingsDao()
                    .put(
                        AppSetting(
                            SETTING_KEY_LOCK_TIMEOUT_MINUTES,
                            safe.toString()
                        )
                    )
            }.onFailure {
                Logger.e(SYNC_LOG_TAG, "保存锁定超时设置失败: ${it.message}", it)
            }
        }
    }

    /**
     * 设置截屏防护开关：关闭后 5 分钟自动恢复开启（期间可手动提前恢复）。
     */
    fun setSecureRecentsEnabled(enabled: Boolean) {
        secureRestoreJob?.cancel()
        secureRestoreJob = null
        _isSecureRecentsEnabled.value = enabled
        if (!enabled) {
            secureRestoreJob = viewModelScope.launch {
                delay(SECURE_RECOVERY_DELAY_MS.milliseconds)
                _isSecureRecentsEnabled.value = true
            }
        }
    }

    /**
     * 设置后台自动锁定开关。
     */
    fun setLockOnBackground(enabled: Boolean) {
        _lockOnBackground.value = enabled
        viewModelScope.launch {
            runCatching {
                AppDatabaseHolder.getInstance(context)
                    .appSettingsDao()
                    .put(
                        AppSetting(
                            SETTING_KEY_LOCK_ON_BACKGROUND,
                            enabled.toString()
                        )
                    )
            }.onFailure {
                Logger.e(SYNC_LOG_TAG, "保存后台锁定设置失败: ${it.message}", it)
            }
        }
    }

    /**
     * 解锁当前库。
     *
     * 仅校验主密码并进入解锁态，数据加载在 [viewModelScope] 中后台执行，
     * 不受调用方协程（如锁定页组合作用域）销毁的影响——冷启动锁定页解锁时
     * 解锁态变化会立即触发导航离开锁定页，若数据加载仍挂在调用方协程上，
     * 会被取消并误报"解锁失败"，同时留下密码/令牌列表为空的半解锁态。
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

        startUnlockDataLoading()
        return true
    }

    /**
     * 解锁成功后后台加载当前库数据（令牌与密码条目），并执行失败回落。
     *
     * 加载成功时按需触发云端自动恢复；重试耗尽仍失败且库仍处于解锁态时，
     * 回落至锁定态并提示，避免停留在数据为空的半解锁主界面。
     */
    private fun startUnlockDataLoading() {
        viewModelScope.launch {
            var loaded = false
            repeat(UNLOCK_LOAD_RETRY_COUNT) { attemptIndex ->
                loaded = loadTokensInternal()
                if (loaded) {
                    return@repeat
                }
                if (attemptIndex < UNLOCK_LOAD_RETRY_COUNT - 1) {
                    delay(UNLOCK_LOAD_RETRY_DELAY_MS.milliseconds)
                }
            }

            if (loaded) {
                if (libraryViewModel.shouldAutoSyncCurrentLibrary()) {
                    Logger.d(SYNC_LOG_TAG, "解锁成功，触发自动恢复")
                    cloudSyncViewModel.autoRestoreTokens(libraryViewModel, kdbxTokenRepository) {
                        loadTokensInternal()
                    }
                }
                return@launch
            }

            // 数据加载失败：若期间未被用户/超时重新锁定，则回落锁定并提示，
            // 主界面守卫会自动导航回锁定页，避免半解锁态。
            if (libraryViewModel.isLibraryUnlocked.value) {
                Logger.e(SYNC_LOG_TAG, "解锁后数据加载失败，回落至锁定态")
                libraryViewModel.lockCurrentLibrary()
                ToastUtils.showShortToast(context, "解锁后数据加载失败，已重新锁定，请重试")
            }
        }
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
    fun persistKdbxFromUri(uri: Uri): String? {
        return runCatching {
            takePersistableUriPermission(uri)
            uri.toString()
        }.getOrNull()
    }

    /**
     * 通过系统 CreateDocument 创建本地 kdbx 文件并就地使用。
     */
    fun createLocalKdbx(uri: Uri, masterPassword: String, keyFileData: ByteArray? = null): String? {
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
            if (currentLocalPath.isNullOrBlank()) {
                _tokens.value = emptyList()
                _tokenCodes.clear()
                return false
            }

            // 使用 Flow 版本加载，底层复用 DatabaseManager 中已缓存的数据库实例
            val loadedTokens = mutableListOf<OtpToken>()
            withContext(Dispatchers.IO) {
                kdbxTokenRepository.loadTokensFlow(currentLocalPath, masterPassword)
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
                localPath = currentLocalPath,
                masterPassword = masterPassword
            )
            passwordViewModel.reloadInitialPasswordData()
            true
        } catch (e: CancellationException) {
            // 协程取消（如调用方组合销毁）不应误报为数据加载失败，原样抛出
            throw e
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
    suspend fun loadPasswordEntryDetail(entryId: Long): PasswordEntry? {
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
     * 批量将条目图标固化为自定义图标（品牌图标写入密码库）。
     */
    suspend fun solidifyEntryBrandIcons(iconUpdates: Map<Long, ByteArray>): Int {
        val count = passwordViewModel.solidifyEntryBrandIcons(
            iconUpdates,
            libraryViewModel.isLibraryUnlocked.value,
            libraryViewModel.currentLibrary.value?.localPath,
            libraryViewModel.getMasterPasswordInternal()
        )
        if (count > 0) {
            onPasswordWriteSuccess()
        }
        return count
    }

    /**
     * 加载指定条目的合并摘要（标准字段 + 自定义字段 + 附件名）。
     */
    suspend fun loadEntryMergeInfos(entryIds: List<Long>): List<DuplicateEntryInfo> {
        return passwordViewModel.loadEntryMergeInfos(
            entryIds,
            libraryViewModel.isLibraryUnlocked.value,
            libraryViewModel.currentLibrary.value?.localPath,
            libraryViewModel.getMasterPasswordInternal()
        )
    }

    /**
     * 检测重复候选组（账号/标题/URL 三个维度命中 ≥2 个）。
     */
    suspend fun detectDuplicateGroups(entryIds: List<Long>): List<DuplicateGroupInfo> {
        return passwordViewModel.detectDuplicateGroups(
            entryIds,
            libraryViewModel.isLibraryUnlocked.value,
            libraryViewModel.currentLibrary.value?.localPath,
            libraryViewModel.getMasterPasswordInternal()
        )
    }

    /**
     * 合并一组重复条目（源条目移入回收站）。
     */
    suspend fun mergeEntryGroup(
        masterEntryId: Long,
        sourceEntryIds: List<Long>,
        fieldSelections: Map<String, Long>
    ): Int {
        val count = passwordViewModel.mergeEntryGroup(
            masterEntryId,
            sourceEntryIds,
            fieldSelections,
            libraryViewModel.isLibraryUnlocked.value,
            libraryViewModel.currentLibrary.value?.localPath,
            libraryViewModel.getMasterPasswordInternal()
        )
        if (count > 0) {
            onPasswordWriteSuccess()
        }
        return count
    }

    /**
     * 将条目附件以增量方式拷贝到指定输出流（供保存到本地文件，避免第二份全量内存拷贝）。
     */
    suspend fun copyEntryAttachmentTo(entryId: Long, name: String, output: OutputStream): Boolean {
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
     * 修改数据库安全设置（主密码 / 密钥文件 / KDF / 压缩）。
     *
     * @param oldPassword 用户输入的当前主密码（用于验证后再修改）
     * @return 是否成功；成功后内存中的主密码同步更新为新值
     */
    suspend fun changeDatabaseSettings(
        oldPassword: String,
        newMasterPassword: String,
        newKeyFileData: ByteArray? = null,
        kdfEngineName: String? = null,
        keyRounds: Long? = null,
        memoryUsage: Long? = null,
        parallelism: Long? = null,
        isCompressionEnabled: Boolean? = null
    ): Boolean {
        val localPath = libraryViewModel.currentLibrary.value?.localPath ?: return false
        val ok = withContext(Dispatchers.IO) {
            kdbxTokenRepository.changeDatabaseSettings(
                localPath = localPath,
                masterPassword = oldPassword,
                newMasterPassword = newMasterPassword,
                newKeyFileData = newKeyFileData,
                kdfEngineName = kdfEngineName,
                keyRounds = keyRounds,
                memoryUsage = memoryUsage,
                parallelism = parallelism,
                isCompressionEnabled = isCompressionEnabled
            )
        }
        if (ok) {
            libraryViewModel.updateMasterPasswordInternal(newMasterPassword)
            if (newKeyFileData != null) {
                DatabaseManager.setKeyFileData(newKeyFileData)
            }
            // 主密码或密钥文件变更后，旧的生物识别自动解锁凭据（用旧主密码加密）已失效，
            // 需清理以免下次用旧凭据解密失败；仅改 KDF/压缩时不涉及凭据，无需失效。
            val passwordChanged = newMasterPassword != oldPassword
            val keyFileChanged = newKeyFileData != null
            if ((passwordChanged || keyFileChanged)) {
                val lib = libraryViewModel.currentLibrary.value
                if (lib?.autoUnlockEnabled == true) {
                    autoUnlockViewModel.invalidateAutoUnlock(lib) { updated ->
                        libraryViewModel.persistLibraryMetadata(updated)
                    }
                }
            }
        }
        return ok
    }

    /**
     * 读取当前库的安全设置信息。
     */
    suspend fun loadDatabaseSettingsInfo(): DatabaseSettingsInfo? {
        val localPath = libraryViewModel.currentLibrary.value?.localPath ?: return null
        return withContext(Dispatchers.IO) {
            kdbxTokenRepository.loadDatabaseSettingsInfo(
                localPath,
                libraryViewModel.getMasterPasswordInternal()
            )
        }
    }

    /**
     * 将当前数据库导出到指定 Uri（CreateDocument 产物）。
     */
    suspend fun exportCurrentDatabase(uri: Uri): Boolean {
        val localPath = libraryViewModel.currentLibrary.value?.localPath ?: return false
        return withContext(Dispatchers.IO) {
            kdbxTokenRepository.exportDatabaseTo(
                localPath,
                libraryViewModel.getMasterPasswordInternal()
            ) {
                context.contentResolver.openOutputStream(uri, "wt")
            }
        }
    }

    /**
     * 将本地 .kdbx 文件合并进当前数据库。
     */
    suspend fun mergeLocalDatabase(uri: Uri, mergeMasterPassword: String): Boolean {
        val localPath = libraryViewModel.currentLibrary.value?.localPath ?: return false
        val ok = withContext(Dispatchers.IO) {
            kdbxTokenRepository.mergeLocalDatabaseFile(
                localPath,
                libraryViewModel.getMasterPasswordInternal(),
                uri.toString(),
                mergeMasterPassword
            )
        }
        if (ok) {
            onPasswordWriteSuccess()
        }
        return ok
    }

    /**
     * 安全性检查：已过期条目与弱密码条目。
     */
    suspend fun loadSecurityIssues(): SecurityIssuesInfo {
        val localPath = libraryViewModel.currentLibrary.value?.localPath ?: return SecurityIssuesInfo(emptyList(), emptyList())
        return withContext(Dispatchers.IO) {
            kdbxTokenRepository.loadSecurityIssues(localPath, libraryViewModel.getMasterPasswordInternal())
        }
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
                delay(1000.milliseconds)
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
