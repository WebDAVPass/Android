package xzynine.WebDAVPass.Android.ui.component

import android.content.Context
import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.documentfile.provider.DocumentFile
import xzylib.base.util.ToastUtils
import xzynine.WebDAVPass.Android.util.BoundedIo

/**
 * 附件选择器：选择文件后按上限有界读取字节，超过上限或读取失败时取消并提示。
 *
 * @param context 用于解析 content provider 与弹出提示
 * @param limitBytes 附件字节上限，声明大小预检与增量读取共用
 * @param onPicked 读取成功回调，参数为显示名与字节内容
 */
@Composable
fun rememberAttachmentPicker(
    context: Context,
    limitBytes: Int,
    onPicked: (name: String, data: ByteArray) -> Unit,
): ManagedActivityResultLauncher<String, Uri?> =
    rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        // 先用声明大小预检（content provider 可能返回 -1，故仅作软提示，真正上限在增量读取处强制）
        val declaredSize = DocumentFile.fromSingleUri(context, uri)?.length() ?: -1L
        if (declaredSize > limitBytes) {
            ToastUtils.showShortToast(context, "附件过大（上限 ${limitBytes / 1024 / 1024} MiB），已取消")
            return@rememberLauncherForActivityResult
        }
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                BoundedIo.readBoundedBytes(input, limitBytes, "附件过大")
            }
        }.onSuccess { bytes ->
            if (bytes != null) {
                val name =
                    uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
                        ?: "attachment_${System.currentTimeMillis()}"
                onPicked(name, bytes)
            }
        }.onFailure {
            ToastUtils.showShortToast(context, "附件过大或读取失败，已取消")
        }
    }
