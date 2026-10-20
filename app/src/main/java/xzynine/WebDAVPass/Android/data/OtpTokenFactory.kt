package xzynine.WebDAVPass.Android.data

import android.net.Uri
import com.kunzisoft.keepass.otp.OtpEntryFields
import com.kunzisoft.keepass.otp.OtpType
import xzynine.WebDAVPass.Android.util.UniqueIdGenerator
import com.kunzisoft.keepass.otp.OtpTokenType as KeepassOtpTokenType

/**
 * OTP令牌工厂，用于从URI创建OtpToken对象
 */
object OtpTokenFactory {
    /**
     * 从URI创建OtpToken对象
     * @param uri OTP令牌的URI，格式为otpauth://totp/或otpauth://hotp/
     * @return 创建的OtpToken对象
     * @throws IllegalArgumentException 如果URI格式无效
     */
    fun createFromUri(uri: Uri): OtpToken {
        // 检查URI协议是否为otpauth
        if (uri.scheme != "otpauth") {
            throw IllegalArgumentException("URI必须以otpauth开头")
        }

        // 解析交由 database 模块实现
        val otpElement =
            OtpEntryFields.parseOTPUri(uri.toString())
                ?: throw IllegalArgumentException("URI必须包含totp或hotp类型")

        // 密钥缺失或格式非法时无法生成代码
        val secretBytes = otpElement.secret
        if (secretBytes == null || secretBytes.isEmpty()) {
            throw IllegalArgumentException("密钥不能为空")
        }

        // Steam 归一为 issuer 为 "Steam" 的 TOTP，app 侧令牌类型仅保留 HOTP/TOTP
        val isSteam = otpElement.tokenType == KeepassOtpTokenType.STEAM || uri.authority == "steam"
        val type = if (otpElement.type == OtpType.HOTP) OtpTokenType.HOTP else OtpTokenType.TOTP
        // OTP 字段未提供 issuer 时会保留占位默认值 "None"，此处按无 issuer 处理
        val issuer =
            if (isSteam) {
                "Steam"
            } else {
                otpElement.issuer.takeIf { it.isNotBlank() && it != "None" } ?: ""
            }
        // 密钥统一取规范化 Base32 文本，与库内读取路径保持一致
        val secret = otpElement.getBase32Secret()
        val algorithm = otpElement.algorithm.name
        val digits = otpElement.digits
        val period = otpElement.period
        // 计数器直接取库内值（不再做 -1 偏移），TOTP 不使用计数器
        val counter = if (type == OtpTokenType.HOTP) otpElement.counter else 0L
        // 图像路径与描述为 app 特有参数，database 侧不解析
        val image = uri.getQueryParameter("image")
        val description = uri.getQueryParameter("description")?.takeIf { it.isNotBlank() }

        // 生成唯一标识符
        val uniqueId = UniqueIdGenerator.generate(secret, algorithm, digits, period)

        // 创建并返回OtpToken对象
        return OtpToken(
            id = 0,
            ordinal = -System.currentTimeMillis(), // 使新令牌出现在列表顶部
            issuer = issuer,
            label = otpElement.name,
            description = description,
            imagePath = image,
            tokenType = type,
            algorithm = algorithm,
            secret = secret,
            digits = digits,
            counter = counter,
            period = period,
            encryptionType = EncryptionType.NONE, // 使用NONE加密类型
            uniqueId = uniqueId,
        )
    }
}
