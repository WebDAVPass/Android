package github.xzynine.two_fas

import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import github.xzynine.two_fas.lib.webdav.Authorization
import github.xzynine.two_fas.lib.webdav.WebDav
import github.xzynine.two_fas.lib.webdav.WebDavFile
import github.xzynine.two_fas.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import top.yukonga.miuix.kmp.basic.*



class FileBrowserActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
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

    top.yukonga.miuix.kmp.basic.Scaffold(
        topBar = {
            top.yukonga.miuix.kmp.basic.TopAppBar(
                title = "WebDAV 文件浏览器",
                navigationIcon = {
                    top.yukonga.miuix.kmp.basic.IconButton(onClick = { navigateUp() }) {
                        top.yukonga.miuix.kmp.basic.Text(text = "←", fontSize = 20.sp)
                    }
                },
                actions = {
                    top.yukonga.miuix.kmp.basic.IconButton(onClick = { 
                        Toast.makeText(context, "上传功能开发中...", Toast.LENGTH_SHORT).show()
                    }) {
                        top.yukonga.miuix.kmp.basic.Text(text = "📤", fontSize = 20.sp)
                    }
                    top.yukonga.miuix.kmp.basic.IconButton(onClick = { loadFileList() }) {
                        top.yukonga.miuix.kmp.basic.Text(text = "🔄", fontSize = 20.sp)
                    }
                }
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    top.yukonga.miuix.kmp.basic.CircularProgressIndicator()
                }
            } else if (errorMessage != null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    top.yukonga.miuix.kmp.basic.Text(text = errorMessage ?: "加载失败")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
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
    top.yukonga.miuix.kmp.basic.Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 使用emoji表示文件夹和文件，更直观
            top.yukonga.miuix.kmp.basic.Text(
                text = if (file.isDir) "📁" else "📄",
                fontSize = 24.sp
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                top.yukonga.miuix.kmp.basic.Text(
                    text = file.displayName,
                    fontSize = 16.sp
                )
                top.yukonga.miuix.kmp.basic.Text(
                    text = if (file.isDir) "文件夹" else formatFileSize(file.size),
                    fontSize = 12.sp
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (file.isDir) {
                top.yukonga.miuix.kmp.basic.Text(text = "→", fontSize = 16.sp)
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

