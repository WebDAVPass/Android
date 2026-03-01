package xzynine.WebDAVPass.Android.data

/**
 * 非双因素键值类型
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
    val valueType: RemainingValueType
)

/**
 * 数据库中的条目摘要及其全部键值。
 */
data class PasswordEntry(
    val entryId: Long,
    val title: String,
    val account: String,
    val keyValues: List<RemainingKeyValue>
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
