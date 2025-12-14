package github.xzynine.two_fas.util

import github.xzynine.two_fas.data.EncryptionType
import github.xzynine.two_fas.data.OtpToken
import github.xzynine.two_fas.data.OtpTokenType

/**
 * 示例数据生成器
 */
object SampleData {
    
    /**
     * 生成示例令牌列表
     */
    fun generateSampleTokens(): List<OtpToken> {
        return listOf(
            OtpToken(
                id = 1,
                ordinal = 0,
                issuer = "Google",
                label = "user@gmail.com",
                imagePath = null,
                tokenType = OtpTokenType.TOTP,
                algorithm = "SHA1",
                secret = "JBSWY3DPEHPK3PXP", // Base32 for "Hello!"
                digits = 6,
                counter = 0,
                period = 30,
                encryptionType = EncryptionType.NONE,
                uniqueId = UniqueIdGenerator.generate("JBSWY3DPEHPK3PXP", "SHA1", 6, 30)
            ),
            OtpToken(
                id = 2,
                ordinal = 1,
                issuer = "GitHub",
                label = "username",
                imagePath = null,
                tokenType = OtpTokenType.TOTP,
                algorithm = "SHA1",
                secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", // Base32 for "12345678901234567890"
                digits = 6,
                counter = 0,
                period = 30,
                encryptionType = EncryptionType.NONE,
                uniqueId = UniqueIdGenerator.generate("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", "SHA1", 6, 30)
            ),
            OtpToken(
                id = 3,
                ordinal = 2,
                issuer = "Microsoft",
                label = "user@outlook.com",
                imagePath = null,
                tokenType = OtpTokenType.TOTP,
                algorithm = "SHA1",
                secret = "ONXW2ZJLVNRSWS3TPO5TW4EQGE4WA4ZU", // Base32 for "abcdefghijklmnopqrstuvwxyz"
                digits = 6,
                counter = 0,
                period = 30,
                encryptionType = EncryptionType.NONE,
                uniqueId = UniqueIdGenerator.generate("ONXW2ZJLVNRSWS3TPO5TW4EQGE4WA4ZU", "SHA1", 6, 30)
            )
        )
    }
}