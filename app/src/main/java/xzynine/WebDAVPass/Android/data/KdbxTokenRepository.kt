package xzynine.WebDAVPass.Android.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import xzylib.base.util.Logger
import com.kunzisoft.keepass.database.element.Database
import com.kunzisoft.keepass.database.element.DateInstant
import com.kunzisoft.keepass.database.element.Entry
import com.kunzisoft.keepass.database.element.Attachment
import com.kunzisoft.keepass.database.element.Field
import com.kunzisoft.keepass.database.element.icon.IconImage
import com.kunzisoft.keepass.database.element.Group
import com.kunzisoft.keepass.database.element.MasterCredential
import com.kunzisoft.keepass.database.element.database.DatabaseVersioned
import com.kunzisoft.keepass.database.element.security.ProtectedString
import com.kunzisoft.keepass.hardware.HardwareKey
import com.kunzisoft.keepass.model.EntryInfo
import com.kunzisoft.keepass.model.GroupInfo
import com.kunzisoft.keepass.otp.OtpElement
import com.kunzisoft.keepass.otp.OtpEntryFields
import com.kunzisoft.keepass.otp.OtpEntryFields.isOTP
import com.kunzisoft.keepass.otp.OtpType
import com.kunzisoft.keepass.otp.TokenCalculator
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlin.math.abs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow

class KdbxTokenRepository(context: Context) {

    private val appContext: Context = context.applicationContext

    @Volatile
    private var lastUnlockErrorMessage: String? = null

    companion object {
        private const val LOG_TAG = "解锁"
        private const val SYNC_LOG_TAG = "同步"
        private const val DATABASE_NAME = "WebDavPass"
        private const val ROOT_GROUP_NAME = "WebDavPass"
        private const val RECYCLE_BIN_FALLBACK_TITLE = "回收站"
        private const val SMALL_BINARY_SIZE = 1024 * 1024

        /** 附件查看/读取上限：1 MiB，超过则拒绝返回以避免 OOM */
        const val MAX_ATTACHMENT_BYTES = 1024 * 1024

        /** 附件导入（写库）上限：10 MiB */
        const val MAX_ATTACHMENT_IMPORT_BYTES = 10 * 1024 * 1024
    }

    private val emptyChallengeResponseRetriever: (HardwareKey, ByteArray?) -> ByteArray = { _, _ ->
        ByteArray(0)
    }

    /**
     * 数据库存储定位。
     */
    private sealed interface DatabaseLocation {
        data class FileLocation(val file: File) : DatabaseLocation
        data class UriLocation(val uri: Uri) : DatabaseLocation
    }

    fun initializeDatabase(localPath: String, masterPassword: String) {
        val location = resolveLocation(localPath)
        when (location) {
            is DatabaseLocation.FileLocation -> {
                val file = location.file
                file.parentFile?.mkdirs()
                if (file.exists() && file.length() > 0) {
                    return
                }
                file.writeBytes(createDatabaseBytes(masterPassword))
            }

            is DatabaseLocation.UriLocation -> {
                if (hasUriData(location.uri)) {
                    return
                }
                openOutputStream(location).use { output ->
                    output.write(createDatabaseBytes(masterPassword))
                }
            }
        }
    }

    fun createDatabaseBytes(masterPassword: String): ByteArray {
        val database = Database().apply {
            createData(DATABASE_NAME, ROOT_GROUP_NAME, null)
        }

        return ByteArrayOutputStream().use { outputStream ->
            val cacheFile = File.createTempFile("kdbx-create-", ".tmp")
            try {
                database.saveData(
                    cacheFile = cacheFile,
                    databaseOutputStream = { outputStream },
                    isNewLocation = true,
                    masterCredential = MasterCredential(password = masterPassword),
                    challengeResponseRetriever = emptyChallengeResponseRetriever
                )
                outputStream.toByteArray()
            } finally {
                runCatching { database.clearAndClose() }
                runCatching { cacheFile.delete() }
            }
        }
    }

    fun validatePassword(localPath: String, masterPassword: String): Boolean {
        lastUnlockErrorMessage = null
        return runCatching {
            val location = resolveLocation(localPath)
            ensureLocationInitialized(location, masterPassword)

            // 此路径已有缓存实例，直接验证可访问性，无需重新解密
            val existing = DatabaseManager.tryGet(localPath)
            if (existing != null) {
                existing.first.rootGroup
                return@runCatching true
            }

            // 关闭其他路径的旧缓存
            DatabaseManager.close()

            // 打开数据库后缓存，不立即关闭，供后续操作复用
            val (database, cacheDirectory) = openDatabase(location, masterPassword)
            try {
                database.rootGroup // 验证根组可访问
            } catch (e: Exception) {
                // 打开成功但验证失败时，释放资源
                database.clearAndClose(cacheDirectory)
                throw e
            }
            DatabaseManager.store(localPath, masterPassword, database, cacheDirectory)
            true
        }.onFailure {
            val hint = buildLocationHint(resolveLocation(localPath))
            lastUnlockErrorMessage = "${it.javaClass.simpleName}: ${it.message ?: "unknown"} | $hint"
            Logger.e(LOG_TAG, "validatePassword failed, path=$localPath, hint=$hint, message=${it.message}", it)
        }.getOrDefault(false)
    }

    fun getLastUnlockErrorMessage(): String? {
        return lastUnlockErrorMessage
    }

    fun loadTokens(localPath: String, masterPassword: String): List<OtpToken> {
        return withDatabase(localPath, masterPassword, saveAfter = false) { db ->
            val entries = collectEntriesOutsideRecycleBin(db, db.rootGroup)
            entries.mapNotNull { entry -> toToken(entry) }
                .sortedBy { it.ordinal }
        }
    }

    /**
     * 流式加载令牌。
     *
     * 对比 [loadTokens]：此方法以 Flow 形式逐条发射，调用方可在收集过程中增量更新 UI；
     * 若 [DatabaseManager] 已有缓存实例，则跳过解密直接读取，性能更优。
     */
    fun loadTokensFlow(localPath: String, masterPassword: String): Flow<OtpToken> = channelFlow {
        withDatabase(localPath, masterPassword, saveAfter = false) { db ->
            collectEntriesOutsideRecycleBin(db, db.rootGroup)
                .mapNotNull { entry -> toToken(entry) }
                .sortedBy { it.ordinal }
                .forEach { token -> trySend(token) }
        }
    }

    /**
     * 读取数据库中全部条目摘要（不包含键值详情）。
     */
    fun loadPasswordEntries(localPath: String, masterPassword: String): List<PasswordEntry> {
        return withDatabase(localPath, masterPassword, saveAfter = false) { db ->
            buildPasswordEntries(
                database = db,
                entries = collectEntriesOutsideRecycleBin(db, db.rootGroup),
                groups = emptyList(),
                includeFieldDetails = false
            )
        }
    }

