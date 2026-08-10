package xzynine.WebDAVPass.Android.ui.Screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
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
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import xzynine.WebDAVPass.Android.data.EditableFieldDraft

/**
 * 过期时间编辑器（详情页与列表页创建对话框共用）。
 */
@Composable
fun ExpiryTimeEditor(
    value: Long?,
    onValueChange: (Long?) -> Unit
) {
    var text by rememberSaveable {
        mutableStateOf(value?.let { formatExpiry(it, false) } ?: "")
    }
    // 仅当外部强制改变（如重新进入编辑、取消重进）且与当前输入不一致时才同步文本，
    // 避免用户手动输入中间串（解析失败）时文本框被清空。
    LaunchedEffect(value) {
        if (parseExpiry(text) != value) {
            text = value?.let { formatExpiry(it, false) } ?: ""
        }
    }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(
            text = "过期时间",
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurfaceSecondary
        )
        TextField(
            value = text,
            onValueChange = {
                text = it
                onValueChange(parseExpiry(it))
            },
            label = "yyyy-MM-dd HH:mm（留空表示不过期）",
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
        )
    }
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
                        Text(
                            text = "受保护",
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary
                        )
                        Button(
                            onClick = {
                                onFieldsChange(fields.toMutableList().apply {
                                    this[index] = field.copy(isProtected = !field.isProtected)
                                })
                            }
                        ) {
                            Text(if (field.isProtected) "取消保护" else "设为保护")
                        }
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
