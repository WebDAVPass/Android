package xzynine.WebDAVPass.Android.data

/**
 * 密码页键值类型
 */
enum class RemainingValueType {
    TEXT,
    PASSWORD,
    OTP,
    URL,
    EMAIL,
    NUMBER,
    BOOLEAN,
    DATE_TIME
}

/**
 * 条目中的键值项
 *
 * @property fieldName 字段名称
 * @property rawValue 原始值
 * @property valueType 推断的值类型
 */
data class RemainingKeyValue(
    val fieldName: String,
    val rawValue: String,
    val valueType: RemainingValueType,
    val isProtected: Boolean = false,
    /**
     * 是否为条目内建标准字段（UserName/Password/URL/Notes）。
     * 标准字段由独立编辑器管理，加载为自定义字段草稿时应排除；
     * 而同名但属于额外字段（extra）的项此值为 false，应被保留，避免在保存时被静默删除。
     */
    val isStandard: Boolean = false
)

/**
 * 条目中的附件摘要（用于展示与查看）。
 *
 * @property name 附件文件名
 * @property size 附件字节大小
 */
data class EntryAttachmentInfo(
    val name: String,
    val size: Long
)

/**
 * 数据库中的条目摘要。
 *
 * 说明：
 * - 列表场景下 `keyValues` 可能为空以减少加载开销；
 * - 详情场景下 `keyValues` 包含完整键值。
 */
data class PasswordEntry(
    val entryId: Long,
    val title: String,
    val account: String,
    val standardIconId: Int,
    val customIconBytes: ByteArray?,
    val keyValues: List<RemainingKeyValue>,
    val attachments: List<EntryAttachmentInfo> = emptyList(),
    val expiryTime: Long? = null,
    val isExpired: Boolean = false,
    val customIconUuid: String? = null,
    val isFolderGroup: Boolean = false,
    val isFolderPlaceholder: Boolean = false
)

/**
 * 值类型中文标签
 */
fun RemainingValueType.toDisplayName(): String {
    return when (this) {
        RemainingValueType.TEXT -> "文本"
        RemainingValueType.PASSWORD -> "密码"
        RemainingValueType.OTP -> "动态令牌"
        RemainingValueType.URL -> "链接"
        RemainingValueType.EMAIL -> "邮箱"
        RemainingValueType.NUMBER -> "数字"
        RemainingValueType.BOOLEAN -> "布尔"
        RemainingValueType.DATE_TIME -> "日期时间"
    }
}
