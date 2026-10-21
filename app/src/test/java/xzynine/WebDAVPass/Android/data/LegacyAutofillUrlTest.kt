/*
 * Copyright 2026 WebDAVPass.
 *
 * 本文件属于 WebDAVPass for Android（GPLv3）。
 * 旧版自动填充污染条目识别与转换纯逻辑回归测试：URL 解析与应用字段槽位选择。
 */

package xzynine.WebDAVPass.Android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LegacyAutofillUrlTest {
    private val hashedUrl =
        "android://83BCC6127662A62784E99C81038073D3493153C87417D413C0F2CBF9D9D15709E0234149589883DEB08426C210FD52B4EB848D771CEA8209E79C0A93F0B23CF4@com.xiaomi.account"

    // region parseLegacyUrl

    @Test
    fun `裸 android 串命中返回包名`() {
        assertEquals("com.xiaomi.account", LegacyAutofillUrl.parseLegacyUrl(hashedUrl))
    }

    @Test
    fun `旧保存写入的 https 前缀形态命中`() {
        assertEquals("com.xiaomi.account", LegacyAutofillUrl.parseLegacyUrl("https://$hashedUrl"))
        assertEquals("com.xiaomi.account", LegacyAutofillUrl.parseLegacyUrl("http://$hashedUrl"))
    }

    @Test
    fun `首尾空白不影响命中`() {
        assertEquals("com.xiaomi.account", LegacyAutofillUrl.parseLegacyUrl("  $hashedUrl "))
    }

    @Test
    fun `普通 URL 不命中`() {
        assertNull(LegacyAutofillUrl.parseLegacyUrl("https://example.com/login"))
        assertNull(LegacyAutofillUrl.parseLegacyUrl("http://example.com"))
        assertNull(LegacyAutofillUrl.parseLegacyUrl("example.com"))
    }

    @Test
    fun `android 串仅作为子串出现不命中`() {
        assertNull(LegacyAutofillUrl.parseLegacyUrl("https://example.com/?next=$hashedUrl"))
        assertNull(LegacyAutofillUrl.parseLegacyUrl("前缀 $hashedUrl"))
    }

    @Test
    fun `null 与空白不命中`() {
        assertNull(LegacyAutofillUrl.parseLegacyUrl(null))
        assertNull(LegacyAutofillUrl.parseLegacyUrl(""))
        assertNull(LegacyAutofillUrl.parseLegacyUrl("   "))
    }

    // endregion

    // region nextAppFieldSlotName

    @Test
    fun `空字段列表取首槽位`() {
        assertEquals("AndroidApp1", LegacyAutofillUrl.nextAppFieldSlotName(emptyList()))
    }

    @Test
    fun `已占用槽位顺延`() {
        assertEquals(
            "AndroidApp2",
            LegacyAutofillUrl.nextAppFieldSlotName(listOf("AndroidApp1", "UserName")),
        )
        assertEquals(
            "AndroidApp3",
            LegacyAutofillUrl.nextAppFieldSlotName(listOf("AndroidApp1", "AndroidApp2")),
        )
    }

    @Test
    fun `占用判定忽略大小写`() {
        assertEquals(
            "AndroidApp2",
            LegacyAutofillUrl.nextAppFieldSlotName(listOf("androidapp1")),
        )
    }

    @Test
    fun `历史形态字段不占数字槽位`() {
        assertEquals(
            "AndroidApp1",
            LegacyAutofillUrl.nextAppFieldSlotName(listOf("AndroidApp", "AndroidApp_1")),
        )
    }

    @Test
    fun `全部槽位占满返回 null`() {
        val allSlots = (1..64).map { "AndroidApp$it" }
        assertNull(LegacyAutofillUrl.nextAppFieldSlotName(allSlots))
    }

    // endregion
}
