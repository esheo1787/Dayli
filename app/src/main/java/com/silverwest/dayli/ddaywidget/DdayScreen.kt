@file:Suppress("DEPRECATION")
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DdayScreen(
    viewModel: DdayViewModel = viewModel(),
    onTabChanged: (Int) -> Unit = {},
    onEditItem: (DdayItem) -> Unit = {}
) {
    val context = LocalContext.current

    // 위젯에서 변경된 데이터를 앱 복귀 시 자동 반영
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.loadAll()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val ddays by viewModel.ddayList.observeAsState(emptyList())
    val todos by viewModel.todoList.observeAsState(emptyList())
    val hiddenDdays by viewModel.hiddenDdays.observeAsState(emptyList())
    val hiddenTodos by viewModel.hiddenTodos.observeAsState(emptyList())
    val currentSort by viewModel.sortOption.observeAsState(SortOption.NEAREST)
    val currentTodoSort by viewModel.todoSortOption.observeAsState(TodoSortOption.MY_ORDER)
    val currentCategory by viewModel.categoryFilter.observeAsState(null)
    val currentTab by viewModel.currentTab.observeAsState(ItemType.DDAY)

    // 탭 인덱스 (HorizontalPager 연동, 마지막 탭 복원)
    val pagerState = rememberPagerState(
        initialPage = DdaySettings.getLastTab(context),
        pageCount = { 2 }
    )
    LaunchedEffect(pagerState.currentPage) {
        DdaySettings.setLastTab(context, pagerState.currentPage)
        onTabChanged(pagerState.currentPage)
    }

    // 현재 탭에 따른 아이템 리스트
    val currentItems = if (pagerState.currentPage == 0) ddays else todos

    // 진행중/완료 항목 분리
    val pendingItems = currentItems.filter { !it.isChecked }

    // To-Do 드래그 순서 변경을 위한 상태
    var todoPendingData by remember { mutableStateOf(pendingItems) }
    LaunchedEffect(pendingItems, pagerState.currentPage) {
        if (pagerState.currentPage == 1) {
            todoPendingData = pendingItems
        }
    }

    // completedItems: todoPendingData에 남아있는 아이템 ID를 제외하여
    // LazyColumn 키 중복 방지 (todoPendingData는 LaunchedEffect로 비동기 갱신되므로
    // 1프레임 동안 동일 아이템이 pending/completed 양쪽에 존재할 수 있음)
    val todoPendingIds = todoPendingData.map { it.id }.toSet()
    val completedItems = currentItems.filter { it.isChecked && it.id !in todoPendingIds }

    // Reorderable 상태 (To-Do 탭 전용)
    val reorderableState = rememberReorderableLazyListState(
        onMove = { from, to ->
            val fromIndex = from.index - 1
            val toIndex = to.index - 1
            if (fromIndex >= 0 && toIndex >= 0 && fromIndex < todoPendingData.size && toIndex < todoPendingData.size) {
                todoPendingData = todoPendingData.toMutableList().apply {
                    add(toIndex, removeAt(fromIndex))
                }
            }
        },
        onDragEnd = { _, _ ->
            viewModel.updateTodoOrder(todoPendingData)
        }
    )

    // D-Day 그룹별 분류 (탭 전환에 관계없이 항상 D-Day 데이터 유지)
    val ddayPendingByGroup = remember(ddays) {
        ddays.filter { !it.isChecked }
            .groupBy { it.groupName ?: "미분류" }
            .toSortedMap(compareBy { if (it == "미분류") "zzz" else it })
    }

    // D-Day 그룹 드래그 순서 (동기 초기화 — 데이터 로드 즉시 그룹 표시)
    var groupOrder by remember(ddayPendingByGroup.keys) {
        val savedOrder = DdaySettings.getGroupOrder(context)
        val ordered = mutableListOf<String>()
        savedOrder.forEach { name -> if (name in ddayPendingByGroup) ordered.add(name) }
        ddayPendingByGroup.keys.forEach { name -> if (name !in ordered) ordered.add(name) }
        mutableStateOf(ordered.toList())
    }

    // 그룹 드래그 중 여부 (드래그 중에는 그룹 접어서 To-Do처럼 평평하게)
    var isDraggingGroups by remember { mutableStateOf(false) }

    // Reorderable 상태 (D-Day 그룹 드래그 — To-Do와 동일 패턴)
    val groupReorderableState = rememberReorderableLazyListState(
        onMove = { from, to ->
            val fromIndex = from.index - 1
            val toIndex = to.index - 1
            if (fromIndex >= 0 && toIndex >= 0 && fromIndex < groupOrder.size && toIndex < groupOrder.size) {
                groupOrder = groupOrder.toMutableList().apply {
                    add(toIndex, removeAt(fromIndex))
                }
            }
        },
        onDragEnd = { _, _ ->
            isDraggingGroups = false
            DdaySettings.setGroupOrder(context, groupOrder)
            // 위젯 동기화
            DdayWidgetProvider.refreshAllWidgets(context)
        }
    )

    // 앱 실행 시 To-Do 탭 스크롤 맨 위로
    LaunchedEffect(Unit) {
        reorderableState.listState.scrollToItem(0)
    }

    // 완료 섹션 펼침/접힘 상태 (저장된 상태 복원)
    var isCompletedExpanded by remember { mutableStateOf(DdaySettings.isCompletedExpanded(context)) }
    LaunchedEffect(isCompletedExpanded) {
        DdaySettings.setCompletedExpanded(context, isCompletedExpanded)
    }

    // 반복 일정 섹션 펼침/접힘 상태 (저장된 상태 복원)
    var isHiddenExpanded by remember { mutableStateOf(DdaySettings.isHiddenDdayExpanded(context)) }
    LaunchedEffect(isHiddenExpanded) {
        DdaySettings.setHiddenDdayExpanded(context, isHiddenExpanded)
    }
    var isHiddenTodoExpanded by remember { mutableStateOf(DdaySettings.isHiddenTodoExpanded(context)) }
    LaunchedEffect(isHiddenTodoExpanded) {
        DdaySettings.setHiddenTodoExpanded(context, isHiddenTodoExpanded)
    }

    // D-Day 그룹 펼침/접힘 상태 (동기 초기화 — 저장된 접힘 상태 복원)
    var expandedGroups by remember(ddayPendingByGroup.keys) {
        val collapsed = DdaySettings.getCollapsedGroups(context)
        mutableStateOf(ddayPendingByGroup.keys.filter { it !in collapsed }.toSet())
    }

    // 그룹 관리 다이얼로그 상태
    var showGroupManageDialog by remember { mutableStateOf(false) }
    var groupEmojiVersion by remember { mutableStateOf(0) }
    val existingGroups by viewModel.existingGroups.observeAsState(emptyList())

    // 템플릿 관리 다이얼로그 상태
    var showTemplateManageDialog by remember { mutableStateOf(false) }
    val templates by viewModel.templates.observeAsState(emptyList())

    // 정렬된 그룹 리스트 (항상 드래그 순서 사용)
    val orderedGroupList = groupOrder.mapNotNull { name ->
        ddayPendingByGroup[name]?.let { name to it }
    }

    // 하위 체크리스트 펼침 상태 (탭 전환 + 앱 재시작 시 유지)
    var expandedSubTaskIds by remember { mutableStateOf(DdaySettings.getExpandedSubTaskIds(context)) }
    LaunchedEffect(expandedSubTaskIds) {
        DdaySettings.setExpandedSubTaskIds(context, expandedSubTaskIds)
    }

    // BottomSheet 상태 (수정/삭제 옵션용)
    var showBottomSheet by remember { mutableStateOf(false) }
    var selectedItem by remember { mutableStateOf<DdayItem?>(null) }
    val bottomSheetState = rememberModalBottomSheetState()

    // Snackbar 상태
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var deletedItem by remember { mutableStateOf<DdayItem?>(null) }

    // Pull to Refresh 상태
    var isRefreshing by remember { mutableStateOf(false) }

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
                selectedTabIndex = pagerState.currentPage,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = pagerState.currentPage == 0,
                    onClick = {
                        scope.launch { pagerState.animateScrollToPage(0) }
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
                    selected = pagerState.currentPage == 1,
                    onClick = {
                        scope.launch { pagerState.animateScrollToPage(1) }
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

            // 탭 콘텐츠 (스와이프로 전환 가능)
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
            Column(modifier = Modifier.fillMaxSize()) {
            // D-Day 탭일 때만 정렬 옵션 표시
            if (page == 0) {
                // 정렬 옵션 버튼 + 그룹 관리 버튼
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(horizontal = 12.dp),
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

            // To-Do 탭일 때 정렬 옵션 + 템플릿 관리 표시
            if (page == 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 템플릿 관리 버튼
                    AssistChip(
                        onClick = { showTemplateManageDialog = true },
                        label = { Text("템플릿 관리", style = MaterialTheme.typography.bodySmall) },
                        leadingIcon = {
                            Text("📋", fontSize = 14.sp)
                        }
                    )

                    // 정렬 옵션
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "정렬: ",
                            style = MaterialTheme.typography.bodySmall
                        )
                        FilterChip(
                            selected = currentTodoSort == TodoSortOption.MY_ORDER,
                            onClick = { viewModel.setTodoSortOption(TodoSortOption.MY_ORDER) },
                            label = { Text("내 순서", style = MaterialTheme.typography.bodySmall) },
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        FilterChip(
                            selected = currentTodoSort == TodoSortOption.INCOMPLETE_FIRST,
                            onClick = { viewModel.setTodoSortOption(TodoSortOption.INCOMPLETE_FIRST) },
                            label = { Text("미완료순", style = MaterialTheme.typography.bodySmall) },
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        FilterChip(
                            selected = currentTodoSort == TodoSortOption.LATEST,
                            onClick = { viewModel.setTodoSortOption(TodoSortOption.LATEST) },
                            label = { Text("최근 추가", style = MaterialTheme.typography.bodySmall) }
                        )
                    }
                }
            }

            // 리스트 (To-Do 탭: 드래그 가능, D-Day 탭: 일반)
            if (page == 1) {
                // To-Do 탭: 드래그 순서 변경 가능
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        isRefreshing = true
                        viewModel.loadAll()
                        scope.launch {
                            delay(500)
                            isRefreshing = false
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                LazyColumn(
                    state = reorderableState.listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .reorderable(reorderableState),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
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
                                    // 드래그 핸들 (내 순서일 때만 표시)
                                    if (currentTodoSort == TodoSortOption.MY_ORDER) {
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
                                    }
                                    // 아이템 내용
                                    Box(modifier = Modifier.weight(1f)) {
                                        val nextDateInfo = if (item.isRepeating()) {
                                            item.getNextOccurrenceDate()?.let { nextDate ->
                                                val cal = java.util.Calendar.getInstance().apply { time = nextDate }
                                                "📅 ${cal.get(java.util.Calendar.YEAR)}년 ${cal.get(java.util.Calendar.MONTH) + 1}월 ${cal.get(java.util.Calendar.DAY_OF_MONTH)}일 표시 예정"
                                            }
                                        } else null
                                        DdayListItem(
                                            item = item,
                                            onToggle = { viewModel.toggleChecked(it) },
                                            onLongPress = {
                                                selectedItem = it
                                                showBottomSheet = true
                                            },
                                            onSubTaskToggle = { ddayItem, index ->
                                                viewModel.toggleSubTask(ddayItem, index)
                                            },
                                            isExpanded = item.id in expandedSubTaskIds,
                                            onExpandToggle = {
                                                expandedSubTaskIds = if (item.id in expandedSubTaskIds)
                                                    expandedSubTaskIds - item.id
                                                else expandedSubTaskIds + item.id
                                            },
                                            infoText = nextDateInfo
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 반복 일정 섹션 (숨겨진 매주/매월/매년 To-Do)
                    if (hiddenTodos.isNotEmpty()) {
                        item(key = "header_hidden_todo") {
                            SectionHeader(
                                title = "반복 일정",
                                count = hiddenTodos.size,
                                isExpandable = true,
                                isExpanded = isHiddenTodoExpanded,
                                onToggle = { isHiddenTodoExpanded = !isHiddenTodoExpanded }
                            )
                        }

                        if (isHiddenTodoExpanded) {
                            items(
                                items = hiddenTodos,
                                key = { "hidden_todo_${it.id}" }
                            ) { item ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RectangleShape,
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    )
                                ) {
                                    val showDateText = item.nextShowDate?.let { showDate ->
                                        val cal = java.util.Calendar.getInstance().apply {
                                            timeInMillis = showDate
                                        }
                                        "다음: ${cal.get(java.util.Calendar.YEAR)}년 ${cal.get(java.util.Calendar.MONTH) + 1}월 ${cal.get(java.util.Calendar.DAY_OF_MONTH)}일"
                                    }
                                    DdayListItem(
                                        item = item,
                                        onToggle = {
                                            viewModel.updateItem(it.copy(isHidden = false, nextShowDate = null))
                                        },
                                        onLongPress = {
                                            selectedItem = it
                                            showBottomSheet = true
                                        },
                                        onSubTaskToggle = { ddayItem, index ->
                                            viewModel.toggleSubTask(ddayItem, index)
                                        },
                                        isExpanded = item.id in expandedSubTaskIds,
                                        onExpandToggle = {
                                            expandedSubTaskIds = if (item.id in expandedSubTaskIds)
                                                expandedSubTaskIds - item.id
                                            else expandedSubTaskIds + item.id
                                        },
                                        forceCheckbox = true,
                                        infoText = showDateText
                                    )
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
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RectangleShape,
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    )
                                ) {
                                    DdayListItem(
                                        item = item,
                                        onToggle = { viewModel.toggleChecked(it) },
                                        onLongPress = {
                                            selectedItem = it
                                            showBottomSheet = true
                                        },
                                        onSubTaskToggle = { ddayItem, index ->
                                            viewModel.toggleSubTask(ddayItem, index)
                                        },
                                        isExpanded = item.id in expandedSubTaskIds,
                                        onExpandToggle = {
                                            expandedSubTaskIds = if (item.id in expandedSubTaskIds)
                                                expandedSubTaskIds - item.id
                                            else expandedSubTaskIds + item.id
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                } // PullToRefreshBox
            } else {
                // D-Day 탭: 그룹 드래그 가능, 그룹 내 아이템은 정렬 옵션에 따라
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        isRefreshing = true
                        groupEmojiVersion++
                        viewModel.loadAll()
                        scope.launch {
                            delay(500)
                            isRefreshing = false
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                LazyColumn(
                    state = groupReorderableState.listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .reorderable(groupReorderableState),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    // 비드래그 헤더 (To-Do의 header_pending과 동일 역할 — 인덱스 오프셋용)
                    item(key = "header_dday_groups") {
                        Spacer(modifier = Modifier.height(1.dp))
                    }

                    items(
                        items = orderedGroupList,
                        key = { "group_${it.first}" }
                    ) { (groupName, groupItems) ->
                        ReorderableItem(groupReorderableState, key = "group_${groupName}") { isDragging ->
                            // 드래그 시작 시점에 isDraggingGroups = true (onMove 대신 여기서 설정)
                            LaunchedEffect(isDragging) {
                                if (isDragging) isDraggingGroups = true
                            }
                            val isGroupExpanded = expandedGroups.contains(groupName) && !isDraggingGroups
                            val elevation = if (isDragging) 8.dp else 0.dp
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RectangleShape,
                                elevation = CardDefaults.cardElevation(defaultElevation = elevation),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                )
                            ) {
                                Column {
                                    // 드래그 핸들 + 그룹 헤더 (To-Do와 동일 패턴)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Menu,
                                            contentDescription = "드래그",
                                            modifier = Modifier
                                                .detectReorder(groupReorderableState)
                                                .padding(start = 8.dp, end = 4.dp)
                                                .padding(vertical = 12.dp)
                                                .size(24.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                        Box(modifier = Modifier.weight(1f)) {
                                            GroupHeader(
                                                groupName = groupName,
                                                count = groupItems.size,
                                                isExpanded = isGroupExpanded,
                                                emojiVersion = groupEmojiVersion,
                                                onToggle = {
                                                    expandedGroups = if (expandedGroups.contains(groupName)) {
                                                        expandedGroups - groupName
                                                    } else {
                                                        expandedGroups + groupName
                                                    }
                                                    DdaySettings.toggleGroupCollapsed(context, groupName)
                                                }
                                            )
                                        }
                                    }
                                    // 그룹 내 항목들 (드래그 중에는 접힘)
                                    if (isGroupExpanded) {
                                      Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                        groupItems.forEach { item ->
                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RectangleShape,
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.surface
                                                )
                                            ) {
                                                val nextDateInfo = if (item.isRepeating()) {
                                                    item.getNextOccurrenceDate()?.let { nextDate ->
                                                        val cal = java.util.Calendar.getInstance().apply { time = nextDate }
                                                        "📅 ${cal.get(java.util.Calendar.YEAR)}년 ${cal.get(java.util.Calendar.MONTH) + 1}월 ${cal.get(java.util.Calendar.DAY_OF_MONTH)}일 표시 예정"
                                                    }
                                                } else null
                                                DdayListItem(
                                                    item = item,
                                                    onToggle = { viewModel.toggleChecked(it) },
                                                    onLongPress = {
                                                        selectedItem = it
                                                        showBottomSheet = true
                                                    },
                                                    onSubTaskToggle = { ddayItem, index ->
                                                        viewModel.toggleSubTask(ddayItem, index)
                                                    },
                                                    infoText = nextDateInfo
                                                )
                                            }
                                        }
                                      }
                                    }
                                }
                            }
                        }
                    }

                    // 반복 일정 섹션 (숨겨진 매월/매년 항목)
                    if (hiddenDdays.isNotEmpty()) {
                        item(key = "header_hidden") {
                            SectionHeader(
                                title = "반복 일정",
                                count = hiddenDdays.size,
                                isExpandable = true,
                                isExpanded = isHiddenExpanded,
                                onToggle = { isHiddenExpanded = !isHiddenExpanded }
                            )
                        }

                        if (isHiddenExpanded) {
                            items(
                                items = hiddenDdays,
                                key = { "hidden_${it.id}" }
                            ) { item ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RectangleShape,
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    )
                                ) {
                                    val showDateText = item.nextShowDate?.let { showDate ->
                                        val cal = java.util.Calendar.getInstance().apply {
                                            timeInMillis = showDate
                                        }
                                        "다음: ${cal.get(java.util.Calendar.YEAR)}년 ${cal.get(java.util.Calendar.MONTH) + 1}월 ${cal.get(java.util.Calendar.DAY_OF_MONTH)}일"
                                    }
                                    DdayListItem(
                                        item = item,
                                        onToggle = { viewModel.toggleChecked(it) },
                                        onLongPress = {
                                            selectedItem = it
                                            showBottomSheet = true
                                        },
                                        onSubTaskToggle = { ddayItem, index ->
                                            viewModel.toggleSubTask(ddayItem, index)
                                        },
                                        showCheckbox = false,
                                        infoText = showDateText
                                    )
                                }
                            }
                        }
                    }

                    // 완료 섹션
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

                        if (isCompletedExpanded) {
                            items(
                                items = completedItems,
                                key = { it.id }
                            ) { item ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RectangleShape,
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    )
                                ) {
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
                } // PullToRefreshBox
            }
            } // Column (HorizontalPager page)
            } // HorizontalPager
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
                viewModel = viewModel,
                onEmojiChanged = { groupEmojiVersion++ }
            )
        }

        // 템플릿 관리 다이얼로그
        if (showTemplateManageDialog) {
            TemplateManageDialog(
                templates = templates,
                onDismiss = { showTemplateManageDialog = false },
                onRenameTemplate = { template, newName ->
                    viewModel.renameTemplate(template, newName)
                },
                onDeleteTemplate = { template ->
                    viewModel.deleteTemplate(template)
                }
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
    val context = LocalContext.current
    val fontScale = DdaySettings.getAppFontScale(context)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isExpandable) Modifier.clickable { onToggle() }
                else Modifier
            )
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontSize = (14 * fontScale).sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "($count)",
                style = MaterialTheme.typography.bodySmall,
                fontSize = (12 * fontScale).sp,
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
    onToggle: () -> Unit,
    emojiVersion: Int = 0
) {
    val context = LocalContext.current
    val groupEmoji = remember(groupName, emojiVersion) {
        DdaySettings.getGroupEmoji(context, groupName)
    }
    val fontScale = DdaySettings.getAppFontScale(context)

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
                text = groupEmoji,
                fontSize = (14 * fontScale).sp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = groupName,
                style = MaterialTheme.typography.titleSmall,
                fontSize = (14 * fontScale).sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "($count)",
                style = MaterialTheme.typography.bodySmall,
                fontSize = (12 * fontScale).sp,
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
    viewModel: DdayViewModel,
    onEmojiChanged: () -> Unit = {}
) {
    val context = LocalContext.current
    var editingGroup by remember { mutableStateOf<String?>(null) }
    var editingName by remember { mutableStateOf("") }
    var deleteConfirmGroup by remember { mutableStateOf<String?>(null) }
    var deleteGroupItemCount by remember { mutableStateOf(0) }
    var emojiPickerGroup by remember { mutableStateOf<String?>(null) }
    var emojiVersion by remember { mutableStateOf(0) }
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
                        items(groups, key = { "${it}_$emojiVersion" }) { groupName ->
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
                                },
                                onEmojiClick = {
                                    emojiPickerGroup = groupName
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

    // 그룹 이모지 피커
    if (emojiPickerGroup != null) {
        val currentEmoji = DdaySettings.getGroupEmoji(context, emojiPickerGroup!!)
        EmojiPickerDialog(
            currentEmoji = currentEmoji,
            categoryColor = MaterialTheme.colorScheme.primary,
            onEmojiSelected = { emoji ->
                DdaySettings.setGroupEmoji(context, emojiPickerGroup!!, emoji)
                emojiVersion++
                onEmojiChanged()
                DdayWidgetProvider.refreshAllWidgets(context)
            },
            onDismiss = { emojiPickerGroup = null }
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
    onDeleteClick: () -> Unit,
    onEmojiClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val groupEmoji = DdaySettings.getGroupEmoji(context, groupName)

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
            // 그룹 이모지 (클릭하여 변경)
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onEmojiClick() },
                contentAlignment = Alignment.Center
            ) {
                Text(groupEmoji, fontSize = 18.sp)
            }
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

/**
 * 템플릿 관리 다이얼로그
 * - 템플릿 목록 표시
 * - 템플릿 이름 변경
 * - 템플릿 삭제
 */
@Composable
private fun TemplateManageDialog(
    templates: List<TodoTemplate>,
    onDismiss: () -> Unit,
    onRenameTemplate: (TodoTemplate, String) -> Unit,
    onDeleteTemplate: (TodoTemplate) -> Unit
) {
    var editingTemplate by remember { mutableStateOf<TodoTemplate?>(null) }
    var editingName by remember { mutableStateOf("") }
    var deleteConfirmTemplate by remember { mutableStateOf<TodoTemplate?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📋", fontSize = 20.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("템플릿 관리")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
            ) {
                if (templates.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "저장된 템플릿이 없습니다",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(templates) { template ->
                            TemplateManageItem(
                                template = template,
                                isEditing = editingTemplate?.id == template.id,
                                editingName = if (editingTemplate?.id == template.id) editingName else template.name,
                                onEditStart = {
                                    editingTemplate = template
                                    editingName = template.name
                                },
                                onEditChange = { editingName = it },
                                onEditConfirm = {
                                    if (editingName.isNotBlank() && editingName != template.name) {
                                        onRenameTemplate(template, editingName.trim())
                                    }
                                    editingTemplate = null
                                    editingName = ""
                                },
                                onEditCancel = {
                                    editingTemplate = null
                                    editingName = ""
                                },
                                onDeleteClick = {
                                    deleteConfirmTemplate = template
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
    if (deleteConfirmTemplate != null) {
        AlertDialog(
            onDismissRequest = { deleteConfirmTemplate = null },
            title = { Text("템플릿 삭제") },
            text = {
                Text("'${deleteConfirmTemplate?.name}' 템플릿을 삭제하시겠습니까?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteConfirmTemplate?.let { onDeleteTemplate(it) }
                        deleteConfirmTemplate = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("삭제")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmTemplate = null }) {
                    Text("취소")
                }
            }
        )
    }
}

@Composable
private fun TemplateManageItem(
    template: TodoTemplate,
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
            // 템플릿 아이콘
            Text(template.iconName, fontSize = 18.sp)
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
                    text = template.name,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                // 서브태스크 개수 표시
                val subTaskCount = template.getSubTaskList().size
                if (subTaskCount > 0) {
                    Text(
                        text = "${subTaskCount}개",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
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
