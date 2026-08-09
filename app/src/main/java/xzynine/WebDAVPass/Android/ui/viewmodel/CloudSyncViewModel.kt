package xzynine.WebDAVPass.Android.ui.ViewModel

import android.content.Context
import android.content.Intent
import android.net.Uri
import xzylib.base.util.Logger
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.documentfile.provider.DocumentFile
import xzynine.WebDAVPass.Android.data.LibraryContext
import xzynine.WebDAVPass.Android.data.KdbxTokenRepository
import github.xzynine.webdav.WebDav
import github.xzynine.webdav.Authorization
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
import java.io.File
import java.io.FileNotFoundException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * 云端同步视图模型
 *
 * 负责管理云端库的上传、下载、合并和同步状态。
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
                    val message = resolveSyncFailureMessage("自动恢复", null, ex)
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
                    val message = resolveSyncFailureMessage("手动恢复", null, ex)
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
                    val message = resolveSyncFailureMessage("备份", null, ex)
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
     * 从当前云端库下载到本地
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
        return withContext(Dispatchers.IO) {
            runCatching {
                val remote = WebDav(cloudLibrary.remoteFilePath!!, Authorization(cloudLibrary.username!!, cloudLibrary.password!!))
                if (!remote.exists()) {
                    Logger.d(SYNC_LOG_TAG, "跳过下载，远端文件不存在")
                    libraryViewModel.updateCloudSyncState(
                        status = SYNC_STATUS_FAILED,
                        errorMessage = "远端文件不存在，无法下载"
                    )
                    return@runCatching false
                }

                val remoteInfo = remote.getWebDavFile()
                val remoteModified = remoteInfo?.lastModify?.takeIf { it > 0 }
                val bytes = remote.download()
                writeBytesToLocalPath(cloudLibrary.localPath, bytes)

                libraryViewModel.updateCloudSyncState(
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
                val message = resolveSyncFailureMessage("下载", cloudLibrary.localPath, it)
                libraryViewModel.updateCloudSyncState(
                    status = SYNC_STATUS_FAILED,
                    errorMessage = message
                )
            }.getOrDefault(false)
        }
    }

    /**
     * 将当前本地库上传到云端
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

        return withContext(Dispatchers.IO) {
            runCatching {
                if (!localPathExists(cloudLibrary.localPath)) {
                    libraryViewModel.updateCloudSyncState(
                        status = SYNC_STATUS_FAILED,
                        errorMessage = "本地数据库文件不存在或不可访问"
                    )
                    return@runCatching false
                }

                val localPathIsUri = asContentUri(cloudLibrary.localPath) != null

                val remote = WebDav(cloudLibrary.remoteFilePath!!, Authorization(cloudLibrary.username!!, cloudLibrary.password!!))
                val remoteInfo = remote.getWebDavFile()
                val remoteModified = remoteInfo?.lastModify?.takeIf { it > 0 }
                val remoteChangedAfterSync = remoteModified != null &&
                    (cloudLibrary.lastRemoteModifiedAt == null || remoteModified > cloudLibrary.lastRemoteModifiedAt)
                val localChangedAfterSync = if (localPathIsUri) {
                    true
                } else {
                    val localModifiedAt = getLocalPathLastModified(cloudLibrary.localPath)
                    cloudLibrary.lastSyncAt?.let { syncAt ->
                        (localModifiedAt ?: Long.MAX_VALUE) > syncAt
                    } ?: true
                }
                Logger.d(
                    SYNC_LOG_TAG,
                    "上传前比较: 远端已变更=$remoteChangedAfterSync, 本地已变更=$localChangedAfterSync"
                )

                if (remoteChangedAfterSync) {
                    Logger.d(SYNC_LOG_TAG, "检测到远端变更，进入下载/合并流程")
                    val remoteBytes = remote.download()
                    val merged = if (localChangedAfterSync) {
                        Logger.d(SYNC_LOG_TAG, "检测到本地也有变更，尝试自动合并")
                        repository.mergeRemoteDatabaseBytes(
                            localPath = cloudLibrary.localPath,
                            masterPassword = masterPassword,
                            remoteBytes = remoteBytes
                        )
                    } else {
                        Logger.d(SYNC_LOG_TAG, "本地无变更，使用远端内容覆盖本地")
                        writeBytesToLocalPath(cloudLibrary.localPath, remoteBytes)
                        true
                    }

                    if (!merged) {
                        Logger.e(SYNC_LOG_TAG, "自动合并失败，标记为冲突")
                        libraryViewModel.updateCloudSyncState(
                            status = SYNC_STATUS_CONFLICT,
                            errorMessage = "自动合并失败，请先手动恢复后再同步",
                            remoteModifiedAt = remoteModified
                        )
                        return@runCatching false
                    }
                    Logger.d(SYNC_LOG_TAG, "自动合并成功")
                }

                if (localPathIsUri) {
                    remote.upload(readBytesFromLocalPath(cloudLibrary.localPath), "application/octet-stream")
                } else {
                    remote.upload(File(cloudLibrary.localPath), "application/octet-stream")
                }
                val refreshedRemoteModified = runCatching {
                    remote.getWebDavFile()?.lastModify
                }.getOrNull()?.takeIf { it > 0 } ?: remoteModified

                val finalStatus = if (remoteChangedAfterSync && localChangedAfterSync) {
                    SYNC_STATUS_MERGED
                } else {
                    SYNC_STATUS_SUCCESS
                }
                libraryViewModel.updateCloudSyncState(
                    status = finalStatus,
                    errorMessage = null,
                    remoteModifiedAt = refreshedRemoteModified,
                    syncAt = System.currentTimeMillis()
                )
                Logger.d(
                    SYNC_LOG_TAG,
                    "上传云端库成功: 最终状态=$finalStatus"
                )
                true
            }.onFailure {
                Logger.e(SYNC_LOG_TAG, "上传云端库失败: ${it.message}", it)
                val message = resolveSyncFailureMessage("上传", cloudLibrary.localPath, it)
                libraryViewModel.updateCloudSyncState(
                    status = SYNC_STATUS_FAILED,
                    errorMessage = message
                )
            }.getOrDefault(false)
        }
    }

    /**
     * 判断路径是否为 Content Uri。
     */
    private fun asContentUri(path: String): Uri? {
        val parsed = runCatching { Uri.parse(path) }.getOrNull() ?: return null
        return if (parsed.scheme.equals("content", ignoreCase = true)) parsed else null
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

    /**
     * 写入本地定位（文件路径或 Uri）。
     */
    private fun writeBytesToLocalPath(localPath: String, bytes: ByteArray) {
        val uri = asContentUri(localPath)
        if (uri != null) {
            takePersistableUriPermission(uri)
            val output = context.contentResolver.openOutputStream(uri, "wt")
                ?: throw IllegalStateException("无法写入本地数据库")
            output.use { stream ->
                stream.write(bytes)
            }
            return
        }

        val localFile = File(localPath)
        localFile.parentFile?.let {
            if (!it.exists()) {
                it.mkdirs()
            }
        }
        localFile.writeBytes(bytes)
    }

    /**
     * 读取本地定位（文件路径或 Uri）。
     */
    private fun readBytesFromLocalPath(localPath: String): ByteArray {
        val uri = asContentUri(localPath)
        if (uri != null) {
            takePersistableUriPermission(uri)
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("无法读取本地数据库")
            return input.use { stream ->
                stream.readBytes()
            }
        }
        return File(localPath).readBytes()
    }

    /**
     * 判断本地定位是否可访问。
     */
    private fun localPathExists(localPath: String): Boolean {
        val uri = asContentUri(localPath)
        if (uri != null) {
            return runCatching {
                takePersistableUriPermission(uri)
                context.contentResolver.openInputStream(uri)?.use { true } ?: false
            }.getOrDefault(false)
        }
        return File(localPath).exists()
    }

    /**
     * 获取本地文件最近修改时间。
     */
    private fun getLocalPathLastModified(localPath: String): Long? {
        val uri = asContentUri(localPath)
        if (uri != null) {
            val modified = runCatching {
                DocumentFile.fromSingleUri(context, uri)?.lastModified()
            }.getOrNull() ?: 0L
            return modified.takeIf { it > 0L }
        }

        val localFile = File(localPath)
        if (!localFile.exists()) {
            return null
        }
        return localFile.lastModified().takeIf { it > 0L }
    }

    /**
     * 拼接异常链文本，便于关键字匹配。
     */
    private fun flattenThrowableMessage(throwable: Throwable): String {
        return generateSequence(throwable) { current ->
            current.cause
        }.joinToString(separator = " | ") { current ->
            "${current.javaClass.simpleName}:${current.message.orEmpty()}"
        }
    }

    /**
     * 判断是否为权限相关异常。
     */
    private fun isPermissionIssue(throwable: Throwable): Boolean {
        if (throwable is SecurityException) {
            return true
        }
        val text = flattenThrowableMessage(throwable)
        return text.contains("permission", ignoreCase = true) ||
            text.contains("denied", ignoreCase = true) ||
            text.contains("ACTION_OPEN_DOCUMENT", ignoreCase = true) ||
            text.contains("persistable", ignoreCase = true) ||
            text.contains("EACCES", ignoreCase = true)
    }

    /**
     * 判断是否为网络异常。
     */
    private fun isNetworkIssue(throwable: Throwable): Boolean {
        return throwable is UnknownHostException ||
            throwable is SocketTimeoutException ||
            throwable is ConnectException ||
            throwable is SocketException
    }

    /**
     * 归一化云端同步失败文案。
     */
    private fun resolveSyncFailureMessage(action: String, localPath: String?, throwable: Throwable): String {
        val isUriPath = !localPath.isNullOrBlank() && asContentUri(localPath) != null
        if (isUriPath && isPermissionIssue(throwable)) {
            return "本地数据库访问权限已失效，请重新选择数据库文件"
        }
        if (throwable is FileNotFoundException) {
            return if (isUriPath) {
                "本地数据库文件不存在或已失效，请重新选择数据库文件"
            } else {
                "本地数据库文件不存在，请检查路径"
            }
        }
        if (isNetworkIssue(throwable)) {
            return "网络异常，请检查网络连接后重试"
        }
        return throwable.message?.takeIf { it.isNotBlank() } ?: "${action}失败"
    }
}
