package xzynine.WebDAVPass.Android.ui.Dialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import xzynine.WebDAVPass.Android.data.DuplicateEntryInfo
import xzynine.WebDAVPass.Android.data.MergeFieldKeys
import xzynine.WebDAVPass.Android.ui.component.MarqueeText

/**
 * 重复条目合并编辑器。
 *
 * 支持：
 * - 单选保留主条目（默认修改时间最新）；
 * - 逐字段选择采用哪个条目的值（标准字段 + 自定义字段并集，点击字段行切换来源）；
 * - 附件自动并集去重（忽略大小写重名保留主条目），本界面仅提示不逐项选择。
 *
 * @param onConfirm 确认合并，参数为选定的主条目 ID 与字段来源映射（字段键 → 源条目 ID）。
 */
@Composable
fun EntryMergeDialog(
    entries: List<DuplicateEntryInfo>,
    show: Boolean,
    initialMasterEntryId: Long?,
    onDismiss: () -> Unit,
    onConfirm: (masterEntryId: Long, fieldSelections: Map<String, Long>) -> Unit
) {
    val masterEntryId = remember { mutableStateOf<Long?>(null) }
    val fieldSelections = remember { mutableStateMapOf<String, Long>() }
    var fieldPickerKey by remember { mutableStateOf<String?>(null) }
    // 密码字段行是否显示实际密码明文（默认脱敏，点击「显示密码」切换）
    val passwordRevealed = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(show) {
        if (show) {
            fieldSelections.clear()
            passwordRevealed.clear()
            masterEntryId.value = initialMasterEntryId ?: entries.maxByOrNull { it.modifiedTime }?.entryId
        }
    }

    // 全部字段键：标准字段在前，自定义字段按出现顺序并集去重。
    // 无差异字段不显示（密码字段除外：无差异时显示「相同」提示行）。
    val allFieldKeys = remember(entries) {
        val customKeys = linkedSetOf<String>()
        entries.forEach { entry ->
            entry.fieldValues.keys.forEach { key ->
                if (key !in MergeFieldKeys.STANDARD_KEYS) {
                    customKeys.add(key)
                }
            }
        }
        val candidates = MergeFieldKeys.STANDARD_KEYS.filter { key -> entries.any { it.fieldValue(key).isNotBlank() } } + customKeys
        candidates.filter { key ->
            key == MergeFieldKeys.PASSWORD || hasValueDiff(entries, key)
        }
    }
    val attachmentNames = remember(entries) {
        val names = linkedSetOf<String>()
        entries.forEach { entry ->
            entry.attachmentNames.forEach { name ->
                if (names.none { it.equals(name, ignoreCase = true) }) {
                    names.add(name)
                }
            }
        }
        names.toList()
    }

    WindowDialog(
        title = "合并重复条目",
        show = show,
        onDismissRequest = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SmallTitle(text = "保留主条目")
            Card {
                entries.forEachIndexed { index, entry ->
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { masterEntryId.value = entry.entryId }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = masterEntryId.value == entry.entryId,
                            onClick = { masterEntryId.value = entry.entryId }
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            MarqueeText(
                                text = entry.title.ifBlank { "未命名" },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onSurface
                            )
                            if (entry.account.isNotBlank()) {
                                MarqueeText(
                                    text = entry.account,
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                )
                            }
                        }
                    }
                }
            }

            SmallTitle(text = "字段采用（点击切换来源）")
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
            ) {
                items(allFieldKeys, key = { it }) { key ->
                    val isPassword = key == MergeFieldKeys.PASSWORD
                    val hasDiff = hasValueDiff(entries, key)
                    val sourceEntryId = fieldSelections[key] ?: masterEntryId.value
                    val sourceEntry = entries.firstOrNull { it.entryId == sourceEntryId }
                    val rawValue = sourceEntry?.fieldValue(key).orEmpty()
                    val revealed = passwordRevealed[key] == true
                    val displayValue = mergeValueDisplay(key, rawValue, revealed = revealed)
                    // 组内该字段的其他不同值（按明文去重，排除当前采用值），用于冲突对比展示
                    val otherValues = if (hasDiff) entries
                        .mapNotNull { it.fieldValue(key).takeIf { value -> value.isNotBlank() } }
                        .distinct()
                        .filterNot { it == rawValue }
                    else emptyList()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = hasDiff) { fieldPickerKey = key }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = sourceEntry?.fieldDisplayName(key) ?: key,
                                fontSize = 14.sp,
                                color = MiuixTheme.colorScheme.onSurface
                            )
                            if (!hasDiff) {
                                // 无差异字段（仅密码会出现）：所有条目该字段完全相同
                                Text(
                                    text = "相同",
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.primary
                                )
                            } else {
                                MarqueeText(
                                    text = displayValue,
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                )
                                if (otherValues.isNotEmpty()) {
                                    MarqueeText(
                                        text = if (isPassword) {
                                            "另有 ${otherValues.size} 个不同密码"
                                        } else {
                                            "另：${otherValues.take(2).joinToString(" ／ ") { mergeValueDisplay(key, it) }}" +
                                                if (otherValues.size > 2) " 等" else ""
                                        },
                                        fontSize = 12.sp,
                                        color = MiuixTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        if (hasDiff && isPassword) {
                            Text(
                                text = if (revealed) "隐藏" else "显示密码",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clickable { passwordRevealed[key] = !revealed }
                                    .padding(horizontal = 6.dp)
                            )
                        }
                        if (hasDiff) {
                            MarqueeText(
                                text = sourceEntry?.title?.ifBlank { "未命名" } ?: "主条目",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            if (attachmentNames.isNotEmpty()) {
                SmallTitle(text = "附件")
                Card {
                    Text(
                        text = attachmentNames.joinToString("、"),
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    text = "取消",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Button(
                    onClick = {
                        val masterId = masterEntryId.value ?: return@Button
                        onConfirm(masterId, fieldSelections.toMap())
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "合并")
                }
            }
        }
    }

    if (fieldPickerKey != null) {
        val pickerKey = fieldPickerKey!!
        val pickedEntryId = fieldSelections[pickerKey] ?: masterEntryId.value ?: -1L
        FieldSourcePickerDialog(
            title = "选择「${entries.firstOrNull { it.fieldValue(pickerKey).isNotBlank() }?.fieldDisplayName(pickerKey) ?: pickerKey}」的值",
            fieldKey = pickerKey,
            entries = entries,
            selectedValue = entries.firstOrNull { it.entryId == pickedEntryId }?.fieldValue(pickerKey).orEmpty(),
            masterEntryId = masterEntryId.value,
            onDismiss = { fieldPickerKey = null },
            onPick = { entryId ->
                fieldSelections[pickerKey] = entryId
                fieldPickerKey = null
            }
        )
    }
}

/**
 * 字段值展示：密码等敏感字段默认脱敏为「已设置」，[revealed] 为 true 时显示实际密码明文；
 * 空值显示「（空）」，其余截断。
 */
private fun mergeValueDisplay(key: String, value: String, revealed: Boolean = false): String {
    if (value.isEmpty()) {
        return "（空）"
    }
    return when {
        key == MergeFieldKeys.PASSWORD && !revealed -> "已设置"
        else -> value
    }
}

/**
 * 判断某字段在组内条目间是否存在差异（按明文值去重，含空值）。
 */
private fun hasValueDiff(entries: List<DuplicateEntryInfo>, key: String): Boolean {
    return entries.map { it.fieldValue(key) }.distinct().size > 1
}

/**
 * 字段值选择对话框：按该字段的具体值分组展示（相同的值归为一组），
 * 点击某组即采用该值（自动取组内主条目或首个条目作为来源）。
 * 密码字段默认脱敏，每组提供「显示」按钮切换实际密码明文。
 */
@Composable
private fun FieldSourcePickerDialog(
    title: String,
    fieldKey: String,
    entries: List<DuplicateEntryInfo>,
    selectedValue: String,
    masterEntryId: Long?,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit
) {
    // 按字段值分组，保持出现顺序；值相同的条目归为一组
    val valueGroups = remember(entries, fieldKey) {
        val groups = linkedMapOf<String, MutableList<DuplicateEntryInfo>>()
        entries.forEach { entry ->
            groups.getOrPut(entry.fieldValue(fieldKey)) { mutableListOf() }.add(entry)
        }
        groups.toList()
    }
    // 密码组是否显示明文（按值记录）
    val revealedValues = remember { mutableStateMapOf<String, Boolean>() }
    val isPassword = fieldKey == MergeFieldKeys.PASSWORD
    WindowDialog(
        title = title,
        show = true,
        onDismissRequest = onDismiss
    ) {
        Column {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
            ) {
                items(valueGroups, key = { it.second.first().entryId }) { (value, groupEntries) ->
                    val chosen = value == selectedValue
                    val revealed = revealedValues[value] == true
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                // 组内优先选主条目，其次第一个条目（值相同，结果一致）
                                onPick(groupEntries.firstOrNull { it.entryId == masterEntryId }?.entryId ?: groupEntries.first().entryId)
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            MarqueeText(
                                text = mergeValueDisplay(fieldKey, value, revealed = revealed),
                                fontSize = 15.sp,
                                fontWeight = if (chosen) FontWeight.Medium else FontWeight.Normal,
                                color = if (chosen) MiuixTheme.colorScheme.primary
                                else MiuixTheme.colorScheme.onSurface
                            )
                            MarqueeText(
                                text = "来源：" + groupEntries.joinToString("、") { it.title.ifBlank { "未命名" } },
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                        }
                        if (isPassword) {
                            Text(
                                text = if (revealed) "隐藏" else "显示",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clickable { revealedValues[value] = !revealed }
                                    .padding(horizontal = 6.dp)
                            )
                        }
                        if (chosen) {
                            Icon(
                                imageVector = MiuixIcons.Ok,
                                contentDescription = "当前采用",
                                tint = MiuixTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}
