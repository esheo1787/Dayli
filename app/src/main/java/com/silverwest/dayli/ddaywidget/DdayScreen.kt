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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
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
                // 정렬 옵션 버튼
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
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

            // 리스트 (To-Do 탭: 드래그 가능, D-Day 탭: 일반)
            if (selectedTabIndex == 1) {
                // To-Do 탭: 드래그 순서 변경 가능
                LazyColumn(
                    state = reorderableState.listState,
                    modifier = Modifier
                        .weight(1f)
                        .reorderable(reorderableState),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
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
                                    }
                                )
                            }
                        }
                    }
                }
            } else {
                // D-Day 탭: 기존 스와이프 삭제만
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    // 진행중 섹션 헤더
                    item {
                        SectionHeader(
                            title = "진행중",
                            count = pendingItems.size,
                            isExpandable = false,
                            isExpanded = true,
                            onToggle = {}
                        )
                    }

                    // 진행중 항목들
                    items(
                        items = pendingItems,
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
                            }
                        )
                    }

                    // 완료 섹션 헤더 (접기/펼치기 가능)
                    if (completedItems.isNotEmpty()) {
                        item {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableDdayItem(
    item: DdayItem,
    onDelete: () -> Unit,
    onToggle: (DdayItem) -> Unit,
    onLongPress: (DdayItem) -> Unit
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
                onLongPress = onLongPress
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
