package github.xzynine.two_fas.util

import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.zxing.BinaryBitmap
import com.google.zxing.ChecksumException
import com.google.zxing.FormatException
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader

/**
 * 二维码解码器，用于解析相机捕获的图像中的二维码
 */
class TokenQRCodeDecoder {

    private val tag: String = TokenQRCodeDecoder::class.java.simpleName
    private val qrCodeReader = QRCodeReader()
    private lateinit var imageData: ByteArray

    /**
     * 二维码解析结果类
     */
    data class ParseResult(
        val success: Boolean,
        val content: String? = null,
        val errorType: ErrorType? = null
    ) {
        /**
         * 二维码解析错误类型
         */
        enum class ErrorType {
            NOT_FOUND,        // 未找到二维码
            CHECKSUM_ERROR,   // 校验和错误
            FORMAT_ERROR,     // 格式错误
            UNKNOWN_ERROR     // 其他未知错误
        }
    }

    /**
     * 从相机图像中解析二维码
     * @param image 相机捕获的图像
     * @return 解析结果对象，包含成功状态、内容和错误类型
     */
    fun parseQRCode(image: ImageProxy): ParseResult {
        // 在某些手机上，行跨度大于宽度。使用行跨度来避免缓冲区溢出
        val rowStride = image.planes[0].rowStride

        if (!::imageData.isInitialized) {
            imageData = ByteArray(rowStride * image.height)
        }

        synchronized(imageData) {
            // 只需要YUV的Y分量

            val y = image.planes[0]
            val ySize = y.buffer.remaining()

            if (ySize > imageData.size) {
                imageData = ByteArray(ySize)
            }

            y.buffer.get(imageData, 0, ySize)

            val ls = PlanarYUVLuminanceSource(
                imageData, rowStride, image.height,
                0, 0, rowStride, image.height, false
            )

            return try {
                val result = qrCodeReader.decode(BinaryBitmap(HybridBinarizer(ls))).text
                ParseResult(success = true, content = result)
            } catch (e: NotFoundException) {
                // 未找到二维码，不记录日志
                ParseResult(success = false, errorType = ParseResult.ErrorType.NOT_FOUND)
            } catch (e: ChecksumException) {
                Log.e(tag, "二维码校验和错误", e)
                ParseResult(success = false, errorType = ParseResult.ErrorType.CHECKSUM_ERROR)
            } catch (e: FormatException) {
                Log.e(tag, "二维码格式错误", e)
                ParseResult(success = false, errorType = ParseResult.ErrorType.FORMAT_ERROR)
            } catch (e: Exception) {
                Log.e(tag, "二维码解析未知错误", e)
                ParseResult(success = false, errorType = ParseResult.ErrorType.UNKNOWN_ERROR)
            } finally {
                qrCodeReader.reset()
            }
        }
    }
}
