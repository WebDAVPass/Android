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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.AddFolder
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import xzynine.WebDAVPass.Android.data.GroupNodeInfo

/**
 * 分组选择对话框（用于移动/复制的目标分组选择）。
 *
 * 以树状缩进展示全部分组（不含回收站），可选择「根目录」或任一分组。
 * [onPick] 回调参数为目标分组稳定 ID，null 表示根目录。
 */
@Composable
fun GroupPickerDialog(
    title: String,
    show: Boolean,
    groups: List<GroupNodeInfo>,
    onDismiss: () -> Unit,
    onPick: (targetGroupId: Long?) -> Unit
) {
    var selectedGroupId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(show) {
        if (show) {
            selectedGroupId = null
        }
    }

    WindowDialog(
        title = title,
        show = show,
        onDismissRequest = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selectedGroupId = null }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = MiuixIcons.AddFolder,
                    contentDescription = null,
                    tint = if (selectedGroupId == null) MiuixTheme.colorScheme.primary
                    else MiuixTheme.colorScheme.onSurfaceSecondary
                )
                Text(
                    text = " 根目录",
                    fontSize = 14.sp,
                    fontWeight = if (selectedGroupId == null) FontWeight.Medium else FontWeight.Normal,
                    color = if (selectedGroupId == null) MiuixTheme.colorScheme.primary
                    else MiuixTheme.colorScheme.onSurface
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
            ) {
                items(groups, key = { it.groupId }) { group ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedGroupId = group.groupId }
                            .padding(
                                start = 14.dp + (group.depth * 20).dp,
                                end = 14.dp,
                                top = 12.dp,
                                bottom = 12.dp
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = MiuixIcons.AddFolder,
                            contentDescription = null,
                            tint = if (selectedGroupId == group.groupId) MiuixTheme.colorScheme.primary
                            else MiuixTheme.colorScheme.onSurfaceSecondary
                        )
                        Text(
                            text = "  " + group.title.ifBlank { "未命名分组" },
                            fontSize = 14.sp,
                            fontWeight = if (selectedGroupId == group.groupId) FontWeight.Medium else FontWeight.Normal,
                            color = if (selectedGroupId == group.groupId) MiuixTheme.colorScheme.primary
                            else MiuixTheme.colorScheme.onSurface
                        )
                    }
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
                    onClick = { onPick(selectedGroupId) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("确定")
                }
            }
        }
    }
}
