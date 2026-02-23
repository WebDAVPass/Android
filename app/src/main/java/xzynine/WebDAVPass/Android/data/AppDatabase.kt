package xzynine.WebDAVPass.Android.data

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * OTP令牌数据库
 */
@Database(entities = [OtpToken::class, WebDavConfig::class, SyncState::class], version = 5, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun otpTokenDao(): OtpTokenDao
    abstract fun webDavConfigDao(): WebDavConfigDao
    abstract fun syncStateDao(): SyncStateDao
}