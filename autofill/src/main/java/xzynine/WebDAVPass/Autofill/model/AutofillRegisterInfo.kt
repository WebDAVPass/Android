/*
 * Copyright 2026 WebDAVPass.
 *
 * 本文件属于 WebDAVPass for Android（GPLv3）。
 * 自动填充库模块（:autofill）的数据模型：待保存的表单数据。
 *
 * 注：字段语义参考自 KeePassDX 的 RegisterInfo，并按本库需要裁剪。
 */

package xzynine.WebDAVPass.Autofill.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * 表单保存时携带的数据。由 [xzynine.WebDAVPass.Autofill.core.KeeAutofillService.onSaveRequest]
 * 解析得到，经 PendingIntent 传递给宿主 app 的注册界面。
 */
@Parcelize
data class AutofillRegisterInfo(
    val searchInfo: AutofillSearchInfo,
    val username: String? = null,
    val password: String? = null,
    /** 宿主注册界面选定要保存到的分组 id；库不关心语义，透传给保存处理。 */
    val targetGroupId: Long? = null,
) : Parcelable
