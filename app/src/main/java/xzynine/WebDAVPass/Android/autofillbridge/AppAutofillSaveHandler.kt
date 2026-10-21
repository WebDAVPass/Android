/*
 * Copyright 2026 WebDAVPass.
 *
 * 本文件属于 WebDAVPass for Android（GPLv3）。
 * 宿主侧自动填充保存处理：将表单数据写入密码库（经 TokenViewModel 创建条目）。
 */

package xzynine.WebDAVPass.Android.autofillbridge

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xzynine.WebDAVPass.Android.data.AppPackageField.normalizeAppPackage
import xzynine.WebDAVPass.Android.data.PasswordEntryEditDraft
import xzynine.WebDAVPass.Android.ui.viewmodel.TokenViewModel
import xzynine.WebDAVPass.Android.util.InstalledAppsProvider
import xzynine.WebDAVPass.Autofill.bridge.AutofillSaveHandler
import xzynine.WebDAVPass.Autofill.model.AutofillRegisterInfo

/**
 * 依据注册信息构造 [PasswordEntryEditDraft] 并写入当前打开的密码库。
 * [AutofillRegisterInfo.targetGroupId] 由选择/注册界面在用户选定分组后填充。
 */
class AppAutofillSaveHandler(
    private val context: Context,
) : AutofillSaveHandler {
    /**
     * 把表单注册信息写入当前打开的密码库。
     *
     * 标题：有网站域名的表单取域名；无网站域名的应用表单取应用显示名，读不到时回落包名。
     * 网站域名同时写入 URL 字段；android:// 混淆来源已在服务侧转换为应用来源，不会进入 URL。
     * 应用表单额外写入应用关联字段与该应用的图标：应用图标读不到时保持无图标，不影响保存。
     *
     * @return 写入成功返回 true，库锁定 / 写入异常返回 false
     */
    override suspend fun save(registerInfo: AutofillRegisterInfo): Boolean {
        val tokenViewModel = TokenViewModel.getSharedInstance(context)
        val applicationId = normalizeAppPackage(registerInfo.searchInfo.applicationId)
        val title =
            if (registerInfo.searchInfo.webDomain.isNullOrBlank() && applicationId.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    runCatching { InstalledAppsProvider.getApplicationLabel(context, applicationId) }
                        .getOrDefault(applicationId)
                }.ifBlank { applicationId }
            } else {
                registerInfo.site
            }
        val draft =
            PasswordEntryEditDraft(
                parentGroupId = registerInfo.targetGroupId,
                title = title,
                username = registerInfo.username.orEmpty(),
                password = registerInfo.password.orEmpty(),
                url =
                    registerInfo.searchInfo.webDomain
                        ?.let { "https://$it" }
                        .orEmpty(),
                notes = "",
                appPackageName = applicationId,
            )
        return runCatching {
            val withAppIcon =
                if (applicationId.isBlank()) {
                    draft
                } else {
                    draft.copy(
                        newCustomIconBytes =
                            runCatching {
                                InstalledAppsProvider.loadAppIconPngBytes(context, applicationId)
                            }.getOrNull(),
                    )
                }
            tokenViewModel.createPasswordEntry(withAppIcon) != null
        }.getOrDefault(false)
    }
}