    /**
     * 读取数据库中一级分组可见条目摘要（根组直系条目 + 一级子组条目）。
     */
    fun loadPasswordEntriesByTopLevel(localPath: String, masterPassword: String): List<PasswordEntry> {
        return withDatabase(localPath, masterPassword, saveAfter = false) { db ->
            val rootGroup = db.rootGroup
            buildPasswordEntries(
                database = db,
                entries = rootGroup?.getChildEntries()?.filterNot { entry -> isEntryInRecycleBin(db, entry) } ?: emptyList(),
                groups = rootGroup?.getChildGroups()?.filterNot { group -> db.groupIsInRecycleBin(group) } ?: emptyList(),
                includeFieldDetails = false
            )
        }
    }

    /**
     * 读取指定分组下的直系条目与子分组摘要。
     */
    fun loadPasswordEntriesByGroup(localPath: String, masterPassword: String, groupStableId: Long): List<PasswordEntry> {
        return withDatabase(localPath, masterPassword, saveAfter = false) { db ->
            val rootGroup = db.rootGroup
            val targetGroup = findGroupByStableId(rootGroup, groupStableId)
                ?: return@withDatabase emptyList()
            if (db.groupIsInRecycleBin(targetGroup)) {
                return@withDatabase emptyList()
            }

            buildPasswordEntries(
                database = db,
                entries = targetGroup.getChildEntries().filterNot { entry -> isEntryInRecycleBin(db, entry) },
                groups = targetGroup.getChildGroups().filterNot { group -> db.groupIsInRecycleBin(group) },
                includeFieldDetails = false
            )
        }
    }

    /**
     * 读取回收站中所有条目摘要（仅条目，不包含文件夹占位）。
     */
    fun loadRecentDeletedPasswordEntries(localPath: String, masterPassword: String): List<PasswordEntry> {
        return withDatabase(localPath, masterPassword, saveAfter = false) { db ->
            val recycleBin = db.recycleBin ?: return@withDatabase emptyList()
            buildPasswordEntries(
                database = db,
                entries = collectEntries(recycleBin),
                groups = emptyList(),
                includeFieldDetails = false
            )
        }
    }

    /**
     * 按稳定 ID 读取单条密码详情（包含全部键值，支持回收站条目）。
     */
    fun loadPasswordEntryById(localPath: String, masterPassword: String, entryId: Long): PasswordEntry? {
        return withDatabase(localPath, masterPassword, saveAfter = false) { db ->
            val entry = findEntryByStableId(db, entryId, includeRecycleBin = true)
                ?: return@withDatabase null
            buildPasswordEntries(
                database = db,
                entries = listOf(entry),
                groups = emptyList(),
                includeFieldDetails = true
            ).firstOrNull()
        }
    }

    /**
     * 按稳定 ID 读取条目编辑草稿。
     */
    fun loadPasswordEntryDraft(localPath: String, masterPassword: String, entryId: Long): PasswordEntryEditDraft? {
        return withDatabase(localPath, masterPassword, saveAfter = false) { db ->
            val entry = findEntryByStableId(db, entryId, includeRecycleBin = true)
                ?: return@withDatabase null
            val entryInfo = entry.getEntryInfo(db, raw = true, removeTemplateConfiguration = false)
            val attachmentPool = db.attachmentPool
            val attachments = entry.getAttachments(attachmentPool).mapNotNull { attachment ->
                val name = attachment.name
                if (name.isBlank()) null else EditableAttachmentDraft(name = name)
            }
            val iconUuid = entry.icon.custom.uuid
            val customIconUuid = if (iconUuid == DatabaseVersioned.UUID_ZERO) null else iconUuid.toString()
            PasswordEntryEditDraft(
                entryId = toStableId(entry),
                parentGroupId = toStableParentGroupId(entry.parent),
                title = entryInfo.title,
                username = entryInfo.username,
                password = entryInfo.password,
                url = entryInfo.url,
                notes = entryInfo.notes,
                customFields = entryInfo.customFields.map { field ->
                    EditableFieldDraft(
                        name = field.name,
                        value = field.protectedValue.stringValue,
                        isProtected = field.protectedValue.isProtected
                    )
                },
                attachments = attachments,
                expiryTime = if (entry.expires) entry.expiryTime.toMilliseconds() else null,
                customIconUuid = customIconUuid,
                iconStandardId = entry.icon.standard.id
            )
        }
    }

    /**
     * 读取条目的历史版本摘要列表（按 KDBX 存储顺序返回）。
     */
    fun loadEntryHistory(localPath: String, masterPassword: String, entryId: Long): List<EntryHistoryInfo> {
        return withDatabase(localPath, masterPassword, saveAfter = false) { db ->
            val entry = findEntryByStableId(db, entryId, includeRecycleBin = true)
                ?: return@withDatabase emptyList()
            entry.getHistory().mapIndexed { index, historyEntry ->
                val info = historyEntry.getEntryInfo(db, raw = true, removeTemplateConfiguration = false)
                EntryHistoryInfo(
                    index = index,
                    lastModificationTime = historyEntry.lastModificationTime.toMilliseconds(),
                    title = info.title,
                    username = info.username,
                    passwordSet = info.password.isNotBlank(),
                    url = info.url,
                    notes = info.notes,
                    customFieldCount = info.customFields.size,
                    attachmentCount = info.attachments.size
                )
            }
        }
    }

    /**
     * 将指定历史版本恢复为条目的当前内容。
     *
     * 恢复后当前条目内容与历史版本一致，且原有历史记录全部保留（新版本会追加进历史）。
     * 逻辑参照 KeePassDX RestoreEntryHistoryDatabaseRunnable。
     */
    fun restoreEntryFromHistory(
        localPath: String,
        masterPassword: String,
        entryId: Long,
        historyIndex: Int
    ): Boolean {
        return withDatabase(localPath, masterPassword, saveAfter = true) { db ->
            val entry = findEntryByStableId(db, entryId, includeRecycleBin = false)
                ?: return@withDatabase false
            val history = entry.getHistory()
            if (historyIndex !in history.indices) {
                return@withDatabase false
            }
            val historyToRestore = history[historyIndex]
            // 将主条目现有历史复制进待恢复版本，避免恢复操作丢失历史记录
            entry.getHistory().forEach { historyToRestore.addEntryToHistory(it) }
            val entryInfo = historyToRestore.getEntryInfo(db, raw = true, removeTemplateConfiguration = false)
            entry.setEntryInfo(db, entryInfo)
            db.updateEntry(entry)
            true
        }
    }

    /**
     * 按稳定 ID 读取分组编辑草稿。
     */
    fun loadPasswordGroupDraft(localPath: String, masterPassword: String, groupId: Long): PasswordGroupEditDraft? {
        return withDatabase(localPath, masterPassword, saveAfter = false) { db ->
            val group = findGroupByStableId(db.rootGroup, groupId)
                ?: return@withDatabase null
            if (group.parent == null) {
                return@withDatabase null
            }
            val groupInfo = group.getGroupInfo()
            PasswordGroupEditDraft(
                groupId = toStableGroupId(group),
                parentGroupId = toStableParentGroupId(group.parent),
                title = groupInfo.title,
                notes = groupInfo.notes ?: ""
            )
        }
    }

