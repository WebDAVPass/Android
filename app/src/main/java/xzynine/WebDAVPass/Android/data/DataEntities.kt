package xzynine.WebDAVPass.Android.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * OTP令牌数据类
 *
 * 说明：令牌实际存储在 kdbx 库文件中，仅作为内存模型使用，不注册 Room 实体。
 */
data class OtpToken (
    val id: Long = 0,
    val ordinal: Long,
    val issuer: String?,
    val label: String,
    val description: String? = null,
    val imagePath: String?,
    val tokenType: OtpTokenType,
    val algorithm: String,
    val secret: String,
    val digits: Int,
    val counter: Long,
    val period: Int,
    val encryptionType: EncryptionType,
    val uniqueId: String
)

/**
 * WebDAV配置实体类
 * 用于存储WebDAV服务器的配置信息到数据库
 * @property id 主键，自增
 * @property name 配置名称
 * @property url WebDAV服务器根地址（不含目录）
 * @property directory 远端相对目录（可空；为空时 url 整体充当完整地址，兼容旧数据）
 * @property username 用户名
 * @property password 密码（加密存储，可还原）
 * @property sortNumber 排序号
 */
@Entity(tableName = "webdav_configs")
data class WebDavConfig(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "name")
    var name: String,
    @ColumnInfo(name = "url")
    var url: String,
    @ColumnInfo(name = "directory")
    var directory: String? = null,
    @ColumnInfo(name = "username")
    var username: String,
    @ColumnInfo(name = "password")
    var password: String,
    @ColumnInfo(name = "sort_number")
    var sortNumber: Int = 0
) {
    /**
     * 解析完整的基础地址。
     *
     * 说明：目录为空时直接以 url 充当（旧数据向前兼容）；否则拼接 url + 目录。
     *
     * @return 以 "/" 结尾的完整基础地址
     */
    fun resolveBaseUrl(): String {
        val dir = directory?.trim()?.trim('/')
        return if (dir.isNullOrBlank()) {
            if (url.endsWith("/")) url else "$url/"
        } else {
            val base = if (url.endsWith("/")) url else "$url/"
            "$base$dir/"
        }
    }
}

/**
 * 应用设置实体类（key-value 存储）
 */
@Entity(tableName = "app_settings")
data class AppSetting(
    @PrimaryKey
    val key: String,
    val value: String
)