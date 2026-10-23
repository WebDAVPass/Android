/*
 * Copyright 2026 WebDAVPass.
 *
 * 本文件属于 WebDAVPass for Android（GPLv3）。
 * 应用 Application：在 onCreate 中安装自动填充库桥接，注入宿主实现。
 */

package xzynine.WebDAVPass.Android

import android.app.Application
import xzynine.WebDAVPass.Android.autofillbridge.AppAutofillEntryProvider
import xzynine.WebDAVPass.Android.autofillbridge.AppAutofillLogger
import xzynine.WebDAVPass.Android.autofillbridge.AppAutofillPreferences
import xzynine.WebDAVPass.Android.autofillbridge.AppAutofillSaveHandler
import xzynine.WebDAVPass.Android.autofillbridge.AppAutofillUiTarget
import xzynine.WebDAVPass.Autofill.AutofillBridge
import xzynine.WebDAVPass.Autofill.AutofillConfig

class WebDavPassApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AutofillBridge.install(
            AutofillConfig(
                entryProvider = AppAutofillEntryProvider(this),
                uiTarget = AppAutofillUiTarget(),
                preferences = AppAutofillPreferences,
                saveHandler = AppAutofillSaveHandler(this),
                logger = AppAutofillLogger,
                queryTimeoutMillis = 2000L,
                appIconRes = R.mipmap.ic_launcher_round,
            ),
        )
        // 异步加载自动填充偏好缓存（加载完成前回落默认：主开关/内联/手动选择开启、提示保存开启）
        AppAutofillPreferences.loadAsync(this)
    }
}
