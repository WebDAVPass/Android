package xzynine.WebDAVPass.Android.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.io.File
import java.io.FileInputStream

fun Uri.isContentScheme() = this.scheme == "content"

fun Uri.isFileScheme() = this.scheme == "file"

/**
 * 简化实现，移除对其他依赖的引用
 */
fun Uri.toRequestBody(contentType: MediaType? = null): RequestBody {
    val uri = this
    return object : RequestBody() {
        override fun contentType() = contentType

        override fun contentLength(): Long {
            return -1 // 简化实现，返回-1表示内容长度未知
        }

        override fun writeTo(sink: BufferedSink) {
            // 简化实现，只支持file://类型的Uri
            if (uri.isFileScheme()) {
                val file = File(uri.path ?: throw IllegalArgumentException("Invalid file path"))
                FileInputStream(file).source().use {
                    sink.writeAll(it)
                }
            } else {
                throw UnsupportedOperationException("Only file:// scheme is supported")
            }
        }
    }
}

/**
 * 通过 ContentResolver 查询 [OpenableColumns.DISPLAY_NAME] 获取 URI 展示名称；
 * 查询失败或名称为空时回退到 `uri.lastPathSegment`（去掉 docid 前缀，如
 * `primary:Documents/key.key` → `key.key`），最后回退到 [fallbackIfEmpty]。
 *
 * 原来在 WelcomeScreen / CreateMasterPasswordDialog 中内联了三份相同逻辑，统一到此扩展函数。
 */
fun Uri.resolveDisplayName(
    context: Context,
    fallbackIfEmpty: String = "未命名",
): String {
    val queried =
        runCatching {
            context.contentResolver
                .query(this, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index >= 0 && !cursor.isNull(index)) {
                            cursor.getString(index)?.takeIf { it.isNotBlank() }
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                }
        }.getOrNull()
    return queried
        // 部分 SAF provider 的 lastPathSegment 是 docid 内嵌路径 (primary:Documents/foo.key)，
        // 取最后一段斜杠后的文件名，避免把完整 docid 当显示名。
        ?: this.lastPathSegment
            ?.substringAfterLast('/')
            ?.takeIf { it.isNotBlank() }
        ?: fallbackIfEmpty
}
