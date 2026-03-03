package xzynine.WebDAVPass.Android.data

import android.util.Log
import com.kunzisoft.keepass.database.element.Database
import com.kunzisoft.keepass.database.element.Entry
import com.kunzisoft.keepass.database.element.Field
import com.kunzisoft.keepass.database.element.Group
import com.kunzisoft.keepass.database.element.MasterCredential
import com.kunzisoft.keepass.database.element.database.DatabaseVersioned
import com.kunzisoft.keepass.hardware.HardwareKey
import com.kunzisoft.keepass.model.EntryInfo
import com.kunzisoft.keepass.otp.OtpElement
import com.kunzisoft.keepass.otp.OtpEntryFields
import com.kunzisoft.keepass.otp.OtpEntryFields.isOTP
import com.kunzisoft.keepass.otp.OtpType
import com.kunzisoft.keepass.otp.TokenCalculator
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlin.math.abs

class KdbxTokenRepository {

    @Volatile
    private var lastUnlockErrorMessage: String? = null

    companion object {
        private const val LOG_TAG = "tag:解锁"
        private const val DATABASE_NAME = "WebDavPass"
        private const val ROOT_GROUP_NAME = "WebDavPass"
    }

    private val emptyChallengeResponseRetriever: (HardwareKey, ByteArray?) -> ByteArray = { _, _ ->
        ByteArray(0)
    }

    fun initializeDatabase(localPath: String, masterPassword: String) {
        val file = File(localPath)
        file.parentFile?.mkdirs()
        if (file.exists() && file.length() > 0) {
            return
        }
        file.writeBytes(createDatabaseBytes(masterPassword))
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
            val file = File(localPath)
            if (!file.exists() || file.length() == 0L) {
                initializeDatabase(localPath, masterPassword)
            }

            val (database, cacheDirectory) = openDatabase(file, masterPassword)
            try {
                database.rootGroup
            } finally {
                database.clearAndClose(cacheDirectory)
            }
            true
        }.onFailure {
            val file = File(localPath)
            val hint = buildFileHint(file)
            lastUnlockErrorMessage = "${it.javaClass.simpleName}: ${it.message ?: "unknown"} | $hint"
            Log.e(LOG_TAG, "validatePassword failed, path=$localPath, hint=$hint, message=${it.message}", it)
        }.getOrDefault(false)
    }

    fun getLastUnlockErrorMessage(): String? {
        return lastUnlockErrorMessage
    }

    fun loadTokens(localPath: String, masterPassword: String): List<OtpToken> {
        return withDatabase(localPath, masterPassword, saveAfter = false) { db ->
            val entries = collectEntries(db.rootGroup)
            entries.mapNotNull { entry -> toToken(entry) }
                .sortedBy { it.ordinal }
        }
    }

    /**
     * 读取数据库中全部条目与键值（包含 OTP 条目与 OTP 字段）。
     */
    fun loadPasswordEntries(localPath: String, masterPassword: String): List<PasswordEntry> {
        return withDatabase(localPath, masterPassword, saveAfter = false) { db ->
            val result = mutableListOf<PasswordEntry>()
            val entries = collectEntries(db.rootGroup)
            entries.forEach { entry ->
                val title = entry.title
                    .takeIf { it.isNotBlank() }
                    ?: entry.url.takeIf { it.isNotBlank() }
                    ?: entry.username.takeIf { it.isNotBlank() }
                    ?: toStableId(entry).toString()
                val account = entry.username.takeIf { it.isNotBlank() } ?: ""
                val values = mutableListOf<RemainingKeyValue>()

                appendStandardField(values, "UserName", entry.username)
                appendStandardField(values, "Password", entry.password, RemainingValueType.PASSWORD)
                appendStandardField(values, "URL", entry.url)
                appendStandardField(values, "Notes", entry.notes)

                entry.getExtraFields()
                    .forEach { field ->
                        val value = field.protectedValue.stringValue
                        if (value.isNotBlank()) {
                            values.add(
                                RemainingKeyValue(
                                    fieldName = field.name,
                                    rawValue = value,
                                    valueType = detectValueType(field, value)
                                )
                            )
                        }
                    }

                result.add(
                    PasswordEntry(
                        entryId = toStableId(entry),
                        title = title,
                        account = account,
                        standardIconId = entry.icon.standard.id,
                        customIconBytes = readCustomIconBytes(db, entry),
                        keyValues = values.sortedBy { it.fieldName.lowercase() }
                    )
                )
            }

            result.sortedWith(
                compareBy<PasswordEntry> { it.title.lowercase() }
                    .thenBy { it.account.lowercase() }
            )
        }
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
            val parent = entry.parent ?: return@withDatabase false
            db.removeEntryFrom(entry, parent)
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
        return collectEntries(db.rootGroup).firstOrNull { entry ->
            val idMatch = toStableId(entry) == token.id
            if (idMatch) {
                return@firstOrNull true
            }

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
        return collectEntries(db.rootGroup).firstOrNull { toStableId(it) == tokenId }
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
        fixedType: RemainingValueType? = null
    ) {
        if (rawValue.isBlank()) {
            return
        }
        target.add(
            RemainingKeyValue(
                fieldName = fieldName,
                rawValue = rawValue,
                valueType = fixedType ?: detectValueType(null, rawValue)
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

    private fun <T> withDatabase(localPath: String, masterPassword: String, saveAfter: Boolean, block: (Database) -> T): T {
        val file = File(localPath)
        if (!file.exists() || file.length() == 0L) {
            initializeDatabase(localPath, masterPassword)
        }

        val (database, cacheDirectory) = openDatabase(file, masterPassword)
        try {
            val result = block(database)
            if (saveAfter && database.dataModifiedSinceLastLoading) {
                saveDatabase(database, file, masterPassword, cacheDirectory)
            }
            return result
        } finally {
            database.clearAndClose(cacheDirectory)
        }
    }

    private fun openDatabase(file: File, masterPassword: String): Pair<Database, File> {
        val cacheDirectory = buildCacheDirectory(file)
        val database = Database()
        file.inputStream().use { input ->
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

    private fun saveDatabase(database: Database, file: File, masterPassword: String, cacheDirectory: File) {
        val cacheFile = File.createTempFile("kdbx-save-", ".tmp", cacheDirectory)
        database.saveData(
            cacheFile = cacheFile,
            databaseOutputStream = { file.outputStream() },
            isNewLocation = true,
            masterCredential = MasterCredential(password = masterPassword),
            challengeResponseRetriever = emptyChallengeResponseRetriever
        )
    }

    private fun buildCacheDirectory(file: File): File {
        val parent = file.parentFile ?: File(System.getProperty("java.io.tmpdir") ?: ".")
        val cacheDirectory = File(parent, ".kdbx-cache")
        if (!cacheDirectory.exists()) {
            cacheDirectory.mkdirs()
        }
        return cacheDirectory
    }

    private fun buildFileHint(file: File): String {
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
        return "size=$length, head=$head"
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
}
