/*
 * Copyright 2026 WebDAVPass.
 *
 * 本文件属于 WebDAVPass for Android（GPLv3）。
 * 自动填充库模块（:autofill）的桥接配置聚合。
 */

package xzynine.WebDAVPass.Autofill

import xzynine.WebDAVPass.Autofill.bridge.AutofillEntryProvider
import xzynine.WebDAVPass.Autofill.bridge.AutofillLogger
import xzynine.WebDAVPass.Autofill.bridge.AutofillPreferences
import xzynine.WebDAVPass.Autofill.bridge.AutofillSaveHandler
import xzynine.WebDAVPass.Autofill.bridge.AutofillUiTarget
import xzynine.WebDAVPass.Autofill.bridge.DefaultLogger

/**
 * 自动填充库的完整配置，聚合所有桥接接口。
 * 宿主 app 在 [AutofillBridge.install] 时注入。
 *
 * @param entryProvider 条目数据源
 * @param uiTarget 选择/注册界面跳转目标
 * @param preferences 偏好设置（同步读取）
 * @param saveHandler 保存处理
 * @param logger 日志输出，默认走 Android Log
 * @param queryTimeoutMillis 条目检索超时（毫秒），超时按未命中处理，避免填充条挂死
 * @param appIconRes 填充提示条目的图标资源，0 表示使用库自带默认图标
 */
data class AutofillConfig(
    val entryProvider: AutofillEntryProvider,
    val uiTarget: AutofillUiTarget,
    val preferences: AutofillPreferences,
    val saveHandler: AutofillSaveHandler,
    val logger: AutofillLogger = DefaultLogger,
    val queryTimeoutMillis: Long = 2000L,
    val appIconRes: Int = 0,
)
