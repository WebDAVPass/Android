package xzynine.WebDAVPass.Android.ui.ViewModel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import xzynine.WebDAVPass.Android.data.EntryHistoryInfo
import xzynine.WebDAVPass.Android.data.GroupNodeInfo
import xzynine.WebDAVPass.Android.data.KdbxTokenRepository
import xzynine.WebDAVPass.Android.data.PasswordEntry
import xzynine.WebDAVPass.Android.data.PasswordEntryEditDraft
import xzynine.WebDAVPass.Android.data.PasswordGroupEditDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 密码视图模型
 *
 * 负责管理密码条目的加载、创建、更新和删除操作。
 */
class PasswordViewModel(private val context: Context) : ViewModel() {

    companion object {
        private const val SYNC_LOG_TAG = "同步"
    }

    private val kdbxTokenRepository: KdbxTokenRepository = KdbxTokenRepository(context)

    private val passwordListModeSubViewModel: PasswordListModeSubViewModel by lazy {
        PasswordListModeSubViewModel(
            repository = kdbxTokenRepository,
            scope = viewModelScope,
            accessProvider = { PasswordDataAccess(false, null, "") }
        )
    }

    private val passwordSubViewModel: PasswordPagingSubViewModel by lazy {
        PasswordPagingSubViewModel(
            repository = kdbxTokenRepository,
            scope = viewModelScope,
            accessProvider = { PasswordDataAccess(false, null, "") },
            listModeProvider = { passwordListModeSubViewModel.passwordListMode.value }
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

    /**
     * 更新数据访问提供者
     */
    fun updateAccessProvider(
        isLibraryUnlocked: Boolean,
        localPath: String?,
        masterPassword: String
    ) {
        passwordListModeSubViewModel.updateAccessProvider {
            PasswordDataAccess(isLibraryUnlocked, localPath, masterPassword)
        }
        passwordSubViewModel.updateAccessProvider {
            PasswordDataAccess(isLibraryUnlocked, localPath, masterPassword)
        }
    }

    /**
     * 刷新密码条目与键值列表。
     *
     * 排序/过滤参数为 null 时沿用 ViewModel 当前持有值，避免分组导航与写入刷新时重置用户设置。
     */
    fun refreshPasswordEntries(
        searchQuery: String = "",
        caseSensitive: Boolean? = null,
        sortMode: PasswordSortMode? = null,
        ascending: Boolean? = null,
        hideExpired: Boolean? = null
    ) {
        passwordSubViewModel.refreshPasswordEntries(searchQuery, caseSensitive, sortMode, ascending, hideExpired)
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
     * 重新加载初始密码数据
     */
    suspend fun reloadInitialPasswordData() {
        passwordSubViewModel.reloadInitialPasswordData()
        passwordListModeSubViewModel.refreshRecentDeletedCount()
    }

    /**
     * 重置所有状态
     */
    fun resetAllState() {
        passwordListModeSubViewModel.resetAllState()
        viewModelScope.launch {
            passwordSubViewModel.clearAll(resetTotalCount = true)
        }
    }

    /**
     * 清除所有数据
     */
    suspend fun clearAll(resetTotalCount: Boolean) {
        passwordSubViewModel.clearAll(resetTotalCount)
        passwordListModeSubViewModel.clearRecentDeletedCount()
    }

    /**
     * 单条读写与批量操作共享的前置检查：构造 [PasswordDataAccess] 校验就绪状态，
     * 解出 localPath，并在 IO 调度器上执行 [block]。未就绪或路径为空时返回 [fallback]。
     */
    private suspend inline fun <T> withAccess(
        isLibraryUnlocked: Boolean,
        localPath: String?,
        masterPassword: String,
        fallback: T,
        crossinline block: (String, String) -> T
    ): T {
        val access = PasswordDataAccess(isLibraryUnlocked, localPath, masterPassword)
        val path = access.localPath?.takeIf { access.isReady() } ?: return fallback
        return withContext(Dispatchers.IO) { block(path, access.masterPassword) }
    }

    /**
     * 按稳定 ID 读取单条密码详情。
     */
    suspend fun loadPasswordEntryDetail(
        entryId: Long,
        isLibraryUnlocked: Boolean,
        localPath: String?,
        masterPassword: String
    ): PasswordEntry? {
        if (entryId < 0) {
            return null
        }
        return withAccess(isLibraryUnlocked, localPath, masterPassword, fallback = null) { path, pwd ->
            kdbxTokenRepository.loadPasswordEntryById(path, pwd, entryId)
        }
    }

    /**
     * 按稳定 ID 读取条目编辑草稿。
     */
    suspend fun loadPasswordEntryDraft(
        entryId: Long,
        isLibraryUnlocked: Boolean,
        localPath: String?,
        masterPassword: String
    ): PasswordEntryEditDraft? {
        if (entryId < 0) {
            return null
        }
        return withAccess(isLibraryUnlocked, localPath, masterPassword, fallback = null) { path, pwd ->
            kdbxTokenRepository.loadPasswordEntryDraft(path, pwd, entryId)
        }
    }

    /**
     * 按稳定 ID 读取条目历史版本摘要列表。
     */
    suspend fun loadEntryHistory(
        entryId: Long,
        isLibraryUnlocked: Boolean,
        localPath: String?,
        masterPassword: String
    ): List<EntryHistoryInfo> {
        if (entryId < 0) {
            return emptyList()
        }
        return withAccess(isLibraryUnlocked, localPath, masterPassword, fallback = emptyList()) { path, pwd ->
            kdbxTokenRepository.loadEntryHistory(path, pwd, entryId)
        }
    }

    /**
     * 将指定历史版本恢复为条目当前内容。
     */
    suspend fun restoreEntryFromHistory(
        entryId: Long,
        historyIndex: Int,
        isLibraryUnlocked: Boolean,
        localPath: String?,
        masterPassword: String
    ): Boolean {
        if (entryId < 0) {
            return false
        }
        return withAccess(isLibraryUnlocked, localPath, masterPassword, fallback = false) { path, pwd ->
            kdbxTokenRepository.restoreEntryFromHistory(path, pwd, entryId, historyIndex)
        }
    }

    /**
     * 按稳定 ID 读取分组编辑草稿。
     */
    suspend fun loadPasswordGroupDraft(
        groupId: Long,
        isLibraryUnlocked: Boolean,
        localPath: String?,
        masterPassword: String
    ): PasswordGroupEditDraft? {
        if (groupId >= 0) {
            return null
        }
        return withAccess(isLibraryUnlocked, localPath, masterPassword, fallback = null) { path, pwd ->
            kdbxTokenRepository.loadPasswordGroupDraft(path, pwd, groupId)
        }
    }

    /**
     * 新建密码条目并返回稳定 ID。
     */
    suspend fun createPasswordEntry(
        draft: PasswordEntryEditDraft,
        isLibraryUnlocked: Boolean,
        localPath: String?,
        masterPassword: String
    ): Long? {
        return withAccess(isLibraryUnlocked, localPath, masterPassword, fallback = null) { path, pwd ->
            kdbxTokenRepository.createPasswordEntry(path, pwd, draft)
        }
    }

    /**
     * 更新密码条目。
     */
    suspend fun updatePasswordEntry(
        draft: PasswordEntryEditDraft,
        isLibraryUnlocked: Boolean,
        localPath: String?,
        masterPassword: String
    ): Boolean {
        return withAccess(isLibraryUnlocked, localPath, masterPassword, fallback = false) { path, pwd ->
            kdbxTokenRepository.updatePasswordEntry(path, pwd, draft)
        }
    }

    /**
     * 删除密码条目（进入回收站）。
     */
    suspend fun deletePasswordEntry(
        entryId: Long,
        isLibraryUnlocked: Boolean,
        localPath: String?,
        masterPassword: String
    ): Boolean {
        if (entryId < 0) {
            return false
        }
        return withAccess(isLibraryUnlocked, localPath, masterPassword, fallback = false) { path, pwd ->
            kdbxTokenRepository.deletePasswordEntry(path, pwd, entryId)
        }
    }

    /**
     * 新建密码分组并返回稳定 ID。
     */
    suspend fun createPasswordGroup(
        draft: PasswordGroupEditDraft,
        isLibraryUnlocked: Boolean,
        localPath: String?,
        masterPassword: String
    ): Long? {
        return withAccess(isLibraryUnlocked, localPath, masterPassword, fallback = null) { path, pwd ->
            kdbxTokenRepository.createPasswordGroup(path, pwd, draft)
        }
    }

    /**
     * 更新密码分组。
     */
    suspend fun updatePasswordGroup(
        draft: PasswordGroupEditDraft,
        isLibraryUnlocked: Boolean,
        localPath: String?,
        masterPassword: String
    ): Boolean {
        return withAccess(isLibraryUnlocked, localPath, masterPassword, fallback = false) { path, pwd ->
            kdbxTokenRepository.updatePasswordGroup(path, pwd, draft)
        }
    }

    /**
     * 删除密码分组（进入回收站）。
     */
    suspend fun deletePasswordGroup(
        groupId: Long,
        isLibraryUnlocked: Boolean,
        localPath: String?,
        masterPassword: String
    ): Boolean {
        if (groupId >= 0) {
            return false
        }
        return withAccess(isLibraryUnlocked, localPath, masterPassword, fallback = false) { path, pwd ->
            kdbxTokenRepository.deletePasswordGroup(path, pwd, groupId)
        }
    }

    /**
     * 从回收站批量恢复条目。
     */
    suspend fun restoreRecentDeletedPasswordEntries(
        entryIds: List<Long>,
        isLibraryUnlocked: Boolean,
        localPath: String?,
        masterPassword: String
    ): Int {
        if (entryIds.isEmpty()) {
            return 0
        }
        return withAccess(isLibraryUnlocked, localPath, masterPassword, fallback = 0) { path, pwd ->
            kdbxTokenRepository.restoreRecentDeletedPasswordEntries(path, pwd, entryIds)
        }
    }

    /**
     * 从回收站批量永久删除条目。
     */
    suspend fun permanentlyDeleteRecentDeletedPasswordEntries(
        entryIds: List<Long>,
        isLibraryUnlocked: Boolean,
        localPath: String?,
        masterPassword: String
    ): Int {
        if (entryIds.isEmpty()) {
            return 0
        }
        return withAccess(isLibraryUnlocked, localPath, masterPassword, fallback = 0) { path, pwd ->
            kdbxTokenRepository.permanentlyDeleteRecentDeletedPasswordEntries(path, pwd, entryIds)
        }
    }

    /**
     * 加载全部分组树（不含回收站）。
     */
    suspend fun loadAllPasswordGroups(
        isLibraryUnlocked: Boolean,
        localPath: String?,
        masterPassword: String
    ): List<GroupNodeInfo> {
        return withAccess(isLibraryUnlocked, localPath, masterPassword, fallback = emptyList()) { path, pwd ->
            kdbxTokenRepository.loadAllPasswordGroups(path, pwd)
        }
    }

    /**
     * 批量移动条目/分组到目标分组。
     */
    suspend fun movePasswordTargets(
        entryIds: List<Long>,
        groupIds: List<Long>,
        targetGroupId: Long?,
        isLibraryUnlocked: Boolean,
        localPath: String?,
        masterPassword: String
    ): Int {
        if (entryIds.isEmpty() && groupIds.isEmpty()) {
            return 0
        }
        return withAccess(isLibraryUnlocked, localPath, masterPassword, fallback = 0) { path, pwd ->
            kdbxTokenRepository.movePasswordTargets(path, pwd, entryIds, groupIds, targetGroupId)
        }
    }

    /**
     * 批量复制条目/分组到目标分组。
     */
    suspend fun copyPasswordTargets(
        entryIds: List<Long>,
        groupIds: List<Long>,
        targetGroupId: Long?,
        isLibraryUnlocked: Boolean,
        localPath: String?,
        masterPassword: String
    ): Int {
        if (entryIds.isEmpty() && groupIds.isEmpty()) {
            return 0
        }
        return withAccess(isLibraryUnlocked, localPath, masterPassword, fallback = 0) { path, pwd ->
            kdbxTokenRepository.copyPasswordTargets(path, pwd, entryIds, groupIds, targetGroupId)
        }
    }
}
