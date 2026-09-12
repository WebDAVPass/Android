/*
 * Copyright 2026 WebDAVPass.
 *
 * 本文件属于 WebDAVPass for Android（GPLv3）。
 * 自动填充库模块（:autofill）的黑名单匹配工具。
 *
 * 移植自 KeePassDX 的 KeeAutofillService.autofillAllowedFor，保留子串匹配语义。
 */

package xzynine.WebDAVPass.Autofill.core

import xzynine.WebDAVPass.Autofill.bridge.AutofillPreferences

/**
 * 黑名单匹配：应用包名 / 网站域名是否在黑名单中。
 * 采用子串匹配（与 KeePassDX 行为一致），黑名单为空集时一律放行。
 */
object AutofillBlocklist {
    private const val APPLICATION_ID_POPUP_WINDOW = "PopupWindow:"

    fun allowedFor(
        applicationId: String?,
        webDomain: String?,
        preferences: AutofillPreferences,
    ): Boolean =
        allowedFor(applicationId, preferences.applicationIdBlocklist) &&
            applicationId?.contains(APPLICATION_ID_POPUP_WINDOW) != true &&
            allowedFor(webDomain, preferences.webDomainBlocklist)

    private fun allowedFor(
        element: String?,
        blockList: Set<String>,
    ): Boolean {
        element?.let { elementNotNull ->
            if (blockList.any { appIdBlocked ->
                    elementNotNull.contains(appIdBlocked)
                }
            ) {
                return false
            }
        }
        return true
    }
}
