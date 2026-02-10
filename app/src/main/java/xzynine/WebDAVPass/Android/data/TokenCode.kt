package xzynine.WebDAVPass.Android.data

/**
 * 令牌代码数据类
 */
data class TokenCode(
    val code: String,        // 验证码
    val start: Long,         // 开始时间戳
    val end: Long,           // 结束时间戳
    val next: TokenCode? = null // 下一个令牌（用于TOTP）
)