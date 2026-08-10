package xzynine.WebDAVPass.Android.ui.Screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import xzynine.WebDAVPass.Android.data.EditableFieldDraft

/**
 * 过期时间编辑器（详情页与列表页创建对话框共用）。
 *
 * 日期部分使用系统 Material3 日期选择器，避免手写日期导致的边界情况；
 * 选定的过期时间取当日 23:59。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ExpiryTimeEditor(
    value: Long?,
    onValueChange: (Long?) -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "过期时间",
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    text = if (value == null) "设置" else "修改",
                    onClick = { showDatePicker = true }
                )
                if (value != null) {
                    TextButton(
                        text = "清除",
                        onClick = { onValueChange(null) }
                    )
                }
            }
        }
        Text(
            text = value?.let { formatExpiry(it, false) } ?: "未设置（永不过期）",
            fontSize = 14.sp,
            color = if (value == null) MiuixTheme.colorScheme.onSurfaceSecondary
            else MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 6.dp)
        )
    }

    if (showDatePicker) {
        val datePickerState = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = value?.let { millisToUtcDateMillis(it) }
        )
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { utcMillis ->
                            // 选择器返回 UTC 午夜：转为本地日期后取当日 23:59 作为过期时间
                            val localDate = java.time.Instant.ofEpochMilli(utcMillis)
                                .atZone(java.time.ZoneOffset.UTC)
                                .toLocalDate()
                            val localMillis = localDate.atTime(23, 59)
                                .atZone(java.time.ZoneId.systemDefault())
                                .toInstant()
                                .toEpochMilli()
                            onValueChange(localMillis)
                        }
                        showDatePicker = false
                    }
                ) {
                    androidx.compose.material3.Text("确定")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDatePicker = false }) {
                    androidx.compose.material3.Text("取消")
                }
            }
        ) {
            androidx.compose.material3.DatePicker(state = datePickerState)
        }
    }
}

/**
 * 将本地时间毫秒转换为日期选择器所需的 UTC 当日零点毫秒。
 */
private fun millisToUtcDateMillis(millis: Long): Long {
    val localDate = java.time.Instant.ofEpochMilli(millis)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalDate()
    return localDate.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
}

/**
 * 自定义字段编辑器（详情页与列表页创建对话框共用）。
 */
@Composable
fun CustomFieldsEditor(
    fields: List<EditableFieldDraft>,
    onFieldsChange: (List<EditableFieldDraft>) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surface),
        cornerRadius = 12.dp,
        pressFeedbackType = PressFeedbackType.None,
        showIndication = false,
        onClick = {}
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            fields.forEachIndexed { index, field ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    TextField(
                        value = field.name,
                        onValueChange = { name ->
                            onFieldsChange(fields.toMutableList().apply {
                                this[index] = field.copy(name = name)
                            })
                        },
                        label = "字段名",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextField(
                        value = field.value,
                        onValueChange = { value ->
                            onFieldsChange(fields.toMutableList().apply {
                                this[index] = field.copy(value = value)
                            })
                        },
                        label = if (field.isProtected) "值（受保护）" else "值",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        if (field.isProtected) {
                            Icon(
                                imageVector = MiuixIcons.Lock,
                                contentDescription = "已保护",
                                tint = MiuixTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }
                        Text(
                            text = "受保护",
                            fontSize = 12.sp,
                            color = if (field.isProtected) MiuixTheme.colorScheme.primary
                            else MiuixTheme.colorScheme.onSurfaceSecondary
                        )
                        Switch(
                            checked = field.isProtected,
                            onCheckedChange = { checked ->
                                onFieldsChange(fields.toMutableList().apply {
                                    this[index] = field.copy(isProtected = checked)
                                })
                            }
                        )
                        IconButton(
                            onClick = {
                                onFieldsChange(fields.filterIndexed { i, _ -> i != index })
                            }
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Delete,
                                contentDescription = "删除字段"
                            )
                        }
                    }
                }
                if (index < fields.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        thickness = 0.5.dp
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onFieldsChange(fields + EditableFieldDraft(name = "", value = "", isProtected = false))
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = MiuixIcons.Edit,
                    contentDescription = "添加字段",
                    tint = MiuixTheme.colorScheme.primary
                )
                Text(
                    text = " 添加字段",
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}
