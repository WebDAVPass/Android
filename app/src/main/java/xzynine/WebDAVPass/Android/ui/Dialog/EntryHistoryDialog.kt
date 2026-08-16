package xzynine.WebDAVPass.Android.ui.Dialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import xzynine.WebDAVPass.Android.data.EntryHistoryInfo
import xzynine.WebDAVPass.Android.util.LocalTimeFormatter

/**
 * 条目历史版本对话框
 *
 * 展示条目的历史版本列表，支持选择某一版本恢复为当前内容。
 * 交互参照 KeePassDX EntryHistoryFragment（列表 + 恢复操作）。
 *
 * @param show 是否显示对话框
 * @param histories 历史版本摘要列表
 * @param onDismiss 关闭回调
 * @param onRestore 确认恢复某历史版本（参数为该版本）
 */
@Composable
fun EntryHistoryDialog(
    show: MutableState<Boolean>,
    histories: List<EntryHistoryInfo>,
    onDismiss: () -> Unit,
    onRestore: (EntryHistoryInfo) -> Unit,
) {
    var pendingRestore by remember { mutableStateOf<EntryHistoryInfo?>(null) }

    WindowDialog(
        title = "历史记录",
        show = show.value,
        onDismissRequest = {
            pendingRestore = null
            onDismiss()
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            val restore = pendingRestore
            if (restore != null) {
                Text(
                    text = "将当前条目恢复为该版本？",
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Text(
                    text = "${LocalTimeFormatter.formatLocalDateTime(restore.lastModificationTime)} · ${restore.title.ifBlank { "（无标题）" }}",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(
                        text = "取消",
                        onClick = { pendingRestore = null },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(
                        onClick = {
                            pendingRestore = null
                            onRestore(restore)
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text = "恢复此版本")
                    }
                }
            } else if (histories.isEmpty()) {
                Text(
                    text = "暂无历史记录",
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(histories, key = { it.index }) { history ->
                        HistoryRow(
                            history = history,
                            onRestoreClick = { pendingRestore = history },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(
    history: EntryHistoryInfo,
    onRestoreClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onRestoreClick)
                    .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = LocalTimeFormatter.formatLocalDateTime(history.lastModificationTime),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Text(
                    text = buildHistorySummary(history),
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            TextButton(
                text = "恢复",
                onClick = onRestoreClick,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        HorizontalDivider(thickness = 0.5.dp, color = MiuixTheme.colorScheme.onSurfaceSecondary.copy(alpha = 0.3f))
    }
}

private fun buildHistorySummary(history: EntryHistoryInfo): String {
    val parts = mutableListOf<String>()
    if (history.username.isNotBlank()) {
        parts.add(history.username)
    }
    if (history.passwordSet) {
        parts.add("已设密码")
    }
    if (history.customFieldCount > 0) {
        parts.add("自定义字段 ${history.customFieldCount}")
    }
    if (history.attachmentCount > 0) {
        parts.add("附件 ${history.attachmentCount}")
    }
    if (parts.isEmpty()) {
        parts.add("无额外字段")
    }
    return parts.joinToString(" · ")
}
