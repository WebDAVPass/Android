package xzynine.WebDAVPass.Android.data

import org.linguafranca.pwdb.kdbx.KdbxCreds
import org.linguafranca.pwdb.kdbx.simple.SimpleDatabase
import org.linguafranca.pwdb.kdbx.simple.SimpleEntry
import org.linguafranca.pwdb.kdbx.simple.SimpleGroup
import java.io.File
import java.util.UUID
import kotlin.math.abs

class KdbxTokenRepository {

    companion object {
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
        val db = SimpleDatabase()
        db.setName("WebDavPass")
        val creds = KdbxCreds(masterPassword.toByteArray(Charsets.UTF_8))
        file.outputStream().use { out ->
            db.save(creds, out)
        }
    }

    fun validatePassword(localPath: String, masterPassword: String): Boolean {
        return runCatching {
            withDatabase(localPath, masterPassword, saveAfter = false) { }
            true
        }.getOrDefault(false)
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

            val entry = db.newEntry(token.label)
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

    private fun findEntryByToken(db: SimpleDatabase, token: OtpToken): SimpleEntry? {
        return collectEntries(db.getRootGroup()).firstOrNull {
            val idProp = it.getProperty(PROP_UNIQUE_ID)
            val uniqueMatch = !idProp.isNullOrBlank() && idProp == token.uniqueId
            uniqueMatch || toStableId(it.getUuid()) == token.id
        }
    }

    private fun findEntryById(db: SimpleDatabase, tokenId: Long): SimpleEntry? {
        return collectEntries(db.getRootGroup()).firstOrNull { toStableId(it.getUuid()) == tokenId }
    }

    private fun toToken(entry: SimpleEntry): OtpToken? {
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

    private fun applyTokenToEntry(entry: SimpleEntry, token: OtpToken) {
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

    private fun collectEntries(group: SimpleGroup): List<SimpleEntry> {
        val list = mutableListOf<SimpleEntry>()
        list.addAll(group.getEntries())
        group.getGroups().forEach { child ->
            list.addAll(collectEntries(child))
        }
        return list
    }

    private fun <T> withDatabase(localPath: String, masterPassword: String, saveAfter: Boolean, block: (SimpleDatabase) -> T): T {
        val file = File(localPath)
        if (!file.exists() || file.length() == 0L) {
            initializeDatabase(localPath, masterPassword)
        }
        val creds = KdbxCreds(masterPassword.toByteArray(Charsets.UTF_8))
        val db = file.inputStream().use { input ->
            SimpleDatabase.load(creds, input)
        }
        val result = block(db)
        if (saveAfter || db.isDirty) {
            file.outputStream().use { out ->
                db.save(creds, out)
            }
        }
        return result
    }

    private fun toStableId(uuid: UUID): Long {
        val mixed = uuid.mostSignificantBits xor uuid.leastSignificantBits
        return when (mixed) {
            Long.MIN_VALUE -> 0L
            else -> abs(mixed)
        }
    }
}
