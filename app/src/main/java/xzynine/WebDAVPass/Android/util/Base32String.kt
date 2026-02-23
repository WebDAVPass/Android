package xzynine.WebDAVPass.Android.util

/**
 * Base32 编解码工具类（从 Google Authenticator 移植）
 */
object Base32String {
    private val BASE_32_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray()
    private val BASE_32_VALUES = IntArray(256)

    init {
        for (i in BASE_32_VALUES.indices) {
            BASE_32_VALUES[i] = -1
        }
        for (i in BASE_32_CHARS.indices) {
            BASE_32_VALUES[BASE_32_CHARS[i].code] = i
        }
    }

    /**
     * 解码 Base32 字符串
     */
    fun decode(encoded: String): ByteArray {
        // 移除空格和等号
        val clean = encoded.trim().uppercase().replace("[ =]".toRegex(), "")
        
        // 计算输出长度
        val outputLength = clean.length * 5 / 8
        val result = ByteArray(outputLength)
        var buffer = 0
        var nextByte = 0
        var bitsLeft = 0
        
        for (c in clean) {
            val value = BASE_32_VALUES[c.code]
            if (value < 0) {
                throw IllegalArgumentException("Invalid Base32 character: $c")
            }
            
            buffer = buffer shl 5
            buffer = buffer or value
            bitsLeft += 5
            
            if (bitsLeft >= 8) {
                result[nextByte++] = (buffer shr (bitsLeft - 8) and 0xFF).toByte()
                bitsLeft -= 8
            }
        }
        
        return result
    }

    /**
     * 编码为 Base32 字符串
     */
    fun encode(data: ByteArray): String {
        if (data.isEmpty()) return ""
        
        val result = StringBuilder()
        var buffer = 0
        var bitsLeft = 0
        
        for (b in data) {
            buffer = buffer shl 8
            buffer = buffer or (b.toInt() and 0xFF)
            bitsLeft += 8
            
            while (bitsLeft >= 5) {
                result.append(BASE_32_CHARS[buffer shr (bitsLeft - 5) and 0x1F])
                bitsLeft -= 5
            }
        }
        
        if (bitsLeft > 0) {
            result.append(BASE_32_CHARS[buffer shl (5 - bitsLeft) and 0x1F])
        }
        
        return result.toString()
    }
}