package xzynine.WebDAVPass.Android.ui.Screen

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TooltipBox
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.AddFolder
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.MoreCircle
import top.yukonga.miuix.kmp.icon.extended.SelectAll
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.icon.extended.Undo
import top.yukonga.miuix.kmp.menu.WindowIconDropdownMenu
import top.yukonga.miuix.kmp.theme.MiuixTheme
import xzynine.WebDAVPass.Android.data.PasswordEntry
import xzynine.WebDAVPass.Android.ui.component.AlphabetIndexScrollbar
import xzynine.WebDAVPass.Android.ui.component.SelectableEntryCard
import xzynine.WebDAVPass.Android.ui.viewmodel.PasswordFolderIndexLabel
import xzynine.WebDAVPass.Android.ui.viewmodel.PasswordSortMode
import xzynine.WebDAVPass.Android.ui.viewmodel.TokenViewModel

@Composable
fun PasswordListScreenContent(
    modifier: Modifier = Modifier,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchExpanded: Boolean,
    onSearchExpandedChange: (Boolean) -> Unit,
    searchCaseSensitive: Boolean,
    onSearchCaseSensitiveChange: (Boolean) -> Unit,
    focusManager: FocusManager,
    groupedEntries: List<Pair<String, List<PasswordEntry>>>,
    listState: LazyListState,
    emptyStateText: String,
    emptySearchStateText: String,
    enableGroupNavigation: Boolean,
    tokenViewModel: TokenViewModel,
    onEntryClick: (Long) -> Unit,
    isSelectionMode: Boolean,
    selectedTargets: Map<Long, Boolean>,
    onItemLongClick: (PasswordEntry) -> Unit,
    onItemCheckedChange: (PasswordEntry, Boolean) -> Unit,
    indexLetters: List<String>,
    enabledIndexLetters: Set<String>,
    activeLetter: String?,
    sectionBoundaries: List<Pair<Int, String>>,
    passwordIndexKeys: List<String>,
    coroutineScope: CoroutineScope,
    context: Context,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        SearchBar(
            modifier = Modifier.fillMaxWidth(),
            inputField = {
                InputField(
                    query = searchQuery,
                    onQueryChange = { onSearchQueryChange(it) },
                    onSearch = {
                        onSearchExpandedChange(false)
                        focusManager.clearFocus()
                    },
                    expanded = searchExpanded,
                    onExpandedChange = { onSearchExpandedChange(it) },
                    label = "搜索",
                )
            },
            expanded = searchExpanded,
            onExpandedChange = {
                onSearchExpandedChange(it)
                if (!it) {
                    focusManager.clearFocus()
                }
            },
            outsideEndAction = {
                IconButton(
                    onClick = {
                        onSearchCaseSensitiveChange(!searchCaseSensitive)
                    },
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    Text(
                        text = "Aa",
                        fontWeight = FontWeight.Bold,
                        color =
                            if (searchCaseSensitive) {
                                MiuixTheme.colorScheme.primary
                            } else {
                                MiuixTheme.colorScheme.onSurface
                            },
                    )
                }
            },
        ) {
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (groupedEntries.isEmpty()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = if (searchQuery.isBlank()) emptyStateText else emptySearchStateText)
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 0.dp, end = 40.dp, top = 4.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    groupedEntries.forEach { (letter, sectionItems) ->
                        item(
                            key = "header_$letter",
                            contentType = "password_section_header",
                        ) {
                            PasswordSectionHeader(letter = letter)
                        }
                        items(
                            items = sectionItems,
                            key = { entry -> entry.entryId },
                            contentType = { entry ->
                                if (entry.isFolderPlaceholder) "password_folder_item" else "password_entry_item"
                            },
                        ) { item ->
                            SelectableEntryCard(
                                itemKey = item.entryId,
                                title = item.title,
                                summary = item.account.ifBlank { null },
                                customIconBytes = item.customIconBytes,
                                standardIconId = item.standardIconId,
                                iconPrimary = item.title,
                                iconSecondary = item.account,
                                isSelectionMode = isSelectionMode,
                                isSelected = selectedTargets.containsKey(item.entryId),
                                onClick = {
                                    if (enableGroupNavigation && item.isFolderPlaceholder) {
                                        tokenViewModel.passwordViewModel.openPasswordGroup(item.entryId, searchQuery)
                                    } else {
                                        onEntryClick(item.entryId)
                                    }
                                },
                                onLongClick = {
                                    if (!isSelectionMode) {
                                        onItemLongClick(item)
                                    }
                                },
                                onCheckedChange = { checked ->
                                    onItemCheckedChange(item, checked)
                                },
                            )
                        }
                    }
                }

                // 去抖 + 即时粗略滚动（解耦索引与实际组加载），提高拖动响应性
                var indexEnsureJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
                AlphabetIndexScrollbar(
                    context = context,
                    letters = indexLetters,
                    enabledLetters = enabledIndexLetters,
                    activeLetter = activeLetter,
                    onLetterSelected = { letter ->
                        val targetKey = if (letter == FolderIndexBarLabel) PasswordFolderIndexLabel else letter

                        // 立即给出粗略反馈：若目标分组已加载则跳到分组标题，否则按分组比例跳到当前已加载区域的近似位置
                        coroutineScope.launch {
                            val immediateIndex =
                                sectionBoundaries.firstOrNull { it.second == targetKey }?.first
                                    ?: run {
                                        val headerPos = passwordIndexKeys.indexOf(targetKey)
                                        val headerCount = passwordIndexKeys.size.coerceAtLeast(1)
                                        val loadedCount = listState.layoutInfo.totalItemsCount
                                        if (loadedCount <= 0) 0 else (loadedCount * headerPos / headerCount).coerceIn(0, loadedCount - 1)
                                    }
                            listState.scrollToItem(immediateIndex)
                        }

                        // 去抖：等待短暂静止后再触发真实加载与精确跳转
                        indexEnsureJob?.cancel()
                        indexEnsureJob =
                            coroutineScope.launch {
                                kotlinx.coroutines.delay(120L)

                                // 后台确保目标分组被加载（SubViewModel 已将重计算移动到 IO 调度器）
                                val loaded = tokenViewModel.passwordViewModel.ensurePasswordIndexLoaded(targetKey)
                                if (!loaded) return@launch

                                val targetIndex =
                                    tokenViewModel.passwordViewModel.getPasswordHeaderScrollIndex(targetKey)
                                        ?: return@launch

                                // 等待 LazyColumn totalItemsCount 覆盖目标下标后再精确滚动
                                snapshotFlow { listState.layoutInfo.totalItemsCount }
                                    .first { count -> count > targetIndex }

                                listState.scrollToItem(targetIndex)
                            }
                    },
                    modifier =
                        Modifier
                            .align(Alignment.CenterEnd)
                            .padding(bottom = 8.dp),
                )
            }
        }
    }
}

