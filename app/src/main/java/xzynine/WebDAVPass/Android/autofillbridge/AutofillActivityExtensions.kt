/*
 * Copyright 2026 WebDAVPass.
 *
 * 本文件属于 WebDAVPass for Android（GPLv3）。
 * 自动填充桥接界面共用的 Activity 扩展。
 */

package xzynine.WebDAVPass.Android.autofillbridge

import android.app.Activity

/** 取消并关闭界面（不回传任何结果数据）。 */
internal fun Activity.cancelAndFinish() {
    setResult(Activity.RESULT_CANCELED)
    finish()
}
