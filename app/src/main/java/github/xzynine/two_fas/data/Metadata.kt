package github.xzynine.two_fas.data

/**
 * 备份元数据类，包含整个备份系统的元数据信息
 * 存储在metadata.json中，保持明文
 * @property schemaVersion 模式版本
 * @property lastUpdated 最后更新时间
 * @property devices 设备信息列表
 * @property tokens 令牌元数据列表
 */
data class Metadata(
    val schemaVersion: String = BackupConstants.SCHEMA_VERSION,
    var lastUpdated: String,
    val devices: MutableMap<String, DeviceInfo>,
    val tokens: MutableMap<String, TokenMetadata>
)
