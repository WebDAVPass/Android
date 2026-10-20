package xzynine.WebDAVPass.Android.ui.Screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.theme.MiuixTheme
import xzynine.WebDAVPass.Android.data.AppPackageField
import xzynine.WebDAVPass.Android.data.EditableFieldDraft
import xzynine.WebDAVPass.Android.ui.Dialog.AppPickerDialog
import xzynine.WebDAVPass.Android.util.InstalledAppsProvider

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
    onValueChange: (Long?) -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "过期时间",
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    text = if (value == null) "设置" else "修改",
                    onClick = { showDatePicker = true },
                )
                if (value != null) {
                    TextButton(
                        text = "清除",
                        onClick = { onValueChange(null) },
                    )
                }
            }
        }
        Text(
            text = value?.let { formatExpiry(it, false) } ?: "未设置（永不过期）",
            fontSize = 14.sp,
            color =
                if (value == null) {
                    MiuixTheme.colorScheme.onSurfaceSecondary
                } else {
                    MiuixTheme.colorScheme.onSurface
                },
            modifier = Modifier.padding(top = 6.dp),
        )
    }

    if (showDatePicker) {
        val datePickerState =
            androidx.compose.material3.rememberDatePickerState(
                initialSelectedDateMillis = value?.let { millisToUtcDateMillis(it) },
            )
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { utcMillis ->
                            // 选择器返回 UTC 午夜：转为本地日期后取当日 23:59 作为过期时间
                            val localDate =
                                java.time.Instant
                                    .ofEpochMilli(utcMillis)
                                    .atZone(java.time.ZoneOffset.UTC)
                                    .toLocalDate()
                            val localMillis =
                                localDate
                                    .atTime(23, 59)
                                    .atZone(java.time.ZoneId.systemDefault())
                                    .toInstant()
                                    .toEpochMilli()
                            onValueChange(localMillis)
                        }
                        showDatePicker = false
                    },
                ) {
                    androidx.compose.material3.Text("确定")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDatePicker = false }) {
                    androidx.compose.material3.Text("取消")
                }
            },
        ) {
            androidx.compose.material3.DatePicker(state = datePickerState)
        }
    }
}

/**
 * 将本地时间毫秒转换为日期选择器所需的 UTC 当日零点毫秒。
 */
private fun millisToUtcDateMillis(millis: Long): Long {
    val localDate =
        java.time.Instant
            .ofEpochMilli(millis)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
    return localDate.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
}

/**
 * 自定义字段编辑器（详情页与列表页创建对话框共用）。
 *
 * 不做卡片包裹：字段与标题/账号/密码等主输入框同级平铺，每个字段压成一行
 * （字段名 | 值 | 受保护 | 删除），避免多层嵌套带来的额外高度。
 * 详情页需要卡片视觉时由调用方用同样的 Card 包裹（见 PasswordEntryDetailContent）。
 */
