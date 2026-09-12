/*
 * Copyright 2026 WebDAVPass.
 *
 * 本文件属于 WebDAVPass for Android（GPLv3）。
 * 宿主侧自动填充 UI 跳转目标：返回选择条目与保存注册两个 Activity 的 ComponentName。
 */

package xzynine.WebDAVPass.Android.autofillbridge

import android.content.ComponentName
import xzynine.WebDAVPass.Autofill.bridge.AutofillUiTarget

/**
 * 选择/注册界面均在本 app 内，使用其完整类名构造显式 Intent。
 */
class AppAutofillUiTarget : AutofillUiTarget {
    override fun selectionActivity(): ComponentName = ComponentName("xzynine.webdavpass", AutofillPickerActivity::class.java.name)

    override fun registrationActivity(): ComponentName = ComponentName("xzynine.webdavpass", AutofillRegistrationActivity::class.java.name)
}
