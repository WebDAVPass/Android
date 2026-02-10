package xzynine.WebDAVPass.Android.data.legacy

/**
 * 旧版令牌数据类（用于导入导出兼容）
 */
data class SavedTokens(
    val tokens: Map<String, LegacyToken>,
    val tokenOrder: List<String>
)

/**
 * 旧版令牌实体
 */
data class LegacyToken(
    val id: Int,
    val issuer: String?,
    val label: String,
    val image: String?,
    val type: String,
    val algo: String,
    val secret: String,
    val digits: Int,
    val counter: Long,
    val period: Int
)