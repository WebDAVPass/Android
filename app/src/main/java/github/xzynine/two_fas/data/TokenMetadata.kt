package github.xzynine.two_fas.data

/**
 * 令牌元数据类，包含令牌的非核心信息
 * 存储在metadata.json中，保持明文
 * @property issuer 发行方
 * @property label 标签
 * @property sort 排序
 * @property imagePath 图片路径
 * @property contentHash 核心文件的内容哈希
 * @property updatedAt 更新时间
 */
data class TokenMetadata(
    var issuer: String?,
    var label: String,
    var sort: Long,
    var imagePath: String?,
    var contentHash: String,
    var updatedAt: String
)
