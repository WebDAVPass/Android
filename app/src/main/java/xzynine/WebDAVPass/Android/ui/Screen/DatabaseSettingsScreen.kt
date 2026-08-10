package xzynine.WebDAVPass.Android.ui.Screen

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
import androidx.compose.foundation.layout.size
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
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowSpinnerPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import xzylib.base.util.ToastUtils
import xzynine.WebDAVPass.Android.ui.ViewModel.TokenViewModel

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
    var kdfEngineName by remember { mutableStateOf("AES") }
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

    val kdfOptions = listOf("AES", "Argon2d", "Argon2id")
    var kdfSelectedIndex by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        val info = viewModel.loadDatabaseSettingsInfo()
        if (info != null) {
            kdfEngineName = info.kdfEngineName
            kdfSelectedIndex = kdfOptions.indexOfFirst { it == info.kdfEngineName }.takeIf { it >= 0 } ?: 0
            keyRounds = info.keyRounds.toString()
            memoryUsageMb = (info.memoryUsage / 1024 / 1024).toString()
            parallelism = info.parallelism.toString()
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
        when {
            oldPassword.isBlank() -> status = "请输入当前主密码"
            newPassword.isBlank() -> status = "请输入新主密码"
            newPassword != confirmPassword -> status = "两次新主密码不一致"
            keyRounds.toLongOrNull() == null && kdfEngineName == "AES" -> status = "轮数必须为数字"
            else -> status = ""
        }
        if (status.isNotBlank()) {
            return
        }
        coroutineScope.launch {
            saving = true
            val ok = viewModel.changeDatabaseSettings(
                newMasterPassword = newPassword,
                newKeyFileData = keyFileData,
                kdfEngineName = if (kdfSelectedIndex >= 0) kdfOptions[kdfSelectedIndex] else null,
                keyRounds = keyRounds.toLongOrNull(),
                memoryUsage = memoryUsageMb.toLongOrNull()?.times(1024 * 1024),
                parallelism = parallelism.toLongOrNull(),
                isCompressionEnabled = isCompressionEnabled
            )
            saving = false
            if (ok) {
                ToastUtils.showShortToast(context, "数据库设置已保存")
                onNavigateBack()
            } else {
                status = "保存失败：当前主密码错误或数据库写入失败"
            }
        }
    }

    Scaffold(
        popupHost = {},
        topBar = {
            TopAppBar(
                title = "数据库设置",
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {},
                defaultWindowInsetsPadding = true
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
                    onValueChange = { newPassword = it; status = "" },
                    label = "新主密码",
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
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

                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "加密与压缩")
                WindowSpinnerPreference(
                    title = "KDF 算法",
                    summary = "当前：$kdfEngineName",
                    items = kdfOptions.map { DropdownItem(text = it) },
                    selectedIndex = kdfSelectedIndex,
                    showValue = true,
                    startAction = {
                        Icon(
                            modifier = Modifier.padding(end = 16.dp),
                            imageVector = MiuixIcons.Settings,
                            contentDescription = "KDF 算法"
                        )
                    },
                    onSelectedIndexChange = { index ->
                        kdfSelectedIndex = index
                        kdfEngineName = kdfOptions[index]
                    }
                )
                if (kdfEngineName == "AES") {
                    TextField(
                        value = keyRounds,
                        onValueChange = { keyRounds = it },
                        label = "加密轮数（AES-KDF）",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
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
                SwitchPreference(
                    title = "启用压缩",
                    summary = "保存时使用 GZIP 压缩数据库内容",
                    checked = isCompressionEnabled,
                    onCheckedChange = { isCompressionEnabled = it }
                )

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
}
