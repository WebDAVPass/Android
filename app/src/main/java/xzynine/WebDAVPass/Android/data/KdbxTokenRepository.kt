package xzynine.WebDAVPass.Android.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import xzylib.base.util.Logger
import com.kunzisoft.keepass.database.crypto.kdf.KdfFactory
import com.kunzisoft.keepass.database.element.Database
import com.kunzisoft.keepass.database.element.DateInstant
import com.kunzisoft.keepass.database.element.Entry
import com.kunzisoft.keepass.database.element.Attachment
import com.kunzisoft.keepass.database.element.Field
import com.kunzisoft.keepass.database.element.database.CompressionAlgorithm
import com.kunzisoft.keepass.database.element.icon.IconImage
import com.kunzisoft.keepass.database.element.Group
import com.kunzisoft.keepass.database.element.MasterCredential
import com.kunzisoft.keepass.database.element.database.DatabaseVersioned
import com.kunzisoft.keepass.database.element.Tags
import com.kunzisoft.keepass.database.element.security.ProtectedString
import com.kunzisoft.keepass.hardware.HardwareKey
import com.kunzisoft.keepass.model.EntryInfo
import com.kunzisoft.keepass.model.GroupInfo
import com.kunzisoft.keepass.otp.OtpElement
import com.kunzisoft.keepass.otp.OtpEntryFields
import com.kunzisoft.keepass.otp.OtpEntryFields.isOTP
import com.kunzisoft.keepass.otp.OtpType
import com.kunzisoft.keepass.otp.TokenCalculator
import xzynine.WebDAVPass.Android.util.PasswordStrength
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

    fun initializeDatabase(localPath: String, masterPassword: String, keyFileData: ByteArray? = null) {
        val location = resolveLocation(localPath)
        when (location) {
            is DatabaseLocation.FileLocation -> {
                val file = location.file
                file.parentFile?.mkdirs()
                if (file.exists() && file.length() > 0) {
                    return
                }
                file.writeBytes(createDatabaseBytes(masterPassword, keyFileData))
            }

            is DatabaseLocation.UriLocation -> {
                if (hasUriData(location.uri)) {
                    return
                }
                openOutputStream(location).use { output ->
                    output.write(createDatabaseBytes(masterPassword, keyFileData))
                }
            }
        }
    }

    fun createDatabaseBytes(masterPassword: String, keyFileData: ByteArray? = null): ByteArray {
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
                    // 新建库凭据仅由参数决定，不继承当前已解锁库的密钥文件，
                    // 避免误用旧密钥文件加密导致新库无法用纯密码解锁
                    masterCredential = MasterCredential(
                        password = masterPassword,
                        keyFileData = keyFileData
                    ),
                    challengeResponseRetriever = emptyChallengeResponseRetriever
                )
                outputStream.toByteArray()
            } finally {
                runCatching { database.clearAndClose() }
            }
        }
    }

    fun validatePassword(localPath: String, masterPassword: String, keyFileData: ByteArray? = null): Boolean {
        val effectiveKeyFileData = keyFileData ?: DatabaseManager.getKeyFileData()
        lastUnlockErrorMessage = null
        return runCatching {
            val location = resolveLocation(localPath)
            ensureLocationInitialized(location, masterPassword, effectiveKeyFileData)

            // 此路径已有缓存实例，直接验证可访问性，无需重新解密
            val existing = DatabaseManager.tryGet(localPath)
            if (existing != null) {
                existing.first.rootGroup
                return@runCatching true
            }

            // 关闭其他路径的旧缓存
            DatabaseManager.close()

            // 打开数据库后缓存，不立即关闭，供后续操作复用
            val (database, cacheDirectory) = openDatabase(location, masterPassword, effectiveKeyFileData)
            try {
                database.rootGroup // 验证根组可访问
            } catch (e: Exception) {
                // 打开成功但验证失败时，释放资源
                database.clearAndClose(cacheDirectory)
                throw e
            }
            DatabaseManager.store(localPath, masterPassword, database, cacheDirectory)
            // 登记密钥文件凭据，使后续 saveDatabase/mergeRemoteDatabaseBytes/exportDatabaseTo
            // 的默认凭据能取到正确的密钥文件（与 LibraryViewModel 解锁后登记保持一致）
            DatabaseManager.setKeyFileData(effectiveKeyFileData)
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
        return withDatabase(localPath, masterPassword, saveAfter = true) { db ->
            migrateLegacyTokenConvention(db)
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
        withDatabase(localPath, masterPassword, saveAfter = true) { db ->
            migrateLegacyTokenConvention(db)
            collectEntriesOutsideRecycleBin(db, db.rootGroup)
                .mapNotNull { entry -> toToken(entry) }
                .sortedBy { it.ordinal }
                .forEach { token -> trySend(token) }
        }
    }

    /**
     * 读取数据库中全部条目摘要（默认不包含键值详情，搜索时需要字段详情）。
     */
    fun loadPasswordEntries(localPath: String, masterPassword: String, includeFieldDetails: Boolean = false): List<PasswordEntry> {
        return withDatabase(localPath, masterPassword, saveAfter = false) { db ->
            buildPasswordEntries(
                database = db,
                entries = collectEntriesOutsideRecycleBin(db, db.rootGroup),
                groups = emptyList(),
                includeFieldDetails = includeFieldDetails
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
    fun loadRecentDeletedPasswordEntries(
        localPath: String,
        masterPassword: String,
        includeFieldDetails: Boolean = false
    ): List<PasswordEntry> {
        return withDatabase(localPath, masterPassword, saveAfter = false) { db ->
            val recycleBin = db.recycleBin ?: return@withDatabase emptyList()
            buildPasswordEntries(
                database = db,
                entries = collectEntries(recycleBin),
                groups = emptyList(),
                includeFieldDetails = includeFieldDetails
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
                iconStandardId = entry.icon.standard.id,
                tags = entry.tags.toList()
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
     * 恢复后当前条目内容与历史版本一致；EntryInfo 不含 history 字段，setEntryInfo
     * 也不会改动历史列表，因此原有历史记录原样保留（不会重复追加）。
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
            // 对齐 KeePass 语义：在覆盖前把当前状态作为快照追加进 history，
            // 避免恢复操作不可逆（用户反悔时仍可回到恢复前的版本）。
            // copyHistory=false：不要把当前 entry 自身的 history 列表再复制进快照，
            // 否则会出现 history 嵌套 history 的冗余结构。
            val snapshotBeforeRestore = Entry(entry, copyHistory = false)
            // 用历史版本的字段覆盖当前条目；history 列表由 setEntryInfo 保留不动，
            // 不要把 historyToRestore 再 addEntryToHistory（它已在 history 中，重复追加会自引用污染）。
            val entryInfo = historyToRestore.getEntryInfo(db, raw = true, removeTemplateConfiguration = false)
            entry.setEntryInfo(db, entryInfo)
            // 追加"恢复前状态"快照到 history 末尾（本次覆盖会让 history 当前版本保持不变）
            entry.addEntryToHistory(snapshotBeforeRestore)
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
                tags = draft.tags.toTags()
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
                tags = draft.tags.toTags()
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
     * 批量将条目图标固化为自定义图标（品牌图标写入密码库图标池）。
     *
     * 相同图片字节使用确定性 UUID（[UUID.nameUUIDFromBytes]），
     * 同一品牌的所有条目共享同一个图标池条目，避免重复膨胀。
     *
     * @return 成功写入的条目数
     */
    fun solidifyEntryBrandIcons(
        localPath: String,
        masterPassword: String,
        iconUpdates: Map<Long, ByteArray>,
    ): Int {
        return withDatabase(localPath, masterPassword, saveAfter = true) { db ->
            var count = 0
            iconUpdates.forEach { (entryId, bytes) ->
                val entry = findEntryByStableId(db, entryId, includeRecycleBin = false)
                    ?: return@forEach
                val customIconId = UUID.nameUUIDFromBytes(bytes)
                db.buildNewCustomIcon(customIconId) { customIcon, binary ->
                    if (customIcon != null && binary != null) {
                        binary.getOutputDataStream(db.binaryCache).use { output ->
                            output.write(bytes)
                        }
                        val entryInfo = entry.getEntryInfo(db, raw = true, removeTemplateConfiguration = false)
                        entryInfo.icon = IconImage(customIcon)
                        entry.setEntryInfo(db, entryInfo)
                        db.updateEntry(entry)
                        count++
                    }
                }
            }
            count
        }
    }

    /**
     * 加载指定条目的合并摘要（标准字段 + 自定义字段 + 附件名），不进行重复分组判断。
     */
    fun loadEntryMergeInfos(localPath: String, masterPassword: String, entryIds: List<Long>): List<DuplicateEntryInfo> {
        if (entryIds.isEmpty()) {
            return emptyList()
        }
        return withDatabase(localPath, masterPassword, saveAfter = false) { db ->
            entryIds.mapNotNull { entryId ->
                val entry = findEntryByStableId(db, entryId, includeRecycleBin = false) ?: return@mapNotNull null
                buildDuplicateEntryInfo(db, entry)
            }
        }
    }

    /**
     * 检测重复候选组。
     *
     * 候选规则：两个条目的「账号完全相等 / 标题互含 / URL 互含」三个维度中命中 ≥2 个
     * 即视为重复候选（均不区分大小写、去除首尾空白）；密码不参与候选判定。
     * 组内条目「账号+密码+URL」完全一致时视为无冲突（[DuplicateGroupInfo.isConflict] 为 false），
     * 可直接自动合并；存在差异的组需要手动逐字段选择。
     */
    fun detectDuplicateGroups(localPath: String, masterPassword: String, entryIds: List<Long>): List<DuplicateGroupInfo> {
        if (entryIds.isEmpty()) {
            return emptyList()
        }
        return withDatabase(localPath, masterPassword, saveAfter = false) { db ->
            val infos = entryIds.mapNotNull { entryId ->
                val entry = findEntryByStableId(db, entryId, includeRecycleBin = false) ?: return@mapNotNull null
                buildDuplicateEntryInfo(db, entry)
            }
            groupDuplicateInfos(infos)
        }
    }

    /**
     * 合并一组重复条目：将 [sourceEntryIds] 合并进 [masterEntryId]，源条目移入回收站。
     *
     * 字段取值规则（key 为 [MergeFieldKeys] 标准键或自定义字段名）：
     * - [fieldSelections] 中指定的字段：采用对应源条目的值；
     * - 未指定的字段：主条目优先，主条目为空时取第一个非空的源条目值；
     * - 自定义字段按名（不区分大小写）并集去重，冲突时按上述规则；
     * - 附件并集去重（忽略大小写重名保留主条目）；
     * - 源条目历史并入主条目历史，主条目合并前的旧版本保留在历史中；
     * - 图标保留主条目。
     *
     * @return 成功合并（移入回收站）的源条目数
     */
    fun mergeEntryGroup(
        localPath: String,
        masterPassword: String,
        masterEntryId: Long,
        sourceEntryIds: List<Long>,
        fieldSelections: Map<String, Long>
    ): Int {
        if (sourceEntryIds.isEmpty()) {
            return 0
        }
        return withDatabase(localPath, masterPassword, saveAfter = true) { db ->
            val master = findEntryByStableId(db, masterEntryId, includeRecycleBin = false)
                ?: return@withDatabase 0
            val sources = sourceEntryIds
                .mapNotNull { findEntryByStableId(db, it, includeRecycleBin = false) }
                .filter { it !== master }
            if (sources.isEmpty()) {
                return@withDatabase 0
            }

            val masterInfo = master.getEntryInfo(db, raw = true, removeTemplateConfiguration = false)
            val sourceInfos = sources.map { source ->
                toStableId(source) to source.getEntryInfo(db, raw = true, removeTemplateConfiguration = false)
            }.toMap()

            // 1. 主条目当前版本先入历史，防止合并覆盖后丢失
            master.addEntryToHistory(Entry(master, copyHistory = false))

            // 2. 标准字段：fieldSelections 指定则采用对应源条目，否则主条目优先、主条目为空时取第一个非空源条目
            val sourceInfoOf: (Long?) -> EntryInfo = { sourceId ->
                sourceId?.let { sourceInfos[it] } ?: masterInfo
            }
            fun <T> pickStandard(key: String, masterValue: T, isEmpty: (T) -> Boolean, getter: (EntryInfo) -> T): T {
                val sourceId = fieldSelections[key]
                if (sourceId != null) {
                    return getter(sourceInfoOf(sourceId))
                }
                if (!isEmpty(masterValue)) {
                    return masterValue
                }
                return sourceInfos.values.firstOrNull { !isEmpty(getter(it)) }?.let { getter(it) } ?: masterValue
            }

            masterInfo.title = pickStandard(MergeFieldKeys.TITLE, masterInfo.title, { it.isBlank() }) { it.title }
            masterInfo.username = pickStandard(MergeFieldKeys.ACCOUNT, masterInfo.username, { it.isBlank() }) { it.username }
            masterInfo.password = pickStandard(MergeFieldKeys.PASSWORD, masterInfo.password, { it.isBlank() }) { it.password }
            masterInfo.url = pickStandard(MergeFieldKeys.URL, masterInfo.url, { it.isBlank() }) { it.url }
            masterInfo.notes = pickStandard(MergeFieldKeys.NOTES, masterInfo.notes, { it.isBlank() }) { it.notes }
            masterInfo.tags = pickStandard(MergeFieldKeys.TAGS, masterInfo.tags, { it.isEmpty() }) { it.tags }

            // 3. 自定义字段：并集去重，fieldSelections 指定则采用对应源条目，否则主优先、主空补从
            val mergedFields = ArrayList(masterInfo.customFields)
            val mergedLowerNames = mergedFields.map { it.name.lowercase() }.toMutableSet()
            for ((sourceId, sourceInfo) in sourceInfos) {
                for (field in sourceInfo.customFields) {
                    val lowerName = field.name.lowercase()
                    if (mergedLowerNames.add(lowerName)) {
                        mergedFields.add(field)
                        continue
                    }
                    // 同名冲突：用户显式指定该字段采用此源条目 → 替换；否则保留主条目
                    if (fieldSelections[lowerName] == sourceId || fieldSelections[field.name] == sourceId) {
                        val existingIndex = mergedFields.indexOfFirst { it.name.lowercase() == lowerName }
                        if (existingIndex >= 0) {
                            mergedFields[existingIndex] = field
                        }
                    }
                }
            }
            masterInfo.customFields = mergedFields

            // 4. 附件：并集去重（忽略大小写重名保留主条目），源条目附件复制二进制
            val mergedAttachments = mutableListOf<Attachment>()
            val usedAttachmentNames = mutableSetOf<String>()
            master.getAttachments(db.attachmentPool).forEach { att ->
                if (usedAttachmentNames.add(att.name.lowercase())) {
                    mergedAttachments.add(att)
                }
            }
            for (source in sources) {
                for (att in source.getAttachments(db.attachmentPool)) {
                    if (!usedAttachmentNames.add(att.name.lowercase())) {
                        continue
                    }
                    val binary = db.buildNewBinaryAttachment() ?: continue
                    att.binaryData.getInputDataStream(db.binaryCache).use { input ->
                        binary.getOutputDataStream(db.binaryCache).use { output ->
                            input.copyTo(output)
                        }
                    }
                    mergedAttachments.add(Attachment(att.name, binary))
                }
            }
            masterInfo.attachments = mergedAttachments.toMutableList()

            // 5. 源条目历史并入主条目
            for (source in sources) {
                source.getHistory().forEach { historyEntry ->
                    master.addEntryToHistory(historyEntry)
                }
            }

            master.setEntryInfo(db, masterInfo)
            db.removeUnlinkedAttachments()

            // 6. 源条目移入回收站
            var mergedCount = 0
            for (source in sources) {
                if (!db.canRecycle(source)) {
                    continue
                }
                val oldParent = source.parent
                db.recycle(source, resolveRecycleBinTitle(db))
                source.setPreviousParentGroup(oldParent)
                mergedCount++
            }
            mergedCount
        }
    }

    /**
     * 构建单条目合并摘要。
     */
    private fun buildDuplicateEntryInfo(database: Database, entry: Entry): DuplicateEntryInfo {
        val info = entry.getEntryInfo(database, raw = true, removeTemplateConfiguration = false)
        val fieldValues = linkedMapOf<String, String>()
        fieldValues[MergeFieldKeys.TITLE] = info.title
        fieldValues[MergeFieldKeys.ACCOUNT] = info.username
        fieldValues[MergeFieldKeys.PASSWORD] = info.password
        fieldValues[MergeFieldKeys.URL] = info.url
        fieldValues[MergeFieldKeys.NOTES] = info.notes
        fieldValues[MergeFieldKeys.TAGS] = info.tags.toString()
        info.customFields.forEach { field ->
            fieldValues[field.name] = field.protectedValue.stringValue
        }
        return DuplicateEntryInfo(
            entryId = toStableId(entry),
            title = info.title,
            account = info.username,
            url = info.url,
            hasPassword = info.password.isNotEmpty(),
            modifiedTime = info.lastModificationTime.toMilliseconds(),
            fieldValues = fieldValues,
            attachmentNames = entry.getAttachments(database.attachmentPool).map { it.name }
        )
    }

    /**
     * 将条目摘要分组为重复候选组（并查集合并交集组）。
     */
    private fun groupDuplicateInfos(infos: List<DuplicateEntryInfo>): List<DuplicateGroupInfo> {
        if (infos.size < 2) {
            return emptyList()
        }
        val parent = IntArray(infos.size) { it }
        fun find(x: Int): Int {
            var root = x
            while (parent[root] != root) {
                root = parent[root]
            }
            var current = x
            while (parent[current] != current) {
                val next = parent[current]
                parent[current] = root
                current = next
            }
            return root
        }
        fun union(a: Int, b: Int) {
            val ra = find(a)
            val rb = find(b)
            if (ra != rb) {
                parent[rb] = ra
            }
        }

        val norms = infos.map { info ->
            Triple(
                info.title.trim().lowercase(),
                info.account.trim().lowercase(),
                info.url.trim().lowercase()
            )
        }
        val passwords = infos.map { it.fieldValue(MergeFieldKeys.PASSWORD) }
        for (i in infos.indices) {
            for (j in i + 1 until infos.size) {
                val (title1, account1, url1) = norms[i]
                val (title2, account2, url2) = norms[j]
                var hits = 0
                if (account1.isNotEmpty() && account1 == account2) {
                    hits++
                }
                if (title1.isNotEmpty() && title2.isNotEmpty() && (title1.contains(title2) || title2.contains(title1))) {
                    hits++
                }
                if (url1.isNotEmpty() && url2.isNotEmpty() && (url1.contains(url2) || url2.contains(url1))) {
                    hits++
                }
                // 账号与密码都不同（两两精确比较）则不算重复，即使标题/URL 匹配
                val samePassword = passwords[i] == passwords[j]
                if (hits >= 2 && (account1 == account2 || samePassword)) {
                    union(i, j)
                }
            }
        }

        val groupMap = mutableMapOf<Int, MutableList<DuplicateEntryInfo>>()
        infos.forEachIndexed { index, info ->
            groupMap.getOrPut(find(index)) { mutableListOf() }.add(info)
        }
        return groupMap.values
            .filter { it.size >= 2 }
            .mapIndexed { index, list ->
                DuplicateGroupInfo(
                    groupId = index,
                    entries = list.sortedByDescending { it.modifiedTime },
                    isConflict = hasFieldConflict(list)
                )
            }
    }

    /**
     * 判断组内条目「账号/密码/URL」是否存在差异（任一维度不一致即视为冲突）。
     */
    private fun hasFieldConflict(infos: List<DuplicateEntryInfo>): Boolean {
        if (infos.size < 2) {
            return false
        }
        val first = infos.first()
        return infos.any { other ->
            other.account != first.account ||
                other.url != first.url ||
                other.hasPassword != first.hasPassword ||
                other.fieldValue(MergeFieldKeys.PASSWORD) != first.fieldValue(MergeFieldKeys.PASSWORD)
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
                    // 条目位于被回收的分组内：恢复其所属的最外层已回收分组（整组连同子条目一起恢复）
                    var recycledGroup: Group? = parent
                    while (recycledGroup?.parent != null && recycledGroup.parent != recycleBin) {
                        recycledGroup = recycledGroup.parent
                    }
                    if (recycledGroup != null && recycledGroup.parent == recycleBin) {
                        val target = findGroupByUuid(db.rootGroup, recycledGroup.previousParentGroup)
                            ?: db.rootGroup
                            ?: return@withDatabase restored
                        // 计数按实际恢复的条目数（整组含子分组）
                        restored += collectEntries(recycledGroup).size
                        db.undoRecycle(recycledGroup, target)
                    }
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
            // 已处理过的回收站分组，避免同组多条条目重复删除
            val deletedGroups = mutableSetOf<Long>()
            entryIds.forEach { entryId ->
                val entry = findEntryByStableId(db, entryId, includeRecycleBin = true)
                    ?: return@forEach
                val parent = entry.parent ?: return@forEach
                if (recycleBin != parent) {
                    // 条目位于被回收的分组内：整组永久删除（其条目无法单独恢复）
                    var recycledGroup: Group? = parent
                    while (recycledGroup?.parent != null && recycledGroup.parent != recycleBin) {
                        recycledGroup = recycledGroup.parent
                    }
                    if (recycledGroup != null && recycledGroup.parent == recycleBin) {
                        val groupId = toStableGroupId(recycledGroup)
                        if (groupId !in deletedGroups) {
                            deletedGroups.add(groupId)
                            // 计数按实际删除的条目数（整组含子分组）
                            deleted += collectEntries(recycledGroup).size
                            db.deleteGroup(recycledGroup)
                        }
                    }
                    return@forEach
                }
                // 先移除条目再清理附件：removeUnlinkedAttachments 只清理无条目引用的孤儿二进制
                db.deleteEntry(entry)
                deleted++
            }
            // 统一清理被删除条目遗留的孤儿附件，避免 KDBX 体积膨胀
            db.removeUnlinkedAttachments()
            deleted
        }
    }

    /**
     * 加载全部分组树（不含回收站），用于移动/复制的目标分组选择。
     */
    fun loadAllPasswordGroups(localPath: String, masterPassword: String): List<GroupNodeInfo> {
        return withDatabase(localPath, masterPassword, saveAfter = false) { db ->
            val rootGroup = db.rootGroup ?: return@withDatabase emptyList()
            val result = mutableListOf<GroupNodeInfo>()
            fun walk(group: Group, depth: Int) {
                group.getChildGroups().forEach { child ->
                    if (!db.groupIsInRecycleBin(child)) {
                        result.add(GroupNodeInfo(groupId = toStableGroupId(child), title = child.title, depth = depth))
                        walk(child, depth + 1)
                    }
                }
            }
            walk(rootGroup, 0)
            result
        }
    }

    /**
     * 批量移动条目/分组到目标分组。
     *
     * @return 成功移动的数量（分组按 1 计）
     */
    fun movePasswordTargets(
        localPath: String,
        masterPassword: String,
        entryIds: List<Long>,
        groupIds: List<Long>,
        targetGroupId: Long?
    ): Int {
        return withDatabase(localPath, masterPassword, saveAfter = true) { db ->
            val target = resolveParentGroup(db, targetGroupId) ?: return@withDatabase 0
            if (db.groupIsInRecycleBin(target)) {
                return@withDatabase 0
            }
            // 一次性建立稳定 ID → 节点的索引，避免对每个 ID 都全树遍历（O(n×m) → O(n+m)）
            val entryIndex = collectEntriesOutsideRecycleBin(db, db.rootGroup)
                .associateBy { toStableId(it) }
            val groupIndex = collectAllGroups(db.rootGroup)
                .associateBy { toStableGroupId(it) }
            var moved = 0
            entryIds.forEach { entryId ->
                val entry = entryIndex[entryId] ?: return@forEach
                if (entry.parent != target) {
                    db.moveEntryTo(entry, target)
                    moved++
                }
            }
            groupIds.forEach { groupId ->
                val group = groupIndex[groupId] ?: return@forEach
                if (group.parent != target && !isGroupInSubtree(group, target)) {
                    db.moveGroupTo(group, target)
                    moved++
                }
            }
            moved
        }
    }

    /**
     * 批量复制条目/分组到目标分组。
     *
     * 复制分组时整棵子树一并复制（标题加 " (~)" 由 [Database.copyEntryTo] 处理）。
     *
     * @return 成功复制的数量（分组按 1 计）
     */
    fun copyPasswordTargets(
        localPath: String,
        masterPassword: String,
        entryIds: List<Long>,
        groupIds: List<Long>,
        targetGroupId: Long?
    ): Int {
        return withDatabase(localPath, masterPassword, saveAfter = true) { db ->
            val target = resolveParentGroup(db, targetGroupId) ?: return@withDatabase 0
            if (db.groupIsInRecycleBin(target)) {
                return@withDatabase 0
            }
            // 一次性建立稳定 ID → 节点的索引，避免对每个 ID 都全树遍历（O(n×m) → O(n+m)）
            val entryIndex = collectEntriesOutsideRecycleBin(db, db.rootGroup)
                .associateBy { toStableId(it) }
            val groupIndex = collectAllGroups(db.rootGroup)
                .associateBy { toStableGroupId(it) }
            var copied = 0
            entryIds.forEach { entryId ->
                val entry = entryIndex[entryId] ?: return@forEach
                if (entry.parent != target) {
                    db.copyEntryTo(entry, target)
                    copied++
                }
            }
            groupIds.forEach { groupId ->
                val group = groupIndex[groupId] ?: return@forEach
                if (!isGroupInSubtree(group, target)) {
                    copyGroupRecursive(db, group, target)
                    copied++
                }
            }
            copied
        }
    }

    /**
     * 递归复制分组（含其下所有条目与子分组），复制出的分组标题追加 " (~)"。
     */
    private fun copyGroupRecursive(database: Database, group: Group, newParent: Group) {
        val copiedGroup = database.createGroup() ?: return
        copiedGroup.title = group.title + " (~)"
        copiedGroup.notes = group.notes
        copiedGroup.icon = group.icon
        database.addGroupTo(copiedGroup, newParent)
        group.getChildEntries().forEach { entry ->
            database.copyEntryTo(entry, copiedGroup)
        }
        group.getChildGroups().forEach { child ->
            copyGroupRecursive(database, child, copiedGroup)
        }
    }

    /**
     * 判断 [candidate] 是否位于 [group] 的子树内（含自身），用于防止移动到自身/子孙。
     */
    private fun isGroupInSubtree(group: Group, candidate: Group): Boolean {
        var current: Group? = candidate
        while (current != null) {
            if (current === group) {
                return true
            }
            current = current.parent
        }
        return false
    }

    /**
     * 修改数据库安全设置（主密码 / 密钥文件 / KDF / 压缩）并重新加密保存。
     *
     * @param masterPassword 当前主密码（用户输入，用于验证后打开数据库；即使已有缓存也会先真实解密验证）
     * @param newMasterPassword 新主密码（与当前相同表示仅修改其他设置）
     * @param newKeyFileData 新密钥文件字节，null 表示沿用当前密钥文件
     * @param kdfEngineName KDF 类型：AES / Argon2d / Argon2id，null 表示不修改
     * @param keyRounds KDF 轮数（仅 AES-KDF 有效）
     * @param memoryUsage KDF 内存占用（字节，仅 Argon2 有效）
     * @param parallelism KDF 并行度（仅 Argon2 有效）
     * @param isCompressionEnabled 是否启用压缩，null 表示不修改
     * @return 是否成功
     */
    fun changeDatabaseSettings(
        localPath: String,
        masterPassword: String,
        newMasterPassword: String,
        newKeyFileData: ByteArray?,
        kdfEngineName: String? = null,
        keyRounds: Long? = null,
        memoryUsage: Long? = null,
        parallelism: Long? = null,
        isCompressionEnabled: Boolean? = null
    ): Boolean {
        return runCatching {
            val location = resolveLocation(localPath)
            // 用用户输入的旧密码显式打开验证（绕过缓存真实解密），
            // 防止未锁屏设备上的他人不输旧密码直接改凭据
            val currentKeyFile = DatabaseManager.getKeyFileData()
            val (verifyDatabase, verifyCacheDirectory) = openDatabase(location, masterPassword, currentKeyFile)
            verifyDatabase.clearAndClose(verifyCacheDirectory)

            // 优先复用已解锁的缓存实例，避免重复解密
            val cachedPair = DatabaseManager.tryGet(localPath)
            val (database, cacheDirectory) = if (cachedPair != null) {
                cachedPair
            } else {
                openDatabase(location, masterPassword, currentKeyFile)
            }
            try {
                kdfEngineName?.let { name ->
                    val engine = when (name) {
                        "Argon2d" -> KdfFactory.argon2dKdf
                        "Argon2id" -> KdfFactory.argon2idKdf
                        else -> KdfFactory.aesKdf
                    }
                    database.kdfEngine = engine
                }
                keyRounds?.let { database.numberKeyEncryptionRounds = it }
                memoryUsage?.let { database.memoryUsage = it }
                parallelism?.let { database.parallelism = it }
                isCompressionEnabled?.let { enabled ->
                    database.compressionAlgorithm =
                        if (enabled) CompressionAlgorithm.GZIP else CompressionAlgorithm.NONE
                }
                val effectiveKeyFile = newKeyFileData ?: DatabaseManager.getKeyFileData()
                saveDatabase(
                    database = database,
                    location = location,
                    masterPassword = newMasterPassword,
                    cacheDirectory = cacheDirectory,
                    keyFileData = effectiveKeyFile
                )
                // 登记新密钥文件，供后续重新加密保存复用
                DatabaseManager.setKeyFileData(effectiveKeyFile)
                true
            } catch (e: Exception) {
                // 保存失败：失效缓存实例，防止内存中已变异的 KDF 设置污染后续保存，
                // 下次操作将从磁盘重新打开（磁盘文件未被改写，旧凭据仍然有效）。
                // 保留 keyFileData，避免密钥文件保护的库后续静默重开缺失凭据。
                DatabaseManager.invalidateCacheKeepKeyFile()
                throw e
            } finally {
                if (cachedPair == null) {
                    database.clearAndClose(cacheDirectory)
                }
            }
        }.onFailure {
            Logger.e(LOG_TAG, "changeDatabaseSettings failed, path=$localPath, message=${it.message}", it)
        }.getOrDefault(false)
    }

    /**
     * 读取数据库当前安全设置（供设置页展示）。
     */
    fun loadDatabaseSettingsInfo(
        localPath: String,
        masterPassword: String
    ): DatabaseSettingsInfo {
        return withDatabase(localPath, masterPassword, saveAfter = false) { db ->
            val kdfName = db.kdfEngine?.let {
                when {
                    it.uuid == KdfFactory.aesKdf.uuid -> "AES"
                    it.uuid == KdfFactory.argon2dKdf.uuid -> "Argon2d"
                    it.uuid == KdfFactory.argon2idKdf.uuid -> "Argon2id"
                    else -> it.toString()
                }
            } ?: "未知"
            DatabaseSettingsInfo(
                kdfEngineName = kdfName,
                keyRounds = db.numberKeyEncryptionRounds,
                memoryUsage = db.memoryUsage,
                parallelism = db.parallelism,
                isCompressionEnabled = db.compressionAlgorithm == CompressionAlgorithm.GZIP
            )
        }
    }

    /**
     * 将当前数据库导出到指定输出流（含删除历史/历史记录/附件等全部内容）。
     *
     * 复用标准保存序列化路径，凭据与当前库一致，不修改内存中的数据库对象。
     *
     * @return 是否成功
     */
    fun exportDatabaseTo(
        localPath: String,
        masterPassword: String,
        outputStreamProvider: () -> OutputStream?
    ): Boolean {
        return runCatching {
            val location = resolveLocation(localPath)
            // 优先复用已解锁的缓存实例，避免重复解密
            val cachedPair = DatabaseManager.tryGet(localPath)
            val (database, cacheDirectory) = if (cachedPair != null) {
                cachedPair
            } else {
                openDatabase(location, masterPassword)
            }
            val cacheFile = File.createTempFile("kdbx-export-", ".tmp", cacheDirectory)
            try {
                database.saveData(
                    cacheFile = cacheFile,
                    databaseOutputStream = outputStreamProvider,
                    isNewLocation = true,
                    masterCredential = MasterCredential(
                        password = masterPassword,
                        keyFileData = DatabaseManager.getKeyFileData()
                    ),
                    challengeResponseRetriever = emptyChallengeResponseRetriever
                )
                true
            } finally {
                if (cachedPair == null) {
                    database.clearAndClose(cacheDirectory)
                }
            }
        }.onFailure {
            Logger.e(LOG_TAG, "exportDatabaseTo failed, path=$localPath, message=${it.message}", it)
        }.getOrDefault(false)
    }

    /**
     * 将本地 .kdbx 文件合并进当前数据库（与云端合并同一语义，KeePassDX mergeData）。
     *
     * @param mergeFileUri 待合并的本地 kdbx 文件 Uri（content:// 或文件路径）
     * @param mergeMasterPassword 待合并文件的主密码（密钥文件与当前库一致）
     * @return 是否成功
     */
    fun mergeLocalDatabaseFile(
        localPath: String,
        masterPassword: String,
        mergeFileUri: String,
        mergeMasterPassword: String
    ): Boolean {
        return runCatching {
            withDatabase(localPath, masterPassword, saveAfter = true) { db ->
                val mergeLocation = resolveLocation(mergeFileUri)
                openInputStream(mergeLocation).use { input ->
                    db.mergeData(
                        databaseToMergeStream = input,
                        // 合并文件的凭据仅使用其主密码（界面只收集密码）；
                        // 使用密钥文件保护的库暂不支持合并
                        databaseToMergeMasterCredential = MasterCredential(
                            password = mergeMasterPassword
                        ),
                        databaseToMergeChallengeResponseRetriever = emptyChallengeResponseRetriever,
                        isRAMSufficient = { true },
                        progressTaskUpdater = null
                    )
                }
                true
            }
        }.onFailure {
            // 合并可能已部分改动内存实例，丢弃缓存以免污染状态被写回磁盘。
            // 使用 invalidateCacheKeepKeyFile 而非 close：保留 keyFileData，
            // 否则密钥文件保护的库后续静默重开会因缺失凭据失败（关联问题 #1/#9）。
            DatabaseManager.invalidateCacheKeepKeyFile()
            Logger.e(LOG_TAG, "mergeLocalDatabaseFile failed, path=$localPath, message=${it.message}", it)
        }.getOrDefault(false)
    }

    /**
     * 安全性检查：返回已过期条目与弱密码条目（不含回收站）。
     *
     * 弱密码判定使用 [PasswordStrength.isWeak]（熵低于 60 bits）。
     */
    fun loadSecurityIssues(localPath: String, masterPassword: String): SecurityIssuesInfo {
        return runCatching {
            withDatabase(localPath, masterPassword, saveAfter = false) { db ->
                val nowMillis = System.currentTimeMillis()
                val expired = mutableListOf<SecurityIssueEntry>()
                val weak = mutableListOf<SecurityIssueEntry>()

            collectEntriesOutsideRecycleBin(db, db.rootGroup).forEach { entry ->
                val entryId = toStableId(entry)
                val title = entry.title
                    .takeIf { it.isNotBlank() }
                    ?: entry.url.takeIf { it.isNotBlank() }
                    ?: entry.username.takeIf { it.isNotBlank() }
                    ?: entryId.toString()
                val account = entry.username.takeIf { it.isNotBlank() } ?: ""
                // 仅当条目显式标记为过期时才参与过期判定：
                // 无过期设置的条目 expiryTime 可能是过去的占位值（如默认「当前时间+30天」），
                // 直接比较时间戳会把从未设置过期的条目误判为已过期
                val expiryMillis = if (entry.expires) entry.expiryTime.toMilliseconds() else 0L
                val password = entry.password
                val strengthBits = PasswordStrength.estimateBits(password)

                if (expiryMillis > 0L && expiryMillis < nowMillis) {
                    expired.add(
                        SecurityIssueEntry(
                            entryId = entryId,
                            title = title,
                            account = account,
                            passwordStrengthBits = strengthBits,
                            expiryTime = expiryMillis
                        )
                    )
                }
                    if (PasswordStrength.isWeak(password)) {
                        weak.add(
                            SecurityIssueEntry(
                                entryId = entryId,
                                title = title,
                                account = account,
                                passwordStrengthBits = strengthBits,
                                expiryTime = expiryMillis.takeIf { it > 0L }
                            )
                        )
                    }
                }

                SecurityIssuesInfo(expiredEntries = expired, weakPasswordEntries = weak)
            }
        }.onFailure {
            Logger.e(LOG_TAG, "loadSecurityIssues failed, path=$localPath, message=${it.message}", it)
        }.getOrDefault(SecurityIssuesInfo(emptyList(), emptyList()))
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
                        databaseToMergeMasterCredential = MasterCredential(
                            password = masterPassword,
                            keyFileData = DatabaseManager.getKeyFileData()
                        ),
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
            // 合并可能已部分改动内存实例，丢弃缓存以免污染状态被写回磁盘。
            // 使用 invalidateCacheKeepKeyFile 而非 close：保留 keyFileData，
            // 否则密钥文件保护的库后续静默重开会因缺失凭据失败（关联问题 #1/#9）。
            DatabaseManager.invalidateCacheKeepKeyFile()
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
            // 按当前条目约定的字段顺序组装 OTP URI：title=issuer、username=label（旧版反存数据按反向传入）
            val oldConvention = isOldTokenConvention(entry, otpElement)
            val otpField = OtpEntryFields.buildOtpField(
                otpElement,
                if (oldConvention) entry.username else entry.title,
                if (oldConvention) entry.title else entry.username
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

    /**
     * 判断令牌条目是否为旧版反存约定（title=label、username=issuer）。
     *
     * 新约定为 title=issuer、username=label，与 OTP 字段内的 issuer/name 对照：
     * 若标题与 OTP name 相同、账号与 OTP issuer 相同，即判定为旧版反存。
     * 两字段均非空时才认定，避免 label 恰与 issuer 相同导致误判。
     */
    private fun isOldTokenConvention(entry: Entry, otpElement: OtpElement): Boolean {
        return entry.title == otpElement.name &&
            entry.username == otpElement.issuer &&
            entry.title.isNotBlank() &&
            entry.username.isNotBlank()
    }

    /**
     * 迁移旧版反存约定的令牌条目（title=label、username=issuer → 互相对换）。
     *
     * 仅处理含 OTP 字段且符合旧约定的条目：直接交换标准字段 title/username，
     * 不触碰 OTP 自定义字段与其余字段。返回迁移条目数，供调用方决定是否落盘
     * （withDatabase 的 saveAfter 会在 [Database.dataModifiedSinceLastLoading] 为真时保存）。
     */
    private fun migrateLegacyTokenConvention(db: Database): Int {
        var migrated = 0
        collectEntriesOutsideRecycleBin(db, db.rootGroup).forEach { entry ->
            val otpElement = entry.getOtpElement() ?: return@forEach
            if (isOldTokenConvention(entry, otpElement)) {
                val oldTitle = entry.title
                entry.title = entry.username
                entry.username = oldTitle
                db.updateEntry(entry)
                migrated++
            }
        }
        return migrated
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

        // 约定：title = 服务商（issuer），username = 账号（label），与 EntryInfo.setOtp 一致。
        // 旧版本曾把两者反存（title=label、username=issuer），按 OTP 字段中的 issuer/name 判定迁移。
        val oldConvention = isOldTokenConvention(entry, otpElement)
        val label = if (oldConvention) entry.title else entry.username.takeIf { it.isNotBlank() }
        val issuer = if (oldConvention) entry.username else entry.title.takeIf { it.isNotBlank() }
        val finalLabel = label
            ?: otpElement.name.takeIf { it.isNotBlank() }
            ?: "Token"
        val finalIssuer = issuer
            ?: otpElement.issuer.takeIf { it.isNotBlank() }
        val description = entry.notes.takeIf { it.isNotBlank() }

        val ordinal = -entry.creationTime.toMilliseconds()

        return OtpToken(
            id = toStableId(entry),
            ordinal = ordinal,
            issuer = finalIssuer,
            label = finalLabel,
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
            title = token.issuer?.takeIf { it.isNotBlank() } ?: token.label
            username = token.label
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
        // 自定义图标按 UUID 记忆化：共享同一图标的条目只解压复制一次
        val customIconBytesCache = HashMap<String, ByteArray?>()

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
                    customIconBytes = readCustomIconBytes(database, entry, customIconBytesCache),
                    keyValues = values,
                    attachments = attachments,
                    expiryTime = if (entry.expires) entry.expiryTime.toMilliseconds() else null,
                    isExpired = entry.expires && entry.isCurrentlyExpires,
                    customIconUuid = customIconUuid,
                    tags = entry.tags.toList(),
                    creationTime = entry.creationTime.toMilliseconds(),
                    modifiedTime = entry.lastModificationTime.toMilliseconds(),
                    isFolderGroup = false,
                    isFolderPlaceholder = false
                )
            )
        }

        // 排序键缓存：避免比较器内每次比较都重复 lowercase()，O(n log n) → 每项仅计算一次
        val sortKeyCache = HashMap<PasswordEntry, Pair<String, String>>()
        val keysOf: (PasswordEntry) -> Pair<String, String> = { entry ->
            sortKeyCache.getOrPut(entry) { entry.title.lowercase() to entry.account.lowercase() }
        }

        return result.sortedWith(
            compareBy<PasswordEntry> { if (it.isFolderGroup) 0 else 1 }
                .thenBy { keysOf(it).first }
                .thenBy { keysOf(it).second }
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

    /**
     * 将字符串列表转换为 KDBX Tags。
     */
    private fun List<String>.toTags(): Tags = Tags().apply {
        this@toTags.forEach { tag -> put(tag) }
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
     * 收集 [group] 下所有后代分组（不含 [group] 自身），用于批量操作时一次性建立索引。
     */
    private fun collectAllGroups(group: Group?): List<Group> {
        if (group == null) {
            return emptyList()
        }
        val list = mutableListOf<Group>()
        group.getChildGroups().forEach { child ->
            list.add(child)
            list.addAll(collectAllGroups(child))
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
    private fun openDatabase(
        location: DatabaseLocation,
        masterPassword: String,
        keyFileData: ByteArray? = DatabaseManager.getKeyFileData()
    ): Pair<Database, File> {
        val cacheDirectory = buildCacheDirectory(location)
        val database = Database()
        openInputStream(location).use { input ->
            database.loadData(
                databaseStream = input,
                masterCredential = MasterCredential(
                    password = masterPassword,
                    keyFileData = keyFileData
                ),
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
     *
     * 成功落盘后通过 [DatabaseManager.onDatabaseSaved] 推进写入代次并同步缓存快照，
     * 让缓存代次快照机制能识别「其他实例在此期间写入过磁盘」的场景，
     * 避免过期缓存回滚并发修改；自身实例的写入不会把缓存误判为过期。
     */
    private fun saveDatabase(
        database: Database,
        location: DatabaseLocation,
        masterPassword: String,
        cacheDirectory: File,
        keyFileData: ByteArray? = DatabaseManager.getKeyFileData()
    ) {
        val cacheFile = File.createTempFile("kdbx-save-", ".tmp", cacheDirectory)
        // Database.saveData 内部 finally 会删除 cacheFile，无需外层重复处理
        when (location) {
            is DatabaseLocation.FileLocation -> {
                // 原子写入：saveData 先把完整数据库写入 cacheFile，再复制到同目录临时文件；
                // 成功后用 rename 原子替换目标，避免写入中途失败导致原数据库被截断/覆盖成不完整内容。
                val target = location.file
                val parent = target.parentFile ?: File(System.getProperty("java.io.tmpdir") ?: ".")
                if (!parent.exists()) parent.mkdirs()
                val tempFile = File(parent, target.name + ".save.tmp")
                try {
                    database.saveData(
                        cacheFile = cacheFile,
                        databaseOutputStream = { tempFile.outputStream() },
                        isNewLocation = true,
                        masterCredential = MasterCredential(
                            password = masterPassword,
                            keyFileData = keyFileData
                        ),
                        challengeResponseRetriever = emptyChallengeResponseRetriever
                    )
                    // 同文件系统下 rename 原子替换目标；失败（如跨卷）则回退到整文件复制
                    if (!tempFile.renameTo(target)) {
                        tempFile.copyTo(target, overwrite = true)
                        tempFile.delete()
                    }
                } finally {
                    runCatching { tempFile.delete() }
                }
            }

            is DatabaseLocation.UriLocation -> {
                // SAF 不支持原子替换：saveData 先将完整数据库写入 cacheFile，
                // 再复制到目标 Uri。复制失败会抛出，调用方捕获（SAF 固有限制下原 Uri 可能被截断）。
                database.saveData(
                    cacheFile = cacheFile,
                    databaseOutputStream = { openOutputStream(location) },
                    isNewLocation = true,
                    masterCredential = MasterCredential(
                        password = masterPassword,
                        keyFileData = keyFileData
                    ),
                    challengeResponseRetriever = emptyChallengeResponseRetriever
                )
            }
        }
        // 所有写入分支完成、未抛异常 = 磁盘已更新。
        // 代次推进必须在写入成功之后，否则代次前进但磁盘仍是旧内容会产生反向"过期"误判。
        DatabaseManager.onDatabaseSaved(database)
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
    private fun ensureLocationInitialized(location: DatabaseLocation, masterPassword: String, keyFileData: ByteArray? = null) {
        when (location) {
            is DatabaseLocation.FileLocation -> {
                val file = location.file
                if (!file.exists() || file.length() == 0L) {
                    initializeDatabase(file.absolutePath, masterPassword, keyFileData)
                }
            }

            is DatabaseLocation.UriLocation -> {
                if (!hasUriData(location.uri)) {
                    initializeDatabase(location.uri.toString(), masterPassword, keyFileData)
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
     * 按图标 UUID 记忆化：同一图标被多条目共享时只解压复制一次。
     */
    private fun readCustomIconBytes(
        database: Database,
        entry: Entry,
        cache: MutableMap<String, ByteArray?>
    ): ByteArray? {
        val iconUuid = entry.icon.custom.uuid
        if (iconUuid == DatabaseVersioned.UUID_ZERO) {
            return null
        }
        val uuidKey = iconUuid.toString()
        if (cache.containsKey(uuidKey)) {
            return cache[uuidKey]
        }

        val bytes = runCatching {
            database.getBinaryForCustomIcon(iconUuid)
                ?.getUnGzipInputDataStream(database.binaryCache)
                ?.use { input -> input.readBytes() }
        }.getOrNull()
        cache[uuidKey] = bytes
        return bytes
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
            // 必须显式重置为「永不过期」：EntryInfo 默认值是「当前时间+30天」，
            // setEntryInfo 会无条件写入，若不重置会导致无过期条目的 expiryTime
            // 被写成过去的时刻，被安全性检查误判为已过期
            entryInfo.expiryTime = DateInstant.NEVER_EXPIRES
        }
        val newCustomIconBytes = draft.newCustomIconBytes
        if (newCustomIconBytes != null) {
            // 用户新选择的自定义图片：写入数据库自定义图标池
            database.buildNewCustomIcon { customIcon, binary ->
                if (customIcon != null && binary != null) {
                    binary.getOutputDataStream(database.binaryCache).use { output ->
                        output.write(newCustomIconBytes)
                    }
                    entryInfo.icon = IconImage(customIcon)
                } else {
                    entryInfo.icon = IconImage(database.getStandardIcon(draft.iconStandardId))
                }
            }
        } else if (draft.customIconUuid == null) {
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
