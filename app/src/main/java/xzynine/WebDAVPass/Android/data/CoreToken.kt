package xzynine.WebDAVPass.Android.data

/**
 * 核心令牌数据类，包含令牌的核心信息
 * 用于加密存储在WebDAV服务器上
 * @property secret 令牌密钥
 * @property algorithm 算法
 * @property digits 位数
 * @property period 周期
 * @property tokenType 令牌类型
 * @property counter HOTP计数器
 * @property updatedAt 更新时间
 */
data class CoreToken(
    val secret: String,
    val algorithm: String,
    val digits: Int,
    val period: Int,
    val tokenType: String,
    val counter: Long,
    val updatedAt: String
)
