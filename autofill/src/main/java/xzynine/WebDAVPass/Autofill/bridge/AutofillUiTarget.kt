/*
 * Copyright 2026 WebDAVPass.
 *
 * 本文件属于 WebDAVPass for Android（GPLv3）。
 * 自动填充库模块（:autofill）的 UI 跳转桥接接口。
 *
 * 库无法承载选择条目与保存注册的界面（依赖 Compose / MIUIX / ViewModel），
 * 因此由宿主 app 提供两个 Activity 的 ComponentName，库据此构造显式 PendingIntent。
 */

package xzynine.WebDAVPass.Autofill.bridge

import android.content.ComponentName

interface AutofillUiTarget {
    /** 选择条目界面（填充时用户手动挑选条目）。 */
    fun selectionActivity(): ComponentName

    /** 保存注册界面（表单保存时创建新条目）。 */
    fun registrationActivity(): ComponentName
}
