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
 * @property lastUsedAt 最近使用时间戳
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
    val lastUsedAt: Long = System.currentTimeMillis()
)
