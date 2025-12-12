package github.xzynine.two_fas.constant

import android.annotation.SuppressLint
import androidx.annotation.Keep
import java.text.SimpleDateFormat

/**
 * 应用常量
 */
@Suppress("ConstPropertyName")
@SuppressLint("SimpleDateFormat")
object AppConst {

    const val APP_TAG = "TwoFA"

    const val channelIdDownload = "channel_download"
    const val channelIdReadAloud = "channel_read_aloud"
    const val channelIdWeb = "channel_web"

    const val UA_NAME = "User-Agent"

    const val MAX_THREAD = 9

    const val DEFAULT_WEBDAV_ID = -1L

    val timeFormat: SimpleDateFormat by lazy {
        SimpleDateFormat("HH:mm")
    }

    val dateFormat: SimpleDateFormat by lazy {
        SimpleDateFormat("yyyy/MM/dd HH:mm")
    }

    val fileNameFormat: SimpleDateFormat by lazy {
        SimpleDateFormat("yy-MM-dd-HH-mm-ss")
    }

    const val imagePathKey = "imagePath"

    val menuViewNames = arrayOf(
        "com.android.internal.view.menu.ListMenuItemView",
        "androidx.appcompat.view.menu.ListMenuItemView"
    )

    @Keep
    data class AppInfo(
        var versionCode: Long = 0L,
        var versionName: String = "1.0",
        var appVariant: String = "UNKNOWN"
    )

    /**
     * The authority of a FileProvider defined in a <provider> element in your app's manifest.
     */
    const val authority = "github.xzynine.two_fas.fileProvider"

    val charsets =
        arrayListOf("UTF-8", "GB2312", "GB18030", "GBK", "Unicode", "UTF-16", "UTF-16LE", "ASCII")

}
