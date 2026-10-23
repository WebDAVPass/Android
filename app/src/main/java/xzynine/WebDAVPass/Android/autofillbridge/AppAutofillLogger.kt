/*
 * Copyright 2026 WebDAVPass.
 *
 * 本文件属于 WebDAVPass for Android（GPLv3）。
 * 宿主侧自动填充日志实现（直接走 Android Log，便于后续接入应用日志体系时替换）。
 */

package xzynine.WebDAVPass.Android.autofillbridge

import xzynine.WebDAVPass.Autofill.bridge.AutofillLogger
import xzynine.WebDAVPass.Autofill.bridge.DefaultLogger

/** 当前实现直接委托给库默认日志（Android Log）。 */
object AppAutofillLogger : AutofillLogger by DefaultLogger
