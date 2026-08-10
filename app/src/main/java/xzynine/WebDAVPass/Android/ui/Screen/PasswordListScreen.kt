package xzynine.WebDAVPass.Android.ui.Screen

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.documentfile.provider.DocumentFile
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
import kotlinx.coroutines.flow.first
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
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.window.WindowDialog
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.menu.WindowIconDropdownMenu
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.AddFolder
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.MoveFile
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.icon.extended.Undo
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import xzynine.WebDAVPass.Android.data.PasswordEntry
import xzynine.WebDAVPass.Android.data.PasswordEntryEditDraft
import xzynine.WebDAVPass.Android.data.EditableAttachmentDraft
import xzynine.WebDAVPass.Android.data.EditableFieldDraft
import xzynine.WebDAVPass.Android.data.RemainingValueType
import xzynine.WebDAVPass.Android.data.GroupNodeInfo
import xzynine.WebDAVPass.Android.data.PasswordGroupEditDraft
import xzynine.WebDAVPass.Android.ui.Dialog.ConfirmationDialog
import xzynine.WebDAVPass.Android.ui.Dialog.GroupPickerDialog
import xzynine.WebDAVPass.Android.ui.Dialog.IconPickerDialog
import xzynine.WebDAVPass.Android.ui.Dialog.TemplatePickerDialog
import com.kunzisoft.keepass.database.element.template.Template
import com.kunzisoft.keepass.database.element.template.TemplateEngine
import xzynine.WebDAVPass.Android.ui.ViewModel.TokenViewModel
import xzynine.WebDAVPass.Android.ui.ViewModel.PasswordFolderIndexLabel
import xzynine.WebDAVPass.Android.ui.ViewModel.PasswordSortMode
import xzynine.WebDAVPass.Android.ui.ViewModel.toPasswordIndexKey
import androidx.compose.ui.platform.LocalContext
import xzynine.WebDAVPass.Android.ui.component.AlphabetIndexScrollbar
import xzynine.WebDAVPass.Android.ui.component.SelectableEntryCard
import xzynine.WebDAVPass.Android.ui.component.EntryIcon
import xzylib.base.util.ToastUtils