    /**
     * 新建条目并返回稳定 ID。
     */
    fun createPasswordEntry(localPath: String, masterPassword: String, draft: PasswordEntryEditDraft): Long? {
        return withDatabase(localPath, masterPassword, saveAfter = true) { db ->
            val parent = resolveParentGroup(db, draft.parentGroupId) ?: return@withDatabase null
            if (db.groupIsInRecycleBin(parent)) {
                return@withDatabase null
            }

            val entry = db.createEntry() ?: return@withDatabase null
            val entryInfo = EntryInfo().apply {
                title = draft.title
                username = draft.username
                password = draft.password
                url = draft.url
                notes = draft.notes
                customFields = mergeCustomFields(db, entry, draft.customFields)
                attachments = buildEntryInfoAttachments(db, entry, draft).toMutableList()
                applyExpiryAndIcon(this, db, draft)
            }
            entry.setEntryInfo(db, entryInfo)
            db.addEntryTo(entry, parent)
            toStableId(entry)
        }
    }

    /**
     * 更新条目。
     */
    fun updatePasswordEntry(localPath: String, masterPassword: String, draft: PasswordEntryEditDraft): Boolean {
        return withDatabase(localPath, masterPassword, saveAfter = true) { db ->
            val entryId = draft.entryId ?: return@withDatabase false
            val entry = findEntryByStableId(db, entryId, includeRecycleBin = false)
                ?: return@withDatabase false

            val entryInfo = entry.getEntryInfo(db, raw = true, removeTemplateConfiguration = false).apply {
                title = draft.title
                username = draft.username
                password = draft.password
                url = draft.url
                notes = draft.notes
                customFields = mergeCustomFields(db, entry, draft.customFields)
                attachments = buildEntryInfoAttachments(db, entry, draft).toMutableList()
                applyExpiryAndIcon(this, db, draft)
            }
            entry.setEntryInfo(db, entryInfo)
            // 清理被删除附件后遗留的孤儿二进制，避免 KDBX 体积膨胀
            db.removeUnlinkedAttachments()

            val targetParent = resolveParentGroup(db, draft.parentGroupId) ?: return@withDatabase false
            if (db.groupIsInRecycleBin(targetParent)) {
                return@withDatabase false
            }

            val currentParent = entry.parent
            val currentParentId = toStableParentGroupId(currentParent)
            val targetParentId = toStableParentGroupId(targetParent)
            if (currentParent != null && currentParentId != targetParentId) {
                db.removeEntryFrom(entry, currentParent)
                db.addEntryTo(entry, targetParent)
            }

            db.updateEntry(entry)
            true
        }
    }

    /**
     * 删除条目（仅回收站删除）。
     */
    fun deletePasswordEntry(localPath: String, masterPassword: String, entryId: Long): Boolean {
        return withDatabase(localPath, masterPassword, saveAfter = true) { db ->
            val entry = findEntryByStableId(db, entryId, includeRecycleBin = false)
                ?: return@withDatabase false
            if (!db.canRecycle(entry)) {
                return@withDatabase false
            }
            val oldParent = entry.parent
            db.recycle(entry, resolveRecycleBinTitle(db))
            entry.setPreviousParentGroup(oldParent)
            true
        }
    }

    /**
     * 新建分组并返回稳定 ID。
     */
    fun createPasswordGroup(localPath: String, masterPassword: String, draft: PasswordGroupEditDraft): Long? {
        return withDatabase(localPath, masterPassword, saveAfter = true) { db ->
            val parent = resolveParentGroup(db, draft.parentGroupId) ?: return@withDatabase null
            if (db.groupIsInRecycleBin(parent)) {
                return@withDatabase null
            }

            val group = db.createGroup(virtual = false) ?: return@withDatabase null
            val groupInfo = GroupInfo().apply {
                title = draft.title
                notes = draft.notes
            }
            group.setGroupInfo(groupInfo)
            db.addGroupTo(group, parent)
            toStableGroupId(group)
        }
    }

    /**
     * 更新分组。
     */
    fun updatePasswordGroup(localPath: String, masterPassword: String, draft: PasswordGroupEditDraft): Boolean {
        return withDatabase(localPath, masterPassword, saveAfter = true) { db ->
            val groupId = draft.groupId ?: return@withDatabase false
            val group = findGroupByStableId(db.rootGroup, groupId)
                ?: return@withDatabase false
            if (group.parent == null || db.groupIsInRecycleBin(group)) {
                return@withDatabase false
            }

            val groupInfo = group.getGroupInfo().apply {
                title = draft.title
                notes = draft.notes
            }
            group.setGroupInfo(groupInfo)

            val targetParent = resolveParentGroup(db, draft.parentGroupId) ?: return@withDatabase false
            if (db.groupIsInRecycleBin(targetParent)) {
                return@withDatabase false
            }

            val currentParent = group.parent
            val currentParentId = toStableParentGroupId(currentParent)
            val targetParentId = toStableParentGroupId(targetParent)
            if (currentParent != null && currentParentId != targetParentId) {
                db.removeGroupFrom(group, currentParent)
                db.addGroupTo(group, targetParent)
            }

            db.updateGroup(group)
            true
        }
    }

    /**
     * 删除分组（仅回收站删除）。
     */
    fun deletePasswordGroup(localPath: String, masterPassword: String, groupId: Long): Boolean {
        return withDatabase(localPath, masterPassword, saveAfter = true) { db ->
            val group = findGroupByStableId(db.rootGroup, groupId)
                ?: return@withDatabase false
            if (group.parent == null || db.groupIsInRecycleBin(group)) {
                return@withDatabase false
            }
            if (!db.canRecycle(group)) {
                return@withDatabase false
            }
            val oldParent = group.parent
            db.recycle(group, resolveRecycleBinTitle(db))
            group.setPreviousParentGroup(oldParent)
            true
        }
    }

    /**
     * 从回收站批量恢复条目到原分组，原分组已不存在时恢复到根分组。
     *
     * @return 成功恢复的数量
     */
    fun restoreRecentDeletedPasswordEntries(localPath: String, masterPassword: String, entryIds: List<Long>): Int {
        return withDatabase(localPath, masterPassword, saveAfter = true) { db ->
            val recycleBin = db.recycleBin ?: return@withDatabase 0
            var restored = 0
            entryIds.forEach { entryId ->
                val entry = findEntryByStableId(db, entryId, includeRecycleBin = true)
                    ?: return@forEach
                val parent = entry.parent ?: return@forEach
                if (recycleBin != parent) {
                    return@forEach
                }
                val target = findGroupByUuid(db.rootGroup, entry.previousParentGroup)
                    ?: db.rootGroup
                    ?: return@withDatabase restored
                db.undoRecycle(entry, target)
                restored++
            }
            restored
        }
    }

    /**
     * 从回收站批量永久删除条目。
     *
     * @return 成功删除的数量
     */
    fun permanentlyDeleteRecentDeletedPasswordEntries(localPath: String, masterPassword: String, entryIds: List<Long>): Int {
        return withDatabase(localPath, masterPassword, saveAfter = true) { db ->
            val recycleBin = db.recycleBin ?: return@withDatabase 0
            var deleted = 0
            entryIds.forEach { entryId ->
                val entry = findEntryByStableId(db, entryId, includeRecycleBin = true)
                    ?: return@forEach
                val parent = entry.parent ?: return@forEach
                if (recycleBin != parent) {
                    return@forEach
                }
                entry.getAttachments(db.attachmentPool).forEach { attachment ->
                    db.removeAttachmentIfNotUsed(attachment)
                }
                db.deleteEntry(entry)
                deleted++
            }
            deleted
        }
    }

