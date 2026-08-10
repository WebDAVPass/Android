package xzynine.WebDAVPass.Android.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 应用数据库
 *
 * 说明：
 * - version 6 → 7：新增 library_contexts、app_settings 表，webdav_configs 增加 directory 列。
 * - version 7 → 8：library_contexts 增加 keyFileUri 列，持久化密钥文件 URI。
 */
@Database(
    entities = [WebDavConfig::class, LibraryContextEntity::class, AppSetting::class],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun webDavConfigDao(): WebDavConfigDao
    abstract fun libraryContextDao(): LibraryContextDao
    abstract fun appSettingsDao(): AppSettingsDao

    companion object {
        /**
         * 6 → 7 迁移：
         * - 新建历史库表 library_contexts；
         * - 新建应用设置表 app_settings；
         * - webdav_configs 增加 directory 列（可空，旧行直接以 url 充当完整地址）。
         */
        val MIGRATION_6_7: Migration = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `library_contexts` (" +
                        "`id` TEXT NOT NULL PRIMARY KEY, " +
                        "`displayName` TEXT NOT NULL, " +
                        "`sourceType` TEXT NOT NULL, " +
                        "`localPath` TEXT NOT NULL, " +
                        "`remoteBaseUrl` TEXT, " +
                        "`remoteFilePath` TEXT, " +
                        "`username` TEXT, " +
                        "`password` TEXT, " +
                        "`autoSyncEnabled` INTEGER NOT NULL, " +
                        "`lastSyncAt` INTEGER, " +
                        "`lastRemoteModifiedAt` INTEGER, " +
                        "`lastSyncStatus` TEXT, " +
                        "`lastSyncError` TEXT, " +
                        "`lastUsedAt` INTEGER NOT NULL, " +
                        "`autoUnlockEnabled` INTEGER NOT NULL, " +
                        "`autoUnlockEnrollDismissed` INTEGER NOT NULL, " +
                        "`encryptedMasterPassword` TEXT, " +
                        "`encryptedMasterPasswordIv` TEXT, " +
                        "`autoUnlockAuthMode` INTEGER NOT NULL, " +
                        "`forceManualUnlockEvery48Hours` INTEGER, " +
                        "`lastManualMasterUnlockAt` INTEGER, " +
                        "`autoUnlockInvalidated` INTEGER NOT NULL" +
                        ")"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `app_settings` (" +
                        "`key` TEXT NOT NULL PRIMARY KEY, " +
                        "`value` TEXT NOT NULL" +
                        ")"
                )
                db.execSQL("ALTER TABLE `webdav_configs` ADD COLUMN `directory` TEXT")
            }
        }

        /**
         * 7 → 8 迁移：
         * - library_contexts 增加 keyFileUri 列（可空），持久化密钥文件 content URI。
         */
        val MIGRATION_7_8: Migration = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `library_contexts` ADD COLUMN `keyFileUri` TEXT")
            }
        }
    }
}
