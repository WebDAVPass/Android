package xzynine.WebDAVPass.Android.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * WebDAV 密码加解密工具
 *
 * 说明：
 * - 密钥保存在 AndroidKeyStore 中（设备绑定、不可导出），全应用共用一把密钥；
 * - 密文格式为 `AES:` 前缀 + Base64(IV + GCM密文)，无前缀视为不可解密的异常数据；
 * - 卸载或清除应用数据时，AndroidKeyStore 密钥与数据库一并移除，无需额外清理。
 */
object WebDavPasswordCipher {
    /**
     * 密文前缀标记，用于区分加密数据与异常数据
     */
    const val PREFIX = "AES:"

    private const val KEY_ALIAS = "webdavpass_password_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_LENGTH = 12
    private const val TAG_BITS = 128

    private val lock = Any()

    @Volatile
    private var cachedKey: SecretKey? = null

    /**
     * 加密明文密码。
     *
     * 说明：
     * - 若入参已是本工具生成的密文则原样返回（幂等，避免重复加密）；
     * - 密钥不存在时自动生成。
     *
     * @param plain 明文密码
     * @return 密文字符串
     */
    fun encrypt(plain: String): String {
        if (plain.startsWith(PREFIX) && decrypt(plain) != null) {
            return plain
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val payload = ByteArray(iv.size + encrypted.size)
        System.arraycopy(iv, 0, payload, 0, iv.size)
        System.arraycopy(encrypted, 0, payload, iv.size, encrypted.size)
        return PREFIX + Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    /**
     * 解密密文字符串。
     *
     * 说明：
     * - 非 `AES:` 前缀或解密失败（如密钥缺失）时返回 null，表示该密码不可用；
     * - 正常使用中不存在明文回退：一次性迁移已将所有存量明文转为密文。
     *
     * @param stored 密文字符串
     * @return 明文密码；不可用时返回 null
     */
    fun decrypt(stored: String): String? {
        if (!stored.startsWith(PREFIX)) {
            return null
        }
        val payload = runCatching { Base64.decode(stored.removePrefix(PREFIX), Base64.NO_WRAP) }.getOrNull() ?: return null
        if (payload.size <= IV_LENGTH) {
            return null
        }
        val key = getKey() ?: return null
        return runCatching {
            val iv = payload.copyOfRange(0, IV_LENGTH)
            val encrypted = payload.copyOfRange(IV_LENGTH, payload.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        }.getOrNull()
    }

    /**
     * 从 AndroidKeyStore 读取密钥（带缓存，避免每次跨进程访问 keystore）。
     *
     * @return 密钥；不存在或不可恢复时返回 null
     */
    private fun getKey(): SecretKey? {
        cachedKey?.let { return it }
        return runCatching {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        }.getOrNull()?.also { cachedKey = it }
    }

    /**
     * 获取或创建密钥（并发安全）。
     *
     * @return 可用的 AES 密钥
     */
    private fun getOrCreateKey(): SecretKey {
        getKey()?.let { return it }
        synchronized(lock) {
            getKey()?.let { return it }
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            keyGenerator.init(
                KeyGenParameterSpec
                    .Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            return keyGenerator.generateKey().also { cachedKey = it }
        }
    }
}
