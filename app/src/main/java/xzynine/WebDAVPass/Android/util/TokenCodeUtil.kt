package xzynine.WebDAVPass.Android.util

import com.kunzisoft.keepass.otp.OtpElement
import com.kunzisoft.keepass.otp.TokenCalculator
import xzynine.WebDAVPass.Android.data.OtpToken
import xzynine.WebDAVPass.Android.data.OtpTokenType
import xzynine.WebDAVPass.Android.data.TokenCode

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
                        (counter + 2) * period * 1000,
                    ),
                )
            }
        }
    }

    /**
     * 生成指定计数器的令牌代码，异常时返回空串
     */
    private fun getHOTP(
        otpToken: OtpToken,
        counter: Long,
    ): String =
        try {
            // 令牌密钥以 Base32 文本存储，转为字节后交由 database 模块计算
            val secret = OtpElement().apply { setBase32Secret(otpToken.secret) }.secret
            val period = otpToken.period.coerceAtLeast(1)
            val algorithm = toHashAlgorithm(otpToken.algorithm)

            when {
                secret == null || secret.isEmpty() -> ""
                // Steam 令牌使用专用字符集
                otpToken.issuer == "Steam" ->
                    TokenCalculator.TOTP_Steam(secret, period, otpToken.digits, algorithm)
                else ->
                    TokenCalculator.HOTP(secret, counter, otpToken.digits, algorithm)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }

    /**
     * 算法名转哈希算法，未知名称回退 SHA1
     */
    private fun toHashAlgorithm(algorithm: String): TokenCalculator.HashAlgorithm =
        try {
            TokenCalculator.HashAlgorithm.valueOf(algorithm.uppercase())
        } catch (e: IllegalArgumentException) {
            TokenCalculator.HashAlgorithm.SHA1
        }
}
