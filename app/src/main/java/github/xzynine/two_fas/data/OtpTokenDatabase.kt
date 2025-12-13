package github.xzynine.two_fas.data

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * OTP令牌数据库
 */
@Database(entities = [OtpToken::class, WebDavConfig::class], version = 2, exportSchema = false)
abstract class OtpTokenDatabase : RoomDatabase() {
    abstract fun otpTokenDao(): OtpTokenDao
    abstract fun webDavConfigDao(): WebDavConfigDao
}