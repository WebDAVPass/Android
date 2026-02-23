package xzynine.WebDAVPass.Android.data

/**
 * 设备信息数据类
 * 用于记录每台设备的同步状态
 * @property lastSyncAt 最后同步时间
 */
data class DeviceInfo(
    var lastSyncAt: String
)
