/*
 * Copyright 2026 WebDAVPass.
 *
 * 本文件属于 WebDAVPass for Android（GPLv3）。
 * android:// 混淆站点来源解析回归测试：哈希与包名拆分、非该格式返回 null。
 */

package xzynine.WebDAVPass.Autofill.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StructureParserHashedWebDomainTest {
    /** 真实样本：128 位十六进制哈希 + 宿主应用包名。 */
    private val sample =
        "android://83BCC6127662A62784E99C81038073D3493153C87417D413C0F2CBF9D9D15709E0234149589883DEB08426C210FD52B4EB848D771CEA8209E79C0A93F0B23CF4@com.xiaomi.account"

    @Test
    fun `真实样本命中返回包名`() {
        assertEquals("com.xiaomi.account", StructureParser.hashedWebDomainPackage(sample))
    }

    @Test
    fun `大小写混合的十六进制仍命中`() {
        assertEquals(
            "com.example.app",
            StructureParser.hashedWebDomainPackage("android://83bCc6127662A62784E99C81038073D34@com.example.app"),
        )
    }

    @Test
    fun `八位以上十六进制命中`() {
        assertEquals("com.example.app", StructureParser.hashedWebDomainPackage("android://0123456789abcdef@com.example.app"))
    }

    @Test
    fun `哈希不足八位不命中`() {
        assertNull(StructureParser.hashedWebDomainPackage("android://abc123@com.example.app"))
    }

    @Test
    fun `缺失分隔符不命中`() {
        assertNull(StructureParser.hashedWebDomainPackage("android://0123456789abcdefcom.example.app"))
        assertNull(StructureParser.hashedWebDomainPackage("android://0123456789abcdef@"))
    }

    @Test
    fun `包名含空白不命中`() {
        assertNull(StructureParser.hashedWebDomainPackage("android://0123456789abcdef@com.example.app x"))
    }

    @Test
    fun `非十六进制哈希不命中`() {
        assertNull(StructureParser.hashedWebDomainPackage("android://zzzzzzzz@com.example.app"))
    }

    @Test
    fun `普通域名与普通 URL 不命中`() {
        assertNull(StructureParser.hashedWebDomainPackage("example.com"))
        assertNull(StructureParser.hashedWebDomainPackage("https://example.com/login"))
    }

    @Test
    fun `null 与空白与首尾空白`() {
        assertNull(StructureParser.hashedWebDomainPackage(null))
        assertNull(StructureParser.hashedWebDomainPackage(""))
        assertNull(StructureParser.hashedWebDomainPackage("   "))
        assertEquals(
            "com.xiaomi.account",
            StructureParser.hashedWebDomainPackage("  $sample  "),
        )
    }
}
