package xzynine.WebDAVPass.Android.data

import android.util.Log
import org.linguafranca.pwdb.kdbx.KdbxCreds
import org.linguafranca.pwdb.kdbx.KdbxHeader
import org.linguafranca.pwdb.kdbx.KdbxSerializer
import org.linguafranca.pwdb.kdbx.dom.DomDatabaseWrapper
import org.linguafranca.pwdb.kdbx.dom.DomEntryWrapper
import org.linguafranca.pwdb.kdbx.dom.DomGroupWrapper
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlin.math.abs

class KdbxTokenRepository {

    @Volatile
    private var lastUnlockErrorMessage: String? = null

    companion object {
        private const val LOG_TAG = "tag:解锁"
        private const val PROP_UNIQUE_ID = "WDP_UNIQUE_ID"
        private const val PROP_ISSUER = "WDP_ISSUER"
        private const val PROP_IMAGE_PATH = "WDP_IMAGE_PATH"
        private const val PROP_TOKEN_TYPE = "WDP_TOKEN_TYPE"
        private const val PROP_ALGORITHM = "WDP_ALGORITHM"
        private const val PROP_DIGITS = "WDP_DIGITS"
        private const val PROP_COUNTER = "WDP_COUNTER"
        private const val PROP_PERIOD = "WDP_PERIOD"
        private const val PROP_ENCRYPTION_TYPE = "WDP_ENCRYPTION_TYPE"
        private const val PROP_ORDINAL = "WDP_ORDINAL"
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
        val db = DomDatabaseWrapper()
        db.setName("WebDavPass")
        val creds = KdbxCreds(masterPassword.toByteArray(Charsets.UTF_8))
        return ByteArrayOutputStream().use { out ->
            db.save(creds, out)
            out.toByteArray()
        }
    }

