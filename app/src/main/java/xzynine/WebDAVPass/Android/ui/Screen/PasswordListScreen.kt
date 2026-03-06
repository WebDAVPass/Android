package xzynine.WebDAVPass.Android.ui.Screen

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.mutableStateMapOf
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
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.extra.WindowDialog
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.AddFolder
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.launch
import xzynine.WebDAVPass.Android.data.PasswordEntry
import xzynine.WebDAVPass.Android.data.PasswordEntryEditDraft
import xzynine.WebDAVPass.Android.data.PasswordGroupEditDraft
import xzynine.WebDAVPass.Android.ui.Dialog.ConfirmationDialog
import xzynine.WebDAVPass.Android.ui.ViewModel.TokenViewModel
import xzynine.WebDAVPass.Android.ui.ViewModel.PasswordFolderIndexLabel
import xzynine.WebDAVPass.Android.ui.ViewModel.toPasswordIndexKey
import xzynine.WebDAVPass.Android.ui.component.AlphabetIndexScrollbar
import xzynine.WebDAVPass.Android.ui.component.SelectableEntryCard

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
    title: String,
    emptyStateText: String,
    emptySearchStateText: String,
    enableGroupNavigation: Boolean,
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
    val allowWriteActions = enableGroupNavigation
    val isSelectionMode = remember { mutableStateOf(false) }
    val selectedTargets = remember { mutableStateMapOf<Long, Boolean>() }

    val showCreateEntryDialog = remember { mutableStateOf(false) }
    val showCreateGroupDialog = remember { mutableStateOf(false) }
    val showDeleteDialog = remember { mutableStateOf(false) }

    var createEntryTitle by remember { mutableStateOf("") }
    var createEntryUsername by remember { mutableStateOf("") }
    var createEntryPassword by remember { mutableStateOf("") }
    var createEntryUrl by remember { mutableStateOf("") }
    var createEntryNotes by remember { mutableStateOf("") }

    var createGroupTitle by remember { mutableStateOf("") }
    var createGroupNotes by remember { mutableStateOf("") }

    fun clearSelectionMode() {
        selectedTargets.clear()
        isSelectionMode.value = false
        showDeleteDialog.value = false
    }

    fun toggleSelection(item: PasswordEntry) {
        if (!allowWriteActions) {
            return
        }
        isSelectionMode.value = true
        if (selectedTargets.containsKey(item.entryId)) {
            selectedTargets.remove(item.entryId)
        } else {
            selectedTargets[item.entryId] = item.isFolderPlaceholder
        }
        if (selectedTargets.isEmpty()) {
            isSelectionMode.value = false
        }
    }

    fun setSelection(item: PasswordEntry, checked: Boolean) {
        if (!allowWriteActions) {
            return
        }
        if (checked) {
            isSelectionMode.value = true
            selectedTargets[item.entryId] = item.isFolderPlaceholder
        } else {
            selectedTargets.remove(item.entryId)
            if (selectedTargets.isEmpty()) {
                isSelectionMode.value = false
            }
        }
    }

    BackHandler(enabled = isSelectionMode.value) {
        clearSelectionMode()
    }

    BackHandler(enabled = enableGroupNavigation && passwordGroupStack.isNotEmpty() && !isSelectionMode.value) {
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
                title = title,
                navigationIcon = {
                    if (enableGroupNavigation && passwordGroupStack.isNotEmpty()) {
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
                actions = {
                    if (allowWriteActions) {
                        if (isSelectionMode.value) {
                            IconButton(
                                onClick = {
                                    if (selectedTargets.isNotEmpty()) {
                                        showDeleteDialog.value = true
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Delete,
                                    contentDescription = "删除"
                                )
                            }
                            IconButton(
                                onClick = {
                                    clearSelectionMode()
                                }
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Close,
                                    contentDescription = "取消选择"
                                )
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    showCreateEntryDialog.value = true
                                }
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Add,
                                    contentDescription = "新建条目"
                                )
                            }
                            IconButton(
                                onClick = {
                                    showCreateGroupDialog.value = true
                                }
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.AddFolder,
                                    contentDescription = "新建分组"
                                )
                            }
                        }
                    }
                },
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
                    IconButton(
                        onClick = {
                            searchQuery = ""
                            searchExpanded = false
                            focusManager.clearFocus()
                        },
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Close,
                            contentDescription = "清空搜索"
                        )
                    }
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
                    Text(text = if (searchQuery.isBlank()) emptyStateText else emptySearchStateText)
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
                                SelectableEntryCard(
                                    itemKey = item.entryId,
                                    title = item.title,
                                    summary = item.account.ifBlank { null },
                                    customIconBytes = item.customIconBytes,
                                    standardIconId = item.standardIconId,
                                    iconPrimary = item.title,
                                    iconSecondary = item.account,
                                    isSelectionMode = isSelectionMode.value,
                                    isSelected = selectedTargets.containsKey(item.entryId),
                                    onClick = {
                                        if (enableGroupNavigation && item.isFolderPlaceholder) {
                                            tokenViewModel.openPasswordGroup(item.entryId, searchQuery)
                                        } else {
                                            onEntryClick(item.entryId)
                                        }
                                    },
                                    onLongClick = {
                                        if (!isSelectionMode.value) {
                                            setSelection(item, true)
                                        }
                                    },
                                    onCheckedChange = { checked ->
                                        setSelection(item, checked)
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

    PasswordEntryEditorDialog(
        title = "新建条目",
        show = showCreateEntryDialog,
        entryTitle = createEntryTitle,
        entryUsername = createEntryUsername,
        entryPassword = createEntryPassword,
        entryUrl = createEntryUrl,
        entryNotes = createEntryNotes,
        onEntryTitleChange = { createEntryTitle = it },
        onEntryUsernameChange = { createEntryUsername = it },
        onEntryPasswordChange = { createEntryPassword = it },
        onEntryUrlChange = { createEntryUrl = it },
        onEntryNotesChange = { createEntryNotes = it },
        onDismiss = {
            showCreateEntryDialog.value = false
        },
        onConfirm = {
            coroutineScope.launch {
                val createdId = tokenViewModel.createPasswordEntry(
                    PasswordEntryEditDraft(
                        entryId = null,
                        parentGroupId = passwordGroupStack.lastOrNull(),
                        title = createEntryTitle.trim(),
                        username = createEntryUsername.trim(),
                        password = createEntryPassword,
                        url = createEntryUrl.trim(),
                        notes = createEntryNotes,
                        customFields = emptyList()
                    )
                )
                if (createdId != null) {
                    showCreateEntryDialog.value = false
                    createEntryTitle = ""
                    createEntryUsername = ""
                    createEntryPassword = ""
                    createEntryUrl = ""
                    createEntryNotes = ""
                }
            }
        }
    )

    PasswordGroupEditorDialog(
        title = "新建分组",
        show = showCreateGroupDialog,
        groupTitle = createGroupTitle,
        groupNotes = createGroupNotes,
        onGroupTitleChange = { createGroupTitle = it },
        onGroupNotesChange = { createGroupNotes = it },
        onDismiss = {
            showCreateGroupDialog.value = false
        },
        onConfirm = {
            coroutineScope.launch {
                val createdId = tokenViewModel.createPasswordGroup(
                    PasswordGroupEditDraft(
                        groupId = null,
                        parentGroupId = passwordGroupStack.lastOrNull(),
                        title = createGroupTitle.trim(),
                        notes = createGroupNotes
                    )
                )
                if (createdId != null) {
                    showCreateGroupDialog.value = false
                    createGroupTitle = ""
                    createGroupNotes = ""
                }
            }
        }
    )

    if (allowWriteActions && isSelectionMode.value && selectedTargets.isNotEmpty()) {
        ConfirmationDialog(
            title = "确认删除",
            summary = "已选 ${selectedTargets.size} 项，将移入回收站。",
            show = showDeleteDialog,
            onDismiss = {
                showDeleteDialog.value = false
            },
            confirmButtonText = "删除",
            isDestructive = true,
            onConfirm = {
                coroutineScope.launch {
                    val targets = selectedTargets.toMap()
                    targets.forEach { (entryId, isFolder) ->
                        if (isFolder) {
                            tokenViewModel.deletePasswordGroup(entryId)
                        } else {
                            tokenViewModel.deletePasswordEntry(entryId)
                        }
                    }
                    clearSelectionMode()
                }
            }
        )
    }
}

/**
 * 条目编辑对话框（用于新增）。
 */
@Composable
private fun PasswordEntryEditorDialog(
    title: String,
    show: androidx.compose.runtime.MutableState<Boolean>,
    entryTitle: String,
    entryUsername: String,
    entryPassword: String,
    entryUrl: String,
    entryNotes: String,
    onEntryTitleChange: (String) -> Unit,
    onEntryUsernameChange: (String) -> Unit,
    onEntryPasswordChange: (String) -> Unit,
    onEntryUrlChange: (String) -> Unit,
    onEntryNotesChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    WindowDialog(
        title = title,
        show = show,
        onDismissRequest = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TextField(
                value = entryTitle,
                onValueChange = onEntryTitleChange,
                label = "标题",
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            TextField(
                value = entryUsername,
                onValueChange = onEntryUsernameChange,
                label = "账号",
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            TextField(
                value = entryPassword,
                onValueChange = onEntryPasswordChange,
                label = "密码",
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            TextField(
                value = entryUrl,
                onValueChange = onEntryUrlChange,
                label = "网站",
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            TextField(
                value = entryNotes,
                onValueChange = onEntryNotesChange,
                label = "备注",
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = MiuixIcons.Close,
                        contentDescription = "取消"
                    )
                }
                Button(
                    onClick = onConfirm,
                    enabled = entryTitle.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = MiuixIcons.Ok,
                        contentDescription = "保存"
                    )
                }
            }
        }
    }
}

/**
 * 分组编辑对话框（用于新增与编辑）。
 */
@Composable
private fun PasswordGroupEditorDialog(
    title: String,
    show: androidx.compose.runtime.MutableState<Boolean>,
    groupTitle: String,
    groupNotes: String,
    onGroupTitleChange: (String) -> Unit,
    onGroupNotesChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    WindowDialog(
        title = title,
        show = show,
        onDismissRequest = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TextField(
                value = groupTitle,
                onValueChange = onGroupTitleChange,
                label = "分组标题",
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            TextField(
                value = groupNotes,
                onValueChange = onGroupNotesChange,
                label = "备注",
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = MiuixIcons.Close,
                        contentDescription = "取消"
                    )
                }
                Button(
                    onClick = onConfirm,
                    enabled = groupTitle.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = MiuixIcons.Ok,
                        contentDescription = "保存"
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
 * 索引栏中用于表示文件夹分组的标记。
 */
private const val FolderIndexBarLabel = "📁"

