package github.xzynine.two_fas.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 同步状态实体类，用于存储同步相关的状态信息
 * @property id 主键，固定为1
 * @property lastSyncTime 上次同步时间
 * @property remoteLastUpdated 远程元数据的最后更新时间
 */
@Entity(tableName = "sync_state")
data class SyncState(
    @PrimaryKey val id: Long = 1, // 固定使用1作为主键，确保只有一条记录
    @ColumnInfo(name = "last_sync_time") val lastSyncTime: Long,
    @ColumnInfo(name = "remote_last_updated") val remoteLastUpdated: String?
)