/** 附件导入大小上限（与仓库 SMALL_BINARY_SIZE 一致），防止无界读入内存导致 OOM。 */
private const val MAX_ATTACHMENT_BYTES = 1024 * 1024

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
    enableRecycleBinActions: Boolean = false,
    onEntryClick: (Long) -> Unit,
    onNavigateBack: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val entries by tokenViewModel.passwordViewModel.passwordEntries.collectAsState(emptyList())
    val passwordGroupStack by tokenViewModel.passwordViewModel.passwordGroupStack.collectAsState(emptyList())
    val passwordIndexKeys by tokenViewModel.passwordViewModel.passwordIndexKeys.collectAsState(emptyList())
    val passwordHasMore by tokenViewModel.passwordViewModel.passwordHasMore.collectAsState(false)
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var searchCaseSensitive by rememberSaveable { mutableStateOf(false) }
    var sortModeOrdinal by rememberSaveable { mutableStateOf(PasswordSortMode.DEFAULT.ordinal) }
    var sortAscending by rememberSaveable { mutableStateOf(true) }
    var hideExpired by rememberSaveable { mutableStateOf(false) }
    val sortMode = PasswordSortMode.entries.getOrElse(sortModeOrdinal) { PasswordSortMode.DEFAULT }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val allowWriteActions = enableGroupNavigation || enableRecycleBinActions
    val isSelectionMode = remember { mutableStateOf(false) }
    val selectedTargets = remember { mutableStateMapOf<Long, Boolean>() }

    val showCreateEntryDialog = remember { mutableStateOf(false) }
    val showCreateGroupDialog = remember { mutableStateOf(false) }
    val showDeleteDialog = remember { mutableStateOf(false) }
    val showPermanentDeleteDialog = remember { mutableStateOf(false) }
    val showGroupPicker = remember { mutableStateOf(false) }
    var groupPickerIsMove by remember { mutableStateOf(false) }
    var pickerGroups by remember { mutableStateOf<List<GroupNodeInfo>>(emptyList()) }

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
        showPermanentDeleteDialog.value = false
    }

    fun restoreSelectedEntries() {
        coroutineScope.launch {
            val targets = selectedTargets.keys.toList()
            if (targets.isEmpty()) {
                return@launch
            }
            val restored = tokenViewModel.restoreRecentDeletedPasswordEntries(targets)
            if (restored > 0) {
                ToastUtils.showShortToast(context, "已恢复 $restored 项")
            } else {
                ToastUtils.showShortToast(context, "恢复失败")
            }
            clearSelectionMode()
        }
    }

    fun openGroupPicker(isMove: Boolean) {
        groupPickerIsMove = isMove
        coroutineScope.launch {
            pickerGroups = tokenViewModel.loadAllPasswordGroups()
            showGroupPicker.value = true
        }
    }

    fun moveOrCopySelectedEntries(targetGroupId: Long?) {
        showGroupPicker.value = false
        coroutineScope.launch {
            val entryIds = selectedTargets.keys.filter { selectedTargets[it] == false }
            val groupIds = selectedTargets.keys.filter { selectedTargets[it] == true }
            val count = if (groupPickerIsMove) {
                tokenViewModel.movePasswordTargets(entryIds, groupIds, targetGroupId)
            } else {
                tokenViewModel.copyPasswordTargets(entryIds, groupIds, targetGroupId)
            }
            if (count > 0) {
                ToastUtils.showShortToast(
                    context,
                    if (groupPickerIsMove) "已移动 $count 项" else "已复制 $count 项"
                )
            } else {
                ToastUtils.showShortToast(context, if (groupPickerIsMove) "移动失败" else "复制失败")
            }
            clearSelectionMode()
        }
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
        tokenViewModel.passwordViewModel.navigateUpPasswordGroup(searchQuery)
    }

    // entries 已由 ViewModel 层排好 section 顺序，直接线性扫描 O(n) 即可，无需重新 groupBy + sortedWith
    val groupedEntries by remember(entries) {
        derivedStateOf {
            if (entries.isEmpty()) {
                emptyList()
            } else {
                val result = mutableListOf<Pair<String, MutableList<PasswordEntry>>>()
                entries.forEach { entry ->
                    val key = entry.toPasswordIndexKey()
                    if (result.isEmpty() || result.last().first != key) {
                        result.add(key to mutableListOf(entry))
                    } else {
                        result.last().second.add(entry)
                    }
                }
                result.map { (k, v) -> k to (v as List<PasswordEntry>) }
            }
        }
    }

    // 首次进入且搜索为空时跳过刷新（reloadInitialPasswordData 已完成加载）；
    // 后续任何搜索/排序/过滤变化均统一在此触发刷新
    val isFirstComposition = remember { mutableStateOf(true) }
    LaunchedEffect(searchQuery, searchCaseSensitive, sortModeOrdinal, sortAscending, hideExpired) {
        if (isFirstComposition.value && searchQuery.isBlank()) {
            isFirstComposition.value = false
            return@LaunchedEffect
        }
        isFirstComposition.value = false
        tokenViewModel.passwordViewModel.refreshPasswordEntries(
            searchQuery = searchQuery,
            caseSensitive = searchCaseSensitive,
            sortMode = sortMode,
            ascending = sortAscending,
            hideExpired = hideExpired
        )
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
            // 提前 15 项开始预加载，给 IO 线程留出充裕时间，减少滚动到底部时的加载卡顿
            if (passwordHasMore && totalCount > 0 && lastVisibleIndex >= totalCount - 15) {
                tokenViewModel.passwordViewModel.loadNextPasswordPage()
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

    // groupedEntries 改变时才重新计算各分区标题的起始下标（含标题行本身）
    // data: List<Pair<startIndex, letter>>，用于 activeLetter 的 O(n) 定位
    val sectionBoundaries = remember(groupedEntries) {
        var idx = 0
        groupedEntries.map { (letter, items) ->
            val start = idx
            idx += 1 + items.size  // 1 个标题行 + N 个条目行
            start to letter
        }
    }

    // activeLetter：每帧只做整数比较，不再访问 groupedEntries 内部结构
    val activeLetter by remember(listState, sectionBoundaries) {
        derivedStateOf {
            val v = listState.firstVisibleItemIndex
            sectionBoundaries.lastOrNull { (start, _) -> v >= start }
                ?.second
                ?.let { letter -> if (letter == PasswordFolderIndexLabel) FolderIndexBarLabel else letter }
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
                                tokenViewModel.passwordViewModel.navigateUpPasswordGroup(searchQuery)
                            }
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = "返回上一级"
                            )
                        }
                    } else {
                        IconButton(
                            onClick = onNavigateBack
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = "返回"
                            )
                        }
                    }
                },
                actions = {
                    if (isSelectionMode.value) {
                        if (enableRecycleBinActions) {
                            IconButton(
                                onClick = {
                                    if (selectedTargets.isNotEmpty()) {
                                        restoreSelectedEntries()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Undo,
                                    contentDescription = "恢复"
                                )
                            }
                            IconButton(
                                onClick = {
                                    if (selectedTargets.isNotEmpty()) {
                                        showPermanentDeleteDialog.value = true
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Delete,
                                    contentDescription = "永久删除"
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
                        } else if (allowWriteActions) {
                            IconButton(
                                onClick = {
                                    if (selectedTargets.isNotEmpty()) {
                                        openGroupPicker(isMove = true)
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.MoveFile,
                                    contentDescription = "移动"
                                )
                            }
                            IconButton(
                                onClick = {
                                    if (selectedTargets.isNotEmpty()) {
                                        openGroupPicker(isMove = false)
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Copy,
                                    contentDescription = "复制"
                                )
                            }
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
                        }
                    } else {
                        WindowIconDropdownMenu(
                            entries = listOf(
                                DropdownEntry(
                                    items = listOf(
                                        DropdownItem(
                                            text = "默认",
                                            selected = sortMode == PasswordSortMode.DEFAULT,
                                            onClick = { sortModeOrdinal = PasswordSortMode.DEFAULT.ordinal }
                                        ),
                                        DropdownItem(
                                            text = "标题",
                                            selected = sortMode == PasswordSortMode.TITLE,
                                            onClick = { sortModeOrdinal = PasswordSortMode.TITLE.ordinal }
                                        ),
                                        DropdownItem(
                                            text = "账号",
                                            selected = sortMode == PasswordSortMode.ACCOUNT,
                                            onClick = { sortModeOrdinal = PasswordSortMode.ACCOUNT.ordinal }
                                        ),
                                        DropdownItem(
                                            text = "修改时间",
                                            selected = sortMode == PasswordSortMode.MODIFIED_TIME,
                                            onClick = { sortModeOrdinal = PasswordSortMode.MODIFIED_TIME.ordinal }
                                        ),
                                        DropdownItem(
                                            text = "创建时间",
                                            selected = sortMode == PasswordSortMode.CREATED_TIME,
                                            onClick = { sortModeOrdinal = PasswordSortMode.CREATED_TIME.ordinal }
                                        )
                                    )
                                ),
                                DropdownEntry(
                                    items = listOf(
                                        DropdownItem(
                                            text = "升序",
                                            selected = sortAscending,
                                            onClick = { sortAscending = true }
                                        ),
                                        DropdownItem(
                                            text = "降序",
                                            selected = !sortAscending,
                                            onClick = { sortAscending = false }
                                        )
                                    )
                                ),
                                DropdownEntry(
                                    items = listOf(
                                        DropdownItem(
                                            text = "隐藏过期条目",
                                            selected = hideExpired,
                                            onClick = { hideExpired = !hideExpired }
                                        )
                                    )
                                )
                            ),
                            collapseOnSelection = false
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Sort,
                                contentDescription = "排序与过滤"
                            )
                        }
                        if (enableGroupNavigation) {
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
                            searchCaseSensitive = !searchCaseSensitive
                        },
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(
                            text = "Aa",
                            fontWeight = FontWeight.Bold,
                            color = if (searchCaseSensitive) {
                                MiuixTheme.colorScheme.primary
                            } else {
                                MiuixTheme.colorScheme.onSurface
                            }
                        )
                    }
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
                                            tokenViewModel.passwordViewModel.openPasswordGroup(item.entryId, searchQuery)
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
                                val immediateIndex = sectionBoundaries.firstOrNull { it.second == targetKey }?.first
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
                            indexEnsureJob = coroutineScope.launch {
                                kotlinx.coroutines.delay(120L)

                                // 后台确保目标分组被加载（SubViewModel 已将重计算移动到 IO 调度器）
                                val loaded = tokenViewModel.passwordViewModel.ensurePasswordIndexLoaded(targetKey)
                                if (!loaded) return@launch

                                val targetIndex = tokenViewModel.passwordViewModel.getPasswordHeaderScrollIndex(targetKey)
                                    ?: return@launch

                                // 等待 LazyColumn totalItemsCount 覆盖目标下标后再精确滚动
                                snapshotFlow { listState.layoutInfo.totalItemsCount }
                                    .first { count -> count > targetIndex }

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
        initialDraft = PasswordEntryEditDraft(
            title = createEntryTitle,
            username = createEntryUsername,
            password = createEntryPassword,
            url = createEntryUrl,
            notes = createEntryNotes
        ),
        onDismiss = {
            showCreateEntryDialog.value = false
        },
        onConfirm = { draft ->
            coroutineScope.launch {
                val createdId = tokenViewModel.createPasswordEntry(
                    draft.copy(
                        entryId = null,
                        parentGroupId = passwordGroupStack.lastOrNull()
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

    if (allowWriteActions && !enableRecycleBinActions && isSelectionMode.value && selectedTargets.isNotEmpty()) {
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

    if (enableRecycleBinActions && isSelectionMode.value && selectedTargets.isNotEmpty()) {
        ConfirmationDialog(
            title = "确认永久删除",
            summary = "已选 ${selectedTargets.size} 项将从回收站永久删除，且不可恢复。",
            show = showPermanentDeleteDialog,
            onDismiss = {
                showPermanentDeleteDialog.value = false
            },
            confirmButtonText = "永久删除",
            isDestructive = true,
            onConfirm = {
                coroutineScope.launch {
                    val targets = selectedTargets.keys.toList()
                    val deleted = tokenViewModel.permanentlyDeleteRecentDeletedPasswordEntries(targets)
                    if (deleted > 0) {
                        ToastUtils.showShortToast(context, "已永久删除 $deleted 项")
                    } else {
                        ToastUtils.showShortToast(context, "删除失败")
                    }
                    clearSelectionMode()
                }
            }
        )
    }

    GroupPickerDialog(
        title = if (groupPickerIsMove) "移动到分组" else "复制到分组",
        show = showGroupPicker.value,
        groups = pickerGroups,
        onDismiss = { showGroupPicker.value = false },
        onPick = { targetGroupId -> moveOrCopySelectedEntries(targetGroupId) }
    )
}

/**
 * 条目编辑对话框（用于新增）。
 *
 * 支持标题/账号/密码/网站/备注、自定义字段、附件、过期时间与图标。
 */
@Composable
private fun PasswordEntryEditorDialog(
    title: String,
    show: androidx.compose.runtime.MutableState<Boolean>,
    initialDraft: PasswordEntryEditDraft = PasswordEntryEditDraft(title = "", username = "", password = "", url = "", notes = ""),
    onDismiss: () -> Unit,
    onConfirm: (PasswordEntryEditDraft) -> Unit
) {
    val context = LocalContext.current

    var entryTitle by remember { mutableStateOf(initialDraft.title) }
    var entryUsername by remember { mutableStateOf(initialDraft.username) }
    var entryPassword by remember { mutableStateOf(initialDraft.password) }
    var entryUrl by remember { mutableStateOf(initialDraft.url) }
    var entryNotes by remember { mutableStateOf(initialDraft.notes) }
    var entryTagsText by remember { mutableStateOf(initialDraft.tags.joinToString(", ")) }
    var customFields by remember { mutableStateOf(initialDraft.customFields) }
    var attachments by remember { mutableStateOf(initialDraft.attachments.map { it.copy() }) }
    var expiryTime by remember { mutableStateOf(initialDraft.expiryTime) }
    var iconStandardId by remember { mutableStateOf(initialDraft.iconStandardId) }
    var customIconUuid by remember { mutableStateOf(initialDraft.customIconUuid) }
    var newCustomIconBytes by remember { mutableStateOf<ByteArray?>(initialDraft.newCustomIconBytes) }
    var showIconPicker by remember { mutableStateOf(false) }
    var showTemplatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(show.value) {
        if (show.value) {
            entryTitle = initialDraft.title
            entryUsername = initialDraft.username
            entryPassword = initialDraft.password
            entryUrl = initialDraft.url
            entryNotes = initialDraft.notes
            entryTagsText = initialDraft.tags.joinToString(", ")
            customFields = initialDraft.customFields
            attachments = initialDraft.attachments.map { it.copy() }
            expiryTime = initialDraft.expiryTime
            iconStandardId = initialDraft.iconStandardId
            customIconUuid = initialDraft.customIconUuid
            newCustomIconBytes = initialDraft.newCustomIconBytes
            showIconPicker = false
        }
    }

    val attachmentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val declaredSize = DocumentFile.fromSingleUri(context, uri)?.length() ?: -1L
        if (declaredSize > MAX_ATTACHMENT_BYTES) {
            ToastUtils.showShortToast(context, "附件过大（上限 ${MAX_ATTACHMENT_BYTES / 1024 / 1024} MiB），已取消")
            return@rememberLauncherForActivityResult
        }
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                // 增量读取并强制上限：content provider 可能返回 -1 的声明大小，故真正限制在这里施加
                val buffer = java.io.ByteArrayOutputStream(8 * 1024)
                val chunk = ByteArray(8 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(chunk)
                    if (read < 0) break
                    total += read
                    if (total > MAX_ATTACHMENT_BYTES) {
                        throw IllegalStateException("附件过大")
                    }
                    buffer.write(chunk, 0, read)
                }
                val bytes = buffer.toByteArray()
                val name = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
                    ?: "attachment_${System.currentTimeMillis()}"
                attachments = attachments + EditableAttachmentDraft(name = name, data = bytes, isNew = true)
            }
        }.onFailure {
            ToastUtils.showShortToast(context, "附件过大或读取失败，已取消")
        }
    }

    WindowDialog(
        title = title,
        show = show.value,
        onDismissRequest = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TextField(
                value = entryTitle,
                onValueChange = { entryTitle = it },
                label = "标题",
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            TextField(
                value = entryUsername,
                onValueChange = { entryUsername = it },
                label = "账号",
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            TextField(
                value = entryPassword,
                onValueChange = { entryPassword = it },
                label = "密码",
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            TextField(
                value = entryUrl,
                onValueChange = { entryUrl = it },
                label = "网站",
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            TextField(
                value = entryNotes,
                onValueChange = { entryNotes = it },
                label = "备注",
                modifier = Modifier.fillMaxWidth()
            )
            TextField(
                value = entryTagsText,
                onValueChange = { entryTagsText = it },
                label = "标签（逗号或分号分隔）",
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showIconPicker = true },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    EntryIcon(
                        customIconBytes = newCustomIconBytes,
                        standardIconId = iconStandardId,
                        primary = null,
                        secondary = null,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = "  选择图标",
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.primary
                    )
                }
                Icon(
                    imageVector = MiuixIcons.Edit,
                    contentDescription = "选择图标",
                    tint = MiuixTheme.colorScheme.onSurfaceSecondary
                )
            }

            SmallTitle(text = "自定义字段")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showTemplatePicker = true },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = MiuixIcons.Edit,
                    contentDescription = "从模板添加",
                    tint = MiuixTheme.colorScheme.primary
                )
                Text(
                    text = "  从模板添加字段",
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            CustomFieldsEditor(fields = customFields) { customFields = it }

            ExpiryTimeEditor(value = expiryTime) { expiryTime = it }

            SmallTitle(text = "附件 (${attachments.count { !it.removed }})")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surface),
                cornerRadius = 12.dp,
                pressFeedbackType = PressFeedbackType.None,
                showIndication = false,
                onClick = {}
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    attachments.filter { !it.removed }.forEachIndexed { index, att ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = att.name, fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurface)
                                if (att.isNew) {
                                    Text(
                                        text = formatFileSize((att.data?.size ?: 0).toLong()) + " · 新增",
                                        fontSize = 12.sp,
                                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                                    )
                                }
                            }
                            IconButton(onClick = {
                                attachments = attachments.map {
                                    if (it === att) it.copy(removed = true) else it
                                }
                            }) {
                                Icon(imageVector = MiuixIcons.Delete, contentDescription = "删除附件")
                            }
                        }
                        if (index < attachments.filter { !it.removed }.lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp), thickness = 0.5.dp)
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { attachmentPicker.launch("*/*") }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = MiuixIcons.Edit, contentDescription = "添加附件", tint = MiuixTheme.colorScheme.primary)
                        Text(
                            text = " 添加附件",
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }

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
                    onClick = {
                        onConfirm(
                            initialDraft.copy(
                                title = entryTitle.trim(),
                                username = entryUsername.trim(),
                                password = entryPassword,
                                url = entryUrl.trim(),
                                notes = entryNotes,
                                tags = entryTagsText.split(',', ';', '，', '；')
                                    .map { it.trim() }
                                    .filter { it.isNotEmpty() }
                                    .distinct(),
                                customFields = customFields,
                                attachments = attachments,
                                expiryTime = expiryTime,
                                customIconUuid = customIconUuid,
                                iconStandardId = iconStandardId,
                                newCustomIconBytes = newCustomIconBytes
                            )
                        )
                    },
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

    if (showIconPicker) {
        IconPickerDialog(
            show = showIconPicker,
            currentStandardIconId = iconStandardId,
            currentCustomIconBytes = newCustomIconBytes,
            onDismiss = { showIconPicker = false },
            onPick = { standardId, bytes ->
                iconStandardId = standardId ?: 0
                customIconUuid = null
                newCustomIconBytes = bytes
                showIconPicker = false
            }
        )
    }

    if (showTemplatePicker) {
        TemplatePickerDialog(
            show = showTemplatePicker,
            onDismiss = { showTemplatePicker = false },
            onPick = { template ->
                showTemplatePicker = false
                if (template != null) {
                    customFields = customFields.toMutableList().apply {
                        applyTemplateFields(template)
                    }
                }
            }
        )
    }
}

/**
 * 将模板字段集应用到当前自定义字段列表。
 *
 * 字段名使用 [TemplateEngine.addTemplateDecorator] 装饰（如 [SSID]），
 * 保证其他支持模板的应用（如 KeePassDX）可识别；已存在的同名字段跳过。
 */
private fun MutableList<EditableFieldDraft>.applyTemplateFields(template: Template) {
    val existingNames = this.map { it.name }.toSet()
    template.sections.forEach { section ->
        section.attributes.forEach { attribute ->
            val decoratedName = TemplateEngine.addTemplateDecorator(attribute.label)
            if (decoratedName !in existingNames) {
                add(
                    EditableFieldDraft(
                        name = decoratedName,
                        value = attribute.options.default ?: "",
                        isProtected = attribute.protected,
                        valueType = RemainingValueType.TEXT
                    )
                )
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
        show = show.value,
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

