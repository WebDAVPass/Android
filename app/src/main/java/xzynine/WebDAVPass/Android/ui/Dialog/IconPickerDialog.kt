package xzynine.WebDAVPass.Android.ui.Dialog

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.kunzisoft.keepass.icon.IconPack
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import xzynine.WebDAVPass.Android.ui.component.EntryIcon
import xzynine.WebDAVPass.Android.ui.component.buildBrandIconBytes
import java.io.ByteArrayOutputStream

/**
 * 自定义图标最大字节数（与 KeePassDX 上限一致）。
 */
private const val MAX_ICON_BYTES = 512 * 1024

/**
 * 图标选择对话框。
 *
 * 提供 KeePass 标准图标网格选择、品牌图标（写入自定义图标）、从图片读取自定义图标
 * 与恢复默认四个操作。确认后通过 [onPick] 回调返回 (standardIconId, customIconBytes)：
 * 恢复默认 / 选择标准图标时 customIconBytes 为 null；选择品牌图标或自定义图片时
 * standardIconId 为 null。
 */
@Composable
fun IconPickerDialog(
    show: Boolean,
    currentStandardIconId: Int,
    currentCustomIconBytes: ByteArray?,
    onDismiss: () -> Unit,
    onPick: (standardIconId: Int?, customIconBytes: ByteArray?) -> Unit,
    iconPrimary: String? = null,
    iconSecondary: String? = null,
) {
    val context = LocalContext.current

    var selectedStandardId by remember { mutableStateOf<Int?>(currentStandardIconId) }
    var customBytes by remember { mutableStateOf(currentCustomIconBytes) }

    LaunchedEffect(show) {
        if (show) {
            selectedStandardId = currentStandardIconId
            customBytes = currentCustomIconBytes
        }
    }

    val iconPack =
        remember {
            runCatching {
                IconPack(
                    context.packageName,
                    context.resources,
                    com.kunzisoft.keepass.icon.material.R.string.resource_id,
                )
            }.getOrNull()
        }
    val iconCount = iconPack?.numberOfIcons() ?: 0

    val imagePicker =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent(),
        ) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val buffer = ByteArrayOutputStream(8 * 1024)
                    val chunk = ByteArray(8 * 1024)
                    var total = 0
                    while (true) {
                        val read = input.read(chunk)
                        if (read < 0) break
                        total += read
                        if (total > MAX_ICON_BYTES) {
                            throw IllegalStateException("图标过大")
                        }
                        buffer.write(chunk, 0, read)
                    }
                    val bytes = buffer.toByteArray()
                    if (bytes.isNotEmpty()) {
                        selectedStandardId = null
                        customBytes = bytes
                    }
                }
            }.onFailure {
                Toast.makeText(context, "图标读取失败：${it.message ?: "未知错误"}", Toast.LENGTH_SHORT).show()
            }
        }

    WindowDialog(
        title = "选择图标",
        show = show,
        onDismissRequest = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                EntryIcon(
                    customIconBytes = customBytes,
                    standardIconId = selectedStandardId,
                    primary = null,
                    secondary = null,
                    modifier = Modifier.size(48.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(text = "品牌图标", onClick = {
                        val brandBytes = buildBrandIconBytes(context, iconPrimary, iconSecondary)
                        if (brandBytes == null) {
                            Toast.makeText(context, "该条目无匹配的品牌图标", Toast.LENGTH_SHORT).show()
                        } else {
                            selectedStandardId = null
                            customBytes = brandBytes
                        }
                    })
                    TextButton(text = "从图片选择", onClick = { imagePicker.launch("image/*") })
                    TextButton(text = "恢复默认", onClick = {
                        selectedStandardId = 0
                        customBytes = null
                    })
                }
            }

            if (iconCount > 0) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(7),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp),
                ) {
                    items(iconCount) { id ->
                        val selected = customBytes == null && selectedStandardId == id
                        Box(
                            modifier =
                                Modifier
                                    .aspectRatio(1f)
                                    .border(
                                        width = if (selected) 2.dp else 1.dp,
                                        color =
                                            if (selected) {
                                                MiuixTheme.colorScheme.primary
                                            } else {
                                                MiuixTheme.colorScheme.onSurfaceSecondary
                                            },
                                        shape = RoundedCornerShape(8.dp),
                                    ).clickable {
                                        selectedStandardId = id
                                        customBytes = null
                                    },
                            contentAlignment = Alignment.Center,
                        ) {
                            EntryIcon(
                                customIconBytes = null,
                                standardIconId = id,
                                primary = null,
                                secondary = null,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(text = "取消", onClick = onDismiss, modifier = Modifier.weight(1f))
                Button(
                    onClick = { onPick(selectedStandardId, customBytes) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("确定")
                }
            }
        }
    }
}
