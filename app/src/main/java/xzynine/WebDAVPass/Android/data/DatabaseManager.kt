package xzynine.WebDAVPass.Android.data

import xzylib.base.util.Logger
import com.kunzisoft.keepass.database.element.Database
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.File
import java.util.concurrent.atomic.AtomicLong

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
     * 写入代次计数器：每次成功把数据库落盘（saveDatabase）后单调递增。
     *
     * 用于解决"异步后台重建 + 并发用户写入"的双实例竞争：
     * - store() 时快照当时的代次；
     * - tryGet() 发现代次已推进（说明在缓存持有期间有另一个实例写过磁盘），
     *   则视为缓存过期，不返回该实例并主动 invalidate；
     * - scheduleCacheRebuildIfNeeded 重建成功后也再次比对代次，
     *   若 KDF 阻塞期间发生过写入则立即丢弃过期缓存。
     */
    private val saveGeneration = AtomicLong(0L)

    /** 当前写入代次（只读），供重建协程在开始/完成时做一致性校验。 */
    fun currentSaveGeneration(): Long = saveGeneration.get()

    /**
     * 由 [KdbxTokenRepository.saveDatabase] 在每次成功落盘后调用。
     *
     * 推进写入代次，并在保存的实例正是当前缓存实例时同步刷新缓存条目的代次快照：
     * 常规写路径（withDatabase 命中缓存 → 修改 → 保存）中缓存内容与磁盘一致，
     * 若只推进代次而不刷新快照，下一次 tryGet 会把刚保存过的缓存误判为过期丢弃，
     * 导致会话退化为每次操作重新 KDF 开库。其他实例（并发/未缓存路径）的写入
     * 只推进代次，缓存快照保持不变，tryGet 仍能识别过期。
     */
    @Synchronized
    fun onDatabaseSaved(database: Database) {
        val new = saveGeneration.incrementAndGet()
        Logger.d(LOG_TAG, "写入代次推进: $new")
        val c = cached
        if (c != null && c.database === database) {
            cached = c.copy(storeGeneration = new)
            Logger.d(LOG_TAG, "缓存代次快照已同步: $new")
        }
    }

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
        val cacheDirectory: File,
        /** 存入缓存时的 [saveGeneration] 快照，用于过期检测 */
        val storeGeneration: Long
    )

    @Volatile
    private var cached: CachedDatabase? = null

    /** 当前库的密钥文件字节（与主密码共同构成解锁凭据），随缓存一起生命周期管理。 */
    @Volatile
    private var keyFileData: ByteArray? = null

    /**
     * 缓存失效事件流：每当 [invalidateCacheKeepKeyFile] 或 [close] 真正丢弃缓存时 emit 一次。
     *
     * LibraryViewModel 在 init 中订阅此 flow，在失败路径（没有并发用户操作）
     * 立刻在后台调度一次预重建，避免下次用户操作时才触发与操作本身并发的重建。
     * extraBufferCapacity=3 应对极少数短时间连续失效的场景，
     * DROP_OLDEST 保证发射永不阻塞（onFailure 同步回调中 emit，不能挂起）。
     */
    private val _cacheInvalidatedEvents = MutableSharedFlow<Unit>(
        extraBufferCapacity = 3,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val cacheInvalidatedEvents: SharedFlow<Unit> = _cacheInvalidatedEvents.asSharedFlow()

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
     * 同时快照当前写入代次，供后续 tryGet / 重建完成时做一致性校验。
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
        val generation = saveGeneration.get()
        cached = CachedDatabase(localPath, masterPassword, database, cacheDirectory, generation)
        Logger.d(LOG_TAG, "已缓存数据库实例: $localPath (写入代次快照: $generation)")
    }

    /**
     * 尝试获取指定路径的缓存数据库实例。
     *
     * 若缓存是在某次写入之前存入的（快照代次 < 当前代次），视为已过期——
     * 说明期间有另一个 withDatabase(非缓存路径) 的实例成功落盘，缓存中的
     * 内存状态早于磁盘，继续使用会读到过期数据、写入时静默回滚并发修改。
     * 过期缓存立即 invalidate，调用方回落至「自行开库/操作/关库」路径。
     *
     * @param localPath 数据库路径或 Uri 字符串，需与存入时一致
     * @return 数据库实例与缓存目录的 Pair；若无缓存、路径不匹配或缓存过期则返回 null
     */
    @Synchronized
    fun tryGet(localPath: String): Pair<Database, File>? {
        val c = cached ?: return null
        if (c.localPath != localPath) return null
        val currentGen = saveGeneration.get()
        if (c.storeGeneration != currentGen) {
            // 缓存已过期：丢弃（但保留密钥文件凭据，与 invalidateCacheKeepKeyFile 一致）
            cached = null
            runCatching { c.database.clearAndClose(c.cacheDirectory) }
            Logger.w(
                LOG_TAG,
                "缓存代次过期（stored=${c.storeGeneration}, current=$currentGen），" +
                    "已失效缓存: ${c.localPath}"
            )
            // 过期不发 cacheInvalidatedEvent——我们并不需要再次重建，
            // 发事件会触发无意义的 scheduleCacheRebuildIfNeeded 循环。
            return null
        }
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
        // 锁定/切库也发失效事件——此时 UI 会把 isLibraryUnlocked 置 false，
        // LibraryViewModel 订阅方在 scheduleCacheRebuildIfNeeded 入口早退，不会做多余工作。
        _cacheInvalidatedEvents.tryEmit(Unit)
    }

    /**
     * 只丢弃当前缓存的数据库实例（保留密钥文件凭据）。
     *
     * 用于"合并/保存过程中内存实例可能已部分变异"的失败路径：
     * 需要关闭缓存避免污染后续写回，但不能清除 [keyFileData]，否则
     * 密钥文件保护的库后续静默重开会因缺失密钥文件凭据而失败。
     *
     * 丢弃后通过 [cacheInvalidatedEvents] 通知订阅方：失败路径返回前同步
     * emit 一次，订阅方立刻在后台调度预重建；该调度发生在用户下一次
     * 操作之前，避免与操作并发打开两个实例形成代次竞争。
     */
    @Synchronized
    fun invalidateCacheKeepKeyFile() {
        val c = cached ?: return
        cached = null
        runCatching { c.database.clearAndClose(c.cacheDirectory) }
        Logger.d(LOG_TAG, "已失效数据库缓存（保留密钥文件凭据）: ${c.localPath}")
        // tryEmit 非挂起，onFailure 回调内同步发射安全。BufferOverflow=DROP_OLDEST 保证永不阻塞。
        _cacheInvalidatedEvents.tryEmit(Unit)
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
