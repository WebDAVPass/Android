package xzynine.WebDAVPass.Android.ui.Screen

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.extra.SuperArrow
import xzynine.WebDAVPass.Android.data.PasswordEntry
import xzynine.WebDAVPass.Android.ui.ViewModel.TokenViewModel
import xzynine.WebDAVPass.Android.ui.ViewModel.PasswordFolderIndexLabel
import xzynine.WebDAVPass.Android.ui.ViewModel.toPasswordIndexKey
import xzynine.WebDAVPass.Android.ui.component.AlphabetIndexScrollbar
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
    val passwordGroupStack by tokenViewModel.passwordGroupStack.collectAsState(emptyList())
    val passwordIndexKeys by tokenViewModel.passwordIndexKeys.collectAsState(emptyList())
    val passwordHasMore by tokenViewModel.passwordHasMore.collectAsState(false)
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    BackHandler(enabled = passwordGroupStack.isNotEmpty()) {
        tokenViewModel.navigateUpPasswordGroup(searchQuery)
    }

    val groupedEntries by remember(entries) {
        derivedStateOf {
            entries.groupBy { item -> item.toPasswordIndexKey() }
                .toList()
                .sortedWith(compareBy<Pair<String, List<PasswordEntry>>> { (letter, _) ->
                    when (letter) {
                        PasswordFolderIndexLabel -> 0
                        "#" -> 1
                        else -> 2
                    }
                }.thenBy { (letter, _) ->
                    if (letter == PasswordFolderIndexLabel) "" else letter
                }
                )
        }
    }

    LaunchedEffect(searchQuery) {
        tokenViewModel.refreshPasswordEntries(searchQuery = searchQuery)
    }

    LaunchedEffect(passwordGroupStack, searchQuery) {
        if (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0) {
            listState.scrollToItem(0)
        }
    }

    LaunchedEffect(listState, passwordHasMore) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisibleIndex to layoutInfo.totalItemsCount
        }.collect { (lastVisibleIndex, totalCount) ->
            if (passwordHasMore && totalCount > 0 && lastVisibleIndex >= totalCount - 4) {
                tokenViewModel.loadNextPasswordPage()
            }
        }
    }

    val headerIndexMap by remember(groupedEntries) {
        derivedStateOf {
            buildMap {
                var currentIndex = 0
                groupedEntries.forEach { (letter, itemsInSection) ->
                    put(letter, currentIndex)
                    if (letter == PasswordFolderIndexLabel) {
                        put(FolderIndexBarLabel, currentIndex)
                    }
                    currentIndex += 1 + itemsInSection.size
                }
            }
        }
    }

    val indexLetters by remember(passwordIndexKeys) {
        derivedStateOf {
            passwordIndexKeys.map { letter ->
                if (letter == PasswordFolderIndexLabel) FolderIndexBarLabel else letter
            }
        }
    }

    val enabledIndexLetters by remember(indexLetters) {
        derivedStateOf { indexLetters.toSet() }
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
                }?.first?.let { letter ->
                    if (letter == PasswordFolderIndexLabel) FolderIndexBarLabel else letter
                }
            }
        }
    }

    Scaffold(
        popupHost = {},
        topBar = {
            TopAppBar(
                title = "全部密码",
                navigationIcon = {
                    if (passwordGroupStack.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                tokenViewModel.navigateUpPasswordGroup(searchQuery)
                            }
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = "返回上一级"
                            )
                        }
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
                            item(
                                key = "header_$letter",
                                contentType = "password_section_header"
                            ) {
                                PasswordSectionHeader(letter = letter)
                            }
                            items(
                                items = sectionItems,
                                key = { entry -> entry.entryId },
                                contentType = { entry ->
                                    if (entry.isFolderPlaceholder) "password_folder_item" else "password_entry_item"
                                }
                            ) { item ->
                                PasswordEntryCard(
                                    item = item,
                                    onClick = {
                                        if (item.isFolderPlaceholder) {
                                            tokenViewModel.openPasswordGroup(item.entryId, searchQuery)
                                        } else {
                                            onEntryClick(item.entryId)
                                        }
                                    }
                                )
                            }
                        }
                    }

                    AlphabetIndexScrollbar(
                        letters = indexLetters,
                        enabledLetters = enabledIndexLetters,
                        activeLetter = activeLetter,
                        onLetterSelected = { letter ->
                            coroutineScope.launch {
                                val targetKey = if (letter == FolderIndexBarLabel) {
                                    PasswordFolderIndexLabel
                                } else {
                                    letter
                                }

                                val loaded = tokenViewModel.ensurePasswordIndexLoaded(targetKey)
                                if (!loaded) {
                                    return@launch
                                }

                                val targetIndex = tokenViewModel.getPasswordHeaderScrollIndex(targetKey)
                                    ?: headerIndexMap[letter]
                                    ?: headerIndexMap[targetKey]
                                    ?: return@launch

                                listState.scrollToItem(targetIndex)
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
    SuperArrow(
        title = item.title,
        summary = if (item.account.isNotBlank()) item.account else null,
        startAction = {
            EntryIcon(
                customIconBytes = item.customIconBytes,
                standardIconId = item.standardIconId,
                primary = item.title,
                secondary = item.account,
                modifier = Modifier.size(32.dp),
                contentDescription = "账号图标"
            )
        },
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * 索引栏中用于表示文件夹分组的标记。
 */
private const val FolderIndexBarLabel = "📁"

