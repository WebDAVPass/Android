/*
 * Copyright 2026 WebDAVPass.
 *
 * 本文件属于 WebDAVPass for Android（GPLv3）。
 * 信用卡有效期（cc-exp）文本解析回归测试（PR #13 评审第 4 条）。
 */

package xzynine.WebDAVPass.Autofill.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class StructureParserExpirationTest {
    /** 期望值为「该年该月 1 日零点」的 epoch millis。 */
    private fun expected(
        year: Int,
        month: Int,
    ): Long =
        LocalDate
            .of(year, month, 1)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

    @Test
    fun `MM斜杠YY 两位年份`() {
        // 原实现只处理长度 7 且按 MM/YYYY 取索引，12/34（长度 5）被直接跳过
        assertEquals(expected(2034, 12), StructureParser.parseCreditCardExpirationMillis("12/34"))
    }

    @Test
    fun `MM斜杠YYYY 四位年份`() {
        assertEquals(expected(2028, 3), StructureParser.parseCreditCardExpirationMillis("03/2028"))
    }

    @Test
    fun `紧凑 MMYY 与其他分隔符`() {
        assertEquals(expected(2031, 1), StructureParser.parseCreditCardExpirationMillis("01/31"))
        assertEquals(expected(2031, 1), StructureParser.parseCreditCardExpirationMillis("0131"))
        assertEquals(expected(2031, 1), StructureParser.parseCreditCardExpirationMillis("01-31"))
        assertEquals(expected(2031, 1), StructureParser.parseCreditCardExpirationMillis("01.31"))
    }

    @Test
    fun `月份超范围返回 null`() {
        assertNull(StructureParser.parseCreditCardExpirationMillis("13/34"))
        assertNull(StructureParser.parseCreditCardExpirationMillis("00/34"))
    }

    @Test
    fun `格式不符返回 null`() {
        assertNull(StructureParser.parseCreditCardExpirationMillis(""))
        assertNull(StructureParser.parseCreditCardExpirationMillis("12"))
        assertNull(StructureParser.parseCreditCardExpirationMillis("abc/def"))
        assertNull(StructureParser.parseCreditCardExpirationMillis("12/34/56"))
    }

    @Test
    fun `首尾空白不影响解析`() {
        assertNotNull(StructureParser.parseCreditCardExpirationMillis(" 12/34 "))
    }
}
