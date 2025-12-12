package github.xzynine.two_fas

import android.content.Intent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.rememberCoroutineScope
import github.xzynine.two_fas.lib.webdav.Authorization
import github.xzynine.two_fas.lib.webdav.WebDav
import github.xzynine.two_fas.theme.AppTheme
import top.yukonga.miuix.kmp.basic.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        this.setContent {
            AppTheme {
                WebDavConfigScreen()
            }
        }
    }
}

@Composable
fun WebDavConfigScreen() {
    val context = LocalContext.current
    var serverUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isTesting by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope() // 使用rememberCoroutineScope代替CoroutineScope

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        top.yukonga.miuix.kmp.basic.Text(
            text = "WebDAV 配置",
            fontSize = 24.sp,
            modifier = Modifier.padding(bottom = 24.dp),
            style = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.headlineLarge
        )

        top.yukonga.miuix.kmp.basic.TextField(
            value = serverUrl,
            onValueChange = { serverUrl = it },
            label = { top.yukonga.miuix.kmp.basic.Text("服务器地址") },
            placeholder = { top.yukonga.miuix.kmp.basic.Text("https://example.com/webdav") },
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        )

        top.yukonga.miuix.kmp.basic.TextField(
            value = username,
            onValueChange = { username = it },
            label = { top.yukonga.miuix.kmp.basic.Text("用户名") },
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        )

        top.yukonga.miuix.kmp.basic.TextField(
            value = password,
            onValueChange = { password = it },
            label = { top.yukonga.miuix.kmp.basic.Text("密码") },
            placeholder = { top.yukonga.miuix.kmp.basic.Text("密码") },
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
        )

        top.yukonga.miuix.kmp.basic.Button(
            onClick = {
                if (serverUrl.trim().isEmpty()) {
                    Toast.makeText(context, "请输入服务器地址", Toast.LENGTH_SHORT).show()
                    return@top.yukonga.miuix.kmp.basic.Button
                }
                isTesting = true
                Toast.makeText(context, "正在测试连接...", Toast.LENGTH_SHORT).show()

                // 在协程中测试连接
                coroutineScope.launch {
                    try {
                        val webDav = WebDav(serverUrl, Authorization(username, password))
                        val success = webDav.check()
                        
                        withContext(Dispatchers.Main) {
                            if (success) {
                                Toast.makeText(context, "连接成功", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "连接失败", Toast.LENGTH_SHORT).show()
                            }
                            isTesting = false
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "连接错误: ${e.message}", Toast.LENGTH_SHORT).show()
                            isTesting = false
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            enabled = !isTesting
        ) {
            top.yukonga.miuix.kmp.basic.Text(text = if (isTesting) "测试中..." else "测试连接")
        }

        top.yukonga.miuix.kmp.basic.Button(
            onClick = {
                if (serverUrl.trim().isEmpty()) {
                    Toast.makeText(context, "请输入服务器地址", Toast.LENGTH_SHORT).show()
                    return@top.yukonga.miuix.kmp.basic.Button
                }

                // 创建包含2fas_xzy子目录的URL
                val webdavUrl = if (serverUrl.endsWith("/")) {
                    "${serverUrl}2fas_xzy/"
                } else {
                    "${serverUrl}/2fas_xzy/"
                }

                // 跳转到文件浏览界面
                val intent = Intent(context, FileBrowserActivity::class.java)
                intent.putExtra("SERVER_URL", webdavUrl)
                intent.putExtra("USERNAME", username)
                intent.putExtra("PASSWORD", password)
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isTesting
        ) {
            top.yukonga.miuix.kmp.basic.Text(text = "浏览文件")
        }
    }
}

