package xzynine.WebDAVPass.Android.ui.Screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import xzynine.WebDAVPass.Android.data.SecurityIssueEntry
import xzynine.WebDAVPass.Android.ui.ViewModel.TokenViewModel
import xzynine.WebDAVPass.Android.util.DateTimeFormatter
import xzynine.WebDAVPass.Android.util.strengthLabel

/**
 * 安全性检查页：展示已过期条目与弱密码条目，点击可进入条目详情。
 */
@Composable
fun SecurityCheckScreen(
    tokenViewModel: TokenViewModel,
    onNavigateBack: () -> Unit,
    onEntryClick: (Long) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var expiredEntries by remember { mutableStateOf<List<SecurityIssueEntry>>(emptyList()) }
    var weakEntries by remember { mutableStateOf<List<SecurityIssueEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val issues = tokenViewModel.loadSecurityIssues()
        expiredEntries = issues.expiredEntries
        weakEntries = issues.weakPasswordEntries
        loading = false
    }

    Scaffold(
        popupHost = {},
        topBar = {
            TopAppBar(
                title = "安全性检查",
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
                Text(
                    text = "已过期条目（${expiredEntries.size}）",
                    modifier = Modifier.padding(top = 4.dp),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                if (expiredEntries.isEmpty()) {
                    Text(
                        text = "暂无过期条目",
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surface),
                        cornerRadius = 12.dp,
                        pressFeedbackType = PressFeedbackType.None,
                        showIndication = false,
                        onClick = {}
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            expiredEntries.forEachIndexed { index, item ->
                                SecurityIssueRow(
                                    item = item,
                                    onClick = { onEntryClick(item.entryId) }
                                )
                                if (index < expiredEntries.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 14.dp),
                                        thickness = 0.5.dp
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.padding(top = 4.dp))

                Text(
                    text = "弱密码（${weakEntries.size}）",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                if (weakEntries.isEmpty()) {
                    Text(
                        text = "暂无弱密码",
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surface),
                        cornerRadius = 12.dp,
                        pressFeedbackType = PressFeedbackType.None,
                        showIndication = false,
                        onClick = {}
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            weakEntries.forEachIndexed { index, item ->
                                SecurityIssueRow(
                                    item = item,
                                    onClick = { onEntryClick(item.entryId) }
                                )
                                if (index < weakEntries.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 14.dp),
                                        thickness = 0.5.dp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SecurityIssueRow(
    item: SecurityIssueEntry,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (item.expiryTime != null && item.expiryTime!! < System.currentTimeMillis()) {
                MiuixIcons.Delete
            } else {
                MiuixIcons.Lock
            },
            contentDescription = null,
            tint = if (item.expiryTime != null && item.expiryTime!! < System.currentTimeMillis()) {
                MiuixTheme.colorScheme.error
            } else {
                MiuixTheme.colorScheme.onSurfaceSecondary
            }
        )
        Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
            Text(
                text = item.title,
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurface
            )
            Text(
                text = buildString {
                    if (item.account.isNotBlank()) {
                        append(item.account)
                        append(" · ")
                    }
                    if (item.expiryTime != null && item.expiryTime!! > 0L) {
                        append("过期时间 ${DateTimeFormatter.formatLocalDateTime(item.expiryTime)}")
                    }
                    if (item.passwordStrengthBits > 0.0) {
                        if (isNotEmpty()) append(" · ")
                        append("强度 ${strengthLabel(item.passwordStrengthBits)}")
                    }
                },
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )
        }
    }
}
