package xzynine.WebDAVPass.Android.data

import android.net.Uri
import xzynine.WebDAVPass.Android.util.UniqueIdGenerator
import java.util.*
import javax.crypto.Mac

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

        // 确定令牌类型
        val type =
            when (uri.authority) {
                "totp" -> OtpTokenType.TOTP
                "hotp" -> OtpTokenType.HOTP
                else -> throw IllegalArgumentException("URI必须包含totp或hotp类型")
            }

        // 获取路径
        var path = uri.path
        if (path == null) {
            throw IllegalArgumentException("令牌路径为空")
        }

        // 移除路径开头的'/'
        while (!path.isNullOrEmpty() && path.startsWith('/')) {
            path = path.substring(1)
        }

        if (path.isEmpty()) {
            throw IllegalArgumentException("令牌路径为空")
        }

        // 解析issuer和label
        val i = path.indexOf(':')
        val issuerExt = if (i < 0) "" else path.substring(0, i)
        val issuerInt = uri.getQueryParameter("issuer")

        val issuer = if (issuerInt != null && issuerInt.isNotBlank()) issuerInt else issuerExt
        val label = path.substring(if (i >= 0) i + 1 else 0)

        // 解析算法
        var algo = uri.getQueryParameter("algorithm")
        if (algo == null) algo = "sha1"
        algo = algo.uppercase(Locale.getDefault())

        // 验证算法是否支持
        Mac.getInstance("Hmac$algo")

        // 解析位数
        var d = uri.getQueryParameter("digits")
        if (d == null) {
            d = if (issuerExt == "Steam") "5" else "6"
        }
        val digits = d.toInt()
        // 验证位数范围
        if (issuerExt != "Steam" && digits !in 5..8) {
            throw IllegalArgumentException("位数必须为5到8之间")
        }

        // 解析周期
        var p = uri.getQueryParameter("period")
        if (p == null) p = "30"
        val period = p.toInt()

        // 解析计数器（仅HOTP需要）
        val counter =
            if (type == OtpTokenType.HOTP) {
                var c = uri.getQueryParameter("counter")
                if (c == null) c = "0"
                c.toLong() - 1
            } else {
                0
            }

        // 解析密钥
        val secret = uri.getQueryParameter("secret") ?: throw IllegalArgumentException("密钥不能为空")
        // 解析图像路径
        val image = uri.getQueryParameter("image")
        val description = uri.getQueryParameter("description")?.takeIf { it.isNotBlank() }

        // 生成唯一标识符
        val uniqueId = UniqueIdGenerator.generate(secret, algo, digits, period)

        // 创建并返回OtpToken对象
        return OtpToken(
            id = 0,
            ordinal = -System.currentTimeMillis(), // 使新令牌出现在列表顶部
            issuer = issuer,
            label = label,
            description = description,
            imagePath = image,
            tokenType = type,
            algorithm = algo,
            secret = secret,
            digits = digits,
            counter = counter,
            period = period,
            encryptionType = EncryptionType.NONE, // 使用NONE加密类型
            uniqueId = uniqueId,
        )
    }
}
