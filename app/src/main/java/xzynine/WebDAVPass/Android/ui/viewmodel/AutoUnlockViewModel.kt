package xzynine.WebDAVPass.Android.ui.ViewModel

import android.content.Context
import xzynine.WebDAVPass.Android.biometric.BiometricKeyStoreManager
import xzynine.WebDAVPass.Android.data.LibraryContext
import xzynine.WebDAVPass.Android.data.KdbxTokenRepository
import androidx.lifecycle.ViewModel
import javax.crypto.Cipher

/**
 * 自动解锁视图模型
 *
 * 负责管理生物识别自动解锁、PIN 解锁等自动解锁功能。
 */
class AutoUnlockViewModel(private val context: Context) : ViewModel() {

    companion object {
        const val AUTO_UNLOCK_AUTH_MODE_DEFAULT = 0
        const val AUTO_UNLOCK_AUTH_MODE_BIOMETRIC = 1
        const val AUTO_UNLOCK_AUTH_MODE_PIN = 2

        private const val MANUAL_UNLOCK_WINDOW_MILLIS = 48L * 60L * 60L * 1000L
        /** 凭据解锁硬性截止时长：超过该时长只能手动输入主密码（宽限期为 48h→64h）。 */
        private const val CREDENTIAL_UNLOCK_DEADLINE_MILLIS = 64L * 60L * 60L * 1000L
        private const val MINUTE_MILLIS = 60L * 1000L
    }

    val biometricKeyStoreManager = BiometricKeyStoreManager(context)

    /**
     * 判断指定库是否可用于自动解锁。
     */
    fun isAutoUnlockAvailable(library: LibraryContext): Boolean {
        return library.autoUnlockEnabled &&
               !library.autoUnlockInvalidated &&
               !library.encryptedMasterPassword.isNullOrBlank() &&
               !library.encryptedMasterPasswordIv.isNullOrBlank() &&
               biometricKeyStoreManager.hasKey(library.id)
    }

    /**
     * 自动解锁是否处于"失效待重验"状态。
     */
    fun isAutoUnlockInvalidated(library: LibraryContext): Boolean {
        return library.autoUnlockEnabled && library.autoUnlockInvalidated
    }

    /**
     * 是否启用"48小时需手动主密码一次"策略。
     */
    fun isManualUnlockWindowEnabled(library: LibraryContext): Boolean {
        return library.forceManualUnlockEvery48Hours != false
    }

    /**
     * 当前库是否已超过手动主密码时限。
     */
    fun isManualUnlockWindowExpired(
        library: LibraryContext,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (!isManualUnlockWindowEnabled(library)) {
            return false
        }
        val lastManualUnlockAt = library.lastManualMasterUnlockAt ?: return true
        return nowMillis - lastManualUnlockAt >= MANUAL_UNLOCK_WINDOW_MILLIS
    }

    /**
     * 获取"48小时手动主密码"策略剩余时长。
     */
    fun getManualUnlockWindowRemainingMillis(
        library: LibraryContext,
        nowMillis: Long = System.currentTimeMillis()
    ): Long? {
        if (!isManualUnlockWindowEnabled(library)) {
            return null
        }
        val lastManualUnlockAt = library.lastManualMasterUnlockAt ?: return 0L
        val deadline = lastManualUnlockAt + MANUAL_UNLOCK_WINDOW_MILLIS
        return (deadline - nowMillis).coerceAtLeast(0L)
    }

    /**
     * 凭据（PIN/生物识别）解锁是否已超过 64 小时硬性截止。
     *
     * 超过该时限后凭据解锁不再可用，只能手动输入主密码；
     * 关闭强制主密码校验策略时恒为 false（不拦截）。
     */
    fun isCredentialUnlockExpired(
        library: LibraryContext,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (!isManualUnlockWindowEnabled(library)) {
            return false
        }
        val lastManualUnlockAt = library.lastManualMasterUnlockAt ?: return true
        return nowMillis - lastManualUnlockAt >= CREDENTIAL_UNLOCK_DEADLINE_MILLIS
    }

