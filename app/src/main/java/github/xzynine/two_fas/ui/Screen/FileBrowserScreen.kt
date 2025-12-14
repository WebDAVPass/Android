package github.xzynine.two_fas.ui.Screen

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import github.xzynine.webdav.Authorization
import github.xzynine.webdav.WebDav
import github.xzynine.webdav.WebDavFile
import github.xzynine.two_fas.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.icons.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.icons.useful.Back
import top.yukonga.miuix.kmp.icon.icons.useful.Info
import top.yukonga.miuix.kmp.icon.icons.useful.New
import top.yukonga.miuix.kmp.icon.icons.useful.Refresh

class FileBrowserActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val serverUrl = intent.getStringExtra("SERVER_URL") ?: ""
        val username = intent.getStringExtra("USERNAME") ?: ""
        val password = intent.getStringExtra("PASSWORD") ?: ""

        setContent {
            AppTheme {
                FileBrowserScreen(
                    serverUrl = serverUrl,
                    username = username,
                    password = password
                )
            }
        }
    }
}

/**
 * 文件浏览器界面组件
 * @param serverUrl WebDAV服务器地址
 * @param username 用户名
 * @param password 密码
 */
@Composable
fun FileBrowserScreen(
    serverUrl: String,
    username: String,
    password: String
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope() // 使用rememberCoroutineScope代替CoroutineScope

    var currentPath by remember { mutableStateOf(serverUrl) }
    var fileList by remember { mutableStateOf(emptyList<WebDavFile>()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // 加载文件列表
    fun loadFileList() {
        coroutineScope.launch {
            isLoading = true
            errorMessage = null
            try {
                withContext(Dispatchers.IO) {
                    val webDav = WebDav(currentPath, Authorization(username, password))

                    // 如果目录不存在，创建目录
                    webDav.makeAsDir()

                    // 获取文件列表
                    val files = webDav.listFiles()
                    fileList = files
                }
            } catch (e: Exception) {
                errorMessage = "加载失败: ${e.message}"
                Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
            }
        }
    }

    // 初始加载
    LaunchedEffect(Unit) {
        loadFileList()
    }

    // 返回上一级目录
    fun navigateUp() {
        if (currentPath != serverUrl) {
            val parentPath = currentPath.substring(0, currentPath.lastIndexOf('/')).let {
                if (it.isEmpty()) serverUrl else it
            }
            currentPath = parentPath
            loadFileList()
        }
    }

    // 文件点击处理
    fun onFileClick(file: WebDavFile) {
        if (file.isDir) {
            // 进入子目录
            currentPath = file.path
            loadFileList()
        } else {
            // 下载文件
            Toast.makeText(context, "下载功能开发中...", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "WebDAV 文件浏览器",
                navigationIcon = {
                    IconButton(onClick = { navigateUp() }) {
                        Icon(
                            imageVector = MiuixIcons.Useful.Back,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        Toast.makeText(context, "上传功能开发中...", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(
                            imageVector = MiuixIcons.Useful.New,
                            contentDescription = "上传"
                        )
                    }
                    IconButton(onClick = { loadFileList() }) {
                        Icon(
                            imageVector = MiuixIcons.Useful.Refresh,
                            contentDescription = "刷新"
                        )
                    }
                },
                defaultWindowInsetsPadding = true
            )
        },
    ) {
        Column(
            modifier = Modifier.Companion
                .fillMaxSize()
                .padding(it)
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier.Companion.fillMaxSize(),
                    contentAlignment = Alignment.Companion.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (errorMessage != null) {
                Box(
                    modifier = Modifier.Companion.fillMaxSize(),
                    contentAlignment = Alignment.Companion.Center
                ) {
                    Text(text = errorMessage ?: "加载失败")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.Companion.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp)
                ) {
                    items(fileList) {
                        FileItem(
                            file = it,
                            onClick = { onFileClick(it) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FileItem(
    file: WebDavFile,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.Companion
            .fillMaxWidth()
            .padding(4.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.Companion
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Companion.CenterVertically
        ) {
            // 使用Miuix图标表示文件夹和文件，更直观
            Icon(
                imageVector = if (file.isDir) MiuixIcons.Useful.New else MiuixIcons.Useful.Info,
                contentDescription = if (file.isDir) "文件夹" else "文件",
                modifier = Modifier.Companion.size(24.dp)
            )
            Spacer(modifier = Modifier.Companion.width(16.dp))
            Column(
                modifier = Modifier.Companion.weight(1f)
            ) {
                Text(
                    text = file.displayName,
                    fontSize = 16.sp
                )
                Text(
                    text = if (file.isDir) "文件夹" else formatFileSize(file.size),
                    fontSize = 12.sp
                )
            }
            Spacer(modifier = Modifier.Companion.width(8.dp))
            if (file.isDir) {
                Icon(
                    imageVector = MiuixIcons.Basic.ArrowRight,
                    contentDescription = "进入目录"
                )
            }
        }
    }
}

// 格式化文件大小
fun formatFileSize(size: Long): String {
    if (size < 1024) {
        return "$size B"
    } else if (size < 1024 * 1024) {
        return "${String.format("%.1f", size / 1024.0)} KB"
    } else if (size < 1024 * 1024 * 1024) {
        return "${String.format("%.1f", size / (1024.0 * 1024.0))} MB"
    } else {
        return "${String.format("%.1f", size / (1024.0 * 1024.0 * 1024.0))} GB"
    }
}