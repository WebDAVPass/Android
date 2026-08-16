package xzynine.WebDAVPass.Android.data

/**
 * 令牌代码数据类
 */
data class TokenCode(
    val code: String, // 验证码
    val start: Long, // 开始时间戳
    val end: Long, // 结束时间戳
    val next: TokenCode? = null, // 下一个令牌（用于TOTP）
) {
    /**
     * 计算令牌周期（秒）
     */
    val period: Int
        get() = maxOf(1, ((end - start) / 1000).toInt())

    /**
     * 计算剩余时间（秒）
     */
    val secondsRemaining: Int
        get() = maxOf(0, period - ((System.currentTimeMillis() / 1000) % period).toInt())

    /**
     * 计算剩余时间（秒），基于指定的当前时间
     */
    fun getSecondsRemaining(currentTime: Long): Int = maxOf(0, period - ((currentTime / 1000) % period).toInt())

    /**
     * 检查是否需要刷新令牌
     */
    fun shouldRefreshToken(): Boolean = secondsRemaining == period

    /**
     * 检查是否需要刷新令牌，基于指定的当前时间
     */
    fun shouldRefreshToken(currentTime: Long): Boolean = getSecondsRemaining(currentTime) == period

    private fun maxOf(
        a: Int,
        b: Int,
    ): Int = if (a > b) a else b
}
