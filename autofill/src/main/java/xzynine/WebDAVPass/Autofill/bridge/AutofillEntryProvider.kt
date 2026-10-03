/*
 * Copyright 2026 WebDAVPass.
 *
 * 本文件属于 WebDAVPass for Android（GPLv3）。
 * 自动填充库模块（:autofill）的条目数据源桥接接口。
 */

package xzynine.WebDAVPass.Autofill.bridge

import xzynine.WebDAVPass.Autofill.model.AutofillQueryResult
import xzynine.WebDAVPass.Autofill.model.AutofillSearchInfo

/**
 * 根据表单上下文检索可填充条目。
 *
 * 实现方应尽量避免阻塞；库已在外层包裹超时，但未命中时请勿长时间挂起。
 * 当密码库未解锁时建议返回 [AutofillQueryResult.Unavailable]，由库展示选择/解锁界面。
 */
interface AutofillEntryProvider {
    suspend fun search(searchInfo: AutofillSearchInfo): AutofillQueryResult
}
