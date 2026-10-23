/*
 * Copyright 2026 WebDAVPass.
 *
 * 本文件属于 WebDAVPass for Android（GPLv3）。
 * 自动填充黑名单匹配回归测试（PR #13 评审第 8 条）：匹配侧大小写不敏感。
 */

package xzynine.WebDAVPass.Autofill.core

import android.content.Context
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import xzynine.WebDAVPass.Autofill.bridge.AutofillPreferences

class AutofillBlocklistTest {
    /** 测试用偏好实现：仅提供黑名单集合，其余取值无关。 */
    private class FakePreferences(
        override val applicationIdBlocklist: Set<String>,
        override val webDomainBlocklist: Set<String>,
    ) : AutofillPreferences {
        override val autofillSuggestionsEnabled: Boolean = true
        override val inlineSuggestionsEnabled: Boolean = true
        override val manualSelectionEnabled: Boolean = true
        override val askToSaveData: Boolean = true

        override fun ensureLoaded(context: Context) = Unit
    }

    @Test
    fun `黑名单为空时一律放行`() {
        val prefs = FakePreferences(emptySet(), emptySet())
        assertTrue(AutofillBlocklist.allowedFor("com.example.app", "example.com", prefs))
    }

    @Test
    fun `大小写混合的包名仍命中黑名单`() {
        // 写入侧只做 trim，用户可能输入 com.Bank.Secure；运行时包名为全小写
        val prefs = FakePreferences(setOf("com.Bank.Secure"), emptySet())
        assertFalse(AutofillBlocklist.allowedFor("com.bank.secure", null, prefs))
    }

    @Test
    fun `大小写混合的域名仍命中黑名单`() {
        val prefs = FakePreferences(emptySet(), setOf("Example.COM"))
        assertFalse(AutofillBlocklist.allowedFor(null, "example.com", prefs))
    }

    @Test
    fun `保留子串匹配语义`() {
        val prefs = FakePreferences(setOf("bank"), setOf("bank"))
        assertFalse(AutofillBlocklist.allowedFor("com.bank.secure", null, prefs))
        assertFalse(AutofillBlocklist.allowedFor(null, "mybank.com.cn", prefs))
    }

    @Test
    fun `弹窗窗口始终拦截`() {
        val prefs = FakePreferences(emptySet(), emptySet())
        assertFalse(AutofillBlocklist.allowedFor("PopupWindow:com.example.app", null, prefs))
    }
}
