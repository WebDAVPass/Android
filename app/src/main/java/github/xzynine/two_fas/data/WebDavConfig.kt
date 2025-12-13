package github.xzynine.two_fas.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * WebDAV配置实体类
 * 用于存储WebDAV服务器的配置信息到数据库
 * @property id 主键，自增
 * @property name 配置名称
 * @property url WebDAV服务器地址
 * @property username 用户名
 * @property password 密码（加密存储）
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
    @ColumnInfo(name = "username")
    var username: String,
    @ColumnInfo(name = "password")
    var password: String,
    @ColumnInfo(name = "sort_number")
    var sortNumber: Int = 0
)