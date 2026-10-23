package xzynine.WebDAVPass.Android.ui.Dialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import xzynine.WebDAVPass.Android.data.GroupNodeInfo
import xzynine.WebDAVPass.Android.ui.component.DialogActionRow

/**
 * 分组选择对话框（用于移动/复制的目标分组选择）。
 *
 * 以树状缩进展示全部分组（不含回收站），可选择「根目录」或任一分组。
 * [onPick] 回调参数为目标分组稳定 ID，null 表示根目录。
 *
 * @param initialSelectedGroupId 打开时回显的选中分组 ID，null 表示根目录。
 *  调用方可传入上一次的选择，使重新打开时保持状态一致。
 */
@Composable
fun GroupPickerDialog(
    title: String,
    show: Boolean,
    groups: List<GroupNodeInfo>,
    onDismiss: () -> Unit,
    onPick: (targetGroupId: Long?) -> Unit,
    initialSelectedGroupId: Long? = null,
) {
    var selectedGroupId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(show) {
        if (show) {
            selectedGroupId = initialSelectedGroupId
        }
    }

    WindowDialog(
        title = title,
        show = show,
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GroupPickerRow(
                label = " 根目录",
                indentDp = 0,
                selected = selectedGroupId == null,
                onClick = { selectedGroupId = null },
            )

            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
            ) {
                items(groups, key = { it.groupId }) { group ->
                    // 深层分组缩进设上限（coerceAtMost(6)），避免极深嵌套时文本被挤出屏幕
                    GroupPickerRow(
                        label = "  " + group.title.ifBlank { "未命名分组" },
                        indentDp = group.depth.coerceAtMost(6) * 20,
                        selected = selectedGroupId == group.groupId,
                        onClick = { selectedGroupId = group.groupId },
                    )
                }
            }

            DialogActionRow(
                onDismiss = onDismiss,
                onConfirm = { onPick(selectedGroupId) },
                confirmButtonText = "确定",
            )
        }
    }
}

/**
 * 分组选择行：图标 + 名称，按 [indentDp] 缩进，选中时使用主题色；[label] 自带与图标之间的前缀空格。
 */
@Composable
private fun GroupPickerRow(
    label: String,
    indentDp: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(
                    start = 14.dp + indentDp.dp,
                    end = 14.dp,
                    top = 12.dp,
                    bottom = 12.dp,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = MiuixIcons.Folder,
            contentDescription = null,
            tint =
                if (selected) {
                    MiuixTheme.colorScheme.primary
                } else {
                    MiuixTheme.colorScheme.onSurfaceSecondary
                },
        )
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color =
                if (selected) {
                    MiuixTheme.colorScheme.primary
                } else {
                    MiuixTheme.colorScheme.onSurface
                },
        )
    }
}
