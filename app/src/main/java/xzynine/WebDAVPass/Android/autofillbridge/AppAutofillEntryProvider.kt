/*
 * Copyright 2026 WebDAVPass.
 *
 * 本文件属于 WebDAVPass for Android（GPLv3）。
 * 宿主侧自动填充条目数据源：从 TokenViewModel 的密码库条目流中检索可填充条目。
 */

package xzynine.WebDAVPass.Android.autofillbridge

import android.content.Context
import xzynine.WebDAVPass.Android.data.PasswordEntry
import xzynine.WebDAVPass.Android.data.RemainingValueType
import xzynine.WebDAVPass.Android.ui.viewmodel.TokenViewModel
import xzynine.WebDAVPass.Autofill.bridge.AutofillEntryProvider
import xzynine.WebDAVPass.Autofill.model.AutofillEntry
import xzynine.WebDAVPass.Autofill.model.AutofillQueryResult
import xzynine.WebDAVPass.Autofill.model.AutofillSearchInfo

/**
 * 将密码库 [PasswordEntry] 映射为库所需的 [AutofillEntry]，并按表单域名/包名过滤。
 *
 * 检索受外层桥接（库）的超时保护；此处仅做内存映射与过滤，不做阻塞 I/O。
 */
class AppAutofillEntryProvider(
    private val context: Context,
) : AutofillEntryProvider {
    /**
     * 按表单检索可填充条目。
     *
     * 库未解锁时返回 [AutofillQueryResult.Unavailable]，由宿主选择界面引导解锁；
     * 手动选择入口（[AutofillSearchInfo.manualSelection]）跳过域名/包名过滤，返回全部条目。
     * 条目详情读取涉及文件 IO，已由 [TokenViewModel.loadAllPasswordEntriesWithDetails] 切到 IO 线程。
     */
    override suspend fun search(searchInfo: AutofillSearchInfo): AutofillQueryResult {
        val tokenViewModel = TokenViewModel.getSharedInstance(context)
        // 库未解锁时返回 Unavailable，由宿主选择界面引导解锁
        if (!tokenViewModel.libraryViewModel.isLibraryUnlocked.value) {
            return AutofillQueryResult.Unavailable
        }
        return runCatching {
            // 自动填充需按包名/域名/自定义字段（AndroidApp1 等）匹配，必须加载含字段详情的条目；
            // 普通列表流不含 keyValues 详情，故独立读取全部条目。
            val entries = tokenViewModel.loadAllPasswordEntriesWithDetails()
            val mapped = entries.mapNotNull { it.toAutofillEntry() }
            if (searchInfo.manualSelection) {
                if (mapped.isEmpty()) {
                    AutofillQueryResult.NotFound
                } else {
                    AutofillQueryResult.Found(mapped)
                }
            } else {
                val filtered = mapped.filter { matches(it, searchInfo) }
                if (filtered.isEmpty()) {
                    AutofillQueryResult.NotFound
                } else {
                    AutofillQueryResult.Found(filtered)
                }
            }
        }.getOrDefault(AutofillQueryResult.NotFound)
    }

    /**
     * 将密码库条目映射为库的 [AutofillEntry]；文件夹分组返回 null（不可填充）。
     *
     * 收集全部非机密字段（标题 / 账号 / 网站 / 附加字段）作为 [AutofillEntry.searchableValues]，
     * 供 [matches] 按域名 / 包名匹配；密码与动态令牌不参与匹配。
     */
    private fun PasswordEntry.toAutofillEntry(): AutofillEntry? {
        if (isFolderGroup) return null
        // 按字段名匹配用户名，不限定 valueType：含 @ 的邮箱型用户名会被 detectValueType 判为
        // EMAIL 类型，若强约束 TEXT 会将其丢弃（KeePassDX 直接使用原始 username 字符串，无此限制）。
        val username =
            keyValues
                .firstOrNull {
                    it.fieldName.equals("username", true) || it.fieldName.equals("user", true) || it.fieldName.equals("email", true)
                }?.rawValue ?: ""
        val password =
            keyValues.firstOrNull { it.valueType == RemainingValueType.PASSWORD }?.rawValue ?: ""
        val url =
            keyValues.firstOrNull { it.valueType == RemainingValueType.URL }?.rawValue ?: ""
        val otp =
            keyValues.firstOrNull { it.valueType == RemainingValueType.OTP }?.rawValue
        // 收集所有非机密字段用于自动填充匹配：标题、账号、网站，以及全部附加字段
        // （如 KeePassDX 迁移来的 AndroidApp1=androidapp://tv.danmaku.bili）。不含密码与动态令牌。
        val searchableValues =
            buildList {
                add(title)
                add(account)
                add(url)
                add(username)
                keyValues.forEach { kv ->
                    if (kv.valueType != RemainingValueType.PASSWORD &&
                        kv.valueType != RemainingValueType.OTP
                    ) {
                        add(kv.rawValue)
                    }
                }
            }.filter { it.isNotBlank() }
        return AutofillEntry(
            id = entryId,
            title = title,
            username = username,
            password = password,
            url = url,
            otpToken = otp,
            searchableValues = searchableValues,
        )
    }

    /**
     * 条目是否适用于当前表单：在任意非机密字段（标题/账号/网站/附加字段）中匹配域名或包名。
     * 匹配规则见 [AutofillEntryMatcher]（网站按域名边界、应用按完整包名精确匹配，
     * 避免 `bank.com` 命中 `ank.com`、`com.bank` 命中 `com.bank.secure`）。
     */
    private fun matches(
        entry: AutofillEntry,
        searchInfo: AutofillSearchInfo,
    ): Boolean =
        AutofillEntryMatcher.matches(
            searchableValues = entry.searchableValues,
            webDomain = searchInfo.webDomain,
            applicationId = searchInfo.applicationId,
        )
}