    /**
     * 一次性加载一级密码条目及全局总计数。
     *
     * 对比分别调用 [loadPasswordEntriesByTopLevel] 和 [countPasswordEntries]，
     * 此方法只打开一次数据库（或复用缓存），避免重复解密。
     *
     * @return Pair<顶级列表, 全局总条目数>
     */
    fun loadPasswordEntriesByTopLevelWithCount(
        localPath: String,
        masterPassword: String
    ): Pair<List<PasswordEntry>, Int> {
        return withDatabase(localPath, masterPassword, saveAfter = false) { db ->
            val rootGroup = db.rootGroup
            val childEntries = rootGroup?.getChildEntries()
                ?.filterNot { entry -> isEntryInRecycleBin(db, entry) }
                ?: emptyList()
            val childGroups = rootGroup?.getChildGroups()
                ?.filterNot { group -> db.groupIsInRecycleBin(group) }
                ?: emptyList()
            val topLevelList = buildPasswordEntries(
                database = db,
                entries = childEntries,
                groups = childGroups,
                includeFieldDetails = false
            )
            // 一次遍历同时统计全局总数
            val totalCount = collectEntriesOutsideRecycleBin(db, rootGroup).size
            topLevelList to totalCount
        }
    }

    /**
     * 一次性加载回收站密码条目及总计数。
     *
     * 对比分别调用 [loadRecentDeletedPasswordEntries] 和 [countRecentDeletedPasswordEntries]，
     * 此方法只打开一次数据库（或复用缓存），避免重复解密。
     *
     * @return Pair<回收站列表, 总计数>
     */
    fun loadRecentDeletedPasswordEntriesWithCount(
        localPath: String,
        masterPassword: String
    ): Pair<List<PasswordEntry>, Int> {
        return withDatabase(localPath, masterPassword, saveAfter = false) { db ->
            val recycleBin = db.recycleBin
                ?: return@withDatabase emptyList<PasswordEntry>() to 0
            val entries = collectEntries(recycleBin)
            val list = buildPasswordEntries(
                database = db,
                entries = entries,
                groups = emptyList(),
                includeFieldDetails = false
            )
            list to entries.size
        }
    }

    /**
     * 统计数据库中全部条目数量（不构建明细对象）。
     */
    fun countPasswordEntries(localPath: String, masterPassword: String): Int {
        return withDatabase(localPath, masterPassword, saveAfter = false) { db ->
            collectEntriesOutsideRecycleBin(db, db.rootGroup).size
        }
    }

    /**
     * 统计回收站中条目数量。
     */
    fun countRecentDeletedPasswordEntries(localPath: String, masterPassword: String): Int {
        return withDatabase(localPath, masterPassword, saveAfter = false) { db ->
            val recycleBin = db.recycleBin ?: return@withDatabase 0
            collectEntries(recycleBin).size
        }
    }

    /**
     * 合并远端数据库二进制内容到本地数据库。
     *
     * 说明：
     * - 使用数据库模块内置 merge 能力；
     * - 合并后按 `dataModifiedSinceLastLoading` 自动决定是否写回。
     */
    fun mergeRemoteDatabaseBytes(localPath: String, masterPassword: String, remoteBytes: ByteArray): Boolean {
        if (remoteBytes.isEmpty()) {
            Logger.d(SYNC_LOG_TAG, "跳过远端合并：远端数据为空")
            return false
        }

        return runCatching {
            Logger.d(
                SYNC_LOG_TAG,
                "开始合并远端数据库：本地路径=$localPath, 远端数据大小=${remoteBytes.size}"
            )
            withDatabase(localPath, masterPassword, saveAfter = true) { db ->
                ByteArrayInputStream(remoteBytes).use { input ->
                    db.mergeData(
                        databaseToMergeStream = input,
                        databaseToMergeMasterCredential = MasterCredential(password = masterPassword),
                        databaseToMergeChallengeResponseRetriever = emptyChallengeResponseRetriever,
                        isRAMSufficient = { true },
                        progressTaskUpdater = null
                    )
                }
                true
            }
        }.onSuccess {
            Logger.d(SYNC_LOG_TAG, "远端数据库合并成功：本地路径=$localPath")
        }.onFailure {
            Logger.e(SYNC_LOG_TAG, "远端数据库合并失败：${it.message}", it)
        }.getOrDefault(false)
    }

    fun isDuplicate(localPath: String, masterPassword: String, secret: String, algorithm: String, digits: Int, period: Int): Boolean {
        return loadTokens(localPath, masterPassword)
            .any { it.secret == secret && it.algorithm == algorithm && it.digits == digits && it.period == period }
    }

    fun addToken(localPath: String, masterPassword: String, token: OtpToken): Boolean {
        return withDatabase(localPath, masterPassword, saveAfter = true) { db ->
            val existing = collectEntries(db.rootGroup).mapNotNull { toToken(it) }
                .any { it.uniqueId == token.uniqueId || (it.secret == token.secret && it.algorithm == token.algorithm && it.digits == token.digits && it.period == token.period) }
            if (existing) {
                return@withDatabase false
            }

            val root = db.rootGroup ?: return@withDatabase false
            val entry = db.createEntry() ?: return@withDatabase false
            applyTokenToEntry(db, entry, token)
            db.addEntryTo(entry, root)
            true
        }
    }

    fun updateToken(localPath: String, masterPassword: String, token: OtpToken): Boolean {
        return withDatabase(localPath, masterPassword, saveAfter = true) { db ->
            val entry = findEntryByToken(db, token) ?: return@withDatabase false
            applyTokenToEntry(db, entry, token)
            db.updateEntry(entry)
            true
        }
    }

    fun deleteToken(localPath: String, masterPassword: String, tokenId: Long): Boolean {
        return withDatabase(localPath, masterPassword, saveAfter = true) { db ->
            val entry = findEntryById(db, tokenId) ?: return@withDatabase false
            if (!db.canRecycle(entry)) {
                return@withDatabase false
            }
            db.recycle(entry, resolveRecycleBinTitle(db))
            true
        }
    }

    fun incrementCounter(localPath: String, masterPassword: String, tokenId: Long): Boolean {
        return withDatabase(localPath, masterPassword, saveAfter = true) { db ->
            val entry = findEntryById(db, tokenId) ?: return@withDatabase false
            val otpElement = entry.getOtpElement() ?: return@withDatabase false
            if (otpElement.type != OtpType.HOTP) {
                return@withDatabase false
            }

            otpElement.counter = otpElement.counter + 1L
            val otpField = OtpEntryFields.buildOtpField(
                otpElement,
                entry.username,
                entry.title
            )

            val entryInfo = entry.getEntryInfo(db, raw = true, removeTemplateConfiguration = false).apply {
                customFields = customFields
                    .filterNot { field -> field.isOTP() }
                    .toMutableList()
                customFields.add(otpField)
            }
            entry.setEntryInfo(db, entryInfo)
            db.updateEntry(entry)
            true
        }
    }

