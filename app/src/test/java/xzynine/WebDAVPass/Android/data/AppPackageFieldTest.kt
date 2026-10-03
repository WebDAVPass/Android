/*
 * Copyright 2026 WebDAVPass.
 *
 * 本文件属于 WebDAVPass for Android（GPLv3）。
 * 条目「应用」字段纯逻辑回归测试：字段名识别、包名归一化与草稿拆分。
 *
 * 覆盖重点：与 KeePassDX / keepass2android 的字段格式互操作（`AndroidApp` 字段名、
 * `androidapp://` 值前缀），以及同前缀相似字段名（`AndroidApp Signature`）不得被误判。
 */

package xzynine.WebDAVPass.Android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPackageFieldTest {
    private fun draft(
        name: String,
        value: String,
    ) = EditableFieldDraft(name = name, originalName = name, value = value)

    // region 字段名识别

    @Test
    fun `应用字段名命中 KDX 与 KP2A 两套命名`() {
        // KeePassDX：AndroidApp / AndroidApp_1（EntryInfo.suffixFieldNamePosition 带下划线）
        assertTrue(AppPackageField.isAppIdFieldName("AndroidApp"))
        assertTrue(AppPackageField.isAppIdFieldName("androidapp"))
        assertTrue(AppPackageField.isAppIdFieldName("AndroidApp_1"))
        assertTrue(AppPackageField.isAppIdFieldName("AndroidApp_12"))
        // keepass2android：AndroidApp1 / AndroidApp2（Util.SetNextFreeUrlField 无下划线）
        assertTrue(AppPackageField.isAppIdFieldName("AndroidApp1"))
        assertTrue(AppPackageField.isAppIdFieldName("AndroidApp2"))
    }

    @Test
    fun `同前缀的签名字段与应用名字段不命中`() {
        assertFalse(AppPackageField.isAppIdFieldName("AndroidApp Signature"))
        assertFalse(AppPackageField.isAppIdFieldName("AndroidApp Signature1"))
        assertFalse(AppPackageField.isAppIdFieldName("AndroidApplication"))
        assertFalse(AppPackageField.isAppIdFieldName("我的应用"))
        assertFalse(AppPackageField.isAppIdFieldName("AndroidApp_"))
    }

    @Test
    fun `新条目写入使用 KP2A 槽位名`() {
        assertEquals("AndroidApp1", AppPackageField.APP_ID_NEW_FIELD_NAME)
    }

    // endregion

    // region 包名归一化

    @Test
    fun `归一化剥离 scheme 与路径`() {
        assertEquals("com.tencent.mm", AppPackageField.normalizeAppPackage("androidapp://com.tencent.mm"))
        assertEquals("com.tencent.mm", AppPackageField.normalizeAppPackage(" ANDROIDAPP://com.tencent.mm/ "))
        assertEquals("com.tencent.mm", AppPackageField.normalizeAppPackage(" com.tencent.mm "))
        assertEquals("com.tencent.mm", AppPackageField.normalizeAppPackage("androidapp://com.tencent.mm/login"))
        assertEquals("", AppPackageField.normalizeAppPackage(""))
        assertEquals("", AppPackageField.normalizeAppPackage("androidapp://"))
        assertEquals("", AppPackageField.normalizeAppPackage(null))
    }

    @Test
    fun `字段值带 scheme 前缀且与归一化往返一致`() {
        val value = AppPackageField.toFieldValue("androidapp://com.tencent.mm")
        assertEquals("androidapp://com.tencent.mm", value)
        assertEquals("com.tencent.mm", AppPackageField.normalizeAppPackage(value))
        assertEquals("", AppPackageField.toFieldValue("  "))
    }

    // endregion

    // region 草稿拆分

    @Test
    fun `空字段列表拆出空包名`() {
        val split = emptyList<EditableFieldDraft>().splitAppPackageField()
        assertEquals("", split.packageName)
        assertTrue(split.fields.isEmpty())
    }

    @Test
    fun `应用字段被摘出且不混在自定义字段中`() {
        val split =
            listOf(
                draft("UserName", "zhangsan"),
                draft("AndroidApp1", "androidapp://com.tencent.mm"),
                draft("备注2", "x"),
            ).splitAppPackageField()

        assertEquals("com.tencent.mm", split.packageName)
        assertEquals(listOf("UserName", "备注2"), split.fields.map { it.name })
    }

    @Test
    fun `KDX 旧命名 AndroidApp 与 AndroidApp_1 同样能识别`() {
        val split =
            listOf(
                draft("AndroidApp", "com.tencent.mm"),
                draft("AndroidApp_1", "androidapp://com.alipay.mobile.androidclient"),
            ).splitAppPackageField()

        assertEquals("com.tencent.mm", split.packageName)
        assertEquals(listOf("AndroidApp_1"), split.fields.map { it.name })
    }

    @Test
    fun `多个应用字段时取首个取值 其余保留在自定义字段中`() {
        val split =
            listOf(
                draft("AndroidApp", "androidapp://com.tencent.mm"),
                draft("AndroidApp_1", "androidapp://com.alipay.mobile.androidclient"),
            ).splitAppPackageField()

        assertEquals("com.tencent.mm", split.packageName)
        assertEquals(listOf("AndroidApp_1"), split.fields.map { it.name })
    }

    @Test
    fun `应用字段值为空时保留原字段不参与包名`() {
        val split =
            listOf(
                draft("AndroidApp", ""),
                draft("AndroidApp_1", "com.tencent.mm"),
            ).splitAppPackageField()

        assertEquals("com.tencent.mm", split.packageName)
        assertEquals(listOf("AndroidApp"), split.fields.map { it.name })
    }

    // endregion
}
