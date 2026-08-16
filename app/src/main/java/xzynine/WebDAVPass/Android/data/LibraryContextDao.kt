package xzynine.WebDAVPass.Android.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * 历史库数据访问对象
 */
@Dao
interface LibraryContextDao {
    /**
     * 获取全部历史库（按最近使用倒序），返回 Flow 支持实时更新
     */
    @Query("SELECT * FROM library_contexts ORDER BY lastUsedAt DESC")
    fun observeAll(): Flow<List<LibraryContextEntity>>

    /**
     * 获取全部历史库，一次性获取
     */
    @Query("SELECT * FROM library_contexts")
    suspend fun getAllOnce(): List<LibraryContextEntity>

    /**
     * 根据 ID 获取历史库
     * @param id 历史库 ID
     */
    @Query("SELECT * FROM library_contexts WHERE id = :id")
    suspend fun getById(id: String): LibraryContextEntity?

    /**
     * 插入或替换历史库（按主键 id）
     * @param entity 历史库实体
     */
    @Upsert
    suspend fun upsert(entity: LibraryContextEntity)

    /**
     * 批量插入或替换历史库（按主键 id）
     * @param entities 历史库实体列表
     */
    @Upsert
    suspend fun upsertAll(entities: List<LibraryContextEntity>)

    /**
     * 按 ID 删除历史库
     * @param id 历史库 ID
     */
    @Query("DELETE FROM library_contexts WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * 批量删除历史库
     * @param ids 历史库 ID 集合
     */
    @Query("DELETE FROM library_contexts WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: Collection<String>)
}
