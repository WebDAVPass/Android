package xzynine.WebDAVPass.Android.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * WebDAV配置数据访问对象
 * 用于执行WebDAV配置的数据库操作
 */
@Dao
interface WebDavConfigDao {
    /**
     * 获取所有WebDAV配置，返回Flow，支持实时更新
     */
    @Query("SELECT * FROM webdav_configs ORDER BY sort_number ASC")
    fun getAll(): Flow<List<WebDavConfig>>

    /**
     * 获取所有WebDAV配置，一次性获取
     */
    @Query("SELECT * FROM webdav_configs ORDER BY sort_number ASC")
    suspend fun getAllOnce(): List<WebDavConfig>

    /**
     * 根据ID获取WebDAV配置
     * @param id 配置ID
     */
    @Query("SELECT * FROM webdav_configs WHERE id = :id")
    suspend fun getById(id: Long): WebDavConfig?

    /**
     * 获取最后一个WebDAV配置的排序值
     */
    @Query("SELECT MAX(sort_number) FROM webdav_configs")
    suspend fun getLastSortNumber(): Int?

    /**
     * 插入WebDAV配置
     * @param config WebDAV配置对象
     * @return 插入的配置ID
     */
    @Insert
    suspend fun insert(config: WebDavConfig): Long

    /**
     * 更新WebDAV配置
     * @param config WebDAV配置对象
     */
    @Update
    suspend fun update(config: WebDavConfig)

    /**
     * 删除WebDAV配置
     * @param config WebDAV配置对象
     */
    @Query("DELETE FROM webdav_configs WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * 删除WebDAV配置
     * @param config WebDAV配置对象
     */
    suspend fun delete(config: WebDavConfig) {
        deleteById(config.id)
    }
}