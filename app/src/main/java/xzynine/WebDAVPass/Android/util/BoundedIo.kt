package xzynine.WebDAVPass.Android.util

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * 有界流式读写助手。
 */
object BoundedIo {
    const val BUFFER_BYTES = 8 * 1024

    /**
     * 增量读取输入流并返回全部字节；累计长度超过 [limit] 时抛出 [IllegalStateException]。
     */
    fun readBoundedBytes(
        input: InputStream,
        limit: Int,
        overflowMessage: String = "内容超过大小上限",
    ): ByteArray {
        val buffer = ByteArrayOutputStream(BUFFER_BYTES)
        val chunk = ByteArray(BUFFER_BYTES)
        var total = 0
        while (true) {
            val read = input.read(chunk)
            if (read < 0) break
            total += read
            if (total > limit) {
                throw IllegalStateException(overflowMessage)
            }
            buffer.write(chunk, 0, read)
        }
        return buffer.toByteArray()
    }

    /**
     * 增量拷贝输入流到输出流；累计长度超过 [limit] 时抛出 [IllegalStateException]。
     */
    fun copyBounded(
        input: InputStream,
        output: OutputStream,
        limit: Int,
        overflowMessage: String = "内容超过大小上限",
    ) {
        val chunk = ByteArray(BUFFER_BYTES)
        var total = 0
        while (true) {
            val read = input.read(chunk)
            if (read < 0) break
            total += read
            if (total > limit) {
                throw IllegalStateException(overflowMessage)
            }
            output.write(chunk, 0, read)
        }
    }
}
