package xzynine.WebDAVPass.Android.ui.ViewModel

import android.content.Context
import xzylib.base.util.Logger
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import xzynine.WebDAVPass.Android.data.LibraryContext
import xzynine.WebDAVPass.Android.data.KdbxTokenRepository
import github.xzynine.webdav.SyncFailureKind
import github.xzynine.webdav.SyncMergeCallback
import github.xzynine.webdav.SyncOutcome
import github.xzynine.webdav.SyncResult
import github.xzynine.webdav.WebDavSyncEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 云端同步视图模型
 *
 * 负责编排云端库的自动/手动恢复与备份流程，
 * 上传下载等传输能力由子模块 [WebDavSyncEngine] 提供，本类仅负责调用与状态持久化。
 */
class CloudSyncViewModel(private val context: Context) : ViewModel() {

    companion object {
        const val SYNC_STATUS_IDLE = "idle"
        const val SYNC_STATUS_SYNCING = "syncing"
        const val SYNC_STATUS_SUCCESS = "success"
        const val SYNC_STATUS_MERGED = "merged"
        const val SYNC_STATUS_CONFLICT = "conflict"
        const val SYNC_STATUS_FAILED = "failed"
        const val SYNC_LOG_TAG = "同步"
    }

    private val cloudSyncMutex = Mutex()

    private val syncEngine = WebDavSyncEngine(context.applicationContext)

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

