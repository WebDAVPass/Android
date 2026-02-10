package xzynine.WebDAVPass.Android.util

import android.net.Uri
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