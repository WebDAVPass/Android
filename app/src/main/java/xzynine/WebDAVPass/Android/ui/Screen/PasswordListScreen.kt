package xzynine.WebDAVPass.Android.ui.Screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import xzynine.WebDAVPass.Android.data.PasswordEntry
import xzynine.WebDAVPass.Android.ui.ViewModel.TokenViewModel
import xzynine.WebDAVPass.Android.ui.component.EntryIcon

/**
 * 条目列表页面（仅显示账号和图标）
 */
@Composable
fun PasswordListScreen(
    tokenViewModel: TokenViewModel,
    onEntryClick: (Long) -> Unit
) {
    val entries by tokenViewModel.passwordEntries.collectAsState(emptyList())

    LaunchedEffect(Unit) {
        tokenViewModel.refreshPasswordEntries()
    }

    Scaffold(
        popupHost = {},
        topBar = {
            TopAppBar(
                title = "密码",
                navigationIcon = {},
                actions = {},
                defaultWindowInsetsPadding = true
            )
        }
    ) {
        if (entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(it),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "暂无条目")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(it),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(entries, key = { it.entryId }) { item ->
                    PasswordEntryCard(item = item, onClick = { onEntryClick(item.entryId) })
                }
            }
        }
    }
}

/**
 * 条目卡片（账号 + 图标）
 */
@Composable
private fun PasswordEntryCard(item: PasswordEntry, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surface),
        pressFeedbackType = PressFeedbackType.Sink,
        showIndication = true,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            EntryIcon(
                primary = item.title,
                secondary = item.account,
                modifier = Modifier.size(32.dp),
                contentDescription = "账号图标"
            )

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = item.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface
                )

                if (item.account.isNotBlank()) {
                    Text(
                        text = item.account,
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                }
            }
        }
    }
}
