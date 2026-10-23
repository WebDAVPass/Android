package xzynine.WebDAVPass.Android.util

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import com.google.zxing.BinaryBitmap
import com.google.zxing.ChecksumException
import com.google.zxing.FormatException
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import xzylib.base.util.Logger

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
        val errorType: ErrorType? = null,
    ) {
        /**
         * 二维码解析错误类型
         */
        enum class ErrorType {
            NOT_FOUND, // 未找到二维码
            CHECKSUM_ERROR, // 校验和错误
            FORMAT_ERROR, // 格式错误
            UNKNOWN_ERROR, // 其他未知错误
        }
    }

    /**
     * 从Bitmap中解析二维码
     * @param bitmap 要解析的位图
     * @return 解析结果对象，包含成功状态、内容和错误类型
     */
    fun parseQRCode(bitmap: Bitmap): ParseResult {
        try {
            // 将Bitmap转换为RGB字节数组
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            // 创建RGB亮度源
            val ls = RGBLuminanceSource(width, height, pixels)

            // 创建二进制位图
            val binaryBitmap = BinaryBitmap(HybridBinarizer(ls))

            // 调用通用解析方法
            return decodeBinaryBitmap(binaryBitmap)
        } catch (e: Exception) {
            // 亮度源创建失败，记录错误
            Logger.e(tag, "二维码处理失败: ${e.message}")
            return ParseResult(success = false, errorType = ParseResult.ErrorType.UNKNOWN_ERROR)
        } finally {
            qrCodeReader.reset()
        }
    }

    /**
     * 从相机图像中解析二维码
     * @param image 相机捕获的图像
     * @return 解析结果对象，包含成功状态、内容和错误类型
     */
    fun parseQRCode(image: ImageProxy): ParseResult {
        // 在某些手机上，行跨度大于宽度，亮度源按 rowStride * height 读取，
        // 因此缓冲区必须按该容量校验；只比较 yBuffer.remaining() 会在分辨率变化时越界
        val width = image.width
        val height = image.height

        synchronized(imageData) {
            // 只需要YUV的Y分量
            val y = image.planes[0]
            val rowStride = y.rowStride
            val required = rowStride * height
            if (imageData.size < required) {
                imageData = ByteArray(required)
            }
            val yBuffer = y.buffer
            val ySize = yBuffer.remaining()
            yBuffer.get(imageData, 0, ySize)

            try {
                // 创建亮度源，使用实际宽度和高度，而不是行跨度
                val ls =
                    PlanarYUVLuminanceSource(
                        imageData,
                        rowStride,
                        height,
                        0,
                        0,
                        width,
                        height,
                        false,
                    )

                // 创建二进制位图
                val binaryBitmap = BinaryBitmap(HybridBinarizer(ls))

                // 调用通用解析方法
                return decodeBinaryBitmap(binaryBitmap)
            } catch (e: Exception) {
                // 亮度源创建失败，记录错误
                Logger.e(tag, "二维码处理失败: ${e.message}")
                return ParseResult(success = false, errorType = ParseResult.ErrorType.UNKNOWN_ERROR)
            } finally {
                qrCodeReader.reset()
            }
        }
    }

    /**
     * 通用二维码解析方法，用于解析二进制位图
     * @param binaryBitmap 二进制位图
     * @return 解析结果对象，包含成功状态、内容和错误类型
     */
    private fun decodeBinaryBitmap(binaryBitmap: BinaryBitmap): ParseResult {
        // 先按最严格的方式解析；失败后仅放宽格式限制重试一次，仍失败再交由无提示解析兜底。
        // 三次尝试共用同一 binaryBitmap，ZXing 每次 decode 都会自行重建状态，可重复调用。
        val attempts =
            listOf(
                mapOf<com.google.zxing.DecodeHintType, Any>(
                    com.google.zxing.DecodeHintType.CHARACTER_SET to "UTF-8",
                    com.google.zxing.DecodeHintType.TRY_HARDER to true,
                    com.google.zxing.DecodeHintType.POSSIBLE_FORMATS to listOf(com.google.zxing.BarcodeFormat.QR_CODE),
                ),
                mapOf<com.google.zxing.DecodeHintType, Any>(
                    com.google.zxing.DecodeHintType.CHARACTER_SET to "UTF-8",
                    com.google.zxing.DecodeHintType.TRY_HARDER to true,
                ),
                emptyMap(),
            )
        // 保留最后一次失败原因：重试期间的真实异常不能被丢弃，否则错误分类会指向错误方向。
        var lastFailure: Exception? = null
        for (hints in attempts) {
            try {
                val resultText = qrCodeReader.decode(binaryBitmap, hints).text
                if (resultText != lastDecodedUrl) {
                    Logger.d(tag, "成功解析二维码: $resultText")
                    lastDecodedUrl = resultText
                }
                return ParseResult(success = true, content = resultText)
            } catch (e: NotFoundException) {
                // 画面中没有二维码属常态，直接返回且不记日志
                lastFailure = e
                return ParseResult(success = false, errorType = ParseResult.ErrorType.NOT_FOUND)
            } catch (e: Exception) {
                lastFailure = e
            } finally {
                qrCodeReader.reset()
            }
        }
        val failure = lastFailure
        return when {
            failure is ChecksumException -> {
                Logger.e(tag, "二维码校验和错误: ${failure.message}", failure)
                ParseResult(success = false, errorType = ParseResult.ErrorType.CHECKSUM_ERROR)
            }

            failure is FormatException -> {
                Logger.e(tag, "二维码格式错误: ${failure.message}", failure)
                ParseResult(success = false, errorType = ParseResult.ErrorType.FORMAT_ERROR)
            }

            else -> {
                failure?.let { Logger.e(tag, "二维码解析未知错误: ${it.message}", it) }
                ParseResult(success = false, errorType = ParseResult.ErrorType.UNKNOWN_ERROR)
            }
        }
    }
}
