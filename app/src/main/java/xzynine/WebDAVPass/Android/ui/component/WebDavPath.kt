package xzynine.WebDAVPass.Android.ui.component

import java.net.URLEncoder

/**
 * 保证服务器根地址以 "/" 结尾。
 */
internal fun normalizeServerRootUrl(raw: String): String = if (raw.endsWith('/')) raw else "$raw/"

/**
 * 规范化相对路径：去除首尾斜杠并合并重复斜杠。
 */
internal fun normalizeRelativePath(path: String): String = path.trim().trim('/').replace("//", "/")

/**
 * 对相对路径逐段 URL 编码。
 */
internal fun encodeRelativePath(path: String): String =
    normalizeRelativePath(path)
        .split('/')
        .filter { it.isNotBlank() }
        .joinToString("/") {
            URLEncoder.encode(it, Charsets.UTF_8.name()).replace("+", "%20")
        }

/**
 * 拼接文件完整地址（不带尾部斜杠）。
 */
internal fun buildFileUrl(
    baseUrl: String,
    relativePath: String,
): String {
    val root = normalizeServerRootUrl(baseUrl)
    val rel = encodeRelativePath(relativePath)
    return if (rel.isBlank()) root else "$root$rel"
}

/**
 * 拼接目录完整地址（带尾部斜杠）。
 */
internal fun buildDirectoryUrl(
    baseUrl: String,
    relativeDirectory: String,
): String {
    val root = normalizeServerRootUrl(baseUrl)
    val rel = encodeRelativePath(relativeDirectory)
    return if (rel.isBlank()) root else "$root$rel/"
}
