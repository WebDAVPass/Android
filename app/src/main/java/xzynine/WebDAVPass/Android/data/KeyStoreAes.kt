package xzynine.WebDAVPass.Android.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.spec.GCMParameterSpec

/**
 * AndroidKeyStore 与 AES/GCM 的公共参数与纯构建助手。
 *
 * 说明：仅收敛两处各自声明的常量与规格构建；密钥别名命名、用户认证策略
 * 以及调用方各自的加解密流程差异仍保留在各自实现内。
 */
object KeyStoreAes {
    /** AndroidKeyStore 提供者名称。 */
    const val ANDROID_KEYSTORE = "AndroidKeyStore"

    /** AES/GCM/NoPadding 变换名称。 */
    const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"

    /** GCM 认证标签位数。 */
    const val AES_GCM_TAG_BITS = 128

    /**
     * 打开 AndroidKeyStore 实例（load(null)）。
     */
    fun openKeyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    /**
     * 构建 AES-256 + GCM + 无填充的密钥规格构建器。
     *
     * 说明：不含用户认证相关设置，由调用方按自身策略追加。
     */
    fun keyGenParameterSpecBuilder(alias: String): KeyGenParameterSpec.Builder =
        KeyGenParameterSpec
            .Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)

    /**
     * 构建 GCM 参数（认证标签为 [AES_GCM_TAG_BITS] 位）。
     */
    fun gcmParameterSpec(iv: ByteArray): GCMParameterSpec = GCMParameterSpec(AES_GCM_TAG_BITS, iv)

    /**
     * Base64 编码（NO_WRAP）。
     */
    fun encodeBase64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    /**
     * Base64 解码（NO_WRAP）；输入非法时抛出 IllegalArgumentException。
     */
    fun decodeBase64(text: String): ByteArray = Base64.decode(text, Base64.NO_WRAP)
}