    /**
     * 获取凭据解锁 64 小时硬性截止的剩余时长（供文案展示）。
     */
    fun getCredentialUnlockRemainingMillis(
        library: LibraryContext,
        nowMillis: Long = System.currentTimeMillis()
    ): Long? {
        if (!isManualUnlockWindowEnabled(library)) {
            return null
        }
        val lastManualUnlockAt = library.lastManualMasterUnlockAt ?: return 0L
        val deadline = lastManualUnlockAt + CREDENTIAL_UNLOCK_DEADLINE_MILLIS
        return (deadline - nowMillis).coerceAtLeast(0L)
    }

    /**
     * 将剩余毫秒格式化为"X小时Y分钟"。
     */
    fun formatRemainingHoursMinutes(remainingMillis: Long): String {
        val normalized = remainingMillis.coerceAtLeast(0L)
        val rounded = ((normalized + MINUTE_MILLIS - 1) / MINUTE_MILLIS) * MINUTE_MILLIS
        val totalMinutes = rounded / MINUTE_MILLIS
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return "${hours}小时${minutes}分钟"
    }

    /**
     * 更新当前库"48小时需手动主密码一次"策略开关。
     */
    fun updateManualUnlockWindowEnabled(
        library: LibraryContext,
        enabled: Boolean,
        onPersist: (LibraryContext) -> Unit
    ) {
        val currentValue = library.forceManualUnlockEvery48Hours
        if (currentValue == enabled) {
            return
        }
        // 关闭时清零剩余时间；重新开启时置 null，使下次凭据/生物解锁回到"需手动输入一次主密码"。
        onPersist(
            library.copy(
                forceManualUnlockEvery48Hours = enabled,
                lastManualMasterUnlockAt = null
            )
        )
    }

    /**
     * 仅测试用途：将当前库的48小时手动主密码窗口直接标记为已到期。
     */
    fun forceManualUnlockWindowExpiredForTesting(
        library: LibraryContext,
        onPersist: (LibraryContext) -> Unit
    ) {
        val expiredAt = System.currentTimeMillis() - MANUAL_UNLOCK_WINDOW_MILLIS - MINUTE_MILLIS
        onPersist(library.copy(lastManualMasterUnlockAt = expiredAt))
    }

    /**
     * 凭据自动解锁成功后的策略收尾。
     */
    fun applyPostCredentialUnlockPolicy(
        library: LibraryContext,
        resolveLibrary: (String) -> LibraryContext?,
        onInvalidate: (LibraryContext) -> Unit
    ): Boolean {
        val latest = resolveLibrary(library.id) ?: library
        if (!isAutoUnlockAvailable(latest)) {
            return false
        }
        if (!isManualUnlockWindowExpired(latest)) {
            return false
        }
        invalidateAutoUnlock(latest, onInvalidate)
        return true
    }

    /**
     * 当前库是否应触发"首次自动解锁引导"。
     */
    fun shouldPromptAutoUnlockEnroll(library: LibraryContext): Boolean {
        return !library.autoUnlockEnabled &&
                !library.autoUnlockEnrollDismissed &&
                library.encryptedMasterPassword.isNullOrBlank() &&
                library.encryptedMasterPasswordIv.isNullOrBlank()
    }

    /**
     * 将认证模式规范到可识别范围。
     */
    fun normalizeAutoUnlockAuthMode(mode: Int): Int {
        return when (mode) {
            AUTO_UNLOCK_AUTH_MODE_DEFAULT,
            AUTO_UNLOCK_AUTH_MODE_BIOMETRIC,
            AUTO_UNLOCK_AUTH_MODE_PIN -> mode
            else -> AUTO_UNLOCK_AUTH_MODE_DEFAULT
        }
    }

