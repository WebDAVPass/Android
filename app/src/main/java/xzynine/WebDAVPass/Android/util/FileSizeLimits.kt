package xzynine.WebDAVPass.Android.util

/**
 * 各类外部文件写入/读取的字节上限（集中定义，避免各处重复声明）。
 */
object FileSizeLimits {
    /** 附件查看/读取上限：1 MiB */
    const val ATTACHMENT_MAX_BYTES = 1024 * 1024

    /** 附件导入（写库）上限：10 MiB */
    const val ATTACHMENT_IMPORT_MAX_BYTES = 10 * 1024 * 1024

    /** 密钥文件大小上限：1 MiB */
    const val KEY_FILE_MAX_BYTES = 1024 * 1024

    /** 自定义图标最大字节数（与 KeePassDX 上限一致）：512 KiB */
    const val ICON_MAX_BYTES = 512 * 1024
}
