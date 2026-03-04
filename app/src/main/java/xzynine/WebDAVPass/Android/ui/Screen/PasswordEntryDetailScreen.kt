package xzynine.WebDAVPass.Android.ui.Screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import xzynine.WebDAVPass.Android.data.RemainingKeyValue
import xzynine.WebDAVPass.Android.data.toDisplayName
import xzynine.WebDAVPass.Android.ui.ViewModel.TokenViewModel
import xzynine.WebDAVPass.Android.ui.component.TokenCard

@Composable
fun PasswordEntryDetailScreen(
    tokenViewModel: TokenViewModel,
    entryId: Long
) {
    val context = LocalContext.current
    val entries by tokenViewModel.passwordEntries.collectAsState(emptyList())
    val tokens by tokenViewModel.tokens.collectAsState(emptyList())
    val currentTimeMillis by tokenViewModel.currentTimeMillis.collectAsState(System.currentTimeMillis())

    val selectedEntry = entries.firstOrNull { it.entryId == entryId }
    val selectedToken = tokens.firstOrNull { it.id == entryId }
    val tokenCode by tokenViewModel.getTokenCode(entryId).collectAsState(null)

    Scaffold(
        popupHost = {},
        topBar = {
            TopAppBar(
                title = selectedEntry?.title ?: "条目详情",
                navigationIcon = {},
                actions = {},
                defaultWindowInsetsPadding = true
            )
        }
    ) { paddingValues ->
        if (selectedEntry == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "条目不存在")
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surface),
                    pressFeedbackType = PressFeedbackType.None,
                    showIndication = false,
                    onClick = {}
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = selectedEntry.title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                        if (selectedEntry.account.isNotBlank()) {
                            Text(
                                text = selectedEntry.account,
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary
                            )
                        }
                    }
                }
            }

            if (selectedToken != null) {
                item {
                    TokenCard(
                        token = selectedToken,
                        tokenCode = tokenCode,
                        currentTimeMillis = currentTimeMillis,
                        onClick = {
                            tokenCode?.let { code ->
                                val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clipData = ClipData.newPlainText("2FA Token", code.code)
                                clipboardManager.setPrimaryClip(clipData)
                                Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (selectedEntry.keyValues.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surface),
                        pressFeedbackType = PressFeedbackType.None,
                        showIndication = false,
                        onClick = {}
                    ) {
                        Text(
                            text = "当前条目暂无可显示键值",
                            modifier = Modifier.padding(14.dp),
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary
                        )
                    }
                }
            }

            items(selectedEntry.keyValues, key = { "${it.fieldName}-${it.rawValue}" }) { item ->
                EntryFieldCard(item = item)
            }
        }
    }
}

@Composable
private fun EntryFieldCard(item: RemainingKeyValue) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surface),
        pressFeedbackType = PressFeedbackType.None,
        showIndication = false,
        onClick = {}
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = item.fieldName,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface
            )
            Text(
                text = "类型：${item.valueType.toDisplayName()}",
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )
            Text(
                text = item.rawValue,
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurface
            )
        }
    }
}
