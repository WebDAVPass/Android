package github.xzynine.two_fas.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

/**
 * 同步状态数据访问对象，用于操作同步状态数据
 */
@Dao
interface SyncStateDao {
    /**
     * 获取同步状态
     * @return 同步状态对象
     */
    @Query("SELECT * FROM sync_state WHERE id = 1")
    suspend fun getSyncState(): SyncState?
    
    /**
     * 插入或更新同步状态
     * @param syncState 同步状态对象
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(syncState: SyncState)
    
    /**
     * 更新同步状态
     * @param syncState 同步状态对象
     */
    @Update
    suspend fun update(syncState: SyncState)
    
    /**
     * 保存上次同步时间
     * @param lastSyncTime 上次同步时间
     */
    @Query("UPDATE sync_state SET last_sync_time = :lastSyncTime WHERE id = 1")
    suspend fun saveLastSyncTime(lastSyncTime: Long)
    
    /**
     * 保存远程元数据的最后更新时间
     * @param remoteLastUpdated 远程元数据的最后更新时间
     */
    @Query("UPDATE sync_state SET remote_last_updated = :remoteLastUpdated WHERE id = 1")
    suspend fun saveRemoteLastUpdated(remoteLastUpdated: String?)
    
    /**
     * 清除同步状态
     */
    @Query("DELETE FROM sync_state WHERE id = 1")
    suspend fun clearSyncState()
}
