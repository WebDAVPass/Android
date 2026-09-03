/*
 * Copyright 2026 WebDAVPass.
 *
 * 本文件属于 WebDAVPass for Android（GPLv3）。
 * 宿主侧自动填充条目数据源：从 TokenViewModel 的密码库条目流中检索可填充条目。
 */

package xzynine.WebDAVPass.Android.autofillbridge

import android.content.Context
import kotlinx.coroutines.flow.first
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

    private fun PasswordEntry.toAutofillEntry(): AutofillEntry? {
        if (isFolderGroup) return null
        val username =
            keyValues
                .firstOrNull {
                    it.valueType == RemainingValueType.TEXT &&
                        (it.fieldName.equals("username", true) || it.fieldName.equals("user", true) || it.fieldName.equals("email", true))
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

    private fun matches(
        entry: AutofillEntry,
        searchInfo: AutofillSearchInfo,
    ): Boolean {
        val domain = searchInfo.webDomain
        val appId = searchInfo.applicationId
        val haystack = entry.searchableValues
        // 在任意非机密字段（标题/账号/网站/附加字段）中按子串匹配包名或域名。
        // 形如 androidapp://tv.danmaku.bili 的附加字段可天然命中请求中的 applicationId=tv.danmaku.bili。
        return when {
            !domain.isNullOrEmpty() -> haystack.any { it.contains(domain, true) }
            !appId.isNullOrEmpty() -> haystack.any { it.contains(appId, true) }
            else -> true
        }
    }
}
