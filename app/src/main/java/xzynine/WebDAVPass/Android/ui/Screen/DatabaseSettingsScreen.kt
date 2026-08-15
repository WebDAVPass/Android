package xzynine.WebDAVPass.Android.ui.Screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.UploadCloud
import xzynine.WebDAVPass.Android.ui.component.Preference
import xzynine.WebDAVPass.Android.ui.component.PreferenceType
import xzynine.WebDAVPass.Android.ui.component.SettingsTopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import xzylib.base.util.ToastUtils
import xzynine.WebDAVPass.Android.ui.Dialog.PasswordInputDialog
import xzynine.WebDAVPass.Android.ui.viewmodel.TokenViewModel
import xzynine.WebDAVPass.Android.util.PasswordStrength
import xzynine.WebDAVPass.Android.util.strengthLabel

/**
 * 数据库设置页。
 *
 * 支持修改主密码（可附带新密钥文件）、切换 KDF 算法与参数、开关压缩。
 * 保存后重新加密写回数据库文件。
 */
@Composable
fun DatabaseSettingsScreen(
    viewModel: TokenViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var keyRounds by remember { mutableStateOf("") }
    var memoryUsageMb by remember { mutableStateOf("") }
    var parallelism by remember { mutableStateOf("") }
    var isCompressionEnabled by remember { mutableStateOf(true) }

    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var keyFileName by remember { mutableStateOf("") }
    var keyFileData by remember { mutableStateOf<ByteArray?>(null) }
    var status by remember { mutableStateOf("") }
    // 弱密码二次确认：修改主密码时若新密码较弱且未更换密钥文件，需再次确认
    var weakPasswordAcknowledged by remember { mutableStateOf(false) }

    // 导出 / 合并数据库
    var pendingMergeUri by remember { mutableStateOf<Uri?>(null) }
    var mergeLoading by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
        onResult = { uri ->
            if (uri == null) {
                return@rememberLauncherForActivityResult
            }
            coroutineScope.launch {
                val ok = viewModel.exportCurrentDatabase(uri)
                if (ok) {
                    ToastUtils.showShortToast(context, "数据库已导出")
                } else {
                    ToastUtils.showShortToast(context, "导出失败")
                }
            }
        }
    )

    val mergeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri == null) {
                return@rememberLauncherForActivityResult
            }
            pendingMergeUri = uri
        }
    )

    val newPasswordStrengthBits = remember(newPassword) { PasswordStrength.estimateBits(newPassword) }
    val newPasswordIsWeak = newPassword.isNotEmpty() && PasswordStrength.isWeak(newPassword)

    val kdfOptions = listOf("AES", "Argon2d", "Argon2id")
    // -1 表示未知 KDF 或尚未加载：保存时 KDF 传 null（不修改），避免把未知 KDF 静默切到 AES
    var kdfSelectedIndex by remember { mutableStateOf(-1) }
    var kdfEngineName by remember { mutableStateOf("未知") }

    // Argon2 引擎默认参数（用于切换 KDF 时预填；memoryUsage 单位为字节）
    val argon2Defaults = remember {
        val engine = com.kunzisoft.keepass.database.crypto.kdf.KdfFactory.argon2dKdf
        Triple(
            (engine.defaultMemoryUsage / 1024 / 1024).coerceAtLeast(1), // 字节 → MiB
            engine.defaultParallelism.coerceAtLeast(1),
            engine.defaultKeyRounds.coerceAtLeast(1)
        )
    }

    fun switchKdf(index: Int) {
        kdfSelectedIndex = index
        kdfEngineName = kdfOptions[index]
        if (kdfEngineName == "AES") {
            // 预填 AES 默认轮数
            keyRounds = com.kunzisoft.keepass.database.crypto.kdf.KdfFactory.aesKdf.defaultKeyRounds.toString()
        } else {
            // 切换为 Argon2 时预填引擎默认参数，避免沿用 AES 的无效值
            memoryUsageMb = argon2Defaults.first.toString()
            parallelism = argon2Defaults.second.toString()
            keyRounds = argon2Defaults.third.toString()
        }
    }

    LaunchedEffect(Unit) {
        val info = viewModel.loadDatabaseSettingsInfo()
        if (info != null) {
            kdfEngineName = info.kdfEngineName
            kdfSelectedIndex = kdfOptions.indexOfFirst { it == info.kdfEngineName }
                .takeIf { it >= 0 } ?: -1
            keyRounds = info.keyRounds.toString()
            // memoryUsage 单位为字节：换算为 MiB 展示
            memoryUsageMb = if (info.memoryUsage > 0) (info.memoryUsage / 1024 / 1024).toString() else ""
            parallelism = if (info.parallelism > 0) info.parallelism.toString() else ""
            isCompressionEnabled = info.isCompressionEnabled
        }
        loading = false
    }

    val keyFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val buffer = java.io.ByteArrayOutputStream(8 * 1024)
                val chunk = ByteArray(8 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(chunk)
                    if (read < 0) break
                    total += read
                    if (total > 1024 * 1024) {
                        throw IllegalStateException("密钥文件过大")
                    }
                    buffer.write(chunk, 0, read)
                }
                val bytes = buffer.toByteArray()
                if (bytes.isNotEmpty()) {
                    keyFileName = uri.lastPathSegment?.substringAfterLast('/')
                        ?.takeIf { it.isNotBlank() } ?: "keyfile"
                    keyFileData = bytes
                }
            }
        }.onFailure {
            ToastUtils.showShortToast(context, "密钥文件读取失败：${it.message ?: "未知错误"}")
        }
    }

    fun save() {
        // 仅对用户实际选中的 KDF 校验参数；未知 KDF（-1）时不修改 KDF，跳过参数校验
        val selectedKdf = kdfOptions.getOrNull(kdfSelectedIndex)
        val kdfIsSelected = kdfSelectedIndex >= 0
        val error = when {
            oldPassword.isBlank() -> "请输入当前主密码"
            newPassword.isBlank() -> "请输入新主密码"
            newPassword != confirmPassword -> "两次新主密码不一致"
            selectedKdf == "AES" && keyRounds.toLongOrNull()?.let { it > 0 } != true -> "轮数必须为正数"
            selectedKdf != null && selectedKdf != "AES" && memoryUsageMb.toLongOrNull()?.let { it > 0 } != true -> "内存占用必须为正数"
            selectedKdf != null && selectedKdf != "AES" && parallelism.toLongOrNull()?.let { it > 0 } != true -> "并行度必须为正数"
            selectedKdf != null && selectedKdf != "AES" && keyRounds.toLongOrNull()?.let { it > 0 } != true -> "迭代次数必须为正数"
            else -> ""
        }
        status = error
        if (error.isNotBlank()) {
            return
        }
        // 未更换密钥文件时，弱密码需二次确认（首次点击仅提示）
        if (keyFileData == null && newPasswordIsWeak && !weakPasswordAcknowledged) {
            weakPasswordAcknowledged = true
            status = "新主密码强度较低（${newPasswordStrengthBits.toInt()} bits），建议增加长度或组合大小写/数字/符号；再次点击「保存设置」可强制使用。"
            return
        }
            coroutineScope.launch {
                saving = true
                val ok = viewModel.changeDatabaseSettings(
                    oldPassword = oldPassword,
                    newMasterPassword = newPassword,
                    newKeyFileData = keyFileData,
                    kdfEngineName = if (kdfIsSelected) kdfOptions[kdfSelectedIndex] else null,
                    // 未知 KDF 时不传任何 KDF 参数，避免把无关的轮数/内存/并行度应用到当前 KDF
                    keyRounds = if (kdfIsSelected) keyRounds.toLongOrNull() else null,
                    memoryUsage = if (kdfIsSelected) memoryUsageMb.toLongOrNull()?.times(1024 * 1024) else null,
                    parallelism = if (kdfIsSelected) parallelism.toLongOrNull() else null,
                    isCompressionEnabled = isCompressionEnabled
                )
                saving = false
                if (ok) {
                    ToastUtils.showShortToast(context, "数据库设置已保存")
                    onNavigateBack()
                } else {
                    status = "保存失败：当前主密码错误或 KDF 参数无效（Argon2 内存/并行度/迭代次数需为正数）"
                }
            }
    }

    Scaffold(
        popupHost = {},
        topBar = {
            SettingsTopAppBar(
                title = "数据库设置",
                onNavigateBack = onNavigateBack
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (loading) {
                        Text(text = "加载中...", fontSize = 14.sp)
                    } else {
                        Text(text = "修改主密码", modifier = Modifier.padding(top = 4.dp))
                        TextField(
                            value = oldPassword,
                            onValueChange = { oldPassword = it; status = "" },
                            label = "当前主密码",
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        TextField(
                            value = newPassword,
                            onValueChange = {
                                newPassword = it
                                status = ""
                                weakPasswordAcknowledged = false
                            },
                            label = "新主密码",
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (newPassword.isNotEmpty()) {
                            val bits = newPasswordStrengthBits
                            Text(
                                text = "强度：${strengthLabel(bits)}（${bits.toInt()} bits）",
                                fontSize = 12.sp,
                                color = if (bits < PasswordStrength.WEAK_PASSWORD_THRESHOLD_BITS)
                                    MiuixTheme.colorScheme.error
                                else MiuixTheme.colorScheme.primary
                            )
                        }
                        TextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it; status = "" },
                            label = "确认新主密码",
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(onClick = { showPassword = !showPassword }) {
                            Text(if (showPassword) "隐藏密码" else "显示密码")
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { keyFilePicker.launch(arrayOf("application/octet-stream", "*/*")) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Lock,
                                contentDescription = "密钥文件",
                                tint = MiuixTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                                Text(
                                    text = "新密钥文件（可选）",
                                    fontSize = 13.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceSecondary
                                )
                                Text(
                                    text = if (keyFileName.isBlank()) "点击选择密钥文件，不选则沿用当前" else keyFileName,
                                    fontSize = 14.sp,
                                    color = if (keyFileName.isBlank()) MiuixTheme.colorScheme.primary
                                    else MiuixTheme.colorScheme.onSurface
                                )
                            }
                            if (keyFileName.isNotBlank()) {
                                IconButton(onClick = {
                                    keyFileName = ""
                                    keyFileData = null
                                }) {
                                    Icon(
                                        imageVector = MiuixIcons.Delete,
                                        contentDescription = "清除密钥文件",
                                        tint = MiuixTheme.colorScheme.onSurfaceSecondary
                                    )
                                }
                            }
                        }

                        if (status.isNotBlank()) {
                            Text(text = status, fontSize = 13.sp, color = MiuixTheme.colorScheme.error)
                        }

                        Button(
                            onClick = { save() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            enabled = !saving
                        ) {
                            Text(if (saving) "保存中..." else "保存设置")
                        }
                    }
                }
            }

            if (!loading) {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(text = "加密与压缩")
                        Preference(
                            type = PreferenceType.Spinner,
                            title = "KDF 算法",
                            summary = "当前：$kdfEngineName",
                            items = kdfOptions.map { DropdownItem(text = it) },
                            selectedIndex = kdfSelectedIndex.coerceAtLeast(0),
                            showValue = true,
                            startAction = {
                                Icon(
                                    modifier = Modifier.padding(end = 16.dp),
                                    imageVector = MiuixIcons.Settings,
                                    contentDescription = "KDF 算法"
                                )
                            },
                            onSelectedIndexChange = { index ->
                                switchKdf(index)
                            }
                        )
                        // 按选中索引决定参数输入框：未知 KDF 隐藏全部参数；AES 仅显示轮数；Argon2 显示全部参数
                        when {
                            kdfSelectedIndex < 0 -> {
                                // 未知 KDF：不展示参数输入框，保存时也不会修改 KDF
                                Text(
                                    text = "当前 KDF 不在可选范围内，保存时将保留原算法与参数",
                                    fontSize = 13.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceSecondary
                                )
                            }
                            kdfEngineName == "AES" -> {
                                TextField(
                                    value = keyRounds,
                                    onValueChange = { keyRounds = it },
                                    label = "加密轮数（AES-KDF）",
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            else -> {
                                TextField(
                                    value = memoryUsageMb,
                                    onValueChange = { memoryUsageMb = it },
                                    label = "内存占用（MB，Argon2）",
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                TextField(
                                    value = parallelism,
                                    onValueChange = { parallelism = it },
                                    label = "并行度（Argon2）",
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                TextField(
                                    value = keyRounds,
                                    onValueChange = { keyRounds = it },
                                    label = "迭代次数（Argon2）",
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        Preference(
                            type = PreferenceType.Switch,
                            title = "启用压缩",
                            summary = "保存时使用 GZIP 压缩数据库内容",
                            checked = isCompressionEnabled,
                            onCheckedChange = { isCompressionEnabled = it }
                        )
                    }
                }
            }

            if (!loading) {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Preference(
                        type = PreferenceType.Arrow,
                        title = "导出数据库",
                        summary = "将当前库另存为 .kdbx 文件",
                        startAction = {
                            Icon(
                                modifier = Modifier.padding(end = 16.dp),
                                imageVector = MiuixIcons.Download,
                                contentDescription = "导出数据库",
                            )
                        },
                        onClick = {
                            exportLauncher.launch("WebDavPass-导出.kdbx")
                        },
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Preference(
                        type = PreferenceType.Arrow,
                        title = "合并数据库",
                        summary = "将其他 .kdbx 文件的内容合并进当前库",
                        startAction = {
                            Icon(
                                modifier = Modifier.padding(end = 16.dp),
                                imageVector = MiuixIcons.UploadCloud,
                                contentDescription = "合并数据库",
                            )
                        },
                        onClick = {
                            mergeLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                        },
                    )
                }

                pendingMergeUri?.let { mergeUri ->
                    PasswordInputDialog(
                        show = true,
                        title = "合并数据库",
                        summary = if (mergeLoading) "正在合并..." else "请输入待合并文件的主密码",
                        confirmButtonText = if (mergeLoading) "合并中..." else "合并",
                        onDismiss = { if (!mergeLoading) pendingMergeUri = null },
                        onConfirm = { mergePassword ->
                            if (mergeLoading) return@PasswordInputDialog
                            pendingMergeUri = null
                            coroutineScope.launch {
                                mergeLoading = true
                                val ok = viewModel.mergeLocalDatabase(mergeUri, mergePassword)
                                mergeLoading = false
                                if (ok) {
                                    ToastUtils.showShortToast(context, "合并完成")
                                } else {
                                    ToastUtils.showShortToast(context, "合并失败：密码错误或文件无效")
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}
