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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import xzynine.WebDAVPass.Android.data.DuplicateGroupInfo
import xzynine.WebDAVPass.Android.data.MergeFieldKeys
import xzynine.WebDAVPass.Android.ui.component.MarqueeText

/**
 * 重复条目检测结果对话框。
 *
 * 每组可展开：单选保留主条目（默认修改时间最新）；
 * 无冲突组显示「合并此组」，冲突组显示「逐项选择」（进入 [EntryMergeDialog] 手动选择）。
 */
@Composable
fun DuplicateScanDialog(
    groups: List<DuplicateGroupInfo>,
    show: Boolean,
    onDismiss: () -> Unit,
    onAutoMerge: (group: DuplicateGroupInfo, masterEntryId: Long) -> Unit,
    onManualMerge: (group: DuplicateGroupInfo, masterEntryId: Long) -> Unit
) {
    val expandedGroups = remember { mutableStateMapOf<Int, Boolean>() }
    val masterSelections = remember { mutableStateMapOf<Int, Long>() }

    LaunchedEffect(show) {
        if (show) {
            expandedGroups.clear()
            masterSelections.clear()
            groups.forEachIndexed { index, group ->
                masterSelections[index] = group.entries.maxByOrNull { it.modifiedTime }?.entryId ?: -1L
                expandedGroups[index] = index == 0
            }
        }
    }

    WindowDialog(
        title = "检测到 ${groups.size} 组重复条目",
        show = show,
        onDismissRequest = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "无冲突组（账号/密码/网站完全一致）可直接合并；冲突组需逐项选择采用哪个条目的值。",
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
            ) {
                items(groups, key = { it.groupId }) { group ->
                    Card {
                        val expanded = expandedGroups[group.groupId] ?: false
                        val masterEntry = group.entries.firstOrNull { it.entryId == masterSelections[group.groupId] }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expandedGroups[group.groupId] = !expanded }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "第 ${group.groupId + 1} 组 · ${group.entries.size} 条" +
                                        if (group.isConflict) " · 需手动处理" else "",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (group.isConflict) MiuixTheme.colorScheme.primary
                                    else MiuixTheme.colorScheme.onSurface
                                )
                                MarqueeText(
                                    text = "主条目：${masterEntry?.title?.ifBlank { "未命名" } ?: "未选择"}",
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                )
                            }
                            Icon(
                                imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                contentDescription = if (expanded) "收起" else "展开",
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                        }
                        if (expanded) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            if (group.isConflict) {
                                ConflictSummary(group = group)
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                            group.entries.forEachIndexed { index, entry ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { masterSelections[group.groupId] = entry.entryId }
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = masterSelections[group.groupId] == entry.entryId,
                                        onClick = { masterSelections[group.groupId] = entry.entryId }
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        MarqueeText(
                                            text = entry.title.ifBlank { "未命名" },
                                            fontSize = 14.sp,
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
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                val masterId = masterSelections[group.groupId]
                                Button(
                                    onClick = {
                                        if (masterId != null) {
                                            if (group.isConflict) {
                                                onManualMerge(group, masterId)
                                            } else {
                                                onAutoMerge(group, masterId)
                                            }
                                        }
                                    },
                                    modifier = Modifier.width(140.dp)
                                ) {
                                    Text(text = if (group.isConflict) "逐项选择" else "合并此组")
                                }
                            }
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    text = "关闭",
                    onClick = onDismiss
                )
            }
        }
    }
}

/**
 * 冲突组字段差异摘要：展示「账号/密码/网站」中不一致的字段及各条目的值，便于判断冲突点。
 */
@Composable
private fun ConflictSummary(group: DuplicateGroupInfo) {
    val conflictKeys = listOf(
        MergeFieldKeys.ACCOUNT to "账号",
        MergeFieldKeys.PASSWORD to "密码",
        MergeFieldKeys.URL to "网站"
    )
    val lines = conflictKeys.mapNotNull { (key, label) ->
        val distinct = group.entries
            .map { it.fieldValue(key) }
            .distinct()
        if (distinct.size <= 1) {
            null
        } else {
            val values = distinct.joinToString(" ／ ") { value ->
                if (value.isEmpty()) "（空）" else if (key == MergeFieldKeys.PASSWORD) "已设置" else value
            }
            "${label}不同：$values"
        }
    }
    if (lines.isNotEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            lines.forEach { line ->
                MarqueeText(
                    text = line,
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.primary
                )
            }
        }
    }
}
