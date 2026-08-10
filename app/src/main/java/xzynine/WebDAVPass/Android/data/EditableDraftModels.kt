package xzynine.WebDAVPass.Android.data

/**
 * 可编辑的自定义字段。
 *
 * @property name 字段名（编辑后可修改）
 * @property originalName 字段初始名，用于与数据库中的原始额外字段稳定匹配。
 *                       新建字段为 null，已存在字段为其在条目中的原始名称。
 * @property value 字段值
 * @property isProtected 是否受保护（保存后决定该字段在 KDBX 中是否加密显示）
 * @property valueType 字段类型（仅用于 UI 展示与过滤）
 * @property removed 标记删除（UI 层负责过滤，保存时据此剔除原字段）
 */
data class EditableFieldDraft(
    val name: String,
    val originalName: String? = null,
    val value: String,
    val isProtected: Boolean = false,
    val valueType: RemainingValueType = RemainingValueType.TEXT,
    val removed: Boolean = false,
    /**
     * 是否为条目内建标准字段（UserName/Password/URL/Notes）。
     * 标准字段由独立编辑器管理，不应出现在自定义字段草稿列表中。
     */
    val isStandard: Boolean = false
)

/**
 * 可编辑的附件草稿。
 *
 * - 新建附件：由调用方读取后通过 [data] 提供字节，[isNew] 为 true；
 * - 已有附件：仅用 [name] 标识，[data] 为空表示保留原附件；
 * - 删除标记为 [removed] 的附件（UI 层负责过滤）。
 */
data class EditableAttachmentDraft(
    val name: String,
    val data: ByteArray? = null,
    val isNew: Boolean = false,
    val removed: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EditableAttachmentDraft) return false
        return name == other.name && isNew == other.isNew && removed == other.removed && data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + (data?.contentHashCode() ?: 0)
        result = 31 * result + isNew.hashCode()
        result = 31 * result + removed.hashCode()
        return result
    }
}

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
    val customFields: List<EditableFieldDraft> = emptyList(),
    val attachments: List<EditableAttachmentDraft> = emptyList(),
    val expiryTime: Long? = null,
    val customIconUuid: String? = null,
    val iconStandardId: Int = 0
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
