package xzynine.WebDAVPass.Android.ui.Screen

import com.kunzisoft.keepass.database.element.template.Template
import com.kunzisoft.keepass.database.element.template.TemplateAttributeType
import com.kunzisoft.keepass.database.element.template.TemplateEngine
import xzynine.WebDAVPass.Android.data.EditableFieldDraft
import xzynine.WebDAVPass.Android.data.RemainingValueType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 过期时间格式化（分钟精度，宽松解析关闭以避免垃圾日期静默进位）。 */
val expiryFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).apply { isLenient = false }

fun formatFileSize(size: Long): String =
    if (size < 1024) {
        "$size B"
    } else if (size < 1024 * 1024) {
        "${size / 1024} KB"
    } else {
        "${size / (1024 * 1024)} MB"
    }

fun formatExpiry(
    timeMillis: Long,
    expired: Boolean,
): String {
    val base = expiryFormatter.format(Date(timeMillis))
    return if (expired) "$base（已过期）" else base
}

fun parseExpiry(text: String): Long? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    val formats =
        listOf(
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).apply { isLenient = false },
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).apply { isLenient = false },
        )
    return formats.firstNotNullOfOrNull { fmt ->
        runCatching { fmt.parse(trimmed)?.time }.getOrNull()
    }
}

/**
 * 将模板字段集应用到当前自定义字段列表。
 *
 * 字段名使用 [TemplateEngine.addTemplateDecorator] 装饰（如 `[SSID]`），
 * 保证其他支持模板的应用（如 KeePassDX）可识别；已存在的同名字段跳过。
 *
 * 字段类型按模板属性保留（如 DATETIME → DATE_TIME），受保护的 TEXT 映射为 PASSWORD；
 * 逐字段去重，避免同一模板内重复标签进入列表。
 */
fun MutableList<EditableFieldDraft>.applyTemplateFields(template: Template) {
    val existingNames = this.map { it.name }.toMutableSet()
    template.sections.forEach { section ->
        section.attributes.forEach { attribute ->
            val decoratedName = TemplateEngine.addTemplateDecorator(attribute.label)
            if (decoratedName !in existingNames) {
                existingNames.add(decoratedName)
                add(
                    EditableFieldDraft(
                        name = decoratedName,
                        value = attribute.options.default,
                        isProtected = attribute.protected,
                        valueType = mapTemplateAttributeType(attribute.type, attribute.protected),
                    ),
                )
            }
        }
    }
}

/**
 * 将模板属性类型映射为列表展示用的 [RemainingValueType]。
 */
fun mapTemplateAttributeType(
    type: TemplateAttributeType,
    protected: Boolean,
): RemainingValueType =
    when (type) {
        TemplateAttributeType.DATETIME -> RemainingValueType.DATE_TIME
        TemplateAttributeType.TEXT ->
            if (protected) RemainingValueType.PASSWORD else RemainingValueType.TEXT
        // LIST 与 DIVIDER 暂无对应的展示类型，回退为 TEXT
        TemplateAttributeType.LIST,
        TemplateAttributeType.DIVIDER,
        ->
            RemainingValueType.TEXT
    }
