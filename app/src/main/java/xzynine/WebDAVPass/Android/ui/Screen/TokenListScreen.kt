package xzynine.WebDAVPass.Android.ui.Screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import xzynine.WebDAVPass.Android.data.OtpToken
import xzynine.WebDAVPass.Android.ui.ViewModel.TokenViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import xzynine.WebDAVPass.Android.ui.Dialog.ConfirmationDialog
import xzynine.WebDAVPass.Android.ui.activity.PasswordEntryDetailActivity
import androidx.compose.runtime.LaunchedEffect
import top.yukonga.miuix.kmp.theme.MiuixTheme
import xzynine.WebDAVPass.Android.ui.component.TokenCard

/**
 * 弹窗状态枚举
 */
enum class DialogState { NONE, EDIT, DELETE }

/**
 * 令牌列表界面
 */
@Composable
fun TokenListScreen(tokenViewModel: TokenViewModel, onEntryClick: (Long) -> Unit) {
    val context = LocalContext.current
    val tokens by tokenViewModel.tokens.collectAsState(emptyList())
    val passwordEntries by tokenViewModel.passwordEntries.collectAsState(emptyList())
    val tokenCodeMap by tokenViewModel.tokenCodeSnapshot.collectAsState(emptyMap())
    val currentTimeMillis by tokenViewModel.currentTimeMillis.collectAsState(System.currentTimeMillis())
    val isLoading by tokenViewModel.isLoading.collectAsState(false)

    /**
     * 通过稳定 ID 建立条目索引，用于令牌列表复用 KeePass 图标数据。
     */
    val entryIconMap by remember(passwordEntries) {
        derivedStateOf { passwordEntries.associateBy { it.entryId } }
    }

    // 长按功能状态管理 - 使用枚举确保单例
    var dialogState by remember { mutableStateOf(DialogState.NONE) }
    var selectedToken by remember { mutableStateOf<OtpToken?>(null) }
    
    // 对话框显示状态
    val showEditDialog = remember { mutableStateOf(dialogState == DialogState.EDIT) }
    val showDeleteDialog = remember { mutableStateOf(dialogState == DialogState.DELETE) }
    
    // 当 dialogState 变化时，更新对话框显示状态
    LaunchedEffect(dialogState) {
        showEditDialog.value = dialogState == DialogState.EDIT
        showDeleteDialog.value = dialogState == DialogState.DELETE
    }

    if (isLoading) {
        Box(
            modifier = Modifier.Companion.fillMaxSize(),
            contentAlignment = Alignment.Companion.Center
        ) {
            CircularProgressIndicator()
        }
    } else if (tokens.isEmpty()) {
        Box(
            modifier = Modifier.Companion.fillMaxSize(),
            contentAlignment = Alignment.Companion.Center
        ) {
            Text(
                text = "暂无令牌，请添加新的2FA令牌",
                color = MiuixTheme.colorScheme.onSurface
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.Companion.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(
                items = tokens,
                key = { it.id },
                contentType = { "token_item" }
            ) { token ->
                val tokenCode = tokenCodeMap[token.id]
                val iconEntry = entryIconMap[token.id]

                TokenCard(
                    token = token,
                    tokenCode = tokenCode,
                    currentTimeMillis = currentTimeMillis,
                    customIconBytes = iconEntry?.customIconBytes,
                    standardIconId = iconEntry?.standardIconId,
                    onClick = {
                        tokenCode?.let { code ->
                            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clipData = ClipData.newPlainText("2FA Token", code.code)
                            clipboardManager.setPrimaryClip(clipData)
                            Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onLongClick = {
                        onEntryClick(token.id)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
                HorizontalDivider(
                    modifier = Modifier.Companion.padding(horizontal = 16.dp),
                    thickness = 0.5.dp
                )
            }
        }

        // 编辑令牌功能暂不可用，已移除 TokenDialog
        // 如需编辑令牌，请在密码详情页面进行操作

        // 删除令牌对话框
        selectedToken?.let {
            ConfirmationDialog(
                title = "确认删除",
                summary = "确定要删除令牌 \"${it.issuer ?: it.label}\" 吗？删除后可在最近删除中查看。",
                show = showDeleteDialog,
                onDismiss = {
                    dialogState = DialogState.NONE
                    selectedToken = null
                },
                confirmButtonText = "删除",
                isDestructive = true,
                onConfirm = {
                    tokenViewModel.deleteToken(it.id)
                    dialogState = DialogState.NONE
                    selectedToken = null
                }
            )
        }
    }
}
