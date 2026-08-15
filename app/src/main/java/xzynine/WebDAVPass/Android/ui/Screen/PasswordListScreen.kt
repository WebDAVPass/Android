package xzynine.WebDAVPass.Android.ui.Screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.launch
import xzynine.WebDAVPass.Android.data.PasswordEntry
import xzynine.WebDAVPass.Android.data.PasswordEntryEditDraft
import xzynine.WebDAVPass.Android.data.DuplicateEntryInfo
import xzynine.WebDAVPass.Android.data.DuplicateGroupInfo
import xzynine.WebDAVPass.Android.data.GroupNodeInfo
import xzynine.WebDAVPass.Android.data.PasswordGroupEditDraft
import xzynine.WebDAVPass.Android.ui.Dialog.ConfirmationDialog
import xzynine.WebDAVPass.Android.ui.Dialog.DuplicateScanDialog
import xzynine.WebDAVPass.Android.ui.Dialog.EntryMergeDialog
import xzynine.WebDAVPass.Android.ui.Dialog.GroupPickerDialog
import xzynine.WebDAVPass.Android.ui.viewmodel.TokenViewModel
import xzynine.WebDAVPass.Android.ui.ViewModel.PasswordFolderIndexLabel
import xzynine.WebDAVPass.Android.ui.ViewModel.PasswordSortMode
import xzynine.WebDAVPass.Android.ui.ViewModel.toPasswordIndexKey
import androidx.compose.ui.platform.LocalContext
import xzynine.WebDAVPass.Android.ui.component.buildBrandIconBytes
import xzylib.base.util.ToastUtils

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
    onNavigateBack: () -> Unit,
    isEmbedded: Boolean = false
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
    val showSolidifyDialog = remember { mutableStateOf(false) }
    var solidifyUpdates by remember { mutableStateOf<Map<Long, ByteArray>>(emptyMap()) }

    val duplicateGroups = remember { mutableStateOf<List<DuplicateGroupInfo>>(emptyList()) }
    val showDuplicateScanDialog = remember { mutableStateOf(false) }
    val mergeDialogEntries = remember { mutableStateOf<List<DuplicateEntryInfo>>(emptyList()) }
    val showMergeDialog = remember { mutableStateOf(false) }
    var mergeDialogMasterId by remember { mutableStateOf<Long?>(null) }
    var mergeDialogFromScan by remember { mutableStateOf(false) }
    val showAutoMergeConfirm = remember { mutableStateOf(false) }
    var autoMergeTarget by remember { mutableStateOf<Pair<Long, List<Long>>?>(null) }
    val showMergeConfirm = remember { mutableStateOf(false) }
    var mergeConfirmTarget by remember { mutableStateOf<Pair<Long, Map<String, Long>>?>(null) }

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
        showSolidifyDialog.value = false
        solidifyUpdates = emptyMap()
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

    fun selectAllVisible() {
        if (!allowWriteActions) {
            return
        }
        entries.forEach { entry ->
            selectedTargets[entry.entryId] = entry.isFolderPlaceholder
        }
        isSelectionMode.value = true
    }

    fun invertSelection() {
        if (!allowWriteActions) {
            return
        }
        val currentlySelected = selectedTargets.keys.toSet()
        entries.forEach { entry ->
            if (entry.entryId in currentlySelected) {
                selectedTargets.remove(entry.entryId)
            } else {
                selectedTargets[entry.entryId] = entry.isFolderPlaceholder
            }
        }
        if (selectedTargets.isEmpty()) {
            isSelectionMode.value = false
        }
    }

    fun solidifySelectedBrandIcons() {
        val entryIds = selectedTargets.keys.filter { selectedTargets[it] == false }.toSet()
        val matched = entries
            .filter { it.entryId in entryIds && !it.isFolderPlaceholder }
            .mapNotNull { entry ->
                buildBrandIconBytes(context, entry.title, entry.account)
                    ?.let { entry.entryId to it }
            }
            .toMap()
        if (matched.isEmpty()) {
            ToastUtils.showShortToast(context, "选中的条目无匹配的品牌图标")
            return
        }
        solidifyUpdates = matched
        showSolidifyDialog.value = true
    }

    /** 当前视图条目 ID（排除分组占位项），用于扫描与多选合并。 */
    fun visibleEntryIds(): List<Long> {
        return entries.filter { !it.isFolderPlaceholder }.map { it.entryId }
    }

    /** 扫描当前视图，检测重复候选组。 */
    fun scanDuplicateEntries() {
        coroutineScope.launch {
            val entryIds = visibleEntryIds()
            if (entryIds.size < 2) {
                ToastUtils.showShortToast(context, "当前视图条目不足，无法检测")
                return@launch
            }
            val groups = tokenViewModel.detectDuplicateGroups(entryIds)
            if (groups.isEmpty()) {
                ToastUtils.showShortToast(context, "未发现重复条目")
                return@launch
            }
            duplicateGroups.value = groups
            showDuplicateScanDialog.value = true
        }
    }

    /** 合并成功后重新扫描，刷新检测结果（全部处理完时自动关闭对话框）。 */
    fun refreshDuplicateGroupsAfterMerge() {
        coroutineScope.launch {
            val groups = tokenViewModel.detectDuplicateGroups(visibleEntryIds())
            duplicateGroups.value = groups
            if (groups.isEmpty()) {
                showDuplicateScanDialog.value = false
            }
        }
    }

    /** 多选合并入口：将选中的条目作为一组打开合并编辑器。 */
    fun openEntryMergeForSelection() {
        val entryIds = selectedTargets.keys.filter { selectedTargets[it] == false }.toList()
        if (entryIds.size < 2) {
            ToastUtils.showShortToast(context, "至少选择两个条目")
            return
        }
        coroutineScope.launch {
            val infos = tokenViewModel.loadEntryMergeInfos(entryIds)
            if (infos.size < 2) {
                ToastUtils.showShortToast(context, "无法加载所选条目")
                return@launch
            }
            mergeDialogEntries.value = infos
            mergeDialogMasterId = infos.maxByOrNull { it.modifiedTime }?.entryId
            mergeDialogFromScan = false
            showMergeDialog.value = true
        }
    }

    /** 冲突组：关闭扫描结果对话框，打开逐项选择编辑器。 */
    fun openManualMergeForGroup(group: DuplicateGroupInfo, masterEntryId: Long) {
        showDuplicateScanDialog.value = false
        mergeDialogEntries.value = group.entries
        mergeDialogMasterId = masterEntryId
        mergeDialogFromScan = true
        showMergeDialog.value = true
    }

    /** 关闭合并编辑器；若来自扫描结果对话框则恢复它，避免取消后需要重新扫描。 */
    fun dismissMergeDialog() {
        showMergeDialog.value = false
        if (mergeDialogFromScan) {
            mergeDialogFromScan = false
            showDuplicateScanDialog.value = true
        }
    }

    /** 编辑器确认：先弹最终确认对话框。 */
    fun requestMerge(masterEntryId: Long, fieldSelections: Map<String, Long>) {
        mergeConfirmTarget = masterEntryId to fieldSelections
        showMergeConfirm.value = true
    }

    /** 执行合并（编辑器或扫描结果触发的自动合并统一走这里）。 */
    fun executeMerge(masterEntryId: Long, sourceEntryIds: List<Long>, fieldSelections: Map<String, Long>) {
        coroutineScope.launch {
            val count = tokenViewModel.mergeEntryGroup(masterEntryId, sourceEntryIds, fieldSelections)
            if (count > 0) {
                ToastUtils.showShortToast(context, "已合并 $count 条条目")
            } else {
                ToastUtils.showShortToast(context, "合并失败")
            }
            mergeConfirmTarget = null
            autoMergeTarget = null
            showMergeDialog.value = false
            showMergeConfirm.value = false
            showAutoMergeConfirm.value = false
            mergeDialogFromScan = false
            mergeDialogEntries.value = emptyList()
            clearSelectionMode()
            refreshDuplicateGroupsAfterMerge()
        }
    }

    /** 自动合并组：弹出确认框后执行。 */
    fun requestAutoMerge(group: DuplicateGroupInfo, masterEntryId: Long) {
        val sourceIds = group.entries.map { it.entryId }.filter { it != masterEntryId }
        autoMergeTarget = masterEntryId to sourceIds
        showAutoMergeConfirm.value = true
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



    // 嵌入模式与独立模式共用 Scaffold：保留顶栏功能区
    // 嵌入模式用紧凑 SmallTopAppBar（由 TopAppBar 自行处理状态栏 insets，缓解高度压缩）
    Scaffold(
        popupHost = {},
        topBar = {
            if (isEmbedded) {
                SmallTopAppBar(
                    title = if (isSelectionMode.value) "已选 ${selectedTargets.size} 项" else title,
                    navigationIcon = {
                        if (isSelectionMode.value) {
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
                        } else if (enableGroupNavigation && passwordGroupStack.isNotEmpty()) {
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
                        }
                    },
                    actions = {
                    PasswordListTopBarActions(
                        isSelectionMode = isSelectionMode.value,
                        selectedTargets = selectedTargets,
                        enableRecycleBinActions = enableRecycleBinActions,
                        allowWriteActions = allowWriteActions,
                        enableGroupNavigation = enableGroupNavigation,
                        sortMode = sortMode,
                        onSortModeChange = { sortModeOrdinal = it.ordinal },
                        sortAscending = sortAscending,
                        onSortAscendingChange = { sortAscending = it },
                        hideExpired = hideExpired,
                        onHideExpiredChange = { hideExpired = it },
                        onSelectAll = { selectAllVisible() },
                        onInvertSelection = { invertSelection() },
                        onRestoreSelected = { restoreSelectedEntries() },
                        onRequestPermanentDelete = { showPermanentDeleteDialog.value = true },
                        onRequestDelete = { showDeleteDialog.value = true },
                        onSolidifyBrandIcons = { solidifySelectedBrandIcons() },
                        onMergeSelection = { openEntryMergeForSelection() },
                        onMoveSelection = { openGroupPicker(isMove = true) },
                        onCopySelection = { openGroupPicker(isMove = false) },
                        onScanDuplicates = { scanDuplicateEntries() },
                        onCreateEntry = { showCreateEntryDialog.value = true },
                        onCreateGroup = { showCreateGroupDialog.value = true }
                    )
                }
                )
            } else {
                TopAppBar(
                    title = if (isSelectionMode.value) "已选 ${selectedTargets.size} 项" else title,
                    navigationIcon = {
                        if (isSelectionMode.value) {
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
                        } else if (enableGroupNavigation && passwordGroupStack.isNotEmpty()) {
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
                    PasswordListTopBarActions(
                        isSelectionMode = isSelectionMode.value,
                        selectedTargets = selectedTargets,
                        enableRecycleBinActions = enableRecycleBinActions,
                        allowWriteActions = allowWriteActions,
                        enableGroupNavigation = enableGroupNavigation,
                        sortMode = sortMode,
                        onSortModeChange = { sortModeOrdinal = it.ordinal },
                        sortAscending = sortAscending,
                        onSortAscendingChange = { sortAscending = it },
                        hideExpired = hideExpired,
                        onHideExpiredChange = { hideExpired = it },
                        onSelectAll = { selectAllVisible() },
                        onInvertSelection = { invertSelection() },
                        onRestoreSelected = { restoreSelectedEntries() },
                        onRequestPermanentDelete = { showPermanentDeleteDialog.value = true },
                        onRequestDelete = { showDeleteDialog.value = true },
                        onSolidifyBrandIcons = { solidifySelectedBrandIcons() },
                        onMergeSelection = { openEntryMergeForSelection() },
                        onMoveSelection = { openGroupPicker(isMove = true) },
                        onCopySelection = { openGroupPicker(isMove = false) },
                        onScanDuplicates = { scanDuplicateEntries() },
                        onCreateEntry = { showCreateEntryDialog.value = true },
                        onCreateGroup = { showCreateGroupDialog.value = true }
                    )
                }
                )
            }
        }
    ) { paddingValues ->
            PasswordListScreenContent(
                modifier = Modifier.padding(paddingValues),
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                searchExpanded = searchExpanded,
                onSearchExpandedChange = { searchExpanded = it },
                searchCaseSensitive = searchCaseSensitive,
                onSearchCaseSensitiveChange = { searchCaseSensitive = it },
                focusManager = focusManager,
                groupedEntries = groupedEntries,
                listState = listState,
                emptyStateText = emptyStateText,
                emptySearchStateText = emptySearchStateText,
                enableGroupNavigation = enableGroupNavigation,
                tokenViewModel = tokenViewModel,
                onEntryClick = onEntryClick,
                isSelectionMode = isSelectionMode.value,
                selectedTargets = selectedTargets,
                onItemLongClick = { item -> setSelection(item, true) },
                onItemCheckedChange = { item, checked -> setSelection(item, checked) },
                indexLetters = indexLetters,
                enabledIndexLetters = enabledIndexLetters,
                activeLetter = activeLetter,
                sectionBoundaries = sectionBoundaries,
                passwordIndexKeys = passwordIndexKeys,
                coroutineScope = coroutineScope,
                context = context
            )
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

    if (allowWriteActions && !enableRecycleBinActions && isSelectionMode.value && solidifyUpdates.isNotEmpty()) {
        ConfirmationDialog(
            title = "固化为品牌图标",
            summary = "将为 ${solidifyUpdates.size} 个匹配条目写入品牌图标并保存到密码库。",
            show = showSolidifyDialog,
            onDismiss = {
                showSolidifyDialog.value = false
            },
            confirmButtonText = "固化",
            onConfirm = {
                coroutineScope.launch {
                    val count = tokenViewModel.solidifyEntryBrandIcons(solidifyUpdates)
                    if (count > 0) {
                        ToastUtils.showShortToast(context, "已固化 $count 个条目")
                    } else {
                        ToastUtils.showShortToast(context, "固化失败")
                    }
                    solidifyUpdates = emptyMap()
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

    if (duplicateGroups.value.isNotEmpty() && showDuplicateScanDialog.value) {
        DuplicateScanDialog(
            groups = duplicateGroups.value,
            show = showDuplicateScanDialog.value,
            onDismiss = { showDuplicateScanDialog.value = false },
            onAutoMerge = { group, masterId -> requestAutoMerge(group, masterId) },
            onManualMerge = { group, masterId -> openManualMergeForGroup(group, masterId) }
        )
    }

    if (mergeDialogEntries.value.isNotEmpty() && showMergeDialog.value) {
        EntryMergeDialog(
            entries = mergeDialogEntries.value,
            show = showMergeDialog.value,
            initialMasterEntryId = mergeDialogMasterId,
            onDismiss = { dismissMergeDialog() },
            onConfirm = { masterId, selections -> requestMerge(masterId, selections) }
        )
    }

    if (showAutoMergeConfirm.value && autoMergeTarget != null) {
        val (masterId, sourceIds) = autoMergeTarget!!
        ConfirmationDialog(
            title = "确认合并",
            summary = "将合并 ${sourceIds.size + 1} 条条目为 1 条，其余 ${sourceIds.size} 条将移入回收站（可恢复）。",
            show = showAutoMergeConfirm,
            onDismiss = { showAutoMergeConfirm.value = false },
            confirmButtonText = "合并",
            onConfirm = { executeMerge(masterId, sourceIds, emptyMap()) }
        )
    }

    if (showMergeConfirm.value && mergeConfirmTarget != null) {
        val (masterId, selections) = mergeConfirmTarget!!
        val sourceIds = mergeDialogEntries.value.map { it.entryId }.filter { it != masterId }
        ConfirmationDialog(
            title = "确认合并",
            summary = "将把 ${sourceIds.size} 条条目合并进所选主条目，并移入回收站（可恢复）。",
            show = showMergeConfirm,
            onDismiss = { showMergeConfirm.value = false },
            confirmButtonText = "合并",
            onConfirm = { executeMerge(masterId, sourceIds, selections) }
        )
    }
}

/**
 * 条目分组标题。
 */
@Composable
fun PasswordSectionHeader(letter: String) {
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
const val FolderIndexBarLabel = "📁"

