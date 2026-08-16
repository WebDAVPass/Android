package xzynine.WebDAVPass.Android.ui.Screen

import android.content.ClipData
import android.content.ClipDescription
import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import android.webkit.MimeTypeMap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
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
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Hide
import top.yukonga.miuix.kmp.icon.extended.Show
import top.yukonga.miuix.kmp.theme.MiuixTheme
import xzynine.WebDAVPass.Android.data.RemainingKeyValue
import xzynine.WebDAVPass.Android.data.RemainingValueType
import xzylib.base.util.ToastUtils

@Composable
fun AdditionalFieldRow(
    item: RemainingKeyValue,
    onCopy: () -> Unit
) {
    // 受保护字段默认以掩码展示，仅在用户主动切换后显示原值
    var showProtectedValue by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val displayName = displayFieldName(item.fieldName)
            Text(
                text = if (item.isProtected) "$displayName（已保护）" else displayName,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface
            )
            Text(
                text = if (item.isProtected && !showProtectedValue) "••••••" else item.rawValue,
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )
        }
        if (item.isProtected) {
            IconButton(onClick = { showProtectedValue = !showProtectedValue }) {
                Icon(
                    imageVector = if (showProtectedValue) MiuixIcons.Hide else MiuixIcons.Show,
                    contentDescription = if (showProtectedValue) "隐藏受保护字段" else "显示受保护字段",
                    tint = MiuixTheme.colorScheme.onSurfaceSecondary
                )
            }
        }
        IconButton(onClick = onCopy) {
            Icon(
                imageVector = MiuixIcons.Copy,
                contentDescription = "复制 ${item.fieldName}"
            )
        }
    }
}

/**
 * 模板装饰字段名（如 [SSID]）去除括号后展示，普通字段名原样返回。
 */
fun displayFieldName(name: String): String {
    return if (name.startsWith("[") && name.endsWith("]")) {
        name.removePrefix("[").removeSuffix("]")
    } else {
        name
    }
}

/**
 * Passkey 信息行（依赖方/用户名/凭据 ID 展示）。
 */
@Composable
fun PasskeyInfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurfaceSecondary,
            modifier = Modifier.width(72.dp)
        )
        Text(
            text = value,
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 通过系统浏览器打开网址，无 scheme 时自动补充 https://。
 *
 * API 30+ 包可见性限制下 resolveActivity 可能返回 null，因此不再预先判断，
 * 直接发起 chooser，无可用处理器时由 ActivityNotFoundException 兜底提示。
 */
fun openUrl(context: Context, rawUrl: String) {
    val schemePattern = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")
    val url = if (schemePattern.containsMatchIn(rawUrl)) rawUrl else "https://$rawUrl"
    try {
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW, Uri.parse(url)), "打开网址"))
    } catch (e: ActivityNotFoundException) {
        ToastUtils.showShortToast(context, "没有可打开该网址的应用")
    } catch (e: Exception) {
        ToastUtils.showShortToast(context, "网址无法打开")
    }
}

/**
 * 将逗号/分号分隔的标签文本解析为去重后的标签列表。
 */
fun parseTagsText(text: String): List<String> {
    return text.split(',', ';')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
}

fun isOtpField(item: RemainingKeyValue): Boolean {
    return item.valueType == RemainingValueType.OTP
        || item.fieldName.equals("otp", ignoreCase = true)
        || item.rawValue.startsWith("otpauth://", ignoreCase = true)
}

fun copySensitiveToClipboard(
    context: Context,
    label: String,
    content: String
) {
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clipData = ClipData.newPlainText(label, content)
    val sensitiveExtras = PersistableBundle().apply {
        val sensitiveKey = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ClipDescription.EXTRA_IS_SENSITIVE
        } else {
            "android.content.extra.IS_SENSITIVE"
        }
        putBoolean(sensitiveKey, true)
    }
    clipData.description.extras = sensitiveExtras
    clipboardManager.setPrimaryClip(clipData)
}

@Composable
fun AttachmentViewRow(
    name: String,
    sizeText: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurface
            )
            Text(
                text = sizeText,
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )
        }
    }
}

@Composable
fun AttachmentEditRow(
    name: String,
    sizeText: String?,
    canDelete: Boolean,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurface
            )
            if (sizeText != null) {
                Text(
                    text = sizeText,
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary
                )
            }
        }
        if (canDelete) {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = MiuixIcons.Delete,
                    contentDescription = "删除附件"
                )
            }
        }
    }
}

/**
 * 将已写入缓存目录的附件通过系统查看器打开，便于用户查看。
 *
 * MIME 通过文件扩展名推断（[MimeTypeMap]）而非依赖 FileProvider 的 getType（其常返回 null），
 * 避免兜底为 application/octet-stream 导致选择器无可用处理器而抛出 ActivityNotFoundException。
 *
 * API 30+ 包可见性限制下 resolveActivity 可能返回 null，因此不再预先判断，
 * 直接发起 chooser，无可用处理器时由 ActivityNotFoundException 兜底提示。
 */
fun openAttachment(context: Context, name: String, file: java.io.File) {
    try {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider",
            file
        )
        val ext = name.substringAfterLast('.', "").lowercase()
        val mime = if (ext.isNotEmpty()) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
                ?: "application/octet-stream"
        } else {
            "application/octet-stream"
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "打开附件"))
    } catch (e: ActivityNotFoundException) {
        ToastUtils.showShortToast(context, "没有可打开该类型附件的应用")
    } catch (e: Exception) {
        ToastUtils.showShortToast(context, "打开附件失败")
    }
}
