package xzynine.WebDAVPass.Android.util

import xzynine.WebDAVPass.Android.data.EncryptionType
import xzynine.WebDAVPass.Android.data.OtpToken
import xzynine.WebDAVPass.Android.data.OtpTokenType
import xzynine.WebDAVPass.Android.data.legacy.LegacyToken
import xzynine.WebDAVPass.Android.data.legacy.SavedTokens

/**
 * 数据迁移工具类
 */
class MigrationUtil {
    
    /**
     * 将旧版令牌转换为新版 OTP 令牌
     */
    fun convertLegacySavedTokensToOtpTokens(savedTokens: SavedTokens): List<OtpToken> {
        return savedTokens.tokenOrder.mapNotNull { key ->
            val legacyToken = savedTokens.tokens[key] ?: return@mapNotNull null
            convertLegacyTokenToOtpToken(legacyToken, savedTokens.tokenOrder.indexOf(key).toLong())
        }
    }

    /**
     * 将新版 OTP 令牌转换为旧版令牌
     */
    fun convertOtpTokensToLegacyTokens(otpTokens: List<OtpToken>): Map<String, LegacyToken> {
        return otpTokens.associate { otpToken ->
            val key = if (otpToken.issuer != null) {
                "${otpToken.issuer}:${otpToken.label}"
            } else {
                otpToken.label
            }
            key to convertOtpTokenToLegacyToken(otpToken)
        }
    }

    private fun convertLegacyTokenToOtpToken(legacyToken: LegacyToken, ordinal: Long): OtpToken {
        // 生成唯一标识符
        val uniqueId = UniqueIdGenerator.generate(
            legacyToken.secret, 
            legacyToken.algo, 
            legacyToken.digits, 
            legacyToken.period
        )
        
        return OtpToken(
            id = legacyToken.id.toLong(),
            ordinal = legacyToken.id.toLong(),
            issuer = legacyToken.issuer,
            label = legacyToken.label,
            imagePath = legacyToken.image,
            tokenType = when (legacyToken.type) {
                "hotp" -> OtpTokenType.HOTP
                else -> OtpTokenType.TOTP
            },
            algorithm = legacyToken.algo,
            secret = legacyToken.secret,
            digits = legacyToken.digits,
            counter = legacyToken.counter,
            period = legacyToken.period,
            encryptionType = EncryptionType.NONE,
            uniqueId = uniqueId
        )
    }

    private fun convertOtpTokenToLegacyToken(otpToken: OtpToken): LegacyToken {
        return LegacyToken(
            id = otpToken.id.toInt(),
            issuer = otpToken.issuer,
            label = otpToken.label,
            image = otpToken.imagePath,
            type = when (otpToken.tokenType) {
                OtpTokenType.HOTP -> "hotp"
                OtpTokenType.TOTP -> "totp"
            },
            algo = otpToken.algorithm,
            secret = otpToken.secret,
            digits = otpToken.digits,
            counter = otpToken.counter,
            period = otpToken.period
        )
    }
}