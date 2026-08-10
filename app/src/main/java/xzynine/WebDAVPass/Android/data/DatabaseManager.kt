package xzynine.WebDAVPass.Android.data

import xzylib.base.util.Logger
import com.kunzisoft.keepass.database.element.Database
import java.io.File

/**
 * 数据库实例管理器（单例）
 *
 * 负责在内存中保持已解锁的数据库实例，避免每次操作重新解密，
 * 从而显著降低 CPU 和 I/O 开销。
 *
 * 使用场景：用户解锁库后，将 Database 实例存入此管理器；
 * 后续所有读写操作优先复用缓存实例，无需重新打开文件。
 */
object DatabaseManager {

    private const val LOG_TAG = "数据库管理"

    /**
     * 内部缓存条目，持有已解锁的数据库实例及必要元数据。
     */
    private data class CachedDatabase(
        /** 标准化的本地路径或 Uri 字符串 */
        val localPath: String,
        /** 主密码，用于写入时保存数据库 */
        val masterPassword: String,
        /** 已解锁的数据库实例 */
        val database: Database,
        /** 数据库操作临时缓存目录 */
        val cacheDirectory: File
    )

    @Volatile
    private var cached: CachedDatabase? = null

    /** 当前库的密钥文件字节（与主密码共同构成解锁凭据），随缓存一起生命周期管理。 */
    @Volatile
    private var keyFileData: ByteArray? = null

    /**
     * 设置当前库的密钥文件字节。
     * 解锁成功后调用；[close] 时自动清除。替换前显式置零旧字节，避免解密凭据残留于堆中直至 GC。
     */
    @Synchronized
    fun setKeyFileData(bytes: ByteArray?) {
        keyFileData?.fill(0)
        keyFileData = bytes
    }

    /**
     * 获取当前库的密钥文件字节。
     *
     * 返回副本，避免调用方就地修改内部数组影响后续保存使用的凭据。
     */
    @Synchronized
    fun getKeyFileData(): ByteArray? {
        return keyFileData?.copyOf()
    }

    /**
     * 存储已打开的数据库实例。
     *
     * 若已有不同数据库实例缓存，会先关闭旧实例再存入新实例。
     *
     * @param localPath 数据库路径或 Uri 字符串
     * @param masterPassword 主密码
     * @param database 已打开的数据库对象
     * @param cacheDirectory 对应的临时缓存目录
     */
    @Synchronized
    fun store(
        localPath: String,
        masterPassword: String,
        database: Database,
        cacheDirectory: File
    ) {
        val old = cached
        // 如果已有不同的数据库实例，先关闭旧实例
        if (old != null && old.database !== database) {
            runCatching { old.database.clearAndClose(old.cacheDirectory) }
            Logger.d(LOG_TAG, "关闭旧数据库缓存: ${old.localPath}")
        }
        cached = CachedDatabase(localPath, masterPassword, database, cacheDirectory)
        Logger.d(LOG_TAG, "已缓存数据库实例: $localPath")
    }

    /**
     * 尝试获取指定路径的缓存数据库实例。
     *
     * @param localPath 数据库路径或 Uri 字符串，需与存入时一致
     * @return 数据库实例与缓存目录的 Pair；若无缓存或路径不匹配则返回 null
     */
    @Synchronized
    fun tryGet(localPath: String): Pair<Database, File>? {
        val c = cached ?: return null
        if (c.localPath != localPath) return null
        return c.database to c.cacheDirectory
    }

    /**
     * 关闭并清除当前缓存的数据库实例，同时清除密钥文件凭据。
     *
     * 切换库、退出应用或真正"锁定"状态转移时调用，释放内存中的解密数据与凭据。
     */
    @Synchronized
    fun close() {
        val c = cached ?: return
        cached = null
        // 关闭前显式置零密钥文件字节，避免解密凭据残留于堆中直至 GC
        keyFileData?.fill(0)
        keyFileData = null
        runCatching { c.database.clearAndClose(c.cacheDirectory) }
        Logger.d(LOG_TAG, "已关闭并清除数据库缓存: ${c.localPath}")
    }

    /**
     * 只丢弃当前缓存的数据库实例（保留密钥文件凭据）。
     *
     * 用于"合并/保存过程中内存实例可能已部分变异"的失败路径：
     * 需要关闭缓存避免污染后续写回，但不能清除 [keyFileData]，否则
     * 密钥文件保护的库后续静默重开会因缺失密钥文件凭据而失败。
     */
    @Synchronized
    fun invalidateCacheKeepKeyFile() {
        val c = cached ?: return
        cached = null
        runCatching { c.database.clearAndClose(c.cacheDirectory) }
        Logger.d(LOG_TAG, "已失效数据库缓存（保留密钥文件凭据）: ${c.localPath}")
    }

    /**
     * 判断当前是否有数据库缓存。
     */
    fun isOpen(): Boolean = cached != null

    /**
     * 判断指定路径是否为当前缓存的数据库路径。
     *
     * @param localPath 需要检查的路径
     */
    fun isCurrentPath(localPath: String): Boolean = cached?.localPath == localPath
}
