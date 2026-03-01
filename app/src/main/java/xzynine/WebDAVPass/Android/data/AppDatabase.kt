package xzynine.WebDAVPass.Android.data

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * OTP令牌数据库
 */
@Database(entities = [WebDavConfig::class], version = 6, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun webDavConfigDao(): WebDavConfigDao
}