package xzynine.WebDAVPass.Android.data

import android.content.Context
import androidx.room.Room

/**
 * 应用数据库单例持有者
 *
 * 说明：统一管理 AppDatabase 实例，供 ViewModel 与数据存储层共用同一数据库连接。
 */
object AppDatabaseHolder {

    @Volatile
    private var INSTANCE: AppDatabase? = null

    /**
     * 获取应用数据库单例
     * @param context 上下文（使用 applicationContext）
     */
    fun getInstance(context: Context): AppDatabase {
        return INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "webdav_config_database"
            )
                .addMigrations(AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_7_8)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
                .also { INSTANCE = it }
        }
    }
}
