/*
 * Copyright 2026 WebDAVPass.
 *
 * 本文件属于 WebDAVPass for Android（GPLv3）。
 * 自动填充条目匹配：把当前表单的「网站域名 / 应用包名」与密码库条目的非机密字段做匹配。
 *
 * 纯 Kotlin 实现（不依赖 Android 运行时），便于单元测试覆盖跨包 / 跨域误匹配回归。
 */

package xzynine.WebDAVPass.Android.autofillbridge

/**
 * 自动填充匹配规则。
 *
 * 两侧均不区分大小写（域名与包名规范本身不区分大小写）。
 *
 * | 侧 | 规则 | 反例（不得命中） |
 * |----|------|------------------|
 * | 网站 [webDomain] | 域名边界匹配：主机名与域名相等，或为其真子域（以 `.域名` 结尾） | `bank.com` 不得命中 `ank.com`；`example.com` 不得命中 `notexample.com` |
 * | 应用 [applicationId] | 完整包名精确匹配（不做子串匹配） | `com.bank` 不得命中 `com.bank.secure` |
 *
 * 子串匹配会把凭据建议暴露给不相干的应用 / 网站，故两侧都收紧为边界匹配。
 * 附加字段形如 `androidapp://tv.danmaku.bili` 时会先剥离该 scheme，再按包名精确比对。
 */
internal object AutofillEntryMatcher {
    private const val SCHEME_SEPARATOR = "://"

    /**
     * 判断条目是否适用于当前表单。
     *
     * @param searchableValues 条目的非机密字段值（标题 / 账号 / 网站 / 附加字段）
     * @param webDomain 当前表单网站域名；为空时回退到包名匹配
     * @param applicationId 当前表单应用包名
     */
    fun matches(
        searchableValues: List<String>,
        webDomain: String?,
        applicationId: String?,
    ): Boolean {
        val domain = webDomain?.trim().orEmpty()
        if (domain.isNotEmpty()) {
            return searchableValues.any { matchesDomain(it, domain) }
        }
        val appId = applicationId?.trim().orEmpty()
        if (appId.isNotEmpty()) {
            return searchableValues.any { matchesApplicationId(it, appId) }
        }
        // 域名与包名都拿不到（如无窗口标题的异常表单）：不做过滤，保持旧行为。
        return true
    }

    /**
     * 域名边界匹配：取字段值的主机名，要求与 [domain] 完全相等，
     * 或为其真子域（主机名以 `.` + [domain] 结尾）。
     *
     * @param value 条目字段值，可为纯主机名，也可为 URL / `androidapp://` 形式
     * @param domain 表单域名
     */
    fun matchesDomain(
        value: String,
        domain: String,
    ): Boolean {
        val host = hostOf(value)
        if (host.isEmpty()) return false
        if (host.equals(domain, ignoreCase = true)) return true
        // 不含点的域名（"com"）不启用子域规则，否则会命中所有同后缀站点
        return domain.contains('.') && host.endsWith(".$domain", ignoreCase = true)
    }

    /**
     * 应用包名精确匹配（忽略大小写）：包名是层级结构，子串匹配没有语义，
     * 会让 `com.bank` 的凭据出现在 `com.bank.secure` 的表单里。
     *
     * @param value 条目字段值，可为裸包名，也可为 `androidapp://包名` / URL 形式
     * @param applicationId 表单应用包名
     */
    fun matchesApplicationId(
        value: String,
        applicationId: String?,
    ): Boolean {
        val expected = applicationId?.trim().orEmpty()
        if (expected.isEmpty()) return false
        val raw = value.trim()
        // URL / androidapp:// 形态取主机名，其余按裸包名比对（仅去掉结尾斜杠）
        val candidate =
            if (raw.contains(SCHEME_SEPARATOR)) {
                hostOf(raw)
            } else {
                raw.trimEnd('/')
            }
        return candidate.equals(expected, ignoreCase = true)
    }

    /**
     * 取出字段值中的主机名：剥离 scheme、用户信息、端口、路径 / 查询 / 片段与 DNS 根点。
     * 非 URL 形态（如纯域名、包名）原样返回（仅去掉首尾空白与结尾的 `.`）。
     */
    private fun hostOf(value: String): String {
        var host = value.trim()
        if (host.contains(SCHEME_SEPARATOR)) {
            host = host.substringAfter(SCHEME_SEPARATOR)
        }
        host = host.substringBefore('/').substringBefore('?').substringBefore('#')
        host = host.substringAfterLast('@').substringBefore(':')
        return host.trim().trimEnd('.')
    }
}