    fun validatePassword(localPath: String, masterPassword: String): Boolean {
        lastUnlockErrorMessage = null
        return runCatching {
            val file = File(localPath)
            if (!file.exists() || file.length() == 0L) {
                initializeDatabase(localPath, masterPassword)
            }

            val creds = KdbxCreds(masterPassword.toByteArray(Charsets.UTF_8))
            file.inputStream().use { encryptedInput ->
                val header = KdbxHeader()
                KdbxSerializer.createUnencryptedInputStream(creds, header, encryptedInput).use { decryptedInput ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (decryptedInput.read(buffer) != -1) {
                        // consume stream to force full decrypt/auth validation
                    }
                }
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
            val entries = collectEntries(db.getRootGroup())
            entries.mapNotNull { entry -> toToken(entry) }
                .sortedBy { it.ordinal }
        }
    }

    fun isDuplicate(localPath: String, masterPassword: String, secret: String, algorithm: String, digits: Int, period: Int): Boolean {
        return loadTokens(localPath, masterPassword)
            .any { it.secret == secret && it.algorithm == algorithm && it.digits == digits && it.period == period }
    }

    fun addToken(localPath: String, masterPassword: String, token: OtpToken): Boolean {
        return withDatabase(localPath, masterPassword, saveAfter = true) { db ->
            val existing = collectEntries(db.getRootGroup()).mapNotNull { toToken(it) }
                .any { it.uniqueId == token.uniqueId || (it.secret == token.secret && it.algorithm == token.algorithm && it.digits == token.digits && it.period == token.period) }
            if (existing) {
                return@withDatabase false
            }

            val entry = db.newEntry().apply { setTitle(token.label) }
            applyTokenToEntry(entry, token)
            db.getRootGroup().addEntry(entry)
            true
        }
    }

    fun updateToken(localPath: String, masterPassword: String, token: OtpToken): Boolean {
        return withDatabase(localPath, masterPassword, saveAfter = true) { db ->
            val entry = findEntryByToken(db, token) ?: return@withDatabase false
            applyTokenToEntry(entry, token)
            true
        }
    }

    fun deleteToken(localPath: String, masterPassword: String, tokenId: Long): Boolean {
        return withDatabase(localPath, masterPassword, saveAfter = true) { db ->
            val entry = findEntryById(db, tokenId) ?: return@withDatabase false
            entry.getParent()?.removeEntry(entry)
            true
        }
    }

    fun incrementCounter(localPath: String, masterPassword: String, tokenId: Long): Boolean {
        return withDatabase(localPath, masterPassword, saveAfter = true) { db ->
            val entry = findEntryById(db, tokenId) ?: return@withDatabase false
            val current = entry.getProperty(PROP_COUNTER)?.toLongOrNull() ?: 0L
            entry.setProperty(PROP_COUNTER, (current + 1L).toString())
            true
        }
    }

    private fun findEntryByToken(db: DomDatabaseWrapper, token: OtpToken): DomEntryWrapper? {
        return collectEntries(db.getRootGroup()).firstOrNull {
            val idProp = it.getProperty(PROP_UNIQUE_ID)
            val uniqueMatch = !idProp.isNullOrBlank() && idProp == token.uniqueId
            uniqueMatch || toStableId(it.getUuid()) == token.id
        }
    }

    private fun findEntryById(db: DomDatabaseWrapper, tokenId: Long): DomEntryWrapper? {
        return collectEntries(db.getRootGroup()).firstOrNull { toStableId(it.getUuid()) == tokenId }
    }

    private fun toToken(entry: DomEntryWrapper): OtpToken? {
        val secret = entry.getPassword()?.takeIf { it.isNotBlank() } ?: return null
        val algorithm = (entry.getProperty(PROP_ALGORITHM) ?: "SHA1").uppercase()
        val digits = entry.getProperty(PROP_DIGITS)?.toIntOrNull() ?: 6
        val period = entry.getProperty(PROP_PERIOD)?.toIntOrNull() ?: 30
        val counter = entry.getProperty(PROP_COUNTER)?.toLongOrNull() ?: 0L
        val tokenType = runCatching {
            OtpTokenType.valueOf(entry.getProperty(PROP_TOKEN_TYPE) ?: OtpTokenType.TOTP.name)
        }.getOrDefault(OtpTokenType.TOTP)
        val encryptionType = runCatching {
            EncryptionType.valueOf(entry.getProperty(PROP_ENCRYPTION_TYPE) ?: EncryptionType.NONE.name)
        }.getOrDefault(EncryptionType.NONE)

        val uniqueId = entry.getProperty(PROP_UNIQUE_ID)
            ?.takeIf { it.isNotBlank() }
            ?: xzynine.WebDAVPass.Android.util.UniqueIdGenerator.generate(secret, algorithm, digits, period)

        val title = entry.getTitle().takeIf { !it.isNullOrBlank() } ?: "Token"
        val issuer = entry.getProperty(PROP_ISSUER)
            ?.takeIf { it.isNotBlank() }
            ?: entry.getUsername()?.takeIf { it.isNotBlank() }
        val description = entry.getNotes()?.takeIf { it.isNotBlank() }
        val imagePath = entry.getProperty(PROP_IMAGE_PATH)?.takeIf { it.isNotBlank() }
        val ordinal = entry.getProperty(PROP_ORDINAL)?.toLongOrNull() ?: -entry.getCreationTime().time

        return OtpToken(
            id = toStableId(entry.getUuid()),
            ordinal = ordinal,
            issuer = issuer,
            label = title,
            description = description,
            imagePath = imagePath,
            tokenType = tokenType,
            algorithm = algorithm,
            secret = secret,
            digits = digits,
            counter = counter,
            period = period,
            encryptionType = encryptionType,
            uniqueId = uniqueId
        )
    }

    private fun applyTokenToEntry(entry: DomEntryWrapper, token: OtpToken) {
        entry.setTitle(token.label)
        entry.setUsername(token.issuer ?: "")
        entry.setPassword(token.secret)
        entry.setNotes(token.description ?: "")
        entry.setProperty(PROP_UNIQUE_ID, token.uniqueId)
        entry.setProperty(PROP_ISSUER, token.issuer ?: "")
        entry.setProperty(PROP_IMAGE_PATH, token.imagePath ?: "")
        entry.setProperty(PROP_TOKEN_TYPE, token.tokenType.name)
        entry.setProperty(PROP_ALGORITHM, token.algorithm.uppercase())
        entry.setProperty(PROP_DIGITS, token.digits.toString())
        entry.setProperty(PROP_COUNTER, token.counter.toString())
        entry.setProperty(PROP_PERIOD, token.period.toString())
        entry.setProperty(PROP_ENCRYPTION_TYPE, token.encryptionType.name)
        entry.setProperty(PROP_ORDINAL, token.ordinal.toString())
    }

    private fun collectEntries(group: DomGroupWrapper): List<DomEntryWrapper> {
        val list = mutableListOf<DomEntryWrapper>()
        list.addAll(group.getEntries())
        group.getGroups().forEach { child ->
            list.addAll(collectEntries(child))
        }
        return list
    }

    private fun <T> withDatabase(localPath: String, masterPassword: String, saveAfter: Boolean, block: (DomDatabaseWrapper) -> T): T {
        val file = File(localPath)
        if (!file.exists() || file.length() == 0L) {
            initializeDatabase(localPath, masterPassword)
        }
        val creds = KdbxCreds(masterPassword.toByteArray(Charsets.UTF_8))
        val db = file.inputStream().use { input ->
            DomDatabaseWrapper.load(creds, input)
        }
        val result = block(db)
        if (saveAfter || db.isDirty) {
            file.outputStream().use { out ->
                db.save(creds, out)
            }
        }
        return result
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

    private fun toStableId(uuid: UUID): Long {
        val mixed = uuid.mostSignificantBits xor uuid.leastSignificantBits
        return when (mixed) {
            Long.MIN_VALUE -> 0L
            else -> abs(mixed)
        }
    }
}
