package xzynine.WebDAVPass.Android.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import xzynine.WebDAVPass.Android.data.OtpToken

/**
 * 二维码生成工具类
 */
object QrCodeUtil {

    /**
     * 生成二维码 bitmap
     */
    fun generateQrCode(text: String, width: Int = 512, height: Int = 512): Bitmap {
        val hints = hashMapOf<EncodeHintType, Any>()
        hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
        
        val bitMatrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, width, height, hints)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        
        return bitmap
    }

    /**
     * 从令牌生成 otpauth URI
     */
    fun generateOtpAuthUri(token: OtpToken): String {
        val builder = StringBuilder()
        builder.append("otpauth://")
        builder.append(when (token.tokenType) {
            xzynine.WebDAVPass.Android.data.OtpTokenType.HOTP -> "hotp"
            xzynine.WebDAVPass.Android.data.OtpTokenType.TOTP -> "totp"
        })
        builder.append("/")
        
        if (token.issuer != null) {
            builder.append(token.issuer)
            builder.append(":")
        }
        builder.append(token.label)
        
        builder.append("?secret=")
        builder.append(token.secret)
        builder.append("&algorithm=")
        builder.append(token.algorithm)
        builder.append("&digits=")
        builder.append(token.digits)
        
        if (token.tokenType == xzynine.WebDAVPass.Android.data.OtpTokenType.TOTP) {
            builder.append("&period=")
            builder.append(token.period)
        } else {
            builder.append("&counter=")
            builder.append(token.counter)
        }
        
        if (token.issuer != null) {
            builder.append("&issuer=")
            builder.append(token.issuer)
        }
        
        return builder.toString()
    }

    /**
     * 从令牌生成二维码 bitmap
     */
    fun generateQrCodeFromToken(token: OtpToken, width: Int = 512, height: Int = 512): Bitmap {
        val uri = generateOtpAuthUri(token)
        return generateQrCode(uri, width, height)
    }
}