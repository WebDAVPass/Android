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

        val topLevelPasswordEntries = withContext(ioDispatcher) {
            when (listMode) {
                PasswordListMode.ALL_PASSWORDS -> repository.loadPasswordEntriesByTopLevel(localPath, masterPassword)
                PasswordListMode.RECENT_DELETED -> repository.loadRecentDeletedPasswordEntries(localPath, masterPassword)
            }
        }
        val passwordEntryCount = withContext(ioDispatcher) {
            when (listMode) {
                PasswordListMode.ALL_PASSWORDS -> repository.countPasswordEntries(localPath, masterPassword)
                PasswordListMode.RECENT_DELETED -> repository.countRecentDeletedPasswordEntries(localPath, masterPassword)
            }
        }

        pagingMutex.withLock {
            if (listMode == PasswordListMode.RECENT_DELETED) {
                _passwordGroupStack.value = emptyList()
            }
            _passwordTotalCount.value = passwordEntryCount
            replaceSourceLocked(topLevelPasswordEntries, "")
        }
    }

    fun refreshPasswordEntries(searchQuery: String = "") {
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
                            repository.loadPasswordEntries(localPath, masterPassword)
                        }
                    }
                    PasswordListMode.RECENT_DELETED -> {
                        repository.loadRecentDeletedPasswordEntries(localPath, masterPassword)
                    }
                }
            }

            pagingMutex.withLock {
                replaceSourceLocked(source, keyword)
            }
        }
    }

    fun loadNextPage() {
        if (_isPageLoading.value || !_passwordHasMore.value) {
            return
        }
        scope.launch {
            pagingMutex.withLock {
                appendNextPageLocked()
            }
        }
    }

    suspend fun ensureSectionLoaded(indexKey: String): Boolean {
        return pagingMutex.withLock {
            val sectionIndex = allSections.indexOfFirst { it.key == indexKey }
            if (sectionIndex < 0) {
                return@withLock false
            }

            val requiredSectionCount = sectionIndex + 1
            if (requiredSectionCount > loadedSectionCount) {
                loadedSectionCount = requiredSectionCount.coerceAtMost(allSections.size)
                _passwordEntries.value = buildLoadedEntriesLocked()
                _passwordHasMore.value = loadedSectionCount < allSections.size
            }
            true
        }
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
        }
    }

    private fun replaceSourceLocked(source: List<PasswordEntry>, keyword: String) {
        val values = source
            .asSequence()
            .filter { item ->
                keyword.isBlank() ||
                    item.title.contains(keyword, ignoreCase = true) ||
                    item.account.contains(keyword, ignoreCase = true)
            }
            .sortedBy { item ->
                item.title.ifBlank { item.account }.lowercase()
            }
            .toList()

        allSections = values
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

        _passwordIndexKeys.value = allSections.map { it.key }
        loadedSectionCount = 0
        _passwordEntries.value = emptyList()
        _passwordHasMore.value = allSections.isNotEmpty()

        appendNextPageLocked()
    }

    private fun appendNextPageLocked() {
        if (allSections.isEmpty()) {
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
        loadedSectionCount = (loadedSectionCount + pageSectionSize).coerceAtMost(allSections.size)
        _passwordEntries.value = buildLoadedEntriesLocked()
        _passwordHasMore.value = loadedSectionCount < allSections.size
        _isPageLoading.value = false
    }

    private fun buildLoadedEntriesLocked(): List<PasswordEntry> {
        return allSections
            .take(loadedSectionCount)
            .flatMap { section -> section.items }
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
