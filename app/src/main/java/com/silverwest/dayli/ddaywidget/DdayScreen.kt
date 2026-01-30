package com.silverwest.dayli.ddaywidget

import android.util.Log
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import org.burnoutcrew.reorderable.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DdayScreen(
    viewModel: DdayViewModel = viewModel(),
    onTabChanged: (Int) -> Unit = {},
    onEditItem: (DdayItem) -> Unit = {}
) {
    val context = LocalContext.current
    val ddays by viewModel.ddayList.observeAsState(emptyList())
    val todos by viewModel.todoList.observeAsState(emptyList())
    val currentSort by viewModel.sortOption.observeAsState(SortOption.NEAREST)
    val currentCategory by viewModel.categoryFilter.observeAsState(null)
    val currentTab by viewModel.currentTab.observeAsState(ItemType.DDAY)

    // 탭 인덱스
    var selectedTabIndex by remember { mutableStateOf(0) }

    // 현재 탭에 따른 아이템 리스트
    val currentItems = if (selectedTabIndex == 0) ddays else todos

    // 진행중/완료 항목 분리
    val pendingItems = currentItems.filter { !it.isChecked }
    val completedItems = currentItems.filter { it.isChecked }

    // To-Do 드래그 순서 변경을 위한 상태
    var todoPendingData by remember { mutableStateOf(pendingItems) }
    LaunchedEffect(pendingItems, selectedTabIndex) {
        if (selectedTabIndex == 1) {
            todoPendingData = pendingItems
        }
    }

    // Reorderable 상태 (To-Do 탭 전용)
    val reorderableState = rememberReorderableLazyListState(
        onMove = { from, to ->
            Log.d("DRAG", "🔄 onMove: from=${from.index}, to=${to.index}")
            // 헤더가 0번 인덱스이므로 실제 아이템 인덱스는 -1
            val fromIndex = from.index - 1
            val toIndex = to.index - 1
            if (fromIndex >= 0 && toIndex >= 0 && fromIndex < todoPendingData.size && toIndex < todoPendingData.size) {
                todoPendingData = todoPendingData.toMutableList().apply {
                    add(toIndex, removeAt(fromIndex))
                }
                Log.d("DRAG", "✅ 순서 변경됨: fromIndex=$fromIndex, toIndex=$toIndex")
            }
        },
        onDragEnd = { _, _ ->
            Log.d("DRAG", "🏁 onDragEnd: 순서 DB 저장")
            // 드래그 완료 시 DB에 순서 저장
            viewModel.updateTodoOrder(todoPendingData)
        }
    )

    // 완료 섹션 펼침/접힘 상태 (기본: 접힘)
    var isCompletedExpanded by remember { mutableStateOf(false) }

    // D-Day 그룹 펼침/접힘 상태 (그룹명 -> 펼침 여부, 기본: 펼침)
    var expandedGroups by remember { mutableStateOf(setOf<String>()) }

    // 그룹 관리 다이얼로그 상태
    var showGroupManageDialog by remember { mutableStateOf(false) }
    val existingGroups by viewModel.existingGroups.observeAsState(emptyList())

    // D-Day 그룹별 분류
    val ddayPendingByGroup = remember(pendingItems, selectedTabIndex) {
        if (selectedTabIndex == 0) {
            pendingItems.groupBy { it.groupName ?: "미분류" }
                .toSortedMap(compareBy { if (it == "미분류") "zzz" else it })  // 미분류를 마지막으로
        } else {
            emptyMap()
        }
    }

    // 그룹 초기 펼침 상태 설정
    LaunchedEffect(ddayPendingByGroup.keys) {
        expandedGroups = ddayPendingByGroup.keys.toSet()  // 기본: 모두 펼침
    }

    // BottomSheet 상태 (수정/삭제 옵션용)
    var showBottomSheet by remember { mutableStateOf(false) }
    var selectedItem by remember { mutableStateOf<DdayItem?>(null) }
    val bottomSheetState = rememberModalBottomSheetState()

    // Snackbar 상태
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var deletedItem by remember { mutableStateOf<DdayItem?>(null) }

    Scaffold(
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = 80.dp)  // FAB 높이만큼 여백
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 탭 바
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = {
                        selectedTabIndex = 0
                        onTabChanged(0)
                    },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("📅", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("D-Day")
                        }
                    }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = {
                        selectedTabIndex = 1
                        onTabChanged(1)
                    },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("✅", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("To-Do")
                        }
                    }
                )
            }

            // D-Day 탭일 때만 정렬 옵션 표시
            if (selectedTabIndex == 0) {
                // 정렬 옵션 버튼 + 그룹 관리 버튼
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 그룹 관리 버튼
                    AssistChip(
                        onClick = { showGroupManageDialog = true },
                        label = { Text("그룹 관리", style = MaterialTheme.typography.bodySmall) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Folder,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )

                    // 정렬 옵션
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "정렬: ",
                            style = MaterialTheme.typography.bodySmall
                        )
                        FilterChip(
                            selected = currentSort == SortOption.NEAREST,
                            onClick = { viewModel.setSortOption(SortOption.NEAREST) },
                            label = { Text("임박순", style = MaterialTheme.typography.bodySmall) },
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        FilterChip(
                            selected = currentSort == SortOption.FARTHEST,
                            onClick = { viewModel.setSortOption(SortOption.FARTHEST) },
                            label = { Text("여유순", style = MaterialTheme.typography.bodySmall) }
                        )
                    }
                }
            }

            // 리스트 (To-Do 탭: 드래그 가능, D-Day 탭: 일반)
            if (selectedTabIndex == 1) {
                // To-Do 탭: 드래그 순서 변경 가능
                LazyColumn(
                    state = reorderableState.listState,
                    modifier = Modifier
                        .weight(1f)
                        .reorderable(reorderableState),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)  // FAB 겹침 방지
                ) {
                    // 진행중 섹션 헤더
                    item(key = "header_pending") {
                        SectionHeader(
                            title = "진행중",
                            count = todoPendingData.size,
                            isExpandable = false,
                            isExpanded = true,
                            onToggle = {}
                        )
                    }

                    // 진행중 To-Do 항목들 (드래그 가능)
                    items(
                        items = todoPendingData,
                        key = { it.id }
                    ) { item ->
                        ReorderableItem(reorderableState, key = item.id) { isDragging ->
                            val elevation = if (isDragging) 8.dp else 0.dp
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RectangleShape,
                                elevation = CardDefaults.cardElevation(defaultElevation = elevation),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // 드래그 핸들 (직접 드래그 가능)
                                    Icon(
                                        imageVector = Icons.Default.Menu,
                                        contentDescription = "드래그",
                                        modifier = Modifier
                                            .detectReorder(reorderableState)
                                            .padding(start = 8.dp, end = 4.dp)
                                            .padding(vertical = 12.dp)
                                            .size(24.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                    // 아이템 내용
                                    Box(modifier = Modifier.weight(1f)) {
                                        DdayListItem(
                                            item = item,
                                            onToggle = { viewModel.toggleChecked(it) },
                                            onLongPress = {
                                                selectedItem = it
                                                showBottomSheet = true
                                            },
                                            onSubTaskToggle = { ddayItem, index ->
                                                viewModel.toggleSubTask(ddayItem, index)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 완료 섹션 헤더
                    if (completedItems.isNotEmpty()) {
                        item(key = "header_completed") {
                            SectionHeader(
                                title = "완료",
                                count = completedItems.size,
                                isExpandable = true,
                                isExpanded = isCompletedExpanded,
                                onToggle = { isCompletedExpanded = !isCompletedExpanded }
                            )
                        }

                        if (isCompletedExpanded) {
                            items(
                                items = completedItems,
                                key = { it.id }
                            ) { item ->
                                SwipeableDdayItem(
                                    item = item,
                                    onDelete = {
                                        deletedItem = item
                                        viewModel.delete(item)
                                        scope.launch {
                                            val result = snackbarHostState.showSnackbar(
                                                message = "'${item.title}' 삭제됨",
                                                actionLabel = "실행취소",
                                                duration = SnackbarDuration.Short
                                            )
                                            if (result == SnackbarResult.ActionPerformed) {
                                                deletedItem?.let { deleted ->
                                                    viewModel.restoreItem(deleted)
                                                }
                                            }
                                            deletedItem = null
                                        }
                                    },
                                    onToggle = { viewModel.toggleChecked(it) },
                                    onLongPress = {
                                        selectedItem = it
                                        showBottomSheet = true
                                    },
                                    onSubTaskToggle = { ddayItem, index ->
                                        viewModel.toggleSubTask(ddayItem, index)
                                    }
                                )
                            }
                        }
                    }
                }
            } else {
                // D-Day 탭: 그룹별 표시
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)  // FAB 겹침 방지
                ) {
                    // 그룹별 진행중 항목
                    ddayPendingByGroup.forEach { (groupName, groupItems) ->
                        val isGroupExpanded = expandedGroups.contains(groupName)

                        // 그룹 헤더
                        item(key = "group_header_$groupName") {
                            GroupHeader(
                                groupName = groupName,
                                count = groupItems.size,
                                isExpanded = isGroupExpanded,
                                onToggle = {
                                    expandedGroups = if (isGroupExpanded) {
                                        expandedGroups - groupName
                                    } else {
                                        expandedGroups + groupName
                                    }
                                }
                            )
                        }

                        // 그룹 내 항목들
                        if (isGroupExpanded) {
                            items(
                                items = groupItems,
                                key = { it.id }
                            ) { item ->
                                SwipeableDdayItem(
                                    item = item,
                                    onDelete = {
                                        deletedItem = item
                                        viewModel.delete(item)
                                        scope.launch {
                                            val result = snackbarHostState.showSnackbar(
                                                message = "'${item.title}' 삭제됨",
                                                actionLabel = "실행취소",
                                                duration = SnackbarDuration.Short
                                            )
                                            if (result == SnackbarResult.ActionPerformed) {
                                                deletedItem?.let { deleted ->
                                                    viewModel.restoreItem(deleted)
                                                }
                                            }
                                            deletedItem = null
                                        }
                                    },
                                    onToggle = { viewModel.toggleChecked(it) },
                                    onLongPress = {
                                        selectedItem = it
                                        showBottomSheet = true
                                    },
                                    onSubTaskToggle = { ddayItem, index ->
                                        viewModel.toggleSubTask(ddayItem, index)
                                    }
                                )
                            }
                        }
                    }

                    // 완료 섹션 헤더 (접기/펼치기 가능)
                    if (completedItems.isNotEmpty()) {
                        item(key = "header_completed_dday") {
                            SectionHeader(
                                title = "완료",
                                count = completedItems.size,
                                isExpandable = true,
                                isExpanded = isCompletedExpanded,
                                onToggle = { isCompletedExpanded = !isCompletedExpanded }
                            )
                        }

                        // 완료 항목들 (펼쳐진 경우에만 표시)
                        if (isCompletedExpanded) {
                            items(
                                items = completedItems,
                                key = { it.id }
                            ) { item ->
                                SwipeableDdayItem(
                                    item = item,
                                    onDelete = {
                                        deletedItem = item
                                        viewModel.delete(item)
                                        scope.launch {
                                            val result = snackbarHostState.showSnackbar(
                                                message = "'${item.title}' 삭제됨",
                                                actionLabel = "실행취소",
                                                duration = SnackbarDuration.Short
                                            )
                                            if (result == SnackbarResult.ActionPerformed) {
                                                deletedItem?.let { deleted ->
                                                    viewModel.restoreItem(deleted)
                                                }
                                            }
                                            deletedItem = null
                                        }
                                    },
                                    onToggle = { viewModel.toggleChecked(it) },
                                    onLongPress = {
                                        selectedItem = it
                                        showBottomSheet = true
                                    },
                                    onSubTaskToggle = { ddayItem, index ->
                                        viewModel.toggleSubTask(ddayItem, index)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // BottomSheet
        if (showBottomSheet && selectedItem != null) {
            ModalBottomSheet(
                onDismissRequest = {
                    showBottomSheet = false
                    selectedItem = null
                },
                sheetState = bottomSheetState
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = selectedItem?.title ?: "",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // 수정 버튼
                    Surface(
                        onClick = {
                            selectedItem?.let { item ->
                                showBottomSheet = false
                                onEditItem(item)
                            }
                        }
                    ) {
                        ListItem(
                            headlineContent = { Text("수정") },
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "수정"
                                )
                            }
                        )
                    }

                    // 삭제 버튼
                    Surface(
                        onClick = {
                            selectedItem?.let { item ->
                                deletedItem = item
                                viewModel.delete(item)
                                showBottomSheet = false
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = "'${item.title}' 삭제됨",
                                        actionLabel = "실행취소",
                                        duration = SnackbarDuration.Short
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        deletedItem?.let { deleted ->
                                            viewModel.restoreItem(deleted)
                                        }
                                    }
                                    deletedItem = null
                                }
                                selectedItem = null
                            }
                        }
                    ) {
                        ListItem(
                            headlineContent = { Text("삭제", color = Color(0xFFFF5252)) },
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "삭제",
                                    tint = Color(0xFFFF5252)
                                )
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }

        // 그룹 관리 다이얼로그
        if (showGroupManageDialog) {
            GroupManageDialog(
                groups = existingGroups,
                onDismiss = { showGroupManageDialog = false },
                onRenameGroup = { oldName, newName ->
                    viewModel.renameGroup(oldName, newName)
                },
                onDeleteGroup = { groupName ->
                    viewModel.deleteGroup(groupName)
                },
                viewModel = viewModel
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    count: Int,
    isExpandable: Boolean,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isExpandable) Modifier.clickable { onToggle() }
                else Modifier
            )
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "($count)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isExpandable) {
            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (isExpanded) "접기" else "펼치기",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GroupHeader(
    groupName: String,
    count: Int,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "📁",
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = groupName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "($count)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = if (isExpanded) "접기" else "펼치기",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableDdayItem(
    item: DdayItem,
    onDelete: () -> Unit,
    onToggle: (DdayItem) -> Unit,
    onLongPress: (DdayItem) -> Unit,
    onSubTaskToggle: (DdayItem, Int) -> Unit = { _, _ -> }
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val color by animateColorAsState(
                when (dismissState.targetValue) {
                    SwipeToDismissBoxValue.EndToStart -> Color(0xFFFF5252)
                    else -> Color.Transparent
                },
                label = "swipe_color"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "삭제",
                        tint = Color.White
                    )
                }
            }
        },
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RectangleShape,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            DdayListItem(
                item = item,
                onToggle = onToggle,
                onLongPress = onLongPress,
                onSubTaskToggle = onSubTaskToggle
            )
        }
    }
}

@Composable
private fun CategoryFilterChip(
    emoji: String,
    label: String,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) {
        color.copy(alpha = 0.2f)
    } else {
        Color.Transparent
    }

    val borderColor = if (isSelected) {
        color
    } else {
        Color.Gray.copy(alpha = 0.3f)
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = emoji,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) color else Color.Gray
        )
    }
}

/**
 * 그룹 관리 다이얼로그
 * - 그룹 목록 표시
 * - 그룹 이름 변경
 * - 그룹 삭제 (해당 그룹의 D-Day는 미분류로 이동)
 */
@Composable
private fun GroupManageDialog(
    groups: List<String>,
    onDismiss: () -> Unit,
    onRenameGroup: (oldName: String, newName: String) -> Unit,
    onDeleteGroup: (groupName: String) -> Unit,
    viewModel: DdayViewModel
) {
    var editingGroup by remember { mutableStateOf<String?>(null) }
    var editingName by remember { mutableStateOf("") }
    var deleteConfirmGroup by remember { mutableStateOf<String?>(null) }
    var deleteGroupItemCount by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("그룹 관리")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
            ) {
                if (groups.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "등록된 그룹이 없습니다",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(groups) { groupName ->
                            GroupManageItem(
                                groupName = groupName,
                                isEditing = editingGroup == groupName,
                                editingName = if (editingGroup == groupName) editingName else groupName,
                                onEditStart = {
                                    editingGroup = groupName
                                    editingName = groupName
                                },
                                onEditChange = { editingName = it },
                                onEditConfirm = {
                                    if (editingName.isNotBlank() && editingName != groupName) {
                                        onRenameGroup(groupName, editingName.trim())
                                    }
                                    editingGroup = null
                                    editingName = ""
                                },
                                onEditCancel = {
                                    editingGroup = null
                                    editingName = ""
                                },
                                onDeleteClick = {
                                    scope.launch {
                                        deleteGroupItemCount = viewModel.getGroupItemCount(groupName)
                                        deleteConfirmGroup = groupName
                                    }
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("닫기")
            }
        }
    )

    // 삭제 확인 다이얼로그
    if (deleteConfirmGroup != null) {
        AlertDialog(
            onDismissRequest = { deleteConfirmGroup = null },
            title = { Text("그룹 삭제") },
            text = {
                Text(
                    text = if (deleteGroupItemCount > 0) {
                        "'$deleteConfirmGroup' 그룹을 삭제하시겠습니까?\n\n이 그룹에 속한 ${deleteGroupItemCount}개의 D-Day가 '미분류'로 이동됩니다."
                    } else {
                        "'$deleteConfirmGroup' 그룹을 삭제하시겠습니까?"
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteConfirmGroup?.let { onDeleteGroup(it) }
                        deleteConfirmGroup = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("삭제")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmGroup = null }) {
                    Text("취소")
                }
            }
        )
    }
}

@Composable
private fun GroupManageItem(
    groupName: String,
    isEditing: Boolean,
    editingName: String,
    onEditStart: () -> Unit,
    onEditChange: (String) -> Unit,
    onEditConfirm: () -> Unit,
    onEditCancel: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 폴더 아이콘
            Text("📁", fontSize = 18.sp)
            Spacer(modifier = Modifier.width(8.dp))

            if (isEditing) {
                // 편집 모드
                OutlinedTextField(
                    value = editingName,
                    onValueChange = onEditChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = onEditConfirm,
                    modifier = Modifier.size(32.dp)
                ) {
                    Text("✓", fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                }
                IconButton(
                    onClick = onEditCancel,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "취소",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // 보기 모드
                Text(
                    text = groupName,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                // 수정 버튼
                IconButton(
                    onClick = onEditStart,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "수정",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // 삭제 버튼
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "삭제",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
