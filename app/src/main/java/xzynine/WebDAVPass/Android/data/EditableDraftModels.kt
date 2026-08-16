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
    val isStandard: Boolean = false,
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
    val removed: Boolean = false,
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
 *
 * 注：[newCustomIconBytes] 为 `ByteArray`，data class 自动生成的 `equals`/`hashCode`
 * 会退化为引用比较。这里显式覆写，对其使用内容比较，确保两个内容相同的草稿判定为相等。
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
    val iconStandardId: Int = 0,
    val newCustomIconBytes: ByteArray? = null,
    val tags: List<String> = emptyList(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PasswordEntryEditDraft) return false
        return entryId == other.entryId &&
            parentGroupId == other.parentGroupId &&
            title == other.title &&
            username == other.username &&
            password == other.password &&
            url == other.url &&
            notes == other.notes &&
            customFields == other.customFields &&
            attachments == other.attachments &&
            expiryTime == other.expiryTime &&
            customIconUuid == other.customIconUuid &&
            iconStandardId == other.iconStandardId &&
            newCustomIconBytes.contentEquals(other.newCustomIconBytes) &&
            tags == other.tags
    }

    override fun hashCode(): Int {
        var result = entryId?.hashCode() ?: 0
        result = 31 * result + (parentGroupId?.hashCode() ?: 0)
        result = 31 * result + title.hashCode()
        result = 31 * result + username.hashCode()
        result = 31 * result + password.hashCode()
        result = 31 * result + url.hashCode()
        result = 31 * result + notes.hashCode()
        result = 31 * result + customFields.hashCode()
        result = 31 * result + attachments.hashCode()
        result = 31 * result + (expiryTime?.hashCode() ?: 0)
        result = 31 * result + (customIconUuid?.hashCode() ?: 0)
        result = 31 * result + iconStandardId
        result = 31 * result + (newCustomIconBytes?.contentHashCode() ?: 0)
        result = 31 * result + tags.hashCode()
        return result
    }
}

/**
 * 分组编辑草稿。
 */
data class PasswordGroupEditDraft(
    val groupId: Long? = null,
    val parentGroupId: Long? = null,
    val title: String,
    val notes: String = "",
)

/**
 * 分组选择树节点（用于移动/复制的目标分组选择）。
 */
data class GroupNodeInfo(
    val groupId: Long,
    val title: String,
    val depth: Int,
)

/**
 * 数据库当前安全设置信息（设置页展示用）。
 */
data class DatabaseSettingsInfo(
    val kdfEngineName: String,
    val keyRounds: Long,
    val memoryUsage: Long,
    val parallelism: Long,
    val isCompressionEnabled: Boolean,
)

/**
 * 安全性检查条目（过期或弱密码）。
 */
data class SecurityIssueEntry(
    val entryId: Long,
    val title: String,
    val account: String,
    val passwordStrengthBits: Double,
    val expiryTime: Long?,
)

/**
 * 安全性检查结果。
 */
data class SecurityIssuesInfo(
    val expiredEntries: List<SecurityIssueEntry>,
    val weakPasswordEntries: List<SecurityIssueEntry>,
) {
    val expiredCount: Int get() = expiredEntries.size
    val weakCount: Int get() = weakPasswordEntries.size
}
