/*
 * Copyright 2026 WebDAVPass.
 *
 * 本文件属于 WebDAVPass for Android（GPLv3）。
 * 自动填充库模块（:autofill）的桥接安装入口。
 *
 * 库与宿主 app 的唯一耦合点：宿主在 Application.onCreate 中调用 [install] 注入实现，
 * 库内所有组件通过 [getConfig] / [requireConfig] 取用。
 */

package xzynine.WebDAVPass.Autofill

import xzynine.WebDAVPass.Autofill.bridge.AutofillLogger

/**
 * 全局桥接安装器。进程内单例，库被系统拉起时通过它获取宿主注入的实现。
 */
object AutofillBridge {
    @Volatile
    private var config: AutofillConfig? = null

    /** 安装（或替换）桥接配置。应在 Application.onCreate 尽早调用。 */
    @Synchronized
    fun install(config: AutofillConfig) {
        this.config = config
    }

    /** 卸载桥接配置（进程退出或测试时调用）。 */
    @Synchronized
    fun uninstall() {
        config = null
    }

    /** 是否已安装。 */
    fun isInstalled(): Boolean = config != null

    /** 获取已安装配置；未安装时返回 null（调用方需安全降级）。 */
    fun getConfig(): AutofillConfig? = config

    /**
     * 获取已安装配置；未安装时抛出 [IllegalStateException]。
     * 库内部在确信宿主已注入的场景（如选择界面回调）使用，便于快速暴露配置缺失问题。
     */
    fun requireConfig(): AutofillConfig =
        config ?: throw IllegalStateException(
            "AutofillBridge 未安装：请在 Application.onCreate 中调用 AutofillBridge.install(config)",
        )

    /** 便捷日志访问（未安装时使用默认日志，避免空指针）。 */
    internal fun logger(): AutofillLogger = config?.logger ?: xzynine.WebDAVPass.Autofill.bridge.DefaultLogger
}
