package xzynine.WebDAVPass.Android.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 历史库实体（对应 [LibraryContext]）
 *
 * 说明：
 * - username 明文存储；
 * - password 使用 [WebDavPasswordCipher] 加密存储（可还原，用于云端访问）；
 * - sourceType 以字符串枚举名存储。
 */
@Entity(tableName = "library_contexts")
data class LibraryContextEntity(
    @PrimaryKey
    val id: String,
    val displayName: String,
    val sourceType: String,
    val localPath: String,
    val remoteBaseUrl: String?,
    val remoteFilePath: String?,
    val username: String?,
    val password: String?,
    val autoSyncEnabled: Boolean,
    val lastSyncAt: Long?,
    val lastRemoteModifiedAt: Long?,
    val lastSyncStatus: String?,
    val lastSyncError: String?,
    val lastUsedAt: Long,
    val autoUnlockEnabled: Boolean,
    val autoUnlockEnrollDismissed: Boolean,
    val encryptedMasterPassword: String?,
    val encryptedMasterPasswordIv: String?,
    val autoUnlockAuthMode: Int,
    val forceManualUnlockEvery48Hours: Boolean?,
    val lastManualMasterUnlockAt: Long?,
    val autoUnlockInvalidated: Boolean
)

/**
 * 实体转领域模型。
 *
 * 说明：password 为密文时解密还原；解密失败（密钥缺失等异常）时置 null，
 * 由上层按"密码不可用"处理（如提示重新输入）。
 */
fun LibraryContextEntity.toLibraryContext(): LibraryContext {
    return LibraryContext(
        id = id,
        displayName = displayName,
        sourceType = runCatching { LibrarySourceType.valueOf(sourceType) }
            .getOrDefault(LibrarySourceType.LOCAL),
        localPath = localPath,
        remoteBaseUrl = remoteBaseUrl,
        remoteFilePath = remoteFilePath,
        username = username,
        password = password?.let { WebDavPasswordCipher.decrypt(it) },
        autoSyncEnabled = autoSyncEnabled,
        lastSyncAt = lastSyncAt,
        lastRemoteModifiedAt = lastRemoteModifiedAt,
        lastSyncStatus = lastSyncStatus,
        lastSyncError = lastSyncError,
        lastUsedAt = lastUsedAt,
        autoUnlockEnabled = autoUnlockEnabled,
        autoUnlockEnrollDismissed = autoUnlockEnrollDismissed,
        encryptedMasterPassword = encryptedMasterPassword,
        encryptedMasterPasswordIv = encryptedMasterPasswordIv,
        autoUnlockAuthMode = autoUnlockAuthMode,
        forceManualUnlockEvery48Hours = forceManualUnlockEvery48Hours,
        lastManualMasterUnlockAt = lastManualMasterUnlockAt,
        autoUnlockInvalidated = autoUnlockInvalidated
    )
}

/**
 * 领域模型转实体。
 *
 * 说明：password 非空时统一加密落库，保证库中不出现明文。
 */
fun LibraryContext.toEntity(): LibraryContextEntity {
    return LibraryContextEntity(
        id = id,
        displayName = displayName,
        sourceType = sourceType.name,
        localPath = localPath,
        remoteBaseUrl = remoteBaseUrl,
        remoteFilePath = remoteFilePath,
        username = username,
        password = password?.takeIf { it.isNotBlank() }?.let { WebDavPasswordCipher.encrypt(it) },
        autoSyncEnabled = autoSyncEnabled,
        lastSyncAt = lastSyncAt,
        lastRemoteModifiedAt = lastRemoteModifiedAt,
        lastSyncStatus = lastSyncStatus,
        lastSyncError = lastSyncError,
        lastUsedAt = lastUsedAt,
        autoUnlockEnabled = autoUnlockEnabled,
        autoUnlockEnrollDismissed = autoUnlockEnrollDismissed,
        encryptedMasterPassword = encryptedMasterPassword,
        encryptedMasterPasswordIv = encryptedMasterPasswordIv,
        autoUnlockAuthMode = autoUnlockAuthMode,
        forceManualUnlockEvery48Hours = forceManualUnlockEvery48Hours,
        lastManualMasterUnlockAt = lastManualMasterUnlockAt,
        autoUnlockInvalidated = autoUnlockInvalidated
    )
}
