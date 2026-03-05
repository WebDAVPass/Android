package xzynine.WebDAVPass.Android.data

/**
 * 可编辑的自定义字段。
 */
data class EditableFieldDraft(
    val name: String,
    val value: String,
    val isProtected: Boolean = false
)

/**
 * 条目编辑草稿。
 */
data class PasswordEntryEditDraft(
    val entryId: Long? = null,
    val parentGroupId: Long? = null,
    val title: String,
    val username: String,
    val password: String,
    val url: String,
    val notes: String,
    val customFields: List<EditableFieldDraft> = emptyList()
)

/**
 * 分组编辑草稿。
 */
data class PasswordGroupEditDraft(
    val groupId: Long? = null,
    val parentGroupId: Long? = null,
    val title: String,
    val notes: String = ""
)
