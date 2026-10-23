/*
 * Copyright 2026 WebDAVPass.
 *
 * 本文件属于 WebDAVPass for Android（GPLv3）。
 * 自动填充条目匹配规则回归测试（PR #13 评审第 6 条）：
 * 相邻域名 / 相邻包名不得互相命中，避免凭据建议出现在错误的应用或网站。
 */

package xzynine.WebDAVPass.Android.autofillbridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutofillEntryMatcherTest {
    // region 网站域名：域名边界匹配

    @Test
    fun `域名相等时命中`() {
        assertTrue(AutofillEntryMatcher.matches(listOf("bank.com"), webDomain = "bank.com", applicationId = null))
    }

    @Test
    fun `子域命中父域名`() {
        assertTrue(
            AutofillEntryMatcher.matches(listOf("https://login.bank.com/login"), webDomain = "bank.com", applicationId = null),
        )
    }

    @Test
    fun `相邻域名 bank 与 ank 不互相命中`() {
        assertFalse(AutofillEntryMatcher.matches(listOf("https://ank.com"), webDomain = "bank.com", applicationId = null))
        assertFalse(AutofillEntryMatcher.matches(listOf("https://bank.com"), webDomain = "ank.com", applicationId = null))
    }

    @Test
    fun `相邻域名 example 与 notexample 不互相命中`() {
        assertFalse(
            AutofillEntryMatcher.matches(listOf("https://notexample.com"), webDomain = "example.com", applicationId = null),
        )
        assertFalse(
            AutofillEntryMatcher.matches(listOf("https://example.com"), webDomain = "notexample.com", applicationId = null),
        )
    }

    @Test
    fun `不含点的域名不启用子域规则`() {
        assertFalse(AutofillEntryMatcher.matches(listOf("https://example.com"), webDomain = "com", applicationId = null))
    }

    @Test
    fun `域名匹配忽略大小写`() {
        assertTrue(
            AutofillEntryMatcher.matches(listOf("HTTPS://Bank.COM/login"), webDomain = "bank.com", applicationId = null),
        )
    }

    // endregion

    // region 应用包名：完整包名精确匹配

    @Test
    fun `包名相等时命中`() {
        assertTrue(
            AutofillEntryMatcher.matches(listOf("com.bank.secure"), webDomain = null, applicationId = "com.bank.secure"),
        )
    }

    @Test
    fun `androidapp 附加字段可命中包名`() {
        assertTrue(
            AutofillEntryMatcher.matches(
                listOf("androidapp://tv.danmaku.bili"),
                webDomain = null,
                applicationId = "tv.danmaku.bili",
            ),
        )
    }

    @Test
    fun `相邻包名不互相命中`() {
        // com.bank 是 com.bank.secure 的前缀，子串匹配会让父包名命中子包名
        assertFalse(
            AutofillEntryMatcher.matches(listOf("com.bank.secure"), webDomain = null, applicationId = "com.bank"),
        )
        assertFalse(
            AutofillEntryMatcher.matches(listOf("com.bank"), webDomain = null, applicationId = "com.bank.secure"),
        )
    }

    @Test
    fun `包名出现在标题或账号中不算命中`() {
        assertFalse(
            AutofillEntryMatcher.matches(
                listOf("我的银行 com.bank.secure 账号"),
                webDomain = null,
                applicationId = "com.bank",
            ),
        )
    }

    @Test
    fun `包名匹配忽略大小写`() {
        assertTrue(
            AutofillEntryMatcher.matches(listOf("COM.BANK.SECURE"), webDomain = null, applicationId = "com.bank.secure"),
        )
    }

    @Test
    fun `条目缺少目标包名时不得命中`() {
        assertFalse(
            AutofillEntryMatcher.matches(
                listOf("标题", "someone@example.com", "https://other.com"),
                webDomain = null,
                applicationId = "com.bank.secure",
            ),
        )
    }

    // endregion

    @Test
    fun `有域名时优先按域名匹配`() {
        // 表单为网站（有 webDomain）时不回退到包名匹配
        assertFalse(
            AutofillEntryMatcher.matches(
                listOf("com.bank.secure"),
                webDomain = "bank.com",
                applicationId = "com.bank.secure",
            ),
        )
    }

    @Test
    fun `域名与包名都缺失时不过滤`() {
        assertTrue(AutofillEntryMatcher.matches(listOf("任意条目"), webDomain = "", applicationId = null))
    }
}
