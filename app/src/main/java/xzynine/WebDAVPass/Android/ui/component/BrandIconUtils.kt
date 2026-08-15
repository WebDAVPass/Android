package xzynine.WebDAVPass.Android.ui.component

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.content.ContextCompat
import org.liberty.android.freeotp.token_images.TokenImage
import org.liberty.android.freeotp.token_images.matchToken
import java.io.ByteArrayOutputStream

/**
 * 品牌图标渲染像素尺寸（写入密码库的自定义图标边长）。
 */
private const val BRAND_ICON_SIZE_PX = 96

/**
 * 将匹配到的品牌图标绘制为 PNG 字节（供写入密码库作为自定义图标）。
 * 未匹配到品牌时返回 null。
 */
fun buildBrandIconBytes(
    context: Context,
    primary: String?,
    secondary: String?
): ByteArray? {
    val tokenImage = TokenImage.values().firstOrNull { it.matchToken(primary, secondary) }
        ?: return null
    val drawable = runCatching {
        ContextCompat.getDrawable(context, tokenImage.resource)
    }.getOrNull() ?: return null
    val bitmap = Bitmap.createBitmap(BRAND_ICON_SIZE_PX, BRAND_ICON_SIZE_PX, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, BRAND_ICON_SIZE_PX, BRAND_ICON_SIZE_PX)
    drawable.draw(canvas)
    return ByteArrayOutputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        out.toByteArray()
    }
}
