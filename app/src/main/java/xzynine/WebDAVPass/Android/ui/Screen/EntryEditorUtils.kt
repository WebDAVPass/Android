package xzynine.WebDAVPass.Android.ui.Screen

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 过期时间格式化（分钟精度，宽松解析关闭以避免垃圾日期静默进位）。 */
val expiryFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).apply { isLenient = false }

fun formatFileSize(size: Long): String =
    if (size < 1024) {
        "$size B"
    } else if (size < 1024 * 1024) {
        "${size / 1024} KB"
    } else {
        "${size / (1024 * 1024)} MB"
    }

fun formatExpiry(
    timeMillis: Long,
    expired: Boolean,
): String {
    val base = expiryFormatter.format(Date(timeMillis))
    return if (expired) "$base（已过期）" else base
}

fun parseExpiry(text: String): Long? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    val formats =
        listOf(
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).apply { isLenient = false },
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).apply { isLenient = false },
        )
    return formats.firstNotNullOfOrNull { fmt ->
        runCatching { fmt.parse(trimmed)?.time }.getOrNull()
    }
}
