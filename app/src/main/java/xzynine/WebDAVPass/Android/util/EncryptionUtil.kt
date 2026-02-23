package xzynine.WebDAVPass.Android.util

import android.util.Base64
import xzynine.WebDAVPass.Android.data.BackupConstants
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 加密工具类，提供AES加密和解密功能
 */
object EncryptionUtil {
    private val secureRandom = SecureRandom()
    
    /**
     * 生成随机盐
     * @return 随机盐
     */
    private fun generateSalt(): ByteArray {
        val salt = ByteArray(BackupConstants.Encryption.SALT_LENGTH)
        secureRandom.nextBytes(salt)
        return salt
    }
    
    /**
     * 生成随机IV
     * @return 随机IV
     */
    private fun generateIv(): ByteArray {
        val iv = ByteArray(BackupConstants.Encryption.IV_LENGTH)
        secureRandom.nextBytes(iv)
        return iv
    }
    
    /**
     * 从密码派生密钥
     * @param password 密码
     * @param salt 盐
     * @return 派生的密钥
     */
    fun deriveKey(password: String, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(
            password.toCharArray(),
            salt,
            BackupConstants.Encryption.ITERATIONS,
            BackupConstants.Encryption.KEY_LENGTH
        )
        val factory = SecretKeyFactory.getInstance(BackupConstants.Encryption.KEY_DERIVATION_ALGORITHM)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }
    
    /**
     * 加密数据
     * @param data 要加密的数据
     * @param password 加密密码
     * @return 加密后的数据，格式：[salt][iv][ciphertext+tag]
     */
    fun encrypt(data: ByteArray, password: String): ByteArray {
        // 生成盐和IV
        val salt = generateSalt()
        val iv = generateIv()
        
        // 派生密钥
        val key = deriveKey(password, salt)
        
        // 初始化加密器
        val cipher = Cipher.getInstance(BackupConstants.Encryption.ALGORITHM)
        val gcmSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec)
        
        // 加密数据，AES-GCM自动将认证标签附加到密文末尾
        val ciphertextWithTag = cipher.doFinal(data)
        
        // 组装结果：盐 + IV + 密文+标签
        val result = ByteArray(
            BackupConstants.Encryption.SALT_LENGTH +
            BackupConstants.Encryption.IV_LENGTH +
            ciphertextWithTag.size
        )
        
        var offset = 0
        System.arraycopy(salt, 0, result, offset, BackupConstants.Encryption.SALT_LENGTH)
        offset += BackupConstants.Encryption.SALT_LENGTH
        
        System.arraycopy(iv, 0, result, offset, BackupConstants.Encryption.IV_LENGTH)
        offset += BackupConstants.Encryption.IV_LENGTH
        
        System.arraycopy(ciphertextWithTag, 0, result, offset, ciphertextWithTag.size)
        
        return result
    }
    
    /**
     * 解密数据
     * @param encryptedData 加密后的数据
     * @param password 解密密码
     * @return 解密后的数据
     */
    fun decrypt(encryptedData: ByteArray, password: String): ByteArray {
        // 解析加密数据
        var offset = 0
        val salt = encryptedData.copyOfRange(
            offset,
            offset + BackupConstants.Encryption.SALT_LENGTH
        )
        offset += BackupConstants.Encryption.SALT_LENGTH
        
        val iv = encryptedData.copyOfRange(
            offset,
            offset + BackupConstants.Encryption.IV_LENGTH
        )
        offset += BackupConstants.Encryption.IV_LENGTH
        
        // 剩余部分是密文+标签（AES-GCM格式）
        val ciphertextWithTag = encryptedData.copyOfRange(offset, encryptedData.size)
        
        // 派生密钥
        val key = deriveKey(password, salt)
        
        // 初始化解密器
        val cipher = Cipher.getInstance(BackupConstants.Encryption.ALGORITHM)
        val gcmSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec)
        
        // 解密数据，AES-GCM自动验证并剥离标签
        return cipher.doFinal(ciphertextWithTag)
    }
    
    /**
     * 将字节数组转换为Base64字符串
     * @param bytes 字节数组
     * @return Base64字符串
     */
    fun toBase64(bytes: ByteArray): String {
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
    
    /**
     * 将Base64字符串转换为字节数组
     * @param base64 Base64字符串
     * @return 字节数组
     */
    fun fromBase64(base64: String): ByteArray {
        return Base64.decode(base64, Base64.NO_WRAP)
    }
}
