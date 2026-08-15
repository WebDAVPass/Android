package xzynine.WebDAVPass.Android.data

/**
 * 重复条目合并流程中使用的字段键（标准字段固定使用以下键名，其余为自定义字段名）。
 */
object MergeFieldKeys {
    const val TITLE = "title"
    const val ACCOUNT = "account"
    const val PASSWORD = "password"
    const val URL = "url"
    const val NOTES = "notes"
    const val TAGS = "tags"

    /** 参与「自动合并」判定的键（账号/密码/URL 全同时视为无冲突）。 */
    val AUTO_MERGE_KEYS = setOf(ACCOUNT, PASSWORD, URL)

    val STANDARD_KEYS = listOf(TITLE, ACCOUNT, PASSWORD, URL, NOTES, TAGS)
}

/**
 * 重复条目检测中的条目摘要。
 *
 * 携带完整字段值（含密码明文），供合并选择界面直接展示与选择，
 * 避免打开合并界面时再次逐条加载详情。
 *
 * @property fieldValues 字段值映射：标准字段用 [MergeFieldKeys] 中的固定键，
 *                       其余为自定义字段名（值为明文）。
 * @property attachmentNames 附件文件名列表。
 */
data class DuplicateEntryInfo(
    val entryId: Long,
    val title: String,
    val account: String,
    val url: String,
    val hasPassword: Boolean,
    val modifiedTime: Long,
    val fieldValues: Map<String, String> = emptyMap(),
    val attachmentNames: List<String> = emptyList()
) {
    /** 字段显示标签。 */
    fun fieldDisplayName(key: String): String {
        return when (key) {
            MergeFieldKeys.TITLE -> "标题"
            MergeFieldKeys.ACCOUNT -> "账号"
            MergeFieldKeys.PASSWORD -> "密码"
            MergeFieldKeys.URL -> "网站"
            MergeFieldKeys.NOTES -> "备注"
            MergeFieldKeys.TAGS -> "标签"
            else -> key
        }
    }

    /** 某字段的值（用于展示，密码等敏感字段由调用方决定是否脱敏）。 */
    fun fieldValue(key: String): String {
        return fieldValues[key] ?: ""
    }
}

/**
 * 重复候选组。
 *
 * @property isConflict 组内条目「账号/密码/URL」未完全一致（存在冲突，需要手动逐字段选择）；
 *                      为 false 时组内字段无冲突，可直接自动合并（主条目字段为空时取其余条目值）。
 */
data class DuplicateGroupInfo(
    val groupId: Int,
    val entries: List<DuplicateEntryInfo>,
    val isConflict: Boolean
)
