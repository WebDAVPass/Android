/*
 * Copyright 2026 WebDAVPass.
 *
 * 本文件属于 WebDAVPass for Android（GPLv3）。
 * 自动填充库模块（:autofill）的偏好设置桥接接口。
 *
 * 这些取值在 onFillRequest / onSaveRequest 主线程中同步读取，宿主 app 应以内存缓存形式实现
 * （例如连接服务时从 Room 异步加载，读时返回缓存值），避免阻塞填充回调。
 */

package xzynine.WebDAVPass.Autofill.bridge

/**
 * 自动填充相关偏好设置。宿主 app 提供实现，库在每次填充/保存请求时同步读取。
 */
interface AutofillPreferences {
    /** 是否启用自动填充建议（主开关）。关闭时仅展示选择界面。 */
    val autofillSuggestionsEnabled: Boolean

    /** 是否在兼容键盘上显示内联建议（Inline Suggestions）。 */
    val inlineSuggestionsEnabled: Boolean

    /** 是否在候选列表中提供「手动选择」入口。 */
    val manualSelectionEnabled: Boolean

    /** 表单提交后是否提示保存。 */
    val askToSaveData: Boolean

    /** 应用黑名单（包名子串匹配）。 */
    val applicationIdBlocklist: Set<String>

    /** 网站黑名单（域名子串匹配）。 */
    val webDomainBlocklist: Set<String>
}
