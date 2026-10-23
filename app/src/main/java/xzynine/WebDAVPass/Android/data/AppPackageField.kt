package xzynine.WebDAVPass.Android.data

/**
 * 条目「应用」字段的纯逻辑。
 *
 * 与上级目录两个参考应用保持同构，条目内以 KeePass 自定义字段存放应用包名，
 * 但两个参考应用的命名与取值并不一致，因此写入与读取分开处理：
 * - 写入：`AndroidApp1` + `androidapp://<包名>`，即 keepass2android 的原生形态
 *   （`Utils/Util.cs` `SetNextFreeUrlField` 的 prefix `AndroidApp` + 从 1 开始的序号）；
 * - 读取：同时兼容三套历史命名 `AndroidApp`（KeePassDX 首槽）、
 *   `AndroidApp_1`/`AndroidApp_2`（KeePassDX `EntryInfo.suffixFieldNamePosition` 的下划线槽位）
 *   与 `AndroidApp1`/`AndroidApp2`（keepass2android 无下划线槽位）；
 * - 取值：`androidapp://` 前缀与裸包名都接受，保存时统一写成带前缀形式
 *   （keepass2android 按值前缀识别，KeePassDX 虽不解析该前缀，但会原样保留该字段）。
 *
 * 无 Android 依赖，便于单元测试覆盖跨工具格式兼容回归。
 */
object AppPackageField {
    /** 应用关联字段的值前缀（keepass2android 的 `AndroidAppScheme`）。 */
    const val ANDROID_APP_SCHEME = "androidapp://"

    /** 应用关联字段的基础名（两个参考应用共用的前缀）。 */
    const val APP_ID_FIELD_NAME = "AndroidApp"

    /**
     * 新条目写入时使用的字段名。
     *
     * 选 keepass2android 的 `AndroidApp1` 而非 KeePassDX 的 `AndroidApp`：
     * 字段名与取值是配套的，本应用写的是 `androidapp://` 值，按 keepass2android 的槽位命名才自洽。
     * 条目里若已存在别的形态（`AndroidApp` / `AndroidApp_1`），保存时沿用原名，不做重命名。
     */
    const val APP_ID_NEW_FIELD_NAME = "${APP_ID_FIELD_NAME}1"

    /**
     * 字段名槽位后缀：`AndroidApp_1` / `AndroidApp1` 两种分隔风格都接受，其余一律不认。
     *
     * 必须整体匹配，避免 KDX 自身 `startsWith(APPLICATION_ID_FIELD_NAME)` 的宽松判断把
     * `AndroidApp Signature`（签名子字段）、`AndroidApplication` 之类的同前缀字段误认为应用关联。
     */
    private val APP_ID_FIELD_NAME_REGEX = Regex("^androidapp(_?\\d+)?$", RegexOption.IGNORE_CASE)

    /**
     * 判断字段名是否为应用关联字段（忽略大小写）。
     *
     * 命中：`AndroidApp`、`AndroidApp_1`、`AndroidApp1`、`AndroidApp2`。
     * 不命中：`AndroidApp Signature`、`AndroidApplication`、`AndroidApp_`（无序号）。
     */
    fun isAppIdFieldName(name: String): Boolean = APP_ID_FIELD_NAME_REGEX.matches(name.trim())

    /**
     * 把字段值归一化为裸包名。
     *
     * 依次：去首尾空白 → 剥离 `androidapp://` 前缀（忽略大小写）→ 截断到第一个 `/` → 去尾部斜杠。
     * 空值或纯 scheme 返回空串，供 UI 直接绑定输入框。
     */
    fun normalizeAppPackage(raw: String?): String {
        var value = raw?.trim().orEmpty()
        if (value.isEmpty()) return ""
        if (value.startsWith(ANDROID_APP_SCHEME, ignoreCase = true)) {
            value = value.substring(ANDROID_APP_SCHEME.length)
        }
        value = value.substringBefore('/').trim()
        return value.trimEnd('/')
    }

    /**
     * 生成写入 KDBX 的应用关联字段值（带 `androidapp://` 前缀）。
     */
    fun toFieldValue(packageName: String?): String {
        val normalized = normalizeAppPackage(packageName)
        return if (normalized.isEmpty()) "" else ANDROID_APP_SCHEME + normalized
    }
}

/**
 * 应用字段拆分结果。
 *
 * @property packageName 归一化后的裸包名；条目没有应用字段时为空串
 * @property fields 摘出应用字段后剩余的字段草稿（含第二个及以后的应用关联字段）
 */
data class AppPackageSplit(
    val packageName: String,
    val fields: List<EditableFieldDraft>,
)

/**
 * 从字段草稿列表中摘出「应用」字段。
 *
 * 条目可能存在多个应用关联字段（KeePassDX 的 `AndroidApp` + `AndroidApp_1`…、
 * keepass2android 的 `AndroidApp1` + `AndroidApp2`…，也可能两套混在同一条目里）：
 * 只把第一个取值非空的应用字段作为界面的「应用」字段，其余原样保留在列表中，
 * 既避免界面上出现重复的输入行，也不会在保存时丢失额外关联。
 */
fun List<EditableFieldDraft>.splitAppPackageField(): AppPackageSplit {
    val remaining = mutableListOf<EditableFieldDraft>()
    var packageName = ""
    for (field in this) {
        val isAppField = AppPackageField.isAppIdFieldName(field.name)
        val normalized = if (isAppField) AppPackageField.normalizeAppPackage(field.value) else ""
        if (packageName.isEmpty() && normalized.isNotEmpty()) {
            packageName = normalized
            continue
        }
        remaining.add(field)
    }
    return AppPackageSplit(packageName = packageName, fields = remaining)
}
