package xzynine.WebDAVPass.Android.data

import java.util.UUID

/**
 * 库来源类型
 */
enum class LibrarySourceType {
    /**
     * 本地来源
     */
    LOCAL,

    /**
     * 云端来源（WebDAV）
     */
    CLOUD
}

/**
 * 已绑定库上下文
 *
 * @property id 历史项唯一标识
 * @property displayName 列表展示名称
 * @property sourceType 来源类型
 * @property localPath 本地文件绝对路径
 * @property remoteBaseUrl 远端根路径（可为空）
 * @property remoteFilePath 远端文件路径（可为空）
 * @property username WebDAV用户名（可为空）
 * @property password WebDAV密码（可为空）
 * @property autoSyncEnabled 是否启用自动同步（仅云端库有效）
 * @property lastSyncAt 最近同步完成时间戳（可为空）
 * @property lastRemoteModifiedAt 最近一次同步后记录的远端修改时间戳（可为空）
 * @property lastSyncStatus 最近同步状态（可为空）
 * @property lastSyncError 最近同步错误信息（可为空）
 * @property lastUsedAt 最近使用时间戳
 * @property forceManualUnlockEvery48Hours 是否启用“48小时需手动主密码一次”策略（null 表示默认启用）
 * @property lastManualMasterUnlockAt 最近一次手动输入主密码并解锁成功的时间戳（可为空）
 * @property autoUnlockInvalidated 自动解锁是否处于“失效待重验”状态（保留开关与认证方式）
 */
data class LibraryContext(
    val id: String = UUID.randomUUID().toString(),
    val displayName: String,
    val sourceType: LibrarySourceType,
    val localPath: String,
    val remoteBaseUrl: String? = null,
    val remoteFilePath: String? = null,
    val username: String? = null,
    val password: String? = null,
    val autoSyncEnabled: Boolean = true,
    val lastSyncAt: Long? = null,
    val lastRemoteModifiedAt: Long? = null,
    val lastSyncStatus: String? = null,
    val lastSyncError: String? = null,
    val lastUsedAt: Long = System.currentTimeMillis(),
    val autoUnlockEnabled: Boolean = false,
    val autoUnlockEnrollDismissed: Boolean = false,
    val encryptedMasterPassword: String? = null,
    val encryptedMasterPasswordIv: String? = null,
    val autoUnlockAuthMode: Int = 0,
    val forceManualUnlockEvery48Hours: Boolean? = null,
    val lastManualMasterUnlockAt: Long? = null,
    val autoUnlockInvalidated: Boolean = false
)
