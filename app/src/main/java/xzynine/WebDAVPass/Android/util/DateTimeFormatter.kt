package xzynine.WebDAVPass.Android.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 时间戳格式化工具
 *
 * 说明：毫秒级 Unix 时间戳本质是 UTC 时间，本工具按设备默认时区
 * 将其转换为本地可读字符串，供 UI 展示历史库同步时间等场景使用。
 */
@Suppress("unused", "MemberVisibilityCanBePrivate")
object DateTimeFormatter {

    /** 默认本地时间格式：年-月-日 时:分:秒 */
    private const val PATTERN_DATETIME = "yyyy-MM-dd HH:mm:ss"

    /**
     * 将毫秒级 Unix 时间戳格式化为设备时区的本地时间字符串。
     *
     * @param epochMillis 毫秒级 Unix 时间戳（UTC），为空或非正数时返回空串
     * @param pattern 目标格式，默认 "yyyy-MM-dd HH:mm:ss"
     * @return 格式化后的本地时间字符串；时间戳非法或格式化失败时返回空串
     */
    fun formatLocalDateTime(epochMillis: Long?, pattern: String = PATTERN_DATETIME): String {
        if (epochMillis == null || epochMillis <= 0L) return ""
        return runCatching {
            // 显式指定设备默认时区，确保 UTC 时间戳按实际时区转换为本地时间
            SimpleDateFormat(pattern, Locale.getDefault())
                .apply { timeZone = TimeZone.getDefault() }
                .format(Date(epochMillis))
        }.getOrDefault("")
    }
}
