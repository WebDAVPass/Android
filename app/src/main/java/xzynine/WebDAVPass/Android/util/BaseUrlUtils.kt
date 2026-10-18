package xzynine.WebDAVPass.Android.util

/**
 * 保证服务器根地址以 "/" 结尾。
 */
internal fun String.ensureTrailingSlash(): String = if (endsWith("/")) this else "$this/"

/**
 * 解析完整的基础地址：先保证根地址以 "/" 结尾，再按需拼接远端目录。
 *
 * 说明：目录为空时不拼接（旧数据向前兼容）；目录首尾的 "/" 会被去除，避免重复斜杠。
 */
internal fun String.resolveBaseUrl(folder: String? = null): String {
    val dir = folder?.trim()?.trim('/')
    val base = ensureTrailingSlash()
    return if (dir.isNullOrBlank()) base else "$base$dir/"
}
