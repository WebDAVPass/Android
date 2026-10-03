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
 *
 * 匹配侧大小写不敏感：包名与域名本身不区分大小写（Android 包名规范为小写、域名亦然），
 * 而写入侧只做 trim，用户输入 `Example.COM`、`com.Bank.Secure` 也能命中运行时的小写值。
 */
object AutofillBlocklist {
    private const val APPLICATION_ID_POPUP_WINDOW = "PopupWindow:"

    /**
     * 判断应用 / 网站是否允许自动填充：应用包名、网站域名均未命中黑名单，且非弹窗窗口。
     *
     * @param applicationId 应用包名（可为空，如无窗口标题的异常表单）
     * @param webDomain 网站域名（可为空）
     * @param preferences 宿主提供的偏好实现，提供两个黑名单集合
     */
    fun allowedFor(
        applicationId: String?,
        webDomain: String?,
        preferences: AutofillPreferences,
    ): Boolean =
        allowedFor(applicationId, preferences.applicationIdBlocklist) &&
            applicationId?.contains(APPLICATION_ID_POPUP_WINDOW) != true &&
            allowedFor(webDomain, preferences.webDomainBlocklist)

    /** 单个元素的子串匹配（忽略大小写）；元素或黑名单为空时放行。 */
    private fun allowedFor(
        element: String?,
        blockList: Set<String>,
    ): Boolean {
        element?.let { elementNotNull ->
            if (blockList.any { appIdBlocked ->
                    elementNotNull.contains(appIdBlocked, ignoreCase = true)
                }
            ) {
                return false
            }
        }
        return true
    }
}
