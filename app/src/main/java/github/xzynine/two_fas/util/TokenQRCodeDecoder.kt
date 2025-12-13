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
     * 从相机图像中解析二维码
     * @param image 相机捕获的图像
     * @return 二维码内容，如果解析失败则返回null
     */
    fun parseQRCode(image: ImageProxy): String? {
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
                qrCodeReader.decode(BinaryBitmap(HybridBinarizer(ls))).text
            } catch (e: NotFoundException) {
                Log.d(tag, "未找到二维码")
                null
            } catch (e: ChecksumException) {
                Log.e(tag, "二维码校验和错误", e)
                null
            } catch (e: FormatException) {
                Log.e(tag, "二维码格式错误", e)
                null
            } finally {
                qrCodeReader.reset()
            }
        }
    }
}
