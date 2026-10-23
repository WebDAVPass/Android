package xzynine.WebDAVPass.Android.data

import xzynine.WebDAVPass.Autofill.core.StructureParser

/**
 * 旧版自动填充污染条目的识别与转换规则（纯逻辑，无 Android 依赖）。
 *
 * 旧版本把 WebView 混淆站点来源 `android://<哈希>@<包名>` 当普通域名写入条目网站字段，
 * 自动填充保存流程写入的形态为 `https://android://<哈希>@<包名>`。
 * 哈希为页面地址的不可逆摘要：转换即清空网站字段，并把包名回填到「应用」关联字段。
 */
object LegacyAutofillUrl {
    /** 单条存量候选：待修复的条目。 */
    data class Candidate(
        val entryId: Long,
        val title: String,
        /** 命中的原始网站字段值；仅 [needsUrlClear] 为 true 时为 android:// 混淆来源，否则为空串。 */
        val originalUrl: String,
        /** 目标应用包名：来自混淆来源解析或既有「应用」关联字段。 */
        val packageName: String,
        /** true 表示网站字段被污染需清空；false 表示网站字段不动、仅回填图标。 */
        val needsUrlClear: Boolean,
        /** 条目是否已有自定义图标；false 时修复回填应用图标。 */
        val hasCustomIcon: Boolean,
    )

    /**
     * 解析旧版污染的网站字段值，命中返回包名；普通 URL 返回 null。
     * 格式判定复用自动填充服务的 [StructureParser.hashedWebDomainPackage]，仅在此外层放宽 http(s) 前缀。
     */
    fun parseLegacyUrl(value: String?): String? {
        val raw = value?.trim().orEmpty()
        if (raw.isEmpty()) {
            return null
        }
        val stripped = raw.removePrefix("https://").removePrefix("http://")
        return StructureParser.hashedWebDomainPackage(stripped)
    }

    /**
     * 选取下一个未占用的应用关联字段名：自 `AndroidApp1` 起连续编号，占用判定忽略大小写。
     * 历史形态（`AndroidApp` / `AndroidApp_1`）不含编号，不占用数字槽位；全满时返回 null。
     */
    fun nextAppFieldSlotName(existingNames: Collection<String>): String? {
        val occupied = existingNames.map { it.trim().lowercase() }.toSet()
        for (index in 1..MAX_SLOT_INDEX) {
            val name = "${AppPackageField.APP_ID_FIELD_NAME}$index"
            if (occupied.none { it == name.lowercase() }) {
                return name
            }
        }
        return null
    }

    /** 槽位编号上限：超出按无空闲槽位处理，避免异常数据触发无限增长。 */
    private const val MAX_SLOT_INDEX = 64
}
