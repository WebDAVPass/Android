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

    // 添加统一的日志前缀，方便查看二维码相关日志
    private val tag: String = "QRCodeScanner"
    private val qrCodeReader = QRCodeReader()
    private lateinit var imageData: ByteArray
    // 缓存最近成功解析的URL，用于避免重复日志
    private var lastDecodedUrl: String? = null

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

            // 创建二进制位图
            val binaryBitmap = BinaryBitmap(HybridBinarizer(ls))
            
            // 配置解析参数，提高成功率
            val hints = hashMapOf<com.google.zxing.DecodeHintType, Any>()
            hints[com.google.zxing.DecodeHintType.CHARACTER_SET] = "UTF-8"
            hints[com.google.zxing.DecodeHintType.TRY_HARDER] = true
            hints[com.google.zxing.DecodeHintType.POSSIBLE_FORMATS] = listOf(com.google.zxing.BarcodeFormat.QR_CODE)
            
            return try {
                // 尝试带参数的解析
                val result = qrCodeReader.decode(binaryBitmap, hints)
                val resultText = result.text
                
                // 检查是否是重复的URL，避免重复日志
                if (resultText != lastDecodedUrl) {
                    Log.d(tag, "成功解析二维码: $resultText")
                    lastDecodedUrl = resultText
                }
                
                ParseResult(success = true, content = resultText)
            } catch (e: NotFoundException) {
                // 未找到二维码，不记录日志，直接返回
                ParseResult(success = false, errorType = ParseResult.ErrorType.NOT_FOUND)
            } catch (e: Exception) {
                Log.d(tag, "解析二维码失败，尝试无参数解析: ${e.javaClass.simpleName} - ${e.message}")
                try {
                    // 尝试不带参数的解析
                    val result = qrCodeReader.decode(binaryBitmap)
                    val resultText = result.text
                    
                    // 检查是否是重复的URL，避免重复日志
                    if (resultText != lastDecodedUrl) {
                        Log.d(tag, "成功解析二维码 (无参数): $resultText")
                        lastDecodedUrl = resultText
                    }
                    
                    ParseResult(success = true, content = resultText)
                } catch (e2: NotFoundException) {
                    // 第二次尝试也未找到二维码，不记录日志
                    ParseResult(success = false, errorType = ParseResult.ErrorType.NOT_FOUND)
                } catch (e2: Exception) {
                    // 其他错误，记录日志
                    when (e2) {
                        is ChecksumException -> {
                            Log.e(tag, "二维码校验和错误 - ${e2.message}", e2)
                            ParseResult(success = false, errorType = ParseResult.ErrorType.CHECKSUM_ERROR)
                        }
                        is FormatException -> {
                            Log.e(tag, "二维码格式错误 - ${e2.message}", e2)
                            ParseResult(success = false, errorType = ParseResult.ErrorType.FORMAT_ERROR)
                        }
                        else -> {
                            Log.e(tag, "二维码解析未知错误 - ${e2.message}", e2)
                            ParseResult(success = false, errorType = ParseResult.ErrorType.UNKNOWN_ERROR)
                        }
                    }
                }
            } finally {
                qrCodeReader.reset()
            }
        }
    }
}