    /**
     * 启用自动解锁并持久化。
     */
    fun enableAutoUnlock(
        library: LibraryContext,
        cipher: Cipher,
        masterPassword: String,
        resolveLibrary: (String) -> LibraryContext?,
        onPersist: (LibraryContext) -> Unit,
        authMode: Int = library.autoUnlockAuthMode
    ): Boolean {
        if (masterPassword.isBlank()) return false

        return try {
            val baseLibrary = resolveLibrary(library.id) ?: library
            val (encrypted, iv) = biometricKeyStoreManager.encrypt(cipher, masterPassword)
            val updated = baseLibrary.copy(
                autoUnlockEnabled = true,
                encryptedMasterPassword = encrypted,
                encryptedMasterPasswordIv = iv,
                autoUnlockAuthMode = normalizeAutoUnlockAuthMode(authMode),
                autoUnlockInvalidated = false,
                autoUnlockEnrollDismissed = false,
                // 进入自动解锁即代表主密码刚被正确输入，刷新48小时窗口时间戳，
                // 覆盖 unlockCurrentLibrary 内 autoRestoreTokens 可能带来的覆盖副作用。
                lastManualMasterUnlockAt = System.currentTimeMillis()
            )
            onPersist(updated)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 禁用自动解锁并清理密钥。
     */
    fun disableAutoUnlock(library: LibraryContext, onPersist: (LibraryContext) -> Unit) {
        biometricKeyStoreManager.deleteKey(library.id)
        val updated = library.copy(
            autoUnlockEnabled = false,
            encryptedMasterPassword = null,
            encryptedMasterPasswordIv = null,
            autoUnlockInvalidated = false
        )
        onPersist(updated)
    }

    /**
     * 标记自动解锁为失效状态（保留开关与认证方式）。
     */
    fun invalidateAutoUnlock(library: LibraryContext, onPersist: (LibraryContext) -> Unit) {
        biometricKeyStoreManager.deleteKey(library.id)
        val updated = library.copy(
            autoUnlockEnabled = true,
            encryptedMasterPassword = null,
            encryptedMasterPasswordIv = null,
            autoUnlockInvalidated = true
        )
        onPersist(updated)
    }

    /**
     * 更新自动解锁认证模式。
     */
    fun updateAutoUnlockAuthMode(
        library: LibraryContext,
        authMode: Int,
        onPersist: (LibraryContext) -> Unit
    ) {
        val normalized = normalizeAutoUnlockAuthMode(authMode)
        if (library.autoUnlockAuthMode == normalized) {
            return
        }
        onPersist(library.copy(autoUnlockAuthMode = normalized))
    }

    /**
     * 标记该库已拒绝首次自动解锁引导。
     */
    fun setAutoUnlockEnrollDismissed(library: LibraryContext, onPersist: (LibraryContext) -> Unit) {
        val updated = library.copy(autoUnlockEnrollDismissed = true)
        onPersist(updated)
    }

    /**
     * 获取用于解密的 Cipher。若密钥无效会自动清理并返回 null。
     */
    fun getCipherForAutoUnlock(
        library: LibraryContext,
        onInvalidate: (LibraryContext) -> Unit
    ): Cipher? {
        if (!isAutoUnlockAvailable(library)) return null
        return try {
            biometricKeyStoreManager.getCipherForDecryption(library.id, library.encryptedMasterPasswordIv!!)
        } catch (e: Exception) {
            invalidateAutoUnlock(library, onInvalidate)
            null
        }
    }

    /**
     * 获取用于加密（启用）的 Cipher。
     */
    fun getCipherForEnrollment(library: LibraryContext): Cipher? {
        return try {
            biometricKeyStoreManager.getCipherForEncryption(library.id)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 使用生物识别解密并解锁库。
     */
    suspend fun unlockWithBiometric(
        library: LibraryContext,
        cipher: Cipher,
        repository: KdbxTokenRepository,
        onUnlock: suspend (String, Boolean) -> Boolean
    ): Boolean {
        return try {
            val decrypted = biometricKeyStoreManager.decrypt(cipher, library.encryptedMasterPassword!!)
            onUnlock(decrypted, false)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 删除指定库的密钥
     */
    fun deleteKey(libraryId: String) {
        biometricKeyStoreManager.deleteKey(libraryId)
    }
}