    private fun findEntryByToken(db: Database, token: OtpToken): Entry? {
        val entries = collectEntriesOutsideRecycleBin(db, db.rootGroup)

        entries.firstOrNull { entry -> toStableId(entry) == token.id }
            ?.let { return it }

        return entries.firstOrNull { entry ->
            val parsed = toToken(entry) ?: return@firstOrNull false
            parsed.uniqueId == token.uniqueId || (
                parsed.secret == token.secret &&
                    parsed.algorithm == token.algorithm &&
                    parsed.digits == token.digits &&
                    parsed.period == token.period
                )
        }
    }

    private fun findEntryById(db: Database, tokenId: Long): Entry? {
        return findEntryByStableId(db, tokenId, includeRecycleBin = false)
    }

    /**
     * 按稳定 ID 查找条目。
     */
    private fun findEntryByStableId(db: Database, entryId: Long, includeRecycleBin: Boolean): Entry? {
        val source = if (includeRecycleBin) {
            collectEntries(db.rootGroup)
        } else {
            collectEntriesOutsideRecycleBin(db, db.rootGroup)
        }
        return source.firstOrNull { toStableId(it) == entryId }
    }

    private fun toToken(entry: Entry): OtpToken? {
        val otpElement = entry.getOtpElement() ?: return null
        val secret = otpElement.getBase32Secret().takeIf { it.isNotBlank() } ?: return null
        val algorithm = otpElement.algorithm.name
        val digits = otpElement.digits
        val tokenType = if (otpElement.type == OtpType.HOTP) {
            OtpTokenType.HOTP
        } else {
            OtpTokenType.TOTP
        }
        val period = if (tokenType == OtpTokenType.TOTP) otpElement.period else 30
        val counter = if (tokenType == OtpTokenType.HOTP) otpElement.counter else 0L

        val uniqueId = xzynine.WebDAVPass.Android.util.UniqueIdGenerator.generate(
            secret,
            algorithm,
            digits,
            period
        )

        val label = entry.title.takeIf { it.isNotBlank() }
            ?: otpElement.name.takeIf { it.isNotBlank() }
            ?: "Token"
        val issuer = entry.username.takeIf { it.isNotBlank() }
            ?: otpElement.issuer.takeIf { it.isNotBlank() }
        val description = entry.notes.takeIf { it.isNotBlank() }

        val ordinal = -entry.creationTime.toMilliseconds()

        return OtpToken(
            id = toStableId(entry),
            ordinal = ordinal,
            issuer = issuer,
            label = label,
            description = description,
            imagePath = null,
            tokenType = tokenType,
            algorithm = algorithm,
            secret = secret,
            digits = digits,
            counter = counter,
            period = period,
            encryptionType = EncryptionType.NONE,
            uniqueId = uniqueId
        )
    }

    private fun applyTokenToEntry(database: Database, entry: Entry, token: OtpToken) {
        val otpType = if (token.tokenType == OtpTokenType.HOTP) OtpType.HOTP else OtpType.TOTP
        val otpElement = OtpElement().apply {
            type = otpType
            issuer = token.issuer ?: ""
            name = token.label
            algorithm = toHashAlgorithm(token.algorithm)
            digits = token.digits
            setBase32Secret(token.secret)
            if (otpType == OtpType.HOTP) {
                counter = token.counter
            } else {
                period = token.period
            }
        }

        val otpField = OtpEntryFields.buildOtpField(
            otpElement,
            token.issuer ?: "",
            token.label
        )

        val filteredFields = entry.getExtraFields()
            .filterNot { field -> field.isOTP() }
            .toMutableList()
            .apply {
                add(otpField)
            }

        val entryInfo = EntryInfo().apply {
            title = token.label
            username = token.issuer ?: ""
            password = ""
            notes = token.description ?: ""
            customFields = filteredFields
        }
        entry.setEntryInfo(database, entryInfo)
    }

    /**
     * 追加标准字段。
     */
    private fun appendStandardField(
        target: MutableList<RemainingKeyValue>,
        fieldName: String,
        rawValue: String,
        fixedType: RemainingValueType? = null,
        isProtected: Boolean = false
    ) {
        if (rawValue.isBlank()) {
            return
        }
        target.add(
            RemainingKeyValue(
                fieldName = fieldName,
                rawValue = rawValue,
                valueType = fixedType ?: detectValueType(null, rawValue),
                isProtected = isProtected,
                isStandard = true
            )
        )
    }

