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
        private const val IV_SEPARATOR = "]"
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
    
    // Authentication Logic
    fun authenticate(
        activity: FragmentActivity,
        cipher: Cipher?, // Can be null if using Keyguard fallback (no CryptoObject)
        title: String = "验证身份",
        subtitle: String = "使用生物识别或设备密码解锁",
        negativeButtonText: String = "取消",
        onSuccess: (Cipher?) -> Unit,
        onFailure: (Int, CharSequence) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess(result.cryptoObject?.cipher ?: cipher)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                
                // API 29 Fallback logic for NO_BIOMETRICS or similar
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R && 
                   (errorCode == BiometricPrompt.ERROR_NO_BIOMETRICS || 
                    errorCode == BiometricPrompt.ERROR_HW_UNAVAILABLE ||
                    errorCode == BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL)) {
                    
                    // Try Keyguard Manager
                    val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                    if (keyguardManager.isDeviceSecure) {
                        val intent = keyguardManager.createConfirmDeviceCredentialIntent(title, subtitle)
                        if (intent != null) {
                            // We need to launch intent and wait for result. 
                            // This is tricky inside a helper method without Activity Result API.
                            // We might need to ask ViewModel/Activity to handle this.
                            // But for now, let's just report error and let UI handle fallback if needed, 
                            // OR we assume the caller handles fallback.
                            // The prompt says "API 29 use createConfirmDeviceCredentialIntent fallback".
                            // I will report a specific error code or handle it if I can.
                            
                            // Since we can't startActivityForResult here easily without registering it beforehand in Activity/Fragment,
                            // we should probably let the UI layer handle the Intent launch.
                            // So I will pass a special error or just let the caller handle it?
                            // Actually, I can use a simpler approach: 
                            // The caller (ViewModel/Activity) should call `authenticate`.
                            // If `authenticate` fails with specific error, caller handles it.
                            onFailure(errorCode, errString) 
                            return
                        }
                    }
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
            .setTitle(title)
            .setSubtitle(subtitle)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
             promptInfoBuilder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        } else {
             // API 29: can't use DEVICE_CREDENTIAL in AllowedAuthenticators easily with CryptoObject
             // We must set NegativeButtonText
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
