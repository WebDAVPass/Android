package xzynine.WebDAVPass.Android.biometric

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.nio.charset.Charset
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class BiometricKeyStoreManager(private val context: Context) {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS_PREFIX = "webdavpass_auto_unlock_"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val ERROR_REQUIRE_DEVICE_CREDENTIAL = -10001
    }

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    fun generateKey(libraryId: String) {
        val alias = KEY_ALIAS_PREFIX + libraryId
        if (keyStore.containsAlias(alias)) {
            // Check if key is valid/usable?
            // Usually we just keep existing key.
            return
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(
                0, // 0 seconds = require auth for every use
                KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
            )
        } else {
            // API 23-29: set validity duration to allow KeyguardManager fallback
            builder.setUserAuthenticationValidityDurationSeconds(10)
        }

        keyGenerator.init(builder.build())
        keyGenerator.generateKey()
    }

    fun deleteKey(libraryId: String) {
        val alias = KEY_ALIAS_PREFIX + libraryId
        if (keyStore.containsAlias(alias)) {
            keyStore.deleteEntry(alias)
        }
    }

    fun hasKey(libraryId: String): Boolean {
        val alias = KEY_ALIAS_PREFIX + libraryId
        return keyStore.containsAlias(alias)
    }

    fun getCipherForEncryption(libraryId: String): Cipher {
        val alias = KEY_ALIAS_PREFIX + libraryId
        if (!keyStore.containsAlias(alias)) {
            generateKey(libraryId)
        }
        val key = keyStore.getKey(alias, null) as SecretKey
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return cipher
    }

    fun getCipherForDecryption(libraryId: String, ivBase64: String): Cipher {
        val alias = KEY_ALIAS_PREFIX + libraryId
        val key = keyStore.getKey(alias, null) as SecretKey
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return cipher
    }

    fun encrypt(cipher: Cipher, plaintext: String): Pair<String, String> {
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charset.forName("UTF-8")))
        val iv = cipher.iv
        return Pair(
            Base64.encodeToString(ciphertext, Base64.NO_WRAP),
            Base64.encodeToString(iv, Base64.NO_WRAP)
        )
    }

    fun decrypt(cipher: Cipher, ciphertextBase64: String): String {
        val ciphertext = Base64.decode(ciphertextBase64, Base64.NO_WRAP)
        val plaintext = cipher.doFinal(ciphertext)
        return String(plaintext, Charset.forName("UTF-8"))
    }

    /**
     * API 29 的设备凭据认证回退入口。
     */
    fun createDeviceCredentialIntent(title: String, subtitle: String): Intent? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return null
        }
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (!keyguardManager.isDeviceSecure) {
            return null
        }
        return keyguardManager.createConfirmDeviceCredentialIntent(title, subtitle)
    }
    
    // Authentication Logic
    fun authenticate(
        activity: FragmentActivity,
        cipher: Cipher?, // Can be null if using Keyguard fallback (no CryptoObject)
        title: String? = null,
        subtitle: String = "使用生物识别或设备密码解锁",
        negativeButtonText: String = "取消",
        authMode: Int = 0,
        libraryFileName: String? = null,
        onSuccess: (Cipher?) -> Unit,
        onFailure: (Int, CharSequence) -> Unit
    ) {
        // 默认标题：自动解锁场景（未显式指定标题）时展示"验证身份并自动解锁{库文件名}"，
        // 未提供库文件名时回退为通用"验证身份"。
        val resolvedTitle = title
            ?: if (libraryFileName.isNullOrBlank()) "验证身份" else "验证身份并自动解锁$libraryFileName"

        // API 29 的 PIN 模式由外层使用 Keyguard Intent 处理。
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R && authMode == 2) {
            onFailure(ERROR_REQUIRE_DEVICE_CREDENTIAL, "需要设备凭据认证")
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess(result.cryptoObject?.cipher ?: cipher)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                
                // API 29 默认模式且生物不可用时，通知外层走设备凭据 Intent。
                if (
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.R
                    && authMode == 0
                    && (
                        errorCode == BiometricPrompt.ERROR_NO_BIOMETRICS
                            || errorCode == BiometricPrompt.ERROR_HW_UNAVAILABLE
                            || errorCode == BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL
                    )
                ) {
                    onFailure(ERROR_REQUIRE_DEVICE_CREDENTIAL, "需要设备凭据认证")
                    return
                }
                onFailure(errorCode, errString)
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                // Biometric recognized but rejected (e.g. wrong fingerprint)
                // Do not call onFailure here, let user retry (BiometricPrompt handles retries)
            }
        }

        val biometricPrompt = BiometricPrompt(activity, executor, callback)
        val promptInfoBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(resolvedTitle)
            .setSubtitle(subtitle)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val authenticators = when (authMode) {
                1 -> BiometricManager.Authenticators.BIOMETRIC_STRONG
                2 -> BiometricManager.Authenticators.DEVICE_CREDENTIAL
                else -> BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            }
            promptInfoBuilder.setAllowedAuthenticators(authenticators)

            // Biometric only 模式下需要显式取消按钮。
            if (authMode == 1) {
                promptInfoBuilder.setNegativeButtonText(negativeButtonText)
            }
        } else {
            // API 29: CryptoObject + 设备凭据不支持，统一走生物识别弹窗。
            promptInfoBuilder.setNegativeButtonText(negativeButtonText)
        }

        val promptInfo = promptInfoBuilder.build()

        try {
            if (cipher != null) {
                biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
            } else {
                biometricPrompt.authenticate(promptInfo)
            }
        } catch (e: Exception) {
             onFailure(BiometricPrompt.ERROR_HW_UNAVAILABLE, e.message ?: "Unknown error")
        }
    }
}
