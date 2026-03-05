package xzynine.WebDAVPass.Android.ui.Screen

import android.net.Uri
import xzylib.base.util.Logger
import xzylib.base.util.ToastUtils
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.WindowDialog
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.CloudFill
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.UploadCloud
import xzynine.WebDAVPass.Android.data.LibraryContext
import xzynine.WebDAVPass.Android.data.LibrarySourceType
import xzynine.WebDAVPass.Android.theme.getAppRoundedCorner
import xzynine.WebDAVPass.Android.ui.Dialog.CloudLibraryDialog
import xzynine.WebDAVPass.Android.ui.Dialog.CloudMode
import xzynine.WebDAVPass.Android.ui.Dialog.CreateMasterPasswordDialog
import xzynine.WebDAVPass.Android.ui.Dialog.CreateMode
import xzynine.WebDAVPass.Android.ui.Dialog.PasswordDialog
import xzynine.WebDAVPass.Android.ui.ViewModel.TokenViewModel



/**
 * 欢迎界面
 */
@Composable
fun WelcomeScreen(
    tokenViewModel: TokenViewModel,
    onEnterLibrary: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val cornerRadius = getAppRoundedCorner()
    val history by tokenViewModel.libraryHistory.collectAsState()
    var showCloudImportDialog by remember { mutableStateOf(false) }
    var showCloudCreateDialog by remember { mutableStateOf(false) }
    var showCreateMasterPasswordDialog by remember { mutableStateOf(false) }
    var createMode by remember { mutableStateOf(CreateMode.LOCAL) }
    var pendingCreateMasterPassword by remember { mutableStateOf("") }
    var pendingUnlockLibrary by remember { mutableStateOf<LibraryContext?>(null) }
    val showUnlockDialog = remember { mutableStateOf(false) }

    fun requestUnlockAndEnter(libraryContext: LibraryContext) {
        pendingUnlockLibrary = libraryContext
        showUnlockDialog.value = true
    }

    val localImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            if (uri == null) {
                return@rememberLauncherForActivityResult
            }
            coroutineScope.launch {
                val path = tokenViewModel.persistKdbxFromUri(uri)
                if (path == null) {
                    ToastUtils.showShortToast(context, "导入失败：无法读取文件")
                    return@launch
                }

                val item = LibraryContext(
                    displayName = path.substringAfterLast('/'),
                    sourceType = LibrarySourceType.LOCAL,
                    localPath = path
                )
                tokenViewModel.openLibraryContext(item)
                requestUnlockAndEnter(item)
            }
        }
    )

    val localCreateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
        onResult = { uri: Uri? ->
            if (uri == null) {
                return@rememberLauncherForActivityResult
            }
            coroutineScope.launch {
                val path = tokenViewModel.createLocalKdbx(uri, pendingCreateMasterPassword)
                if (path == null) {
                    ToastUtils.showShortToast(context, "新建失败：无法创建文件")
                    return@launch
                }

                val item = LibraryContext(
                    displayName = path.substringAfterLast('/'),
                    sourceType = LibrarySourceType.LOCAL,
                    localPath = path
                )
                tokenViewModel.openLibraryContext(item)
                val unlockOk = tokenViewModel.unlockCurrentLibrary(pendingCreateMasterPassword)
                pendingCreateMasterPassword = ""
                if (unlockOk) {
                    onEnterLibrary()
                } else {
                    requestUnlockAndEnter(item)
                }
            }
        }
    )

    Scaffold(
        popupHost = {},
        topBar = {
            TopAppBar(
                title = "欢迎使用 WebDAVPass",
                navigationIcon = {},
                actions = {},
                defaultWindowInsetsPadding = true
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "请选择数据库来源", fontSize = 18.sp)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        localImportLauncher.launch(arrayOf("*/*"))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = MiuixIcons.Download, contentDescription = "本地导入")
                    Text(text = "本地导入")
                }

                Button(
                    onClick = {
                        showCloudImportDialog = true
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = MiuixIcons.CloudFill, contentDescription = "云端导入")
                    Text(text = "云端导入")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        createMode = CreateMode.LOCAL
                        showCreateMasterPasswordDialog = true
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = MiuixIcons.UploadCloud, contentDescription = "本地新建")
                    Text(text = "本地新建")
                }

                Button(
                    onClick = {
                        createMode = CreateMode.CLOUD
                        showCreateMasterPasswordDialog = true
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = MiuixIcons.CloudFill, contentDescription = "云端新建")
                    Text(text = "云端新建")
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "历史库")

            if (history.isEmpty()) {
                Text(text = "暂无历史记录")
            } else {
                history.forEach { item ->
                    SuperArrow(
                        title = item.displayName,
                        summary = if (item.sourceType == LibrarySourceType.CLOUD) {
                            item.remoteFilePath ?: item.remoteBaseUrl.orEmpty()
                        } else {
                            item.localPath
                        },
                        startAction = {
                            Icon(
                                modifier = Modifier.padding(end = 16.dp),
                                imageVector = if (item.sourceType == LibrarySourceType.CLOUD) MiuixIcons.CloudFill else MiuixIcons.Download,
                                contentDescription = "历史库"
                            )
                        },
                        onClick = {
                            coroutineScope.launch {
                                tokenViewModel.switchLibrary(item.id)
                                requestUnlockAndEnter(item)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                    )
                }
            }
        }
    }

    if (showCloudImportDialog) {
        CloudLibraryDialog(
            tokenViewModel = tokenViewModel,
            mode = CloudMode.IMPORT,
            onDismiss = { showCloudImportDialog = false },
            onSelected = { library, _ ->
                coroutineScope.launch {
                    tokenViewModel.openLibraryContext(library)
                    showCloudImportDialog = false
                    requestUnlockAndEnter(library)
                }
            }
        )
    }

    if (showCloudCreateDialog) {
        CloudLibraryDialog(
            tokenViewModel = tokenViewModel,
            mode = CloudMode.CREATE,
            createMasterPassword = pendingCreateMasterPassword,
            onDismiss = { showCloudCreateDialog = false },
            onSelected = { library, createdMasterPassword ->
                coroutineScope.launch {
                    tokenViewModel.openLibraryContext(library)
                    showCloudCreateDialog = false
                    val password = createdMasterPassword.orEmpty()
                    val unlockOk = tokenViewModel.unlockCurrentLibrary(password)
                    pendingCreateMasterPassword = ""
                    if (unlockOk) {
                        onEnterLibrary()
                    } else {
                        requestUnlockAndEnter(library)
                    }
                }
            }
        )
    }

    if (showCreateMasterPasswordDialog) {
        CreateMasterPasswordDialog(
            mode = createMode,
            onDismiss = {
                showCreateMasterPasswordDialog = false
                pendingCreateMasterPassword = ""
            },
            onConfirm = { password ->
                pendingCreateMasterPassword = password
                showCreateMasterPasswordDialog = false
                if (createMode == CreateMode.LOCAL) {
                    localCreateLauncher.launch("WebDavPass.kdbx")
                } else {
                    showCloudCreateDialog = true
                }
            }
        )
    }

    if (showUnlockDialog.value) {
        PasswordDialog(
            title = "解锁数据库",
            summary = pendingUnlockLibrary?.displayName,
            show = showUnlockDialog,
            onDismiss = {
                pendingUnlockLibrary = null
                showUnlockDialog.value = false
            },
            onConfirm = { password ->
                coroutineScope.launch {
                    val ok = tokenViewModel.unlockCurrentLibrary(password)
                    if (ok) {
                        showUnlockDialog.value = false
                        pendingUnlockLibrary = null
                        onEnterLibrary()
                    } else {
                        val message = tokenViewModel.getLastUnlockErrorMessage()
                            ?: "解锁失败：主密码不正确或文件无效"
                        ToastUtils.showShortToast(context, message)
                    }
                }
            },
            confirmButtonText = "解锁"
        )
    }
}



