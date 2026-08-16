package xzynine.WebDAVPass.Android.util

import java.net.URL

@Suppress("unused", "MemberVisibilityCanBePrivate")
object NetworkUtil {
    /**
     * 获取绝对地址
     */
    fun getAbsoluteURL(
        baseURL: String?,
        relativePath: String,
    ): String {
        if (baseURL.isNullOrEmpty()) return relativePath.trim()
        var absoluteUrl: URL? = null
        try {
            absoluteUrl = URL(baseURL.substringBefore(","))
        } catch (e: Exception) {
            // 简化实现，移除对printOnDebug的依赖
        }
        return getAbsoluteURL(absoluteUrl, relativePath)
    }

    /**
     * 获取绝对地址
     */
    fun getAbsoluteURL(
        baseURL: URL?,
        relativePath: String,
    ): String {
        val relativePathTrim = relativePath.trim()
        if (baseURL == null) return relativePathTrim
        if (relativePathTrim.startsWith("http://", true) || relativePathTrim.startsWith("https://", true)) return relativePathTrim
        if (relativePathTrim.startsWith("data:")) return relativePathTrim
        if (relativePathTrim.startsWith("javascript")) return ""
        var relativeUrl = relativePathTrim
        try {
            val parseUrl = URL(baseURL, relativePath)
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
        if (url.startsWith("http://", true) ||
            url.startsWith("https://", true)
        ) {
            val index = url.indexOf("/", 9)
            return if (index == -1) {
                url
            } else {
                url.substring(0, index)
            }
        }
        return null
    }
}
