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
        val width = image.width
        val height = image.height

        if (!::imageData.isInitialized) {
            imageData = ByteArray(rowStride * height)
        }

        synchronized(imageData) {
            // 只需要YUV的Y分量
            val y = image.planes[0]
            val yBuffer = y.buffer
            val ySize = yBuffer.remaining()
            val rowStride = y.rowStride

            if (ySize > imageData.size) {
                imageData = ByteArray(ySize)
            }

            yBuffer.get(imageData, 0, ySize)

            try {
                // 创建亮度源，使用实际宽度和高度，而不是行跨度
                val ls = PlanarYUVLuminanceSource(
                    imageData, 
                    rowStride, 
                    height,
                    0, 0, 
                    width, height, 
                    false
                )

                // 创建二进制位图
                val binaryBitmap = BinaryBitmap(HybridBinarizer(ls))
                
                // 配置解析参数，提高成功率
                val hints = hashMapOf<com.google.zxing.DecodeHintType, Any>()
                hints[com.google.zxing.DecodeHintType.CHARACTER_SET] = "UTF-8"
                hints[com.google.zxing.DecodeHintType.TRY_HARDER] = true
                hints[com.google.zxing.DecodeHintType.POSSIBLE_FORMATS] = listOf(com.google.zxing.BarcodeFormat.QR_CODE)
                
                // 第一次尝试：带所有参数
                try {
                    val result = qrCodeReader.decode(binaryBitmap, hints)
                    val resultText = result.text
                    
                    // 检查是否是重复的URL，避免重复日志
                    if (resultText != lastDecodedUrl) {
                        Log.d(tag, "成功解析二维码: $resultText")
                        lastDecodedUrl = resultText
                    }
                    
                    return ParseResult(success = true, content = resultText)
                } catch (e: NotFoundException) {
                    // 未找到二维码，不记录任何日志
                    return ParseResult(success = false, errorType = ParseResult.ErrorType.NOT_FOUND)
                } catch (e: Exception) {
                    // 其他错误，尝试第二次解析
                    try {
                        // 第二次尝试：降低参数要求
                        val hints2 = hashMapOf<com.google.zxing.DecodeHintType, Any>()
                        hints2[com.google.zxing.DecodeHintType.CHARACTER_SET] = "UTF-8"
                        hints2[com.google.zxing.DecodeHintType.TRY_HARDER] = true
                        
                        val result = qrCodeReader.decode(binaryBitmap, hints2)
                        val resultText = result.text
                        
                        // 检查是否是重复的URL，避免重复日志
                        if (resultText != lastDecodedUrl) {
                            Log.d(tag, "成功解析二维码: $resultText")
                            lastDecodedUrl = resultText
                        }
                        
                        return ParseResult(success = true, content = resultText)
                    } catch (e2: NotFoundException) {
                        // 再次未找到二维码，不记录任何日志
                        return ParseResult(success = false, errorType = ParseResult.ErrorType.NOT_FOUND)
                    } catch (e2: Exception) {
                        // 第三次尝试：无任何参数
                        try {
                            val result = qrCodeReader.decode(binaryBitmap)
                            val resultText = result.text
                            
                            // 检查是否是重复的URL，避免重复日志
                            if (resultText != lastDecodedUrl) {
                                Log.d(tag, "成功解析二维码: $resultText")
                                lastDecodedUrl = resultText
                            }
                            
                            return ParseResult(success = true, content = resultText)
                        } catch (e3: NotFoundException) {
                            // 最终未找到二维码，不记录任何日志
                            return ParseResult(success = false, errorType = ParseResult.ErrorType.NOT_FOUND)
                        } catch (e3: ChecksumException) {
                            // 二维码校验和错误，记录日志
                            Log.e(tag, "二维码校验和错误")
                            return ParseResult(success = false, errorType = ParseResult.ErrorType.CHECKSUM_ERROR)
                        } catch (e3: FormatException) {
                            // 二维码格式错误，记录日志
                            Log.e(tag, "二维码格式错误")
                            return ParseResult(success = false, errorType = ParseResult.ErrorType.FORMAT_ERROR)
                        } catch (e3: Exception) {
                            // 其他错误，记录日志
                            Log.e(tag, "二维码解析未知错误: ${e3.message}", e3)
                            return ParseResult(success = false, errorType = ParseResult.ErrorType.UNKNOWN_ERROR)
                        }
                    }
                }
            } catch (e: Exception) {
                // 亮度源创建失败，记录错误
                Log.e(tag, "二维码处理失败: ${e.message}")
                return ParseResult(success = false, errorType = ParseResult.ErrorType.UNKNOWN_ERROR)
            } finally {
                qrCodeReader.reset()
            }
        }
    }
}
