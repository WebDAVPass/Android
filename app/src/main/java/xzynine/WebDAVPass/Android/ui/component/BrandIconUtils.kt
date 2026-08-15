package xzynine.WebDAVPass.Android.ui.component

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.content.ContextCompat
import org.liberty.android.freeotp.token_images.TokenImage
import org.liberty.android.freeotp.token_images.matchToken
import java.io.ByteArrayOutputStream
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 品牌图标渲染像素尺寸（写入密码库的自定义图标边长）。
 */
private const val BRAND_ICON_SIZE_PX = 96

/**
 * 将匹配到的品牌图标绘制为 PNG 字节（供写入密码库作为自定义图标）。
 * 异形图标按宽高比等比缩放并居中，避免被拉伸变形。
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
    val intrinsicWidth = drawable.intrinsicWidth
    val intrinsicHeight = drawable.intrinsicHeight
    val bounds: android.graphics.Rect = if (intrinsicWidth > 0 && intrinsicHeight > 0) {
        val scale = min(
            BRAND_ICON_SIZE_PX.toFloat() / intrinsicWidth,
            BRAND_ICON_SIZE_PX.toFloat() / intrinsicHeight
        )
        val drawWidth = (intrinsicWidth * scale).roundToInt()
        val drawHeight = (intrinsicHeight * scale).roundToInt()
        val left = (BRAND_ICON_SIZE_PX - drawWidth) / 2
        val top = (BRAND_ICON_SIZE_PX - drawHeight) / 2
        android.graphics.Rect(left, top, left + drawWidth, top + drawHeight)
    } else {
        android.graphics.Rect(0, 0, BRAND_ICON_SIZE_PX, BRAND_ICON_SIZE_PX)
    }
    val bitmap = Bitmap.createBitmap(BRAND_ICON_SIZE_PX, BRAND_ICON_SIZE_PX, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.bounds = bounds
    drawable.draw(canvas)
    return ByteArrayOutputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        out.toByteArray()
    }
}
