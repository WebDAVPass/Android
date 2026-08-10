package xzynine.WebDAVPass.Android.ui.ViewModel

import android.icu.text.Transliterator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import xzynine.WebDAVPass.Android.data.KdbxTokenRepository
import xzynine.WebDAVPass.Android.data.PasswordEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class PasswordDataAccess(
    val isLibraryUnlocked: Boolean,
    val localPath: String?,
    val masterPassword: String
) {
    fun isReady(): Boolean {
        return isLibraryUnlocked && !localPath.isNullOrBlank() && masterPassword.isNotBlank()
    }
}

internal const val PasswordFolderIndexLabel = "文件夹"

/**
 * 密码列表排序方式。
 */
enum class PasswordSortMode {
    DEFAULT, TITLE, ACCOUNT, MODIFIED_TIME, CREATED_TIME
}

internal class PasswordPagingSubViewModel(
    private val repository: KdbxTokenRepository,
    private val scope: CoroutineScope,
    private var accessProvider: () -> PasswordDataAccess,
    private val listModeProvider: () -> PasswordListMode,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val pageSectionSize: Int = 4
) {
    private data class IndexedSection(
        val key: String,
        val items: List<PasswordEntry>
    )

    private val _passwordEntries = MutableStateFlow<List<PasswordEntry>>(emptyList())
    val passwordEntries: StateFlow<List<PasswordEntry>> = _passwordEntries.asStateFlow()

    private val _passwordTotalCount = MutableStateFlow(0)
    val passwordTotalCount: StateFlow<Int> = _passwordTotalCount.asStateFlow()

    private val _passwordGroupStack = MutableStateFlow<List<Long>>(emptyList())
    val passwordGroupStack: StateFlow<List<Long>> = _passwordGroupStack.asStateFlow()

    private val _passwordIndexKeys = MutableStateFlow<List<String>>(emptyList())
    val passwordIndexKeys: StateFlow<List<String>> = _passwordIndexKeys.asStateFlow()

    private val _passwordHasMore = MutableStateFlow(false)
    val passwordHasMore: StateFlow<Boolean> = _passwordHasMore.asStateFlow()

    private val _isPageLoading = MutableStateFlow(false)
    val isPageLoading: StateFlow<Boolean> = _isPageLoading.asStateFlow()

    private var refreshJob: Job? = null
    private val pagingMutex = Mutex()
    private var allSections: List<IndexedSection> = emptyList()
    private var loadedSectionCount: Int = 0
    // 增量累积已加载条目，避免每次翻页都重建全量列表（O(n²) → O(n)）
    private var accumulatedEntries: List<PasswordEntry> = emptyList()

    // 排序/过滤配置提升为持有状态，避免分组导航与写入刷新时回落到默认值。
    // refreshPasswordEntries 的对应参数为 null 时沿用当前值。
    private var currentCaseSensitive: Boolean = false
    private var currentSortMode: PasswordSortMode = PasswordSortMode.DEFAULT
    private var currentAscending: Boolean = true
    private var currentHideExpired: Boolean = false

    /**
     * 更新数据访问提供者
     */
    fun updateAccessProvider(provider: () -> PasswordDataAccess) {
        accessProvider = provider
    }

    suspend fun reloadInitialPasswordData() {
        val access = accessProvider()
        if (!access.isReady()) {
            clearAll(resetTotalCount = true)
            return
        }

        val localPath = access.localPath ?: return
        val masterPassword = access.masterPassword
        val listMode = listModeProvider()

        // 使用合并方法一次性加载列表与计数，避免同一数据库被打开两次
        val (topLevelPasswordEntries, passwordEntryCount) = withContext(ioDispatcher) {
            when (listMode) {
                PasswordListMode.ALL_PASSWORDS -> repository.loadPasswordEntriesByTopLevelWithCount(localPath, masterPassword)
                PasswordListMode.RECENT_DELETED -> repository.loadRecentDeletedPasswordEntriesWithCount(localPath, masterPassword)
            }
        }

        // 在 IO 线程完成排序/分组，避免首次进入时主线程阻塞导致动画卡顿
        // 复用当前持有的排序/过滤配置，使重新加载（如解锁后）尊重用户已选设置
        val sections = withContext(ioDispatcher) {
            computeSections(
                topLevelPasswordEntries,
                "",
                currentCaseSensitive,
                currentSortMode,
                currentAscending,
                currentHideExpired
            )
        }

        pagingMutex.withLock {
            if (listMode == PasswordListMode.RECENT_DELETED) {
                _passwordGroupStack.value = emptyList()
            }
            _passwordTotalCount.value = passwordEntryCount
            applyComputedSectionsLocked(sections)
        }
    }

    fun refreshPasswordEntries(
        searchQuery: String = "",
        caseSensitive: Boolean? = null,
        sortMode: PasswordSortMode? = null,
        ascending: Boolean? = null,
        hideExpired: Boolean? = null
    ) {
        // null 表示沿用当前持有状态，避免分组导航/写入刷新时重置用户的排序与过滤设置
        if (caseSensitive != null) currentCaseSensitive = caseSensitive
        if (sortMode != null) currentSortMode = sortMode
        if (ascending != null) currentAscending = ascending
        if (hideExpired != null) currentHideExpired = hideExpired
        val effCaseSensitive = currentCaseSensitive
        val effSortMode = currentSortMode
        val effAscending = currentAscending
        val effHideExpired = currentHideExpired
        refreshJob?.cancel()
        refreshJob = scope.launch {
            val access = accessProvider()
            if (!access.isReady()) {
                pagingMutex.withLock {
                    _passwordEntries.value = emptyList()
                    _passwordIndexKeys.value = emptyList()
                    _passwordHasMore.value = false
                    allSections = emptyList()
                    loadedSectionCount = 0
                    accumulatedEntries = emptyList()
                }
                return@launch
            }

            val localPath = access.localPath ?: return@launch
            val masterPassword = access.masterPassword
            val keyword = searchQuery.trim()
            val listMode = listModeProvider()
            val currentGroupId = if (listMode == PasswordListMode.ALL_PASSWORDS) {
                _passwordGroupStack.value.lastOrNull()
            } else {
                null
            }

            if (listMode == PasswordListMode.RECENT_DELETED && _passwordGroupStack.value.isNotEmpty()) {
                _passwordGroupStack.value = emptyList()
            }

            val source = withContext(ioDispatcher) {
                when (listMode) {
                    PasswordListMode.ALL_PASSWORDS -> {
                        if (keyword.isBlank()) {
                            if (currentGroupId == null) {
                                repository.loadPasswordEntriesByTopLevel(localPath, masterPassword)
                            } else {
                                repository.loadPasswordEntriesByGroup(localPath, masterPassword, currentGroupId)
                            }
                        } else {
                            // 搜索时加载字段详情，以便匹配备注/URL/自定义字段等
                            repository.loadPasswordEntries(localPath, masterPassword, includeFieldDetails = true)
                        }
                    }
                    PasswordListMode.RECENT_DELETED -> {
                        repository.loadRecentDeletedPasswordEntries(localPath, masterPassword)
                    }
                }
            }

            // 在 IO 线程完成排序/分组，避免主线程阻塞
            val sections = withContext(ioDispatcher) {
                computeSections(source, keyword, effCaseSensitive, effSortMode, effAscending, effHideExpired)
            }

            pagingMutex.withLock {
                applyComputedSectionsLocked(sections)
            }
        }
    }

    fun loadNextPage() {
        if (_isPageLoading.value || !_passwordHasMore.value) {
            return
        }
        // 在 IO 线程执行 flatMap + list copy，避免主线程阻塞导致快速滚动卡顿
        scope.launch(ioDispatcher) {
            pagingMutex.withLock {
                appendNextPageLocked()
            }
        }
    }

    suspend fun ensureSectionLoaded(indexKey: String): Boolean {
        // 使用短锁+后台计算的模式：在锁内快速预留要加载的分组区间并复制切片引用，
        // 然后在 IO 线程上完成重的 flatMap 操作，最后再短锁合并结果。
        var prevCount = -1
        var sectionsToLoad: List<IndexedSection> = emptyList()
        var found = false

        pagingMutex.withLock {
            val sectionIndex = allSections.indexOfFirst { it.key == indexKey }
            if (sectionIndex < 0) {
                // 目标分组不存在
                return@withLock
            }
            found = true
            val requiredSectionCount = sectionIndex + 1
            if (requiredSectionCount <= loadedSectionCount) {
                // 已经加载，无需操作
                return@withLock
            }
            prevCount = loadedSectionCount
            val realRequired = requiredSectionCount.coerceAtMost(allSections.size)
            // 复制分组切片（浅拷贝引用），以便在锁外安全地展开 items
            sectionsToLoad = allSections.subList(prevCount, realRequired).toList()
            // 预先更新已加载分组计数，防止并发重复加载相同区间
            loadedSectionCount = realRequired
        }

        if (!found) return false
        if (prevCount == -1) return true

        // 在 IO 线程展开新增分组的 items（heavy），避免阻塞持锁区
        val gapEntries = withContext(ioDispatcher) {
            sectionsToLoad.flatMap { it.items }
        }

        // 合并到累积列表：再短锁验证/合并，若并发引起差异则安全降级为重建已加载区
        pagingMutex.withLock {
            // 计算预期的先前条目数（仅统计大小，开销远小于复制所有元素）
            val expectedPrevEntriesCount = allSections.take(prevCount).sumOf { it.items.size }
            if (accumulatedEntries.size != expectedPrevEntriesCount) {
                // 出现并发变更：重建到当前 loadedSectionCount 的累积列表以保证一致性
                accumulatedEntries = allSections.take(loadedSectionCount).flatMap { it.items }
            } else {
                accumulatedEntries = accumulatedEntries + gapEntries
            }
            _passwordEntries.value = accumulatedEntries
            _passwordHasMore.value = loadedSectionCount < allSections.size
        }

        return true
    }

    suspend fun getHeaderScrollIndex(indexKey: String): Int? {
        return pagingMutex.withLock {
            var scrollIndex = 0
            allSections.take(loadedSectionCount).forEach { section ->
                if (section.key == indexKey) {
                    return@withLock scrollIndex
                }
                scrollIndex += 1 + section.items.size
            }
            null
        }
    }

    fun openPasswordGroup(groupStableId: Long, searchQuery: String = "") {
        if (listModeProvider() == PasswordListMode.RECENT_DELETED) {
            return
        }
        if (_passwordGroupStack.value.lastOrNull() == groupStableId) {
            return
        }
        _passwordGroupStack.value = _passwordGroupStack.value + groupStableId
        refreshPasswordEntries(searchQuery)
    }

    fun navigateUpPasswordGroup(searchQuery: String = "") {
        if (listModeProvider() == PasswordListMode.RECENT_DELETED) {
            return
        }
        val stack = _passwordGroupStack.value
        if (stack.isEmpty()) {
            return
        }

        _passwordGroupStack.value = stack.dropLast(1)
        refreshPasswordEntries(searchQuery)
    }

    fun resetPasswordGroupNavigation(searchQuery: String = "") {
        if (listModeProvider() == PasswordListMode.RECENT_DELETED) {
            refreshJob?.cancel()
            refreshJob = null
            _passwordGroupStack.value = emptyList()
            refreshPasswordEntries(searchQuery)
            return
        }
        refreshJob?.cancel()
        refreshJob = null
        _passwordGroupStack.value = emptyList()
        refreshPasswordEntries(searchQuery)
    }

    fun resetPasswordGroupStackOnly() {
        refreshJob?.cancel()
        refreshJob = null
        _passwordGroupStack.value = emptyList()
    }

    suspend fun clearAll(resetTotalCount: Boolean) {
        refreshJob?.cancel()
        refreshJob = null
        pagingMutex.withLock {
            _passwordEntries.value = emptyList()
            _passwordIndexKeys.value = emptyList()
            _passwordHasMore.value = false
            _isPageLoading.value = false
            _passwordGroupStack.value = emptyList()
            if (resetTotalCount) {
                _passwordTotalCount.value = 0
            }
            allSections = emptyList()
            loadedSectionCount = 0
            accumulatedEntries = emptyList()
        }
    }

    /**
     * 纯计算：对数据源进行过滤、排序、分组，返回有序分组列表。
     * 可在任意线程安全调用（无副作用）。
     */
    private fun computeSections(
        source: List<PasswordEntry>,
        keyword: String,
        caseSensitive: Boolean = false,
        sortMode: PasswordSortMode = PasswordSortMode.DEFAULT,
        ascending: Boolean = true,
        hideExpired: Boolean = false
    ): List<IndexedSection> {
        val values = source
            .asSequence()
            .filter { item ->
                if (hideExpired && item.isExpired) {
                    return@filter false
                }
                keyword.isBlank() || matchesKeyword(item, keyword, caseSensitive)
            }
            .sortedWith(passwordEntryComparator(sortMode, ascending))
            .toList()

        return values
            .groupBy { it.toPasswordIndexKey() }
            .toList()
            .sortedWith(
                compareBy<Pair<String, List<PasswordEntry>>> { (letter, _) ->
                    when (letter) {
                        PasswordFolderIndexLabel -> 0
                        "#" -> 1
                        else -> 2
                    }
                }.thenBy { (letter, _) ->
                    if (letter == PasswordFolderIndexLabel) "" else letter
                }
            )
            .map { (key, items) -> IndexedSection(key = key, items = items) }
    }

    /**
     * 构造排序比较器：文件夹始终置前，再按选定键排序。
     */
    private fun passwordEntryComparator(sortMode: PasswordSortMode, ascending: Boolean): Comparator<PasswordEntry> {
        val byKey: Comparator<PasswordEntry> = when (sortMode) {
            PasswordSortMode.DEFAULT -> compareBy { it.title.ifBlank { it.account }.lowercase() }
            PasswordSortMode.TITLE -> compareBy { it.title.lowercase() }
            PasswordSortMode.ACCOUNT -> compareBy { it.account.lowercase() }
            PasswordSortMode.MODIFIED_TIME -> compareBy { it.modifiedTime }
            PasswordSortMode.CREATED_TIME -> compareBy { it.creationTime }
        }
        val keyComp = if (ascending) byKey else byKey.reversed()
        // 文件夹始终置前：Boolean 自然序为 false<true 会把文件夹排到末尾，
        // 显式映射为 0/1 使文件夹在前，且不受 ascending 反序影响。
        return compareBy<PasswordEntry> { if (it.isFolderPlaceholder) 0 else 1 }.then(keyComp)
    }

    /**
     * 判断条目是否命中搜索关键词：匹配标题/账号/URL/备注/自定义字段键与值/标签。
     */
    private fun matchesKeyword(item: PasswordEntry, keyword: String, caseSensitive: Boolean): Boolean {
        val contains: (String) -> Boolean = { field ->
            if (caseSensitive) field.contains(keyword) else field.contains(keyword, ignoreCase = true)
        }
        if (contains(item.title) || contains(item.account)) {
            return true
        }
        if (item.keyValues.any { kv -> contains(kv.fieldName) || contains(kv.rawValue) }) {
            return true
        }
        if (item.tags.any { contains(it) }) {
            return true
        }
        return false
    }

    /**
     * 应用已在 IO 线程预计算好的分组列表，必须在 pagingMutex 持有时调用。
     */
    private fun applyComputedSectionsLocked(sections: List<IndexedSection>) {
        allSections = sections
        _passwordIndexKeys.value = sections.map { it.key }
        loadedSectionCount = 0
        accumulatedEntries = emptyList()
        _passwordEntries.value = emptyList()
        _passwordHasMore.value = sections.isNotEmpty()
        appendNextPageLocked()
    }

    private fun appendNextPageLocked() {
        if (allSections.isEmpty()) {
            accumulatedEntries = emptyList()
            _passwordEntries.value = emptyList()
            _passwordHasMore.value = false
            _isPageLoading.value = false
            return
        }

        if (loadedSectionCount >= allSections.size) {
            _passwordHasMore.value = false
            _isPageLoading.value = false
            return
        }

        _isPageLoading.value = true
        val prevCount = loadedSectionCount
        loadedSectionCount = (loadedSectionCount + pageSectionSize).coerceAtMost(allSections.size)
        // 只 flatMap 新增分组，拼接到已有列表，避免从头重建
        val newEntries = allSections.subList(prevCount, loadedSectionCount).flatMap { it.items }
        accumulatedEntries = accumulatedEntries + newEntries
        _passwordEntries.value = accumulatedEntries
        _passwordHasMore.value = loadedSectionCount < allSections.size
        _isPageLoading.value = false
    }
}

internal fun PasswordEntry.toPasswordIndexKey(): String {
    if (isFolderGroup) {
        return PasswordFolderIndexLabel
    }

    val source = title.ifBlank { account }.trim()
    if (source.isBlank()) {
        return "#"
    }

    val transformed = HanToLatinTransliterator.transliterate(source)
    val first = transformed.firstOrNull { ch -> ch.isLetterOrDigit() } ?: return "#"
    val upper = first.uppercaseChar()
    return if (upper in 'A'..'Z') upper.toString() else "#"
}

private val HanToLatinTransliterator: Transliterator by lazy {
    Transliterator.getInstance("Han-Latin; Latin-ASCII")
}
