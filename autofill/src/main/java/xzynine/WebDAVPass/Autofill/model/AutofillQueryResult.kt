/*
 * Copyright 2026 WebDAVPass.
 *
 * 本文件属于 WebDAVPass for Android（GPLv3）。
 * 自动填充库模块（:autofill）的数据模型：条目检索三态结果。
 *
 * 对应 KeePassDX 的 SearchHelper.checkAutoSearchInfo 三回调：
 * Found=onItemsFound（直接返回可填 Dataset）；NotFound=onItemNotFound（展示选择界面）；
 * Unavailable=onDatabaseClosed（库未就绪，同样展示选择/解锁界面）。
 */

package xzynine.WebDAVPass.Autofill.model

import xzynine.WebDAVPass.Autofill.model.AutofillEntry

sealed interface AutofillQueryResult {
    /** 检索命中，携带可填充条目列表。 */
    data class Found(
        val entries: List<AutofillEntry>,
    ) : AutofillQueryResult

    /** 未检索到匹配条目。 */
    data object NotFound : AutofillQueryResult

    /** 密码库未就绪（未解锁 / 未选择文件）。 */
    data object Unavailable : AutofillQueryResult
}
