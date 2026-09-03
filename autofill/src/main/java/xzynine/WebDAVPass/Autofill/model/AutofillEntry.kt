/*
 * Copyright 2026 WebDAVPass.
 *
 * 本文件属于 WebDAVPass for Android（GPLv3）。
 * 自动填充库模块（:autofill）的数据模型：可填充条目与信用卡信息。
 *
 * 注：AutofillEntry 的字段定义参考自 KeePassDX 的 EntryInfo，并按本库需要裁剪。
 */

package xzynine.WebDAVPass.Autofill.model

/**
 * 一条可用于自动填充的密码库条目。
 *
 * 由宿主 app 的 [xzynine.WebDAVPass.Autofill.bridge.AutofillEntryProvider] 提供，
 * 库不关心其来源（KeePass / WebDAV 等），只消费以下字段。
 */
data class AutofillEntry(
    val id: Long,
    val title: String,
    val username: String = "",
    val password: String = "",
    val url: String = "",
    val otpToken: String? = null,
    /** 信用卡信息（可选，当前 app 一般不提供）。 */
    val creditCard: CreditCard? = null,
)

/**
 * 信用卡字段。年/月/日均为「人类可读」数值：year 可为 4 位或 2 位，
 * month 为 1-12，day 为 1-31（缺省为 null）。
 */
data class CreditCard(
    val holder: String? = null,
    val number: String? = null,
    val cvv: String? = null,
    val expiryYear: Int? = null,
    val expiryMonth: Int? = null,
    val expiryDay: Int? = null,
)
