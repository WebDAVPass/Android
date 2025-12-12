package github.xzynine.two_fas.utils

import android.annotation.SuppressLint
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import cn.hutool.core.lang.Validator
import github.xzynine.two_fas.constant.AppLog
import okhttp3.internal.publicsuffix.PublicSuffixDatabase

import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException
import java.net.URL
import java.util.BitSet
import java.util.Enumeration

@Suppress("unused", "MemberVisibilityCanBePrivate")
object NetworkUtils {

    /**
     * 获取绝对地址
     */
    fun getAbsoluteURL(baseURL: String?, relativePath: String): String {
        if (baseURL.isNullOrEmpty()) return relativePath.trim()
        var absoluteUrl: java.net.URL? = null
        try {
            absoluteUrl = java.net.URL(baseURL.substringBefore(","))
        } catch (e: Exception) {
            // 简化实现，移除对printOnDebug的依赖
        }
        return getAbsoluteURL(absoluteUrl, relativePath)
    }

    /**
     * 获取绝对地址
     */
    fun getAbsoluteURL(baseURL: java.net.URL?, relativePath: String): String {
        val relativePathTrim = relativePath.trim()
        if (baseURL == null) return relativePathTrim
        if (relativePathTrim.startsWith("http://", true) || relativePathTrim.startsWith("https://", true)) return relativePathTrim
        if (relativePathTrim.startsWith("data:")) return relativePathTrim
        if (relativePathTrim.startsWith("javascript")) return ""
        var relativeUrl = relativePathTrim
        try {
            val parseUrl = java.net.URL(baseURL, relativePath)
            relativeUrl = parseUrl.toString()
            return relativeUrl
        } catch (e: Exception) {
            // 简化实现，移除对AppLog的依赖
        }
        return relativeUrl
    }

    /**
     * 获取基础URL
     */
    fun getBaseUrl(url: String?): String? {
        url ?: return null
        if (url.startsWith("http://", true)
            || url.startsWith("https://", true)
        ) {
            val index = url.indexOf("/", 9)
            return if (index == -1) {
                url
            } else url.substring(0, index)
        }
        return null
    }

}