@Composable
fun CustomFieldsEditor(
    fields: List<EditableFieldDraft>,
    modifier: Modifier = Modifier,
    onFieldsChange: (List<EditableFieldDraft>) -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (fields.isEmpty()) {
            Text(
                text = "暂无自定义字段",
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                modifier = Modifier.padding(start = 14.dp, top = 2.dp, bottom = 8.dp),
            )
        }
        fields.forEachIndexed { index, field ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextField(
                    value = field.name,
                    onValueChange = { name ->
                        onFieldsChange(
                            fields.toMutableList().apply {
                                this[index] = field.copy(name = name)
                            },
                        )
                    },
                    label = "字段名",
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                TextField(
                    value = field.value,
                    onValueChange = { value ->
                        onFieldsChange(
                            fields.toMutableList().apply {
                                this[index] = field.copy(value = value)
                            },
                        )
                    },
                    label = if (field.isProtected) "值（受保护）" else "值",
                    singleLine = true,
                    modifier = Modifier.weight(1.5f),
                )
                Switch(
                    checked = field.isProtected,
                    onCheckedChange = { checked ->
                        onFieldsChange(
                            fields.toMutableList().apply {
                                this[index] = field.copy(isProtected = checked)
                            },
                        )
                    },
                )
                IconButton(
                    onClick = {
                        onFieldsChange(fields.filterIndexed { i, _ -> i != index })
                    },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = MiuixIcons.Delete,
                        contentDescription = "删除字段",
                    )
                }
            }
            if (index < fields.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    thickness = 0.5.dp,
                )
            }
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        onFieldsChange(fields + EditableFieldDraft(name = "", value = "", isProtected = false))
                    }.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = MiuixIcons.Edit,
                contentDescription = "添加字段",
                tint = MiuixTheme.colorScheme.primary,
            )
            Text(
                text = " 添加字段",
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/**
 * 条目「应用」字段编辑器（新建弹窗与详情页编辑器共用）。
 *
 * 固定提供一行空白可填的包名输入 + 应用选择器；写入的包名以 KDBX 自定义字段 `AndroidApp`
 * （`androidapp://<包名>`）保存，与 KeePassDX / keepass2android 的应用字段同构，
 * 供自动填充按包名匹配条目。
 *
 * 写入包名后，若条目当前没有自定义图标（[hasCustomIcon] 为 false），自动把该应用的图标
 * 作为条目自定义图标回填（[onAppIconPicked]）；用户已选定的图标一律不被覆盖。
 *
 * @param value 当前包名（裸包名，界面态）
 * @param onValueChange 包名变更回调（选择器选中或手工键入均走此回调）
 * @param hasCustomIcon 条目是否已有自定义图标（已有则不再回填应用图标）
 * @param onAppIconPicked 应用图标 PNG 字节回调，由调用方写入草稿
 */
@Composable
fun AppPackageFieldEditor(
    value: String,
    onValueChange: (String) -> Unit,
    hasCustomIcon: Boolean,
    onAppIconPicked: (ByteArray) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showAppPicker by remember { mutableStateOf(false) }
    val normalizedPackage = AppPackageField.normalizeAppPackage(value)

    // 已写入包名且条目尚无自定义图标时，把应用图标回填为条目图标；
    // 取图标失败（未安装 / 无图标）静默跳过，不影响包名本身的保存
    LaunchedEffect(normalizedPackage, hasCustomIcon) {
        if (hasCustomIcon || normalizedPackage.isEmpty()) return@LaunchedEffect
        val bytes = InstalledAppsProvider.loadAppIconPngBytes(context, normalizedPackage)
        if (bytes != null && bytes.isNotEmpty()) {
            onAppIconPicked(bytes)
        }
    }

    // 输入框下方展示已安装应用名，未安装时如实提示
    var appLabel by remember(normalizedPackage) { mutableStateOf<String?>(null) }
    LaunchedEffect(normalizedPackage) {
        appLabel =
            if (normalizedPackage.isEmpty()) {
                null
            } else {
                withContext(Dispatchers.IO) {
                    runCatching {
                        InstalledAppsProvider.getApplicationLabel(context, normalizedPackage)
                    }.getOrNull()
                }
            }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TextField(
                value = value,
                onValueChange = { onValueChange(it) },
                label = "应用（包名）",
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                text = "选择",
                onClick = { showAppPicker = true },
            )
            if (normalizedPackage.isNotEmpty()) {
                TextButton(
                    text = "清除",
                    onClick = { onValueChange("") },
                )
            }
        }
        if (appLabel != null) {
            Text(
                // 未安装时应用名读取会回落到包名，此处显式区分「未安装」，避免看起来像重复文案
                text =
                    if (appLabel == normalizedPackage) {
                        "$normalizedPackage · 未安装"
                    } else {
                        "$appLabel · $normalizedPackage"
                    },
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                modifier = Modifier.padding(start = 14.dp, bottom = 4.dp),
            )
        }
    }

    AppPickerDialog(
        show = showAppPicker,
        selectedPackageName = normalizedPackage,
        onDismiss = { showAppPicker = false },
        onPick = { app ->
            showAppPicker = false
            onValueChange(app.packageName)
        },
    )
}
