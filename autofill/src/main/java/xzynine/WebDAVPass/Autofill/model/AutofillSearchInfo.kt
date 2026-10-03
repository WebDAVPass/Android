/*
 * Copyright 2026 WebDAVPass.
 *
 * 本文件属于 WebDAVPass for Android（GPLv3）。
 * 自动填充库模块（:autofill）的数据模型：表单检索上下文。
 *
 * 注：字段语义参考自 KeePassDX 的 SearchInfo，并按本库需要裁剪。
 */

package xzynine.WebDAVPass.Autofill.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * 当前表单的检索上下文，由 [xzynine.WebDAVPass.Autofill.core.StructureParser] 解析得到，
 * 经 PendingIntent 传递给宿主 app 的选择/注册界面。
 */
@Parcelize
data class AutofillSearchInfo(
    val applicationId: String? = null,
    val webScheme: String? = null,
    val webDomain: String? = null,
    /** 是否为「手动选择」入口触发（此时宿主应返回全部条目，跳过过滤）。 */
    val manualSelection: Boolean = false,
) : Parcelable {
    fun containsOnlyNullValues(): Boolean = applicationId == null && webScheme == null && webDomain == null
}
