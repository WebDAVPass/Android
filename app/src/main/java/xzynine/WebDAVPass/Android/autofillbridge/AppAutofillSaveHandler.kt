/*
 * Copyright 2026 WebDAVPass.
 *
 * 本文件属于 WebDAVPass for Android（GPLv3）。
 * 宿主侧自动填充保存处理：将表单数据写入密码库（经 TokenViewModel 创建条目）。
 */

package xzynine.WebDAVPass.Android.autofillbridge

import android.content.Context
import xzynine.WebDAVPass.Android.data.PasswordEntryEditDraft
import xzynine.WebDAVPass.Android.ui.viewmodel.TokenViewModel
import xzynine.WebDAVPass.Autofill.bridge.AutofillSaveHandler
import xzynine.WebDAVPass.Autofill.model.AutofillRegisterInfo

/**
 * 依据注册信息构造 [PasswordEntryEditDraft] 并写入当前打开的密码库。
 * [AutofillRegisterInfo.targetGroupId] 由选择/注册界面在用户选定分组后填充。
 */
class AppAutofillSaveHandler(
    private val context: Context,
) : AutofillSaveHandler {
    override suspend fun save(registerInfo: AutofillRegisterInfo): Boolean {
        val tokenViewModel = TokenViewModel.getSharedInstance(context)
        val site =
            registerInfo.searchInfo.webDomain
                ?: registerInfo.searchInfo.applicationId
                ?: "自动填充"
        val draft =
            PasswordEntryEditDraft(
                parentGroupId = registerInfo.targetGroupId,
                title = site,
                username = registerInfo.username.orEmpty(),
                password = registerInfo.password.orEmpty(),
                url =
                    registerInfo.searchInfo.webDomain
                        ?.let { "https://$it" }
                        .orEmpty(),
                notes = "",
            )
        return runCatching {
            tokenViewModel.createPasswordEntry(draft) != null
        }.getOrDefault(false)
    }
}
