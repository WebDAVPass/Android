package xzynine.WebDAVPass.Android.util

import java.security.MessageDigest
import java.util.Locale

/**
 * 唯一标识生成器，用于生成令牌的唯一标识符
 */
object UniqueIdGenerator {
    private val messageDigest = MessageDigest.getInstance("SHA-256")
    
    /**
     * 生成唯一标识符
     * @param secret 令牌密钥
     * @param algorithm 算法
     * @param digits 位数
     * @param period 周期
     * @return 唯一标识符（SHA-256哈希值的十六进制表示）
     */
    fun generate(secret: String, algorithm: String, digits: Int, period: Int): String {
        // 标准化处理：密钥去除空格，算法转为大写
        val normalizedSecret = secret.trim()
        val normalizedAlgorithm = algorithm.uppercase(Locale.ROOT)
        
        // 生成输入字符串
        val input = "$normalizedSecret$normalizedAlgorithm$digits$period"
        
        // 计算SHA-256哈希
        val hashBytes = messageDigest.digest(input.toByteArray())
        
        // 转换为十六进制字符串
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
