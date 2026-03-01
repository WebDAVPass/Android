package xzynine.WebDAVPass.Android.util

import xzynine.WebDAVPass.Android.data.OtpToken
import xzynine.WebDAVPass.Android.data.OtpTokenType
import xzynine.WebDAVPass.Android.data.TokenCode
import java.nio.ByteBuffer
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * TOTP/HOTP 令牌代码生成工具类
 */
class TokenCodeUtil {
    
    /**
     * 生成令牌代码
     */
    fun generateTokenCode(otpToken: OtpToken): TokenCode {
        val cur = System.currentTimeMillis()
        val period = otpToken.period.coerceAtLeast(1)

        when (otpToken.tokenType) {
            OtpTokenType.HOTP ->
                return TokenCode(getHOTP(otpToken, otpToken.counter), cur, cur + period * 1000)
            OtpTokenType.TOTP -> {
                val counter: Long = cur / 1000 / period
                return TokenCode(
                    getHOTP(otpToken, counter + 0),
                    (counter + 0) * period * 1000,
                    (counter + 1) * period * 1000,
                    TokenCode(
                        getHOTP(otpToken, counter + 1),
                        (counter + 1) * period * 1000,
                        (counter + 2) * period * 1000
                    )
                )
            }
        }
    }

    /**
     * 生成 HOTP 代码
     */
    private fun getHOTP(otpToken: OtpToken, counter: Long): String {
        // 编码计数器为网络字节序
        val bb = ByteBuffer.allocate(8)
        bb.putLong(counter)

        // 创建位数除数
        var div = 1
        for (i in otpToken.digits downTo 1) div *= 10

        // 创建 HMAC
        try {
            val mac = Mac.getInstance("Hmac${otpToken.algorithm}")
            mac.init(SecretKeySpec(Base32String.decode(otpToken.secret), "Hmac${otpToken.algorithm}"))

            // 执行哈希计算
            val digest = mac.doFinal(bb.array())

            // 动态截断
            var binary: Int
            val off = digest[digest.size - 1].toInt() and 0xf
            binary = digest[off].toInt() and 0x7f shl 0x18
            binary = binary or (digest[off + 1].toInt() and 0xff shl 0x10)
            binary = binary or (digest[off + 2].toInt() and 0xff shl 0x08)
            binary = binary or (digest[off + 3].toInt() and 0xff)
            var hotp = ""
            
            // Steam 特殊处理
            if (otpToken.issuer == "Steam") {
                for (i in 0 until otpToken.digits) {
                    hotp += STEAMCHARS[binary % STEAMCHARS.size]
                    binary /= STEAMCHARS.size
                }
            } else {
                binary %= div

                // 零填充
                hotp = binary.toString()
                while (hotp.length != otpToken.digits) hotp = "0$hotp"
            }
            return hotp
        } catch (e: InvalidKeyException) {
            e.printStackTrace()
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
        } catch (e: IllegalArgumentException) {
            // 捕获 Base32 解码异常
            e.printStackTrace()
        } catch (e: ArrayIndexOutOfBoundsException) {
            // 捕获数组越界异常
            e.printStackTrace()
        }
        return ""
    }

    companion object {
        // Steam 令牌字符集
        private val STEAMCHARS = charArrayOf(
            '2', '3', '4', '5', '6', '7', '8', '9', 'B', 'C',
            'D', 'F', 'G', 'H', 'J', 'K', 'M', 'N', 'P', 'Q',
            'R', 'T', 'V', 'W', 'X', 'Y'
        )
    }
}