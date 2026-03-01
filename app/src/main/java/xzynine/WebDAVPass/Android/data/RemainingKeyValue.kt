package xzynine.WebDAVPass.Android.data

/**
 * 非双因素键值类型
 */
enum class RemainingValueType {
    TEXT,
    PASSWORD,
    URL,
    EMAIL,
    NUMBER,
    BOOLEAN,
    DATE_TIME
}

/**
 * 数据库中非双因素键值条目
 *
 * @property entryTitle 所属条目标题
 * @property fieldName 字段名称
 * @property rawValue 原始值
 * @property valueType 推断的值类型
 */
data class RemainingKeyValue(
    val entryTitle: String,
    val fieldName: String,
    val rawValue: String,
    val valueType: RemainingValueType
)

/**
 * 值类型中文标签
 */
fun RemainingValueType.toDisplayName(): String {
    return when (this) {
        RemainingValueType.TEXT -> "文本"
        RemainingValueType.PASSWORD -> "密码"
        RemainingValueType.URL -> "链接"
        RemainingValueType.EMAIL -> "邮箱"
        RemainingValueType.NUMBER -> "数字"
        RemainingValueType.BOOLEAN -> "布尔"
        RemainingValueType.DATE_TIME -> "日期时间"
    }
}