    /**
     * 推断字段值类型，无法识别时返回文本类型。
     */
    private fun detectValueType(field: Field?, rawValue: String): RemainingValueType {
        val normalized = rawValue.trim()
        val fieldName = field?.name?.lowercase() ?: ""

        if (field?.isOTP() == true
            || fieldName == "otp"
            || normalized.startsWith("otpauth://", ignoreCase = true)
        ) {
            return RemainingValueType.OTP
        }

        if (fieldName.contains("password") || fieldName.contains("密码")) {
            return RemainingValueType.PASSWORD
        }

        if (normalized.startsWith("http://", ignoreCase = true)
            || normalized.startsWith("https://", ignoreCase = true)
        ) {
            return RemainingValueType.URL
        }

        if (normalized.matches(Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"))) {
            return RemainingValueType.EMAIL
        }

        if (normalized.equals("true", true)
            || normalized.equals("false", true)
            || normalized == "0"
            || normalized == "1"
        ) {
            return RemainingValueType.BOOLEAN
        }

        if (normalized.matches(Regex("^-?\\d+(\\.\\d+)?$"))) {
            return RemainingValueType.NUMBER
        }

        if (normalized.matches(Regex("^\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}([ T]\\d{1,2}:\\d{1,2}(:\\d{1,2})?)?$"))) {
            return RemainingValueType.DATE_TIME
        }

        return RemainingValueType.TEXT
    }

    /**
     * 将仓库条目映射为 UI 结构。
     */
    private fun buildPasswordEntries(
        database: Database,
        entries: List<Entry>,
        groups: List<Group>,
        includeFieldDetails: Boolean
    ): List<PasswordEntry> {
        val result = mutableListOf<PasswordEntry>()

        groups.forEach { group ->
            val groupTitle = group.title.takeIf { it.isNotBlank() } ?: "未命名文件夹"
            result.add(
                PasswordEntry(
                    entryId = toStableGroupId(group),
                    title = groupTitle,
                    account = "",
                    standardIconId = group.icon.standard.id,
                    customIconBytes = readCustomIconBytes(database, group),
                    keyValues = emptyList(),
                    isFolderGroup = true,
                    isFolderPlaceholder = true
                )
            )
        }

        entries.forEach { entry ->
            val title = entry.title
                .takeIf { it.isNotBlank() }
                ?: entry.url.takeIf { it.isNotBlank() }
                ?: entry.username.takeIf { it.isNotBlank() }
                ?: toStableId(entry).toString()
            val account = entry.username.takeIf { it.isNotBlank() } ?: ""
            val values = if (includeFieldDetails) {
                buildEntryKeyValues(entry)
            } else {
                emptyList()
            }

            val attachmentPool = database.attachmentPool
            val attachments = entry.getAttachments(attachmentPool).mapNotNull { attachment ->
                val name = attachment.name
                if (name.isBlank()) {
                    null
                } else {
                    EntryAttachmentInfo(name = name, size = attachment.binaryData.getSize())
                }
            }
            val iconUuid = entry.icon.custom.uuid
            val customIconUuid = if (iconUuid == DatabaseVersioned.UUID_ZERO) null else iconUuid.toString()

            result.add(
                PasswordEntry(
                    entryId = toStableId(entry),
                    title = title,
                    account = account,
                    standardIconId = entry.icon.standard.id,
                    customIconBytes = readCustomIconBytes(database, entry),
                    keyValues = values,
                    attachments = attachments,
                    expiryTime = if (entry.expires) entry.expiryTime.toMilliseconds() else null,
                    isExpired = entry.expires && entry.isCurrentlyExpires,
                    customIconUuid = customIconUuid,
                    isFolderGroup = false,
                    isFolderPlaceholder = false
                )
            )
        }

        return result.sortedWith(
            compareBy<PasswordEntry> { if (it.isFolderGroup) 0 else 1 }
                .thenBy { it.title.lowercase() }
                .thenBy { it.account.lowercase() }
        )
    }

    /**
     * 构建单条目详情所需的全部键值。
     */
    private fun buildEntryKeyValues(entry: Entry): List<RemainingKeyValue> {
        val values = mutableListOf<RemainingKeyValue>()

        appendStandardField(values, "UserName", entry.username)
        appendStandardField(values, "Password", entry.password, RemainingValueType.PASSWORD, isProtected = true)
        appendStandardField(values, "URL", entry.url)
        appendStandardField(values, "Notes", entry.notes)

        entry.getExtraFields().forEach { field ->
            val value = field.protectedValue.stringValue
            if (value.isNotBlank()) {
                values.add(
                    RemainingKeyValue(
                        fieldName = field.name,
                        rawValue = value,
                        valueType = detectValueType(field, value),
                        isProtected = field.protectedValue.isProtected
                    )
                )
            }
        }

        return values.sortedBy { it.fieldName.lowercase() }
    }

    private fun collectEntries(group: Group?): List<Entry> {
        if (group == null) {
            return emptyList()
        }

        val list = mutableListOf<Entry>()
        list.addAll(group.getChildEntries())
        group.getChildGroups().forEach { child ->
            list.addAll(collectEntries(child))
        }
        return list
    }

    /**
     * 收集非回收站范围内的条目。
     */
    private fun collectEntriesOutsideRecycleBin(database: Database, group: Group?): List<Entry> {
        return collectEntries(group).filterNot { entry -> isEntryInRecycleBin(database, entry) }
    }

    private fun isEntryInRecycleBin(database: Database, entry: Entry): Boolean {
        val parent = entry.parent ?: return false
        return database.groupIsInRecycleBin(parent)
    }

    private fun resolveRecycleBinTitle(database: Database): String {
        return database.recycleBin?.title?.takeIf { it.isNotBlank() } ?: RECYCLE_BIN_FALLBACK_TITLE
    }

    /**
     * 按节点 UUID 查找分组（含自身），用于恢复条目到原分组。
     */
    private fun findGroupByUuid(group: Group?, uuid: java.util.UUID): Group? {
        if (group == null) {
            return null
        }
        val groupUuid = (group.nodeId as? com.kunzisoft.keepass.database.element.node.NodeIdUUID)?.id
        if (groupUuid != null && groupUuid == uuid) {
            return group
        }
        group.getChildGroups().forEach { child ->
            val matched = findGroupByUuid(child, uuid)
            if (matched != null) {
                return matched
            }
        }
        return null
    }

    /**
     * 通过稳定ID查找分组。
     */
    private fun findGroupByStableId(group: Group?, groupStableId: Long): Group? {
        if (group == null) {
            return null
        }

        group.getChildGroups().forEach { child ->
            if (toStableGroupId(child) == groupStableId) {
                return child
            }

            val matched = findGroupByStableId(child, groupStableId)
            if (matched != null) {
                return matched
            }
        }
        return null
    }

    /**
     * 解析草稿中的父分组，空值表示根分组。
     */
    private fun resolveParentGroup(database: Database, parentGroupId: Long?): Group? {
        if (parentGroupId == null) {
            return database.rootGroup
        }
        return findGroupByStableId(database.rootGroup, parentGroupId)
    }

    /**
     * 将父分组转换为稳定 ID，根分组返回 null。
     */
    private fun toStableParentGroupId(parent: Group?): Long? {
        parent ?: return null
        if (parent.parent == null) {
            return null
        }
        return toStableGroupId(parent)
    }

    /**
     * 读取分组自定义图标二进制数据。
     */
    private fun readCustomIconBytes(database: Database, group: Group): ByteArray? {
        val iconUuid = group.icon.custom.uuid
        if (iconUuid == DatabaseVersioned.UUID_ZERO) {
            return null
        }

        return runCatching {
            val binary = database.getBinaryForCustomIcon(iconUuid) ?: return null
            binary.getUnGzipInputDataStream(database.binaryCache).use { input ->
                input.readBytes()
            }
        }.getOrNull()
    }

    /**
     * 生成分组稳定ID，使用负值避免和条目ID冲突。
     */
    private fun toStableGroupId(group: Group): Long {
        val uuid = (group.nodeId as? com.kunzisoft.keepass.database.element.node.NodeIdUUID)?.id
            ?: UUID(0L, 0L)
        val mixed = uuid.mostSignificantBits xor uuid.leastSignificantBits
        val absolute = when (mixed) {
            Long.MIN_VALUE -> 0L
            else -> abs(mixed)
        }
        return -(absolute + 1L)
    }

    private fun <T> withDatabase(localPath: String, masterPassword: String, saveAfter: Boolean, block: (Database) -> T): T {
        // 优先使用已缓存的数据库实例，避免重复解密
        val cachedPair = DatabaseManager.tryGet(localPath)
        if (cachedPair != null) {
            val (database, cacheDirectory) = cachedPair
            val result = block(database)
            if (saveAfter && database.dataModifiedSinceLastLoading) {
                val location = resolveLocation(localPath)
                saveDatabase(database, location, masterPassword, cacheDirectory)
            }
            return result
        }

        // 缓存未命中：打开数据库，执行操作后关闭（兼容未解锁状态）
        val location = resolveLocation(localPath)
        ensureLocationInitialized(location, masterPassword)
        val (database, cacheDirectory) = openDatabase(location, masterPassword)
        try {
            val result = block(database)
            if (saveAfter && database.dataModifiedSinceLastLoading) {
                saveDatabase(database, location, masterPassword, cacheDirectory)
            }
            return result
        } finally {
            database.clearAndClose(cacheDirectory)
        }
    }

    /**
     * 根据定位打开数据库。
     */
    private fun openDatabase(location: DatabaseLocation, masterPassword: String): Pair<Database, File> {
        val cacheDirectory = buildCacheDirectory(location)
        val database = Database()
        openInputStream(location).use { input ->
            database.loadData(
                databaseStream = input,
                masterCredential = MasterCredential(password = masterPassword),
                challengeResponseRetriever = emptyChallengeResponseRetriever,
                readOnly = false,
                allowUserVerification = false,
                cacheDirectory = cacheDirectory,
                isRAMSufficient = { true },
                fixDuplicateUUID = false,
                progressTaskUpdater = null
            )
        }
        return database to cacheDirectory
    }

    /**
     * 将数据库保存回定位目标。
     */
    private fun saveDatabase(database: Database, location: DatabaseLocation, masterPassword: String, cacheDirectory: File) {
        val cacheFile = File.createTempFile("kdbx-save-", ".tmp", cacheDirectory)
        database.saveData(
            cacheFile = cacheFile,
            databaseOutputStream = { openOutputStream(location) },
            isNewLocation = true,
            masterCredential = MasterCredential(password = masterPassword),
            challengeResponseRetriever = emptyChallengeResponseRetriever
        )
    }

    /**
     * 为定位构建缓存目录。
     */
    private fun buildCacheDirectory(location: DatabaseLocation): File {
        val cacheDirectory = when (location) {
            is DatabaseLocation.FileLocation -> {
                val parent = location.file.parentFile ?: File(System.getProperty("java.io.tmpdir") ?: ".")
                File(parent, ".kdbx-cache")
            }

            is DatabaseLocation.UriLocation -> {
                val raw = location.uri.toString().hashCode().toLong()
                val safeHash = if (raw == Long.MIN_VALUE) 0L else abs(raw)
                File(appContext.cacheDir, ".kdbx-cache-$safeHash")
            }
        }
        if (!cacheDirectory.exists()) {
            cacheDirectory.mkdirs()
        }
        return cacheDirectory
    }

    /**
     * 标准化定位字符串为文件或 Uri。
     */
    private fun resolveLocation(localPath: String): DatabaseLocation {
        val maybeUri = runCatching { Uri.parse(localPath) }.getOrNull()
        if (maybeUri != null && maybeUri.scheme.equals("content", ignoreCase = true)) {
            return DatabaseLocation.UriLocation(maybeUri)
        }
        return DatabaseLocation.FileLocation(File(localPath))
    }

    /**
     * 按定位打开输入流。
     */
    private fun openInputStream(location: DatabaseLocation): InputStream {
        return when (location) {
            is DatabaseLocation.FileLocation -> {
                location.file.inputStream()
            }

            is DatabaseLocation.UriLocation -> {
                appContext.contentResolver.openInputStream(location.uri)
                    ?: throw IllegalStateException("无法读取数据库文件")
            }
        }
    }

    /**
     * 按定位打开输出流。
     */
    private fun openOutputStream(location: DatabaseLocation): OutputStream {
        return when (location) {
            is DatabaseLocation.FileLocation -> {
                location.file.parentFile?.let {
                    if (!it.exists()) {
                        it.mkdirs()
                    }
                }
                location.file.outputStream()
            }

            is DatabaseLocation.UriLocation -> {
                appContext.contentResolver.openOutputStream(location.uri, "wt")
                    ?: throw IllegalStateException("无法写入数据库文件")
            }
        }
    }

    /**
     * 确保定位目标可初始化。
     */
    private fun ensureLocationInitialized(location: DatabaseLocation, masterPassword: String) {
        when (location) {
            is DatabaseLocation.FileLocation -> {
                val file = location.file
                if (!file.exists() || file.length() == 0L) {
                    initializeDatabase(file.absolutePath, masterPassword)
                }
            }

            is DatabaseLocation.UriLocation -> {
                if (!hasUriData(location.uri)) {
                    initializeDatabase(location.uri.toString(), masterPassword)
                }
            }
        }
    }

    /**
     * 判断 Uri 是否已有内容。
     */
    private fun hasUriData(uri: Uri): Boolean {
        return appContext.contentResolver.openInputStream(uri)?.use { input ->
            input.read() != -1
        } ?: false
    }

    /**
     * 构建定位调试信息。
     */
    private fun buildLocationHint(location: DatabaseLocation): String {
        return when (location) {
            is DatabaseLocation.FileLocation -> {
                val file = location.file
                if (!file.exists()) {
                    return "fileMissing"
                }
                val length = file.length()
                val head = runCatching {
                    file.inputStream().use { input ->
                        val bytes = ByteArray(8)
                        val read = input.read(bytes)
                        if (read <= 0) {
                            "empty"
                        } else {
                            bytes.take(read).joinToString(separator = "") { b -> "%02X".format(b) }
                        }
                    }
                }.getOrElse { "readError:${it.javaClass.simpleName}" }
                "size=$length, head=$head"
            }

            is DatabaseLocation.UriLocation -> {
                val size = queryUriSize(location.uri)?.toString() ?: "unknown"
                val head = runCatching {
                    appContext.contentResolver.openInputStream(location.uri)?.use { input ->
                        val bytes = ByteArray(8)
                        val read = input.read(bytes)
                        if (read <= 0) {
                            "empty"
                        } else {
                            bytes.take(read).joinToString(separator = "") { b -> "%02X".format(b) }
                        }
                    } ?: "openNull"
                }.getOrElse { "readError:${it.javaClass.simpleName}" }
                "uriSize=$size, head=$head"
            }
        }
    }

    /**
     * 查询 Uri 的声明大小。
     */
    private fun queryUriSize(uri: Uri): Long? {
        val cursor = appContext.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?: return null
        return try {
            if (!cursor.moveToFirst()) {
                return null
            }
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (sizeIndex < 0 || cursor.isNull(sizeIndex)) {
                null
            } else {
                cursor.getLong(sizeIndex)
            }
        } finally {
            cursor.close()
        }
    }

    private fun toStableId(entry: Entry): Long {
        val uuid = (entry.nodeId as? com.kunzisoft.keepass.database.element.node.NodeIdUUID)?.id
            ?: UUID(0L, 0L)
        val mixed = uuid.mostSignificantBits xor uuid.leastSignificantBits
        return when (mixed) {
            Long.MIN_VALUE -> 0L
            else -> abs(mixed)
        }
    }

    private fun toHashAlgorithm(algorithm: String): TokenCalculator.HashAlgorithm {
        return when (algorithm.uppercase()) {
            TokenCalculator.HashAlgorithm.SHA256.name -> TokenCalculator.HashAlgorithm.SHA256
            TokenCalculator.HashAlgorithm.SHA512.name -> TokenCalculator.HashAlgorithm.SHA512
            else -> TokenCalculator.HashAlgorithm.SHA1
        }
    }

    /**
     * 读取条目自定义图标二进制数据。
     *
     * 优先从条目的 custom icon UUID 读取数据库中的二进制，读取失败时返回 null。
     */
    private fun readCustomIconBytes(database: Database, entry: Entry): ByteArray? {
        val iconUuid = entry.icon.custom.uuid
        if (iconUuid == DatabaseVersioned.UUID_ZERO) {
            return null
        }

        return runCatching {
            val binary = database.getBinaryForCustomIcon(iconUuid) ?: return null
            binary.getUnGzipInputDataStream(database.binaryCache).use { input ->
                input.readBytes()
            }
        }.getOrNull()
    }

    /**
     * 合并自定义字段（额外字段，不含标准字段）。
     *
     * 以 [EditableFieldDraft.originalName] 与数据库原始额外字段稳定匹配，正确处理：
     * - 删除：UI 标记 [EditableFieldDraft.removed] 的字段不写回；
     * - 重命名：原名字段按 [EditableFieldDraft.originalName] 匹配，使用新名字写回，不会残留旧名；
     * - 新增：originalName 为空且名字非空的字段作为新字段追加；
     * - 保留：数据库中与任何草稿都不匹配的原始额外字段（例如从其他工具迁移来的、命名为
     *   "Password"/"UserName" 的标准名字段）一律原样保留，避免在保存编辑时被静默删除；
     * - 去重：新建字段名（不区分大小写）若与已有字段（含被保留的原始字段）冲突，则跳过后续重复项。
     *
     * 同时透传 [EditableFieldDraft.isProtected]，避免受保护字段（如 OTP 种子）保存后丢失保护标志。
     */
    private fun mergeCustomFields(
        database: Database,
        entry: Entry,
        uiFields: List<EditableFieldDraft>
    ): MutableList<Field> {
        val originalExtras = entry.getExtraFields()
        val result = mutableListOf<Field>()
        val usedLowerNames = mutableSetOf<String>()

        // 1. 先保留所有原始额外字段，但对有草稿匹配的项按草稿更新
        for (orig in originalExtras) {
            val draft = uiFields.firstOrNull { it.originalName == orig.name }
            if (draft != null && !draft.removed) {
                val name = draft.name.ifBlank { orig.name }
                if (usedLowerNames.add(name.lowercase())) {
                    result.add(Field(name, ProtectedString(draft.isProtected, draft.value)))
                }
            } else if (draft == null) {
                // 没有草稿对应的原始额外字段（如从其他工具迁移来的标准名字段），原样保留
                if (usedLowerNames.add(orig.name.lowercase())) {
                    result.add(Field(orig.name, orig.protectedValue))
                }
            }
            // draft != null 且 removed -> 用户显式删除，不加入 result
        }

        // 2. 新增字段（originalName 为空、名字非空、未删除），不与已有字段重名
        uiFields
            .filter { it.originalName == null && it.name.isNotBlank() && !it.removed }
            .forEach { draft ->
                if (usedLowerNames.add(draft.name.lowercase())) {
                    result.add(Field(draft.name, ProtectedString(draft.isProtected, draft.value)))
                }
            }

        return result
    }

    /**
     * 构建最终的附件列表，直接交给 [Entry.setEntryInfo] 全量替换：
     * - 原附件中未标记删除的保留；
     * - 草稿中新建且带有字节的作为新附件写入。
     */
    private fun buildEntryInfoAttachments(
        database: Database,
        entry: Entry,
        draft: PasswordEntryEditDraft
    ): List<Attachment> {
        val removedNames = draft.attachments
            .filter { it.removed }
            .map { it.name }
            .toSet()

        val kept = entry.getAttachments(database.attachmentPool)
            .filter { it.name !in removedNames }
            .toMutableList()

        draft.attachments.forEach { att ->
            if (att.isNew && att.data != null && !att.removed) {
                // 避免与已保留的附件重名产生两个同名二进制
                var finalName = att.name
                var suffix = 1
                while (kept.any { it.name.equals(finalName, ignoreCase = true) }) {
                    finalName = "${att.name} ($suffix)"
                    suffix++
                }
                val binary = database.buildNewBinaryAttachment() ?: return@forEach
                binary.getOutputDataStream(database.binaryCache).use { output ->
                    output.write(att.data)
                }
                kept.add(Attachment(finalName, binary))
            }
        }
        return kept
    }

    /**
     * 应用过期时间与图标到 [EntryInfo]。
     */
    private fun applyExpiryAndIcon(entryInfo: EntryInfo, database: Database, draft: PasswordEntryEditDraft) {
        if (draft.expiryTime != null) {
            entryInfo.expires = true
            entryInfo.expiryTime = DateInstant.fromMilliseconds(draft.expiryTime)
        } else {
            entryInfo.expires = false
        }
        if (draft.customIconUuid == null) {
            entryInfo.icon = IconImage(database.getStandardIcon(draft.iconStandardId))
        }
    }

    /**
     * 读取条目附件的字节内容（供 UI 查看/保存）。
     */
    /**
     * 读取条目附件的字节内容（供 UI 查看/保存）。
     *
     * 采用增量读取并施加 [MAX_ATTACHMENT_BYTES] 硬性上限：超过上限的附件直接拒绝返回，
     * 避免在内存中全量展开导致 OOM。
     */
    fun getEntryAttachmentBytes(localPath: String, masterPassword: String, entryId: Long, name: String): ByteArray? {
        return withDatabase(localPath, masterPassword, saveAfter = false) { db ->
            val entry = findEntryByStableId(db, entryId, includeRecycleBin = false) ?: return@withDatabase null
            val pool = db.attachmentPool
            val attachment = entry.getAttachments(pool).firstOrNull { it.name == name } ?: return@withDatabase null
            runCatching {
                attachment.binaryData.getInputDataStream(db.binaryCache).use { input ->
                    readBoundedBytes(input, MAX_ATTACHMENT_BYTES)
                }
            }.getOrNull()
        }
    }

    /**
     * 将条目附件以增量方式拷贝到指定输出流（供 UI 保存到本地文件）。
     *
     * 同样施加 [MAX_ATTACHMENT_BYTES] 上限，避免读取端无限缓冲。输出流由调用方负责关闭。
     */
    fun copyEntryAttachmentTo(
        localPath: String,
        masterPassword: String,
        entryId: Long,
        name: String,
        output: OutputStream
    ): Boolean {
        return withDatabase(localPath, masterPassword, saveAfter = false) { db ->
            val entry = findEntryByStableId(db, entryId, includeRecycleBin = false) ?: return@withDatabase false
            val pool = db.attachmentPool
            val attachment = entry.getAttachments(pool).firstOrNull { it.name == name } ?: return@withDatabase false
            runCatching {
                attachment.binaryData.getInputDataStream(db.binaryCache).use { input ->
                    copyBounded(input, output, MAX_ATTACHMENT_BYTES)
                }
            }.isSuccess
        }
    }

    /**
     * 增量读取输入流，超过 [limit] 字节则抛出 [IllegalStateException] 以拒绝超大附件。
     */
    private fun readBoundedBytes(input: InputStream, limit: Int): ByteArray {
        val buffer = ByteArrayOutputStream(8 * 1024)
        val chunk = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val read = input.read(chunk)
            if (read < 0) break
            total += read
            if (total > limit) {
                throw IllegalStateException("附件过大（超过 ${limit / 1024} KiB），已拒绝读取")
            }
            buffer.write(chunk, 0, read)
        }
        return buffer.toByteArray()
    }

    /**
     * 增量拷贝输入流到输出流，超过 [limit] 字节则抛出 [IllegalStateException] 以拒绝超大附件。
     */
    private fun copyBounded(input: InputStream, output: OutputStream, limit: Int) {
        val chunk = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val read = input.read(chunk)
            if (read < 0) break
            total += read
            if (total > limit) {
                throw IllegalStateException("附件过大（超过 ${limit / 1024} KiB），已拒绝读取")
            }
            output.write(chunk, 0, read)
        }
    }
}
