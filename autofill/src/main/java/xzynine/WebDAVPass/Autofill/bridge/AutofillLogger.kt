/*
 * Copyright 2026 WebDAVPass.
 *
 * 本文件属于 WebDAVPass for Android（GPLv3）。
 * 自动填充库模块（:autofill）的日志桥接接口。
 */

package xzynine.WebDAVPass.Autofill.bridge

import android.util.Log

/**
 * 库内部日志输出接口，宿主 app 可实现后接入自身日志体系（或沿用默认 [DefaultLogger]）。
 */
interface AutofillLogger {
    fun d(
        tag: String,
        message: String,
    )

    fun w(
        tag: String,
        message: String,
    )

    fun e(
        tag: String,
        message: String,
        throwable: Throwable? = null,
    )
}

/** 默认实现：直接走 Android [Log]。 */
object DefaultLogger : AutofillLogger {
    override fun d(
        tag: String,
        message: String,
    ) {
        Log.d(tag, message)
    }

    override fun w(
        tag: String,
        message: String,
    ) {
        Log.w(tag, message)
    }

    override fun e(
        tag: String,
        message: String,
        throwable: Throwable?,
    ) {
        Log.e(tag, message, throwable)
    }
}