@Composable
fun RowScope.PasswordListTopBarActions(
    isSelectionMode: Boolean,
    selectedTargets: Map<Long, Boolean>,
    enableRecycleBinActions: Boolean,
    allowWriteActions: Boolean,
    enableGroupNavigation: Boolean,
    sortMode: PasswordSortMode,
    onSortModeChange: (PasswordSortMode) -> Unit,
    sortAscending: Boolean,
    onSortAscendingChange: (Boolean) -> Unit,
    hideExpired: Boolean,
    onHideExpiredChange: (Boolean) -> Unit,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onRestoreSelected: () -> Unit,
    onRequestPermanentDelete: () -> Unit,
    onRequestDelete: () -> Unit,
    onSolidifyBrandIcons: () -> Unit,
    onMergeSelection: () -> Unit,
    onMoveSelection: () -> Unit,
    onCopySelection: () -> Unit,
    onScanDuplicates: () -> Unit,
    onCreateEntry: () -> Unit,
    onCreateGroup: () -> Unit,
) {
    if (isSelectionMode) {
        TooltipBox(text = "全选") {
            IconButton(
                onClick = {
                    onSelectAll()
                },
            ) {
                Icon(
                    imageVector = MiuixIcons.SelectAll,
                    contentDescription = "全选",
                )
            }
        }
        TooltipBox(text = "反选") {
            IconButton(
                onClick = {
                    onInvertSelection()
                },
            ) {
                Icon(
                    imageVector = Icons.Rounded.Flip,
                    contentDescription = "反选",
                )
            }
        }
        if (enableRecycleBinActions) {
            TooltipBox(text = "恢复") {
                IconButton(
                    onClick = {
                        if (selectedTargets.isNotEmpty()) {
                            onRestoreSelected()
                        }
                    },
                ) {
                    Icon(
                        imageVector = MiuixIcons.Undo,
                        contentDescription = "恢复",
                    )
                }
            }
            TooltipBox(text = "永久删除") {
                IconButton(
                    onClick = {
                        if (selectedTargets.isNotEmpty()) {
                            onRequestPermanentDelete()
                        }
                    },
                ) {
                    Icon(
                        imageVector = MiuixIcons.Delete,
                        contentDescription = "永久删除",
                    )
                }
            }
        } else if (allowWriteActions) {
            TooltipBox(text = "删除") {
                IconButton(
                    onClick = {
                        if (selectedTargets.isNotEmpty()) {
                            onRequestDelete()
                        }
                    },
                ) {
                    Icon(
                        imageVector = MiuixIcons.Delete,
                        contentDescription = "删除",
                    )
                }
            }
            TooltipBox(text = "更多操作") {
                WindowIconDropdownMenu(
                    entries =
                        listOf(
                            DropdownEntry(
                                items =
                                    listOf(
                                        DropdownItem(
                                            text = "固化为品牌图标",
                                            onClick = { onSolidifyBrandIcons() },
                                        ),
                                    ),
                            ),
                            DropdownEntry(
                                items =
                                    listOf(
                                        DropdownItem(
                                            text = "合并条目",
                                            onClick = { onMergeSelection() },
                                        ),
                                    ),
                            ),
                            DropdownEntry(
                                items =
                                    listOf(
                                        DropdownItem(
                                            text = "移动",
                                            onClick = {
                                                if (selectedTargets.isNotEmpty()) {
                                                    onMoveSelection()
                                                }
                                            },
                                        ),
                                        DropdownItem(
                                            text = "复制",
                                            onClick = {
                                                if (selectedTargets.isNotEmpty()) {
                                                    onCopySelection()
                                                }
                                            },
                                        ),
                                    ),
                            ),
                        ),
                    collapseOnSelection = true,
                ) {
                    Icon(
                        imageVector = MiuixIcons.MoreCircle,
                        contentDescription = "更多操作",
                    )
                }
            }
        }
    } else {
        TooltipBox(text = "排序与过滤") {
            WindowIconDropdownMenu(
                entries =
                    listOf(
                        DropdownEntry(
                            items =
                                listOf(
                                    DropdownItem(
                                        text = "默认",
                                        selected = sortMode == PasswordSortMode.DEFAULT,
                                        onClick = { onSortModeChange(PasswordSortMode.DEFAULT) },
                                    ),
                                    DropdownItem(
                                        text = "标题",
                                        selected = sortMode == PasswordSortMode.TITLE,
                                        onClick = { onSortModeChange(PasswordSortMode.TITLE) },
                                    ),
                                    DropdownItem(
                                        text = "账号",
                                        selected = sortMode == PasswordSortMode.ACCOUNT,
                                        onClick = { onSortModeChange(PasswordSortMode.ACCOUNT) },
                                    ),
                                    DropdownItem(
                                        text = "修改时间",
                                        selected = sortMode == PasswordSortMode.MODIFIED_TIME,
                                        onClick = { onSortModeChange(PasswordSortMode.MODIFIED_TIME) },
                                    ),
                                    DropdownItem(
                                        text = "创建时间",
                                        selected = sortMode == PasswordSortMode.CREATED_TIME,
                                        onClick = { onSortModeChange(PasswordSortMode.CREATED_TIME) },
                                    ),
                                ),
                        ),
                        DropdownEntry(
                            items =
                                listOf(
                                    DropdownItem(
                                        text = "升序",
                                        selected = sortAscending,
                                        onClick = { onSortAscendingChange(true) },
                                    ),
                                    DropdownItem(
                                        text = "降序",
                                        selected = !sortAscending,
                                        onClick = { onSortAscendingChange(false) },
                                    ),
                                ),
                        ),
                        DropdownEntry(
                            items =
                                listOf(
                                    DropdownItem(
                                        text = "隐藏过期条目",
                                        selected = hideExpired,
                                        onClick = { onHideExpiredChange(!hideExpired) },
                                    ),
                                ),
                        ),
                    ),
                collapseOnSelection = false,
            ) {
                Icon(
                    imageVector = MiuixIcons.Sort,
                    contentDescription = "排序与过滤",
                )
            }
        }
        TooltipBox(text = "更多操作") {
            WindowIconDropdownMenu(
                entries =
                    listOf(
                        DropdownEntry(
                            items =
                                listOf(
                                    DropdownItem(
                                        text = "检测重复条目",
                                        onClick = { onScanDuplicates() },
                                    ),
                                ),
                        ),
                    ),
                collapseOnSelection = true,
            ) {
                Icon(
                    imageVector = MiuixIcons.MoreCircle,
                    contentDescription = "更多操作",
                )
            }
        }
        if (enableGroupNavigation) {
            TooltipBox(text = "新建条目") {
                IconButton(
                    onClick = {
                        onCreateEntry()
                    },
                ) {
                    Icon(
                        imageVector = MiuixIcons.Add,
                        contentDescription = "新建条目",
                    )
                }
            }
            TooltipBox(text = "新建分组") {
                IconButton(
                    onClick = {
                        onCreateGroup()
                    },
                ) {
                    Icon(
                        imageVector = MiuixIcons.AddFolder,
                        contentDescription = "新建分组",
                    )
                }
            }
        }
    }
}
