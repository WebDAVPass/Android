package xzynine.WebDAVPass.Android.ui.ViewModel

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xzynine.WebDAVPass.Android.data.KdbxTokenRepository

/**
 * 密码列表模式。
 */
enum class PasswordListMode {
    ALL_PASSWORDS,
    RECENT_DELETED
}

/**
 * 管理密码列表模式与最近删除计数的子 ViewModel。
 */
internal class PasswordListModeSubViewModel(
    private val repository: KdbxTokenRepository,
    private val scope: CoroutineScope,
    private val accessProvider: () -> PasswordDataAccess,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val _passwordListMode = MutableStateFlow(PasswordListMode.ALL_PASSWORDS)
    val passwordListMode: StateFlow<PasswordListMode> = _passwordListMode.asStateFlow()

    private val _recentDeletedCount = MutableStateFlow(0)
    val recentDeletedCount: StateFlow<Int> = _recentDeletedCount.asStateFlow()

    fun setPasswordListMode(mode: PasswordListMode): Boolean {
        if (_passwordListMode.value == mode) {
            return false
        }
        _passwordListMode.value = mode
        return true
    }

    fun resetPasswordListMode() {
        _passwordListMode.value = PasswordListMode.ALL_PASSWORDS
    }

    fun clearRecentDeletedCount() {
        _recentDeletedCount.value = 0
    }

    fun resetAllState() {
        resetPasswordListMode()
        clearRecentDeletedCount()
    }

    fun refreshRecentDeletedCount() {
        scope.launch {
            val access = accessProvider()
            if (!access.isReady()) {
                _recentDeletedCount.value = 0
                return@launch
            }

            val localPath = access.localPath
            if (localPath.isNullOrBlank()) {
                _recentDeletedCount.value = 0
                return@launch
            }

            val count = withContext(ioDispatcher) {
                repository.countRecentDeletedPasswordEntries(localPath, access.masterPassword)
            }
            _recentDeletedCount.value = count
        }
    }
}
