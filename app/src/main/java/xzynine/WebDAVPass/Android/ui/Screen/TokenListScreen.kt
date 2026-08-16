package xzynine.WebDAVPass.Android.ui.Screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import xzylib.base.util.ToastUtils
import xzynine.WebDAVPass.Android.ui.component.TokenCard
import xzynine.WebDAVPass.Android.ui.viewmodel.TokenViewModel

/**
 * 令牌列表界面
 */
@Composable
fun TokenListScreen(
    tokenViewModel: TokenViewModel,
    onEntryClick: (Long) -> Unit,
) {
    val context = LocalContext.current
    val tokens by tokenViewModel.tokens.collectAsState(emptyList())
    val passwordEntries by tokenViewModel.passwordViewModel.passwordEntries.collectAsState(emptyList())
    val tokenCodeMap by tokenViewModel.tokenCodeSnapshot.collectAsState(emptyMap())
    val currentTimeMillis by tokenViewModel.currentTimeMillis.collectAsState(System.currentTimeMillis())
    val isLoading by tokenViewModel.libraryViewModel.isLoading.collectAsState(false)

    /**
     * 通过稳定 ID 建立条目索引，用于令牌列表复用 KeePass 图标数据。
     */
    val entryIconMap by remember(passwordEntries) {
        derivedStateOf { passwordEntries.associateBy { it.entryId } }
    }

    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
    } else if (tokens.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "暂无令牌，请添加新的2FA令牌",
                color = MiuixTheme.colorScheme.onSurface,
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(
                items = tokens,
                key = { it.id },
                contentType = { "token_item" },
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
                            ToastUtils.showShortToast(context, "已复制到剪贴板")
                        }
                    },
                    onLongClick = {
                        onEntryClick(token.id)
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 0.5.dp,
                )
            }
        }
    }
}