    /**
     * 自动恢复令牌
     */
    fun autoRestoreTokens(
        libraryViewModel: LibraryViewModel,
        repository: KdbxTokenRepository,
        onReloadTokens: suspend () -> Boolean
    ) {
        viewModelScope.launch {
            cloudSyncMutex.withLock {
                try {
                    Logger.d(SYNC_LOG_TAG, "开始自动恢复")
                    if (!libraryViewModel.shouldAutoSyncCurrentLibrary()) {
                        Logger.d(SYNC_LOG_TAG, "自动恢复跳过：自动同步未开启")
                        _backupStatus.value = "当前云端库未启用自动同步"
                        return@withLock
                    }

                    _isRestoreInProgress.value = true
                    _restoreProgress.value = 0
                    _backupStatus.value = "正在尝试自动恢复..."
                    libraryViewModel.updateCloudSyncState(status = SYNC_STATUS_SYNCING)

                    val cloudLibrary = libraryViewModel.getCurrentCloudLibrary()
                    if (cloudLibrary != null) {
                        val success = downloadCurrentCloudLibrary(
                            cloudLibrary,
                            libraryViewModel,
                            repository
                        )
                        Logger.d(SYNC_LOG_TAG, "自动恢复下载结果=$success")
                        if (success) {
                            onReloadTokens()
                        }
                        _backupStatus.value = if (success) {
                            "云端库自动同步完成"
                        } else {
                            "云端库自动同步失败：${libraryViewModel.currentCloudSyncError()}"
                        }
                        return@withLock
                    }

                    _backupStatus.value = "未绑定云端 .kdbx，已跳过自动恢复"
                } catch (ex: Exception) {
                    Logger.e(SYNC_LOG_TAG, "自动恢复失败: ${ex.message}", ex)
                    val message = resolveSyncFailureMessage("自动恢复", null, syncEngine.classifyFailure(ex))
                    _backupStatus.value = "自动恢复失败：$message"
                    libraryViewModel.updateCloudSyncState(
                        status = SYNC_STATUS_FAILED,
                        errorMessage = message
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
    fun manualRestoreTokens(
        libraryViewModel: LibraryViewModel,
        repository: KdbxTokenRepository,
        onReloadTokens: suspend () -> Boolean
    ) {
        viewModelScope.launch {
            cloudSyncMutex.withLock {
                try {
                    Logger.d(SYNC_LOG_TAG, "开始手动恢复")
                    _isRestoreInProgress.value = true
                    _restoreProgress.value = 0
                    _backupStatus.value = "正在手动恢复..."
                    libraryViewModel.updateCloudSyncState(status = SYNC_STATUS_SYNCING)

                    val cloudLibrary = libraryViewModel.getCurrentCloudLibrary()
                    if (cloudLibrary != null) {
                        val success = downloadCurrentCloudLibrary(
                            cloudLibrary,
                            libraryViewModel,
                            repository
                        )
                        Logger.d(SYNC_LOG_TAG, "手动恢复下载结果=$success")
                        if (success) {
                            onReloadTokens()
                        }
                        _backupStatus.value = if (success) {
                            "云端库恢复成功"
                        } else {
                            "云端库恢复失败：${libraryViewModel.currentCloudSyncError()}"
                        }
                        return@withLock
                    }

                    _backupStatus.value = "未绑定云端 .kdbx，无法手动恢复"
                } catch (ex: Exception) {
                    Logger.e(SYNC_LOG_TAG, "手动恢复失败: ${ex.message}", ex)
                    val message = resolveSyncFailureMessage("手动恢复", null, syncEngine.classifyFailure(ex))
                    _backupStatus.value = "手动恢复失败：$message"
                    libraryViewModel.updateCloudSyncState(
                        status = SYNC_STATUS_FAILED,
                        errorMessage = message
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
    fun backupTokens(
        libraryViewModel: LibraryViewModel,
        repository: KdbxTokenRepository,
        masterPassword: String,
        force: Boolean = false
    ) {
        viewModelScope.launch {
            cloudSyncMutex.withLock {
                try {
                    Logger.d(SYNC_LOG_TAG, "开始执行备份同步: force=$force")
                    val cloudLibrary = libraryViewModel.getCurrentCloudLibrary()
                    if (cloudLibrary == null && !force) {
                        Logger.d(SYNC_LOG_TAG, "备份同步跳过：未绑定云端库且 force=false")
                        return@withLock
                    }

                    _isBackupInProgress.value = true
                    _backupStatus.value = "正在备份..."
                    _backupProgress.value = 0
                    libraryViewModel.updateCloudSyncState(status = SYNC_STATUS_SYNCING)

                    if (cloudLibrary != null) {
                        val success = uploadCurrentCloudLibrary(
                            cloudLibrary,
                            libraryViewModel,
                            repository,
                            masterPassword
                        )
                        Logger.d(SYNC_LOG_TAG, "备份上传结果=$success")
                        _backupStatus.value = if (success) {
                            when (libraryViewModel.currentLibrary.value?.lastSyncStatus) {
                                SYNC_STATUS_MERGED -> "云端库自动合并并同步成功"
                                else -> "云端库同步成功"
                            }
                        } else {
                            "云端库同步失败：${libraryViewModel.currentCloudSyncError()}"
                        }
                        return@withLock
                    }

                    _backupStatus.value = "未绑定云端 .kdbx，无法同步备份"
                } catch (ex: Exception) {
                    Logger.e(SYNC_LOG_TAG, "备份同步失败: ${ex.message}", ex)
                    val message = resolveSyncFailureMessage("备份", null, syncEngine.classifyFailure(ex))
                    _backupStatus.value = "备份失败：$message"
                    libraryViewModel.updateCloudSyncState(
                        status = SYNC_STATUS_FAILED,
                        errorMessage = message
                    )
                } finally {
                    _isBackupInProgress.value = false
                    _backupProgress.value = 0
                }
            }
        }
    }

    /**
     * 从当前云端库下载到本地（调用子模块同步引擎）
     */
    private suspend fun downloadCurrentCloudLibrary(
        cloudLibrary: LibraryContext,
        libraryViewModel: LibraryViewModel,
        repository: KdbxTokenRepository
    ): Boolean {
        Logger.d(
            SYNC_LOG_TAG,
            "开始下载云端库: 本地路径=${cloudLibrary.localPath}, 远端路径=${cloudLibrary.remoteFilePath.orEmpty()}"
        )
        val outcome = syncEngine.download(
            remotePath = cloudLibrary.remoteFilePath!!,
            username = cloudLibrary.username!!,
            password = cloudLibrary.password!!,
            localPath = cloudLibrary.localPath
        )
        return when {
            outcome.isSuccess -> {
                libraryViewModel.updateCloudSyncState(
                    status = SYNC_STATUS_SUCCESS,
                    errorMessage = null,
                    remoteModifiedAt = outcome.remoteModifiedAt,
                    syncAt = System.currentTimeMillis()
                )
                Logger.d(SYNC_LOG_TAG, "下载云端库成功: 远端修改时间=${outcome.remoteModifiedAt}")
                true
            }
            else -> {
                Logger.e(SYNC_LOG_TAG, "下载云端库失败: ${outcome.errorMessage}")
                val message = resolveSyncFailureMessage("下载", cloudLibrary.localPath, outcome)
                libraryViewModel.updateCloudSyncState(
                    status = SYNC_STATUS_FAILED,
                    errorMessage = message
                )
                false
            }
        }
    }

    /**
     * 将当前本地库上传到云端（调用子模块同步引擎）
     */
    private suspend fun uploadCurrentCloudLibrary(
        cloudLibrary: LibraryContext,
        libraryViewModel: LibraryViewModel,
        repository: KdbxTokenRepository,
        masterPassword: String
    ): Boolean {
        Logger.d(
            SYNC_LOG_TAG,
            "开始上传云端库: 本地路径=${cloudLibrary.localPath}, 远端路径=${cloudLibrary.remoteFilePath.orEmpty()}"
        )
        if (masterPassword.isBlank()) {
            Logger.d(SYNC_LOG_TAG, "上传中止，主密码为空")
            libraryViewModel.updateCloudSyncState(
                status = SYNC_STATUS_FAILED,
                errorMessage = "未解锁数据库，无法执行云端同步"
            )
            return false
        }

        val outcome = syncEngine.upload(
            remotePath = cloudLibrary.remoteFilePath!!,
            username = cloudLibrary.username!!,
            password = cloudLibrary.password!!,
            localPath = cloudLibrary.localPath,
            masterPassword = masterPassword,
            merge = SyncMergeCallback { localPath, masterPassword, remoteBytes ->
                repository.mergeRemoteDatabaseBytes(localPath, masterPassword, remoteBytes)
            },
            lastRemoteModifiedAt = cloudLibrary.lastRemoteModifiedAt,
            lastSyncAt = cloudLibrary.lastSyncAt
        )
        return when (outcome.result) {
            SyncResult.SUCCESS, SyncResult.MERGED -> {
                libraryViewModel.updateCloudSyncState(
                    status = if (outcome.result == SyncResult.MERGED) {
                        SYNC_STATUS_MERGED
                    } else {
                        SYNC_STATUS_SUCCESS
                    },
                    errorMessage = null,
                    remoteModifiedAt = outcome.remoteModifiedAt,
                    syncAt = System.currentTimeMillis()
                )
                Logger.d(SYNC_LOG_TAG, "上传云端库成功: 最终状态=${outcome.result}")
                true
            }
            SyncResult.CONFLICT -> {
                Logger.e(SYNC_LOG_TAG, "自动合并失败，标记为冲突")
                libraryViewModel.updateCloudSyncState(
                    status = SYNC_STATUS_CONFLICT,
                    errorMessage = "自动合并失败，请先手动恢复后再同步",
                    remoteModifiedAt = outcome.remoteModifiedAt
                )
                false
            }
            SyncResult.FAILED -> {
                Logger.e(SYNC_LOG_TAG, "上传云端库失败: ${outcome.errorMessage}")
                val message = resolveSyncFailureMessage("上传", cloudLibrary.localPath, outcome)
                libraryViewModel.updateCloudSyncState(
                    status = SYNC_STATUS_FAILED,
                    errorMessage = message
                )
                false
            }
        }
    }

    /**
     * 将引擎的结构化失败类型映射为云端同步失败文案
     */
    private fun resolveSyncFailureMessage(action: String, localPath: String?, outcome: SyncOutcome): String {
        val isUriPath = !localPath.isNullOrBlank() && localPath.startsWith("content://")
        return when (outcome.errorKind) {
            SyncFailureKind.PERMISSION -> "本地数据库访问权限已失效，请重新选择数据库文件"
            SyncFailureKind.NETWORK -> "网络异常，请检查网络连接后重试"
            SyncFailureKind.FILE_NOT_FOUND -> {
                val specific = outcome.errorMessage
                if (specific == "远端文件不存在，无法下载" || specific == "本地数据库文件不存在或不可访问") {
                    specific
                } else if (isUriPath) {
                    "本地数据库文件不存在或已失效，请重新选择数据库文件"
                } else {
                    "本地数据库文件不存在，请检查路径"
                }
            }
            SyncFailureKind.UNKNOWN -> outcome.errorMessage?.takeIf { it.isNotBlank() } ?: "${action}失败"
        }
    }
}
