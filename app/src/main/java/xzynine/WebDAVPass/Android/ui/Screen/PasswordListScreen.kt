package xzynine.WebDAVPass.Android.ui.Screen

import android.icu.text.Transliterator
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import kotlinx.coroutines.launch
import xzynine.WebDAVPass.Android.data.PasswordEntry
import xzynine.WebDAVPass.Android.ui.ViewModel.TokenViewModel
import xzynine.WebDAVPass.Android.ui.component.AlphabetIndexScrollbar
import xzynine.WebDAVPass.Android.ui.component.DefaultIndexLetters
import xzynine.WebDAVPass.Android.ui.component.EntryIcon

/**
 * 全部密码列表页面。
 *
 * 功能：
 * - 搜索标题/账号
 * - 按首字母分组展示
 * - 右侧字母索引快速跳转
 */
@Composable
fun PasswordListScreen(
    tokenViewModel: TokenViewModel,
    onEntryClick: (Long) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val entries by tokenViewModel.passwordEntries.collectAsState(emptyList())
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        tokenViewModel.refreshPasswordEntries()
    }

    val filteredEntries by remember(entries, searchQuery) {
        derivedStateOf {
            val keyword = searchQuery.trim()
            entries
                .asSequence()
                .filter { item ->
                    keyword.isBlank() ||
                        item.title.contains(keyword, ignoreCase = true) ||
                        item.account.contains(keyword, ignoreCase = true)
                }
                .sortedBy { item ->
                    item.title.ifBlank { item.account }.lowercase()
                }
                .toList()
        }
    }

    val groupedEntries by remember(filteredEntries) {
        derivedStateOf {
            filteredEntries.groupBy { item -> item.toIndexLetter() }
                .toList()
                .sortedBy { (letter, _) ->
                    if (letter == "#") "ZZZ" else letter
                }
        }
    }

    val availableLetters by remember(groupedEntries) {
        derivedStateOf { groupedEntries.map { it.first }.toSet() }
    }

    val headerIndexMap by remember(groupedEntries) {
        derivedStateOf {
            buildMap {
                var currentIndex = 0
                groupedEntries.forEach { (letter, itemsInSection) ->
                    put(letter, currentIndex)
                    currentIndex += 1 + itemsInSection.size
                }
            }
        }
    }

    val activeLetter by remember(listState, groupedEntries) {
        derivedStateOf {
            if (groupedEntries.isEmpty()) {
                null
            } else {
                val visibleItemIndex = listState.firstVisibleItemIndex
                var currentIndex = 0
                groupedEntries.firstOrNull { (_, itemsInSection) ->
                    val sectionStart = currentIndex
                    val sectionEnd = currentIndex + itemsInSection.size
                    currentIndex = sectionEnd + 1
                    visibleItemIndex in sectionStart..sectionEnd
                }?.first
            }
        }
    }

    Scaffold(
        popupHost = {},
        topBar = {
            TopAppBar(
                title = "全部密码",
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
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            SearchBar(
                modifier = Modifier.fillMaxWidth(),
                inputField = {
                    InputField(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onSearch = {
                            searchExpanded = false
                            focusManager.clearFocus()
                        },
                        expanded = searchExpanded,
                        onExpandedChange = { searchExpanded = it },
                        label = "搜索"
                    )
                },
                expanded = searchExpanded,
                onExpandedChange = {
                    searchExpanded = it
                    if (!it) {
                        focusManager.clearFocus()
                    }
                },
                outsideEndAction = {
                    Text(
                        text = "清空",
                        color = MiuixTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .clickable {
                                searchQuery = ""
                                searchExpanded = false
                                focusManager.clearFocus()
                            }
                    )
                }
            ) {
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (groupedEntries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = if (searchQuery.isBlank()) "暂无条目" else "无匹配条目")
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 0.dp, end = 40.dp, top = 4.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        groupedEntries.forEach { (letter, sectionItems) ->
                            item(key = "header_$letter") {
                                PasswordSectionHeader(letter = letter)
                            }
                            items(sectionItems, key = { entry -> entry.entryId }) { item ->
                                PasswordEntryCard(item = item, onClick = { onEntryClick(item.entryId) })
                            }
                        }
                    }

                    AlphabetIndexScrollbar(
                        letters = DefaultIndexLetters,
                        enabledLetters = availableLetters,
                        activeLetter = activeLetter,
                        onLetterSelected = { letter ->
                            headerIndexMap[letter]?.let { targetIndex ->
                                coroutineScope.launch {
                                    listState.animateScrollToItem(targetIndex)
                                }
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(bottom = 8.dp)
                    )
                }
            }
        }
    }
}

/**
 * 条目分组标题。
 */
@Composable
private fun PasswordSectionHeader(letter: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp)
    ) {
        Text(
            text = letter,
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurfaceSecondary,
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp)
        )
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

/**
 * 计算条目用于分组和索引的首字母。
 *
 * 规则：
 * - 优先取标题，其次账号
 * - 中文会先转拼音再取首字母
 * - 非 A-Z 归类为 #
 */
private fun PasswordEntry.toIndexLetter(): String {
    val source = title.ifBlank { account }.trim()
    if (source.isBlank()) return "#"

    val transformed = HanToLatinTransliterator.transliterate(source)
    val first = transformed.firstOrNull { ch -> ch.isLetterOrDigit() } ?: return "#"
    val upper = first.uppercaseChar()
    return if (upper in 'A'..'Z') upper.toString() else "#"
}

/**
 * 中文转拉丁的转换器，用于首字母分组。
 */
private val HanToLatinTransliterator: Transliterator by lazy {
    Transliterator.getInstance("Han-Latin; Latin-ASCII")
}
