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

    @Query("select count(*) from otp_tokens where secret = :secret")
    suspend fun countBySecret(secret: String): Int
    
    @Query("select count(*) from otp_tokens where secret = :secret and issuer = :issuer and label = :label")
    suspend fun countBySecretIssuerLabel(secret: String, issuer: String?, label: String): Int

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