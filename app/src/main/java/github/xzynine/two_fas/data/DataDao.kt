package github.xzynine.two_fas.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.room.Update
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * OTP令牌数据访问对象
 */
@Dao
interface OtpTokenDao {

    @Query("select * from otp_tokens order by ordinal")
    fun getAll(): Flow<List<OtpToken>>

    @Query("select * from otp_tokens order by ordinal")
    suspend fun getAllOnce(): List<OtpToken>

    @Query("select * from otp_tokens where id = :id")
    fun get(id: Long): Flow<OtpToken?>

    @Query("select ordinal from otp_tokens order by ordinal desc limit 1")
    fun getLastOrdinal(): Long?



    @Query("select count(*) from otp_tokens where secret = :secret and algorithm = :algorithm and digits = :digits and period = :period")
    suspend fun countBySecretAlgorithmDigitsPeriod(secret: String, algorithm: String, digits: Int, period: Int): Int

    /**
     * 根据唯一标识查询令牌
     * 唯一标识由secret、algorithm、digits和period生成
     */
    @Query("select * from otp_tokens where secret = :secret and algorithm = :algorithm and digits = :digits and period = :period limit 1")
    suspend fun getByUniqueIdentifier(secret: String, algorithm: String, digits: Int, period: Int): OtpToken?

    @Query("delete from otp_tokens where id = :id")
    suspend fun deleteById(id: Long): Void

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(otpTokenList: List<OtpToken>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(otpTokenList: OtpToken)

    @Update
    suspend fun update(otpTokenList: OtpToken)

    @Query("update otp_tokens set ordinal = :ordinal where id = :id")
    suspend fun updateOrdinal(id: Long, ordinal: Long)

    /**
     * 递增计数器（使用原始查询避免触发Flow更新）
     */
    suspend fun incrementCounter(id: Long) {
        incrementCounterRaw(
            SimpleSQLiteQuery("update otp_tokens set counter = counter + 1 where id = ?",
                arrayOf(id))
        )
    }

    @RawQuery
    suspend fun incrementCounterRaw(query: SupportSQLiteQuery): Int

    @Transaction
    suspend fun movePairs(pairs : List<Pair<Long,Long>>){
        for(pair in pairs.listIterator()) {
            withContext(Dispatchers.IO) {
                val token1 = get(pair.first).first()
                val token2 = get(pair.second).first()

                if (token1 == null || token2 == null) {
                    return@withContext
                }
                updateOrdinal(pair.first, token2.ordinal)
                updateOrdinal(pair.second, token1.ordinal)
            }
        }
    }
}

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