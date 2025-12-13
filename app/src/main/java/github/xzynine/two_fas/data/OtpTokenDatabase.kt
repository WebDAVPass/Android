package github.xzynine.two_fas.data

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * OTP令牌数据库
 */
@Database(entities = [OtpToken::class], version = 1, exportSchema = false)
abstract class OtpTokenDatabase : RoomDatabase() {
    abstract fun otpTokenDao(): OtpTokenDao
}