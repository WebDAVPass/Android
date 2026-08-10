package xzynine.WebDAVPass.Android.util

import kotlin.math.abs
import kotlin.math.log2

/**
 * 密码强度估算工具。
 *
 * 采用熵估算：密码熵 = 长度 × log2(有效字符集大小)，再对连续重复字符与
 * 递增/递减序列进行扣分。数值含义与 KeePass 质量估算一致（bits），
 * 60 bits 以下视为弱密码。
 */
object PasswordStrength {

    /** 弱密码阈值（bits）：低于该值视为弱密码。 */
    const val WEAK_PASSWORD_THRESHOLD_BITS = 60.0

    /** 常见符号集合大小（估算用）。 */
    private const val SYMBOL_SET_SIZE = 33.0

    /**
     * 估算密码熵（bits）。
     *
     * @param password 待评估的密码
     * @return 熵值（bits），空密码返回 0
     */
    fun estimateBits(password: String): Double {
        if (password.isEmpty()) {
            return 0.0
        }
        val length = password.length

        var charsetSize = 0
        if (password.any { it.isLowerCase() }) charsetSize += 26
        if (password.any { it.isUpperCase() }) charsetSize += 26
        if (password.any { it.isDigit() }) charsetSize += 10
        if (password.any { !it.isLetterOrDigit() }) charsetSize += SYMBOL_SET_SIZE.toInt()

        val baseEntropy = length * log2(charsetSize.coerceAtLeast(1).toDouble())

        // 惩罚：连续重复字符
        var repeatPenalty = 0.0
        for (i in 1 until length) {
            if (password[i] == password[i - 1]) {
                repeatPenalty += 2.0
            }
        }

        // 惩罚：递增/递减序列（如 abc、321）
        var sequencePenalty = 0.0
        for (i in 0 until length - 2) {
            val d1 = password[i + 1] - password[i]
            val d2 = password[i + 2] - password[i + 1]
            if (d1 == d2 && abs(d1) == 1) {
                sequencePenalty += 3.0
            }
        }

        return (baseEntropy - repeatPenalty - sequencePenalty).coerceAtLeast(0.0)
    }

    /**
     * 判断密码是否为弱密码。
     */
    fun isWeak(password: String): Boolean {
        return password.isNotEmpty() && estimateBits(password) < WEAK_PASSWORD_THRESHOLD_BITS
    }
}

/**
 * 熵 bits 转可视化等级（仅供 UI 展示）。
 */
fun strengthLabel(bits: Double): String {
    return when {
        bits < 30.0 -> "非常弱"
        bits < 60.0 -> "弱"
        bits < 100.0 -> "中等"
        else -> "强"
    }
}
