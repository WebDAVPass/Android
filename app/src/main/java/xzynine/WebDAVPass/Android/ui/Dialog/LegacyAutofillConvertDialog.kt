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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import xzynine.WebDAVPass.Android.data.LegacyAutofillUrl
import xzynine.WebDAVPass.Android.ui.component.MarqueeText

/**
 * 旧版自动填充污染条目转换对话框。
 *
 * 候选行可勾选（默认全选）：展示条目标题、原网站字段值与回填的应用包名；
 * 底部「转换」交由调用方弹出最终确认后批量执行。
 */
@Composable
fun LegacyAutofillConvertDialog(
    candidates: List<LegacyAutofillUrl.Candidate>,
    show: Boolean,
    onDismiss: () -> Unit,
    onConvert: (selectedEntryIds: List<Long>) -> Unit,
) {
    val selectedIds = remember { mutableStateMapOf<Long, Boolean>() }

    LaunchedEffect(show, candidates) {
        if (show) {
            selectedIds.clear()
            candidates.forEach { candidate -> selectedIds[candidate.entryId] = true }
        }
    }

    WindowDialog(
        title = "发现 ${candidates.size} 条旧版自动填充污染条目",
        show = show,
        onDismissRequest = onDismiss,
    ) {
        // 列表已用 heightIn 限高；外层滚动保证小屏/大字号下底部按钮不被压成 0 高度
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text =
                    "网站字段为 android:// 混淆来源，哈希不可逆、无法还原网址；" +
                        "转换将清空网站字段，并把包名回填到「应用」关联字段。",
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
            ) {
                items(candidates, key = { it.entryId }) { candidate ->
                    Card {
                        val checked = selectedIds[candidate.entryId] ?: false
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedIds[candidate.entryId] = !checked }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector =
                                    if (checked) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                contentDescription = if (checked) "已选中" else "未选中",
                                tint =
                                    if (checked) {
                                        MiuixTheme.colorScheme.primary
                                    } else {
                                        MiuixTheme.colorScheme.onSurfaceVariantSummary
                                    },
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                MarqueeText(
                                    text = candidate.title.ifBlank { "未命名" },
                                    fontSize = 14.sp,
                                    color = MiuixTheme.colorScheme.onSurface,
                                )
                                MarqueeText(
                                    text = candidate.originalUrl,
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                                MarqueeText(
                                    text = "应用 → ${candidate.packageName}",
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    text = "关闭",
                    onClick = onDismiss,
                )
                val selectedCount = selectedIds.count { it.value }
                Button(
                    onClick = { onConvert(selectedIds.filter { it.value }.keys.toList()) },
                    enabled = selectedCount > 0,
                    modifier = Modifier.padding(start = 12.dp),
                ) {
                    Text(text = "转换 $selectedCount 条")
                }
            }
        }
    }
}
