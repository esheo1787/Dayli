@file:Suppress("DEPRECATION")
package com.silverwest.dayli.ddaywidget

import android.util.Log
import androidx.compose.ui.text.style.TextAlign
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
import androidx.compose.ui.text.style.TextOverflow
import org.burnoutcrew.reorderable.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    onEditItem: (DdayItem) -> Unit = {},
    searchQuery: String = "",
    isCalendarView: Boolean = false,
    onAddForDate: (java.util.Date) -> Unit = {},
    twoPane: Boolean = false,  // 넓은 화면(폴드 펼침·태블릿): 왼쪽 목록 + 오른쪽 캘린더 항상 표시
    onImportPhoneEvent: (PhoneEvent) -> Unit = {},
    phoneImportVersion: Int = 0  // 가져오기가 끝나면 올라감 → "가져옴" 표시 갱신
) {
    val context = LocalContext.current
    // 2단에서는 캘린더가 오른쪽에 항상 있으므로 왼쪽은 목록만
    val showCalendarInList = isCalendarView && !twoPane

    // 위젯에서 변경된 데이터를 앱 복귀 시 자동 반영 (폰 캘린더 일정도 다시 읽음: 권한·설정 변경 반영)
    var phoneRefresh by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.loadAll()
                phoneRefresh++
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

    // 검색 필터 적용 (remember로 캐싱하여 불필요한 재계산 방지)
    val filteredDdays = remember(searchQuery, ddays) {
        if (searchQuery.isBlank()) ddays
        else ddays.filter { it.title.contains(searchQuery, ignoreCase = true) }
    }
    val filteredTodos = remember(searchQuery, todos) {
        if (searchQuery.isBlank()) todos
        else todos.filter { item ->
            item.title.contains(searchQuery, ignoreCase = true) ||
                item.getSubTaskList().any { it.title.contains(searchQuery, ignoreCase = true) }
        }
    }
    val filteredHiddenDdays = remember(searchQuery, hiddenDdays) {
        if (searchQuery.isBlank()) hiddenDdays
        else hiddenDdays.filter { it.title.contains(searchQuery, ignoreCase = true) }
    }
    val filteredHiddenTodos = remember(searchQuery, hiddenTodos) {
        if (searchQuery.isBlank()) hiddenTodos
        else hiddenTodos.filter { item ->
            item.title.contains(searchQuery, ignoreCase = true) ||
                item.getSubTaskList().any { it.title.contains(searchQuery, ignoreCase = true) }
        }
    }

    // 현재 탭에 따른 아이템 리스트
    val currentItems = if (pagerState.currentPage == 0) filteredDdays else filteredTodos

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
    val ddayPendingByGroup = remember(filteredDdays) {
        filteredDdays.filter { !it.isChecked }
            .groupBy { it.groupName ?: "미분류" }
            .toSortedMap(compareBy { if (it == "미분류") "zzz" else it })
    }

    // D-Day 그룹 드래그 순서 (안정적인 MutableState — To-Do와 동일 패턴)
    var groupOrder by remember { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(ddayPendingByGroup.keys.toSet()) {
        val savedOrder = DdaySettings.getGroupOrder(context)
        val ordered = mutableListOf<String>()
        savedOrder.forEach { name -> if (name in ddayPendingByGroup) ordered.add(name) }
        ddayPendingByGroup.keys.forEach { name -> if (name !in ordered) ordered.add(name) }
        groupOrder = ordered.toList()
    }

    // Reorderable 상태 (D-Day 그룹 드래그)
    val groupReorderableState = rememberReorderableLazyListState(
        onMove = { from, to ->
            val fromIndex = groupOrder.indexOf(groupNameFromKey(from.key))
            val toIndex = groupOrder.indexOf(groupNameFromKey(to.key))
            if (fromIndex >= 0 && toIndex >= 0) {
                groupOrder = groupOrder.toMutableList().apply {
                    add(toIndex, removeAt(fromIndex))
                }
            }
        },
        // 그룹끼리만 자리 바꿈 (가장 가까운 일정 카드·섹션 위로는 이동 불가)
        canDragOver = { draggedOver, _ -> groupNameFromKey(draggedOver.key) != null },
        onDragEnd = { _, _ ->
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
    var showShareDialog by remember { mutableStateOf(false) }

    // 가장 가까운 일정 카드 설정 (설정 변경 시 상위에서 key로 재구성됨)
    val heroEnabled = remember { DdaySettings.isHeroCardEnabled(context) }
    val heroColorSetting = remember { DdaySettings.getHeroCardColor(context) }

    // Snackbar 상태
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var deletedItem by remember { mutableStateOf<DdayItem?>(null) }

    // Pull to Refresh 상태
    var isRefreshing by remember { mutableStateOf(false) }

    // D-Day 캘린더 (1단: 목록 대신 / 2단: 오른쪽에 항상)
    // 월·선택 날짜는 여기서 보관 → 두 배치가 같은 상태를 써서 1단↔2단 전환에도 유지
    val calendarState = rememberCalendarState()

    // 폰 캘린더(구글·삼성) 일정: 보고 있는 달 앞뒤로 넉넉히 읽음
    val phoneEvents by produceState(
        initialValue = emptyList<PhoneEvent>(),
        calendarState.year, calendarState.month, phoneRefresh, Premium.unlocked
    ) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val start = java.util.Calendar.getInstance().apply {
                clear(); set(calendarState.year, calendarState.month, 1); add(java.util.Calendar.DAY_OF_MONTH, -7)
            }
            val end = (start.clone() as java.util.Calendar).apply { add(java.util.Calendar.DAY_OF_MONTH, 7 + 31 + 7) }
            PhoneCalendar.enabledEvents(context, start.timeInMillis, end.timeInMillis)
        }
    }
    // 가져온 D-Day가 지워졌으면 "가져옴"을 풀어 다시 가져올 수 있게 (남아 있는 D-Day 번호로 거름, 검색과 무관하게 전체 기준)
    val existingDdayIds = remember(ddays, hiddenDdays) { (ddays + hiddenDdays).map { it.id }.toSet() }
    val importedPhoneKeys = remember(phoneImportVersion, phoneRefresh, existingDdayIds) {
        DdaySettings.importedPhoneKeys(context, existingDdayIds)
    }

    val calendarContent: @Composable () -> Unit = {
        DdayCalendarView(
            ddayItems = filteredDdays + filteredHiddenDdays,
            onToggle = { viewModel.toggleChecked(it) },
            onLongPress = {
                selectedItem = it
                showBottomSheet = true
            },
            onSubTaskToggle = { ddayItem, index ->
                viewModel.toggleSubTask(ddayItem, index)
            },
            expandedSubTaskIds = expandedSubTaskIds,
            onExpandSubTask = { id ->
                expandedSubTaskIds = if (id in expandedSubTaskIds)
                    expandedSubTaskIds - id
                else expandedSubTaskIds + id
            },
            searchQuery = searchQuery,
            onAddForDate = onAddForDate,
            state = calendarState,
            phoneEvents = phoneEvents,
            importedPhoneKeys = importedPhoneKeys,
            onImportPhoneEvent = onImportPhoneEvent
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = 80.dp)  // FAB 높이만큼 여백
            )
        }
    ) { paddingValues ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            // D-Day / To-Do 전환 (개수 표시)
            SegmentedTabs(
                labels = listOf("D-Day", "To-Do"),
                counts = listOf(ddays.count { !it.isChecked }, todos.count { !it.isChecked }),
                selected = pagerState.currentPage,
                onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )

            // 탭 콘텐츠 (스와이프로 전환 가능)
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
            Column(modifier = Modifier.fillMaxSize()) {
            // D-Day 탭일 때만 정렬 옵션 표시 (리스트 모드만)
            if (page == 0 && !showCalendarInList) {
                ListToolbar(
                    summary = "전체 ${filteredDdays.count { !it.isChecked }}개",
                    sortLabel = if (currentSort == SortOption.FARTHEST) "여유순" else "임박순",
                    sortOptions = listOf("임박순" to { viewModel.setSortOption(SortOption.NEAREST) }, "여유순" to { viewModel.setSortOption(SortOption.FARTHEST) }),
                    actionLabel = "그룹",
                    onAction = { showGroupManageDialog = true }
                )
            }

            // To-Do 탭일 때 정렬 옵션 + 템플릿 관리 표시
            if (page == 1) {
                ListToolbar(
                    summary = "남은 할 일 ${filteredTodos.count { !it.isChecked }}개",
                    sortLabel = when (currentTodoSort) {
                        TodoSortOption.INCOMPLETE_FIRST -> "미완료순"
                        TodoSortOption.LATEST -> "최근 추가"
                        else -> "내 순서"
                    },
                    sortOptions = listOf(
                        "내 순서" to { viewModel.setTodoSortOption(TodoSortOption.MY_ORDER) },
                        "미완료순" to { viewModel.setTodoSortOption(TodoSortOption.INCOMPLETE_FIRST) },
                        "최근 추가" to { viewModel.setTodoSortOption(TodoSortOption.LATEST) }
                    ),
                    actionLabel = "템플릿",
                    onAction = { showTemplateManageDialog = true }
                )
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
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp)  // FAB 겹침 방지
                ) {
                    // 검색 결과 없음 표시 (To-Do 탭)
                    if (searchQuery.isNotBlank() && filteredTodos.isEmpty() && filteredHiddenTodos.isEmpty()) {
                        item(key = "no_results_todo") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "검색 결과가 없습니다",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // 빈 상태 온보딩 (To-Do 탭) — 검색 아니고 데이터 0개일 때
                    val todoFullyEmpty = searchQuery.isBlank() &&
                        todos.isEmpty() && hiddenTodos.isEmpty()
                    if (todoFullyEmpty) {
                        item(key = "onboarding_todo") {
                            OnboardingEmptyState(itemType = ItemType.TODO)
                        }
                    }

                    if (!todoFullyEmpty) {
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
                                shape = RoundedCornerShape(12.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = elevation),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color.Transparent
                                )
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // 드래그 핸들 (내 순서 + 검색 중이 아닐 때만 표시)
                                    if (currentTodoSort == TodoSortOption.MY_ORDER && searchQuery.isBlank()) {
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
                                            infoText = nextDateInfo,
                                            searchQuery = searchQuery
                                        )
                                    }
                                }
                            }
                        }
                    }
                    }

                    // 반복 일정 섹션 (숨겨진 매주/매월/매년 To-Do)
                    if (filteredHiddenTodos.isNotEmpty()) {
                        item(key = "header_hidden_todo") {
                            SectionHeader(
                                title = "반복 일정",
                                count = filteredHiddenTodos.size,
                                isExpandable = true,
                                isExpanded = isHiddenTodoExpanded,
                                onToggle = { isHiddenTodoExpanded = !isHiddenTodoExpanded }
                            )
                        }

                        if (isHiddenTodoExpanded) {
                            items(
                                items = filteredHiddenTodos,
                                key = { "hidden_todo_${it.id}" }
                            ) { item ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color.Transparent
                                    )
                                ) {
                                    val showDateText = item.nextShowDate?.let { showDate ->
                                        val cal = java.util.Calendar.getInstance().apply {
                                            timeInMillis = showDate
                                        }
                                        "📅 ${cal.get(java.util.Calendar.YEAR)}년 ${cal.get(java.util.Calendar.MONTH) + 1}월 ${cal.get(java.util.Calendar.DAY_OF_MONTH)}일 표시 예정"
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
                                        infoText = showDateText,
                                        searchQuery = searchQuery
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
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color.Transparent
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
                                        },
                                        searchQuery = searchQuery
                                    )
                                }
                            }
                        }
                    }
                }
                } // PullToRefreshBox
            } else if (showCalendarInList) {
                // D-Day 캘린더 뷰 모드
                calendarContent()
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
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp)
                ) {
                    // 검색 결과 없음 표시 (D-Day 탭)
                    if (searchQuery.isNotBlank() && filteredDdays.isEmpty() && filteredHiddenDdays.isEmpty()) {
                        item(key = "no_results_dday") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "검색 결과가 없습니다",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // 빈 상태 온보딩 (D-Day 탭) — 검색 아니고 데이터 0개일 때
                    val ddayFullyEmpty = searchQuery.isBlank() &&
                        ddays.isEmpty() && hiddenDdays.isEmpty()
                    if (ddayFullyEmpty) {
                        item(key = "onboarding_dday") {
                            OnboardingEmptyState(itemType = ItemType.DDAY)
                        }
                    }

                    if (!ddayFullyEmpty) {
                        val heroItem = if (heroEnabled && searchQuery.isBlank()) DayliUi.nearestUpcoming(ddays) else null
                        if (heroItem != null) {
                            item(key = "hero_card") {
                                HeroCard(
                                    item = heroItem,
                                    colorSetting = heroColorSetting,
                                    fontScale = DdaySettings.getAppFontScale(context),
                                    onClick = {
                                        selectedItem = heroItem
                                        showBottomSheet = true
                                    }
                                )
                            }
                        }
                    items(
                        items = orderedGroupList,
                        key = { "group_${it.first}" }
                    ) { (groupName, groupItems) ->
                        ReorderableItem(groupReorderableState, key = "group_${groupName}") { isDragging ->
                            val isGroupExpanded = expandedGroups.contains(groupName)
                            val elevation = if (isDragging) 8.dp else 0.dp
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = elevation),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isDragging) MaterialTheme.colorScheme.background else Color.Transparent
                                )
                            ) {
                                Column {
                                    // 그룹 헤더 (오른쪽 손잡이를 끌어 그룹 순서 이동)
                                    GroupHeader(
                                        groupName = groupName,
                                        count = groupItems.size,
                                        isExpanded = isGroupExpanded,
                                        emojiVersion = groupEmojiVersion,
                                        dragHandleModifier = Modifier.detectReorder(groupReorderableState),
                                        onToggle = {
                                            expandedGroups = if (isGroupExpanded) {
                                                expandedGroups - groupName
                                            } else {
                                                expandedGroups + groupName
                                            }
                                            DdaySettings.toggleGroupCollapsed(context, groupName)
                                        }
                                    )
                                    // 그룹 내 항목들
                                    if (isGroupExpanded) {
                                      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        groupItems.forEach { item ->
                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(12.dp),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = Color.Transparent
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
                                                    infoText = nextDateInfo,
                                                    searchQuery = searchQuery
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
                    if (filteredHiddenDdays.isNotEmpty()) {
                        item(key = "header_hidden") {
                            SectionHeader(
                                title = "반복 일정",
                                count = filteredHiddenDdays.size,
                                isExpandable = true,
                                isExpanded = isHiddenExpanded,
                                onToggle = { isHiddenExpanded = !isHiddenExpanded }
                            )
                        }

                        if (isHiddenExpanded) {
                            items(
                                items = filteredHiddenDdays,
                                key = { "hidden_${it.id}" }
                            ) { item ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color.Transparent
                                    )
                                ) {
                                    val showDateText = item.nextShowDate?.let { showDate ->
                                        val cal = java.util.Calendar.getInstance().apply {
                                            timeInMillis = showDate
                                        }
                                        "📅 ${cal.get(java.util.Calendar.YEAR)}년 ${cal.get(java.util.Calendar.MONTH) + 1}월 ${cal.get(java.util.Calendar.DAY_OF_MONTH)}일 표시 예정"
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
                                        infoText = showDateText,
                                        searchQuery = searchQuery
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
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color.Transparent
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
                                        searchQuery = searchQuery
                                    )
                                }
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
        if (twoPane) {
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                calendarContent()
            }
        }
        } // Row (2단)

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

                    // 공유 버튼
                    Surface(
                        onClick = {
                            showShareDialog = true
                        }
                    ) {
                        ListItem(
                            headlineContent = { Text("공유") },
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "공유"
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

        // 공유 방식 선택 다이얼로그
        if (showShareDialog && selectedItem != null) {
            AlertDialog(
                onDismissRequest = {
                    showShareDialog = false
                    selectedItem = null
                },
                title = { Text("공유") },
                text = {
                    Column {
                        Surface(
                            onClick = {
                                selectedItem?.let { DdayShareHelper.shareImage(context, it) }
                                showShareDialog = false
                                showBottomSheet = false
                                selectedItem = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ListItem(
                                headlineContent = { Text("이미지로 공유") },
                                leadingContent = {
                                    Icon(Icons.Default.Image, contentDescription = null)
                                }
                            )
                        }
                        Surface(
                            onClick = {
                                selectedItem?.let { DdayShareHelper.shareText(context, it) }
                                showShareDialog = false
                                showBottomSheet = false
                                selectedItem = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ListItem(
                                headlineContent = { Text("텍스트로 공유") },
                                leadingContent = {
                                    Icon(Icons.Default.Share, contentDescription = null)
                                }
                            )
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = {
                        showShareDialog = false
                        selectedItem = null
                    }) { Text("취소") }
                }
            )
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
                onAddGroup = { name, emoji ->
                    viewModel.addGroup(name, emoji)
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
                },
                onUpdateEmoji = { template, emoji ->
                    viewModel.updateTemplate(template.copy(iconName = emoji))
                },
                onEmojiChanged = { viewModel.loadAll() }
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
            .clip(RoundedCornerShape(8.dp))
            .then(if (isExpandable) Modifier.clickable { onToggle() } else Modifier)
            .heightIn(min = 40.dp)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = (14 * fontScale).sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(6.dp))
        CountBadge(count, fontScale)
        Spacer(modifier = Modifier.weight(1f))
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
    emojiVersion: Int = 0,
    dragHandleModifier: Modifier? = null
) {
    val context = LocalContext.current
    val groupEmoji = remember(groupName, emojiVersion) {
        DdaySettings.getGroupEmoji(context, groupName)
    }
    val fontScale = DdaySettings.getAppFontScale(context)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onToggle() }
            .heightIn(min = 40.dp)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 이름 + 개수는 왼쪽 묶음(긴 이름은 말줄임), 화살표는 오른쪽 끝 고정
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            EmojiText(groupEmoji, fontSize = (15 * fontScale).sp)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = groupName,
                fontSize = (14.5f * fontScale).sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(modifier = Modifier.width(6.dp))
            CountBadge(count, fontScale)
        }
        // 그룹 순서 손잡이: 작고 흐리게 (끌기 전용, 누르면 접기/펼치기와 겹치지 않음)
        dragHandleModifier?.let { handle ->
            Icon(
                imageVector = Icons.Default.DragIndicator,
                contentDescription = "그룹 순서 바꾸기",
                modifier = handle
                    .size(40.dp)
                    .padding(10.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
            )
        }
        Icon(
            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = if (isExpanded) "접기" else "펼치기",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** LazyColumn 그룹 항목 키("group_이름") → 그룹 이름. 그룹 항목이 아니면 null */
internal fun groupNameFromKey(key: Any?): String? =
    (key as? String)?.takeIf { it.startsWith(GROUP_KEY_PREFIX) }?.removePrefix(GROUP_KEY_PREFIX)

private const val GROUP_KEY_PREFIX = "group_"

/** 목록 위 한 줄: 개수 요약 + 정렬 메뉴 + 관리 버튼 */
@Composable
private fun ListToolbar(
    summary: String,
    sortLabel: String,
    sortOptions: List<Pair<String, () -> Unit>>,
    actionLabel: String,
    onAction: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(summary, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Box {
            ToolbarChip(text = sortLabel, trailingArrow = true, onClick = { menuOpen = true })
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                sortOptions.forEach { (label, action) ->
                    DropdownMenuItem(
                        text = { Text(label, fontWeight = if (label == sortLabel) FontWeight.Bold else FontWeight.Normal) },
                        onClick = {
                            menuOpen = false
                            action()
                        }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(6.dp))
        ToolbarChip(text = actionLabel, trailingArrow = false, onClick = onAction)
    }
}

@Composable
private fun ToolbarChip(text: String, trailingArrow: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
        if (trailingArrow) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableDdayItem(
    item: DdayItem,
    onDelete: () -> Unit,
    onToggle: (DdayItem) -> Unit,
    onLongPress: (DdayItem) -> Unit,
    onSubTaskToggle: (DdayItem, Int) -> Unit = { _, _ -> },
    searchQuery: String = ""
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
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.Transparent
            )
        ) {
            DdayListItem(
                item = item,
                onToggle = onToggle,
                onLongPress = onLongPress,
                onSubTaskToggle = onSubTaskToggle,
                searchQuery = searchQuery
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
        EmojiText(emoji, fontSize = 14.sp)
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
 * - 새 그룹 만들기 (D-Day 없이도 미리 만들어 둘 수 있음)
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
    onAddGroup: (groupName: String, emoji: String) -> Unit,
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
    // 새 그룹 입력 (펼쳤을 때만 입력칸 표시)
    var isAdding by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newEmoji by remember { mutableStateOf("📁") }
    var showNewEmojiPicker by remember { mutableStateOf(false) }
    val trimmedNewName = newName.trim()
    val isDuplicate = groups.any { it == trimmedNewName }

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
                    .heightIn(max = 460.dp)
            ) {
                // 새 그룹 만들기
                if (isAdding) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surface)
                                        .clickable { showNewEmojiPicker = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    EmojiText(newEmoji, fontSize = 24.sp)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                OutlinedTextField(
                                    value = newName,
                                    onValueChange = { newName = it },
                                    modifier = Modifier.weight(1f),
                                    placeholder = { Text("예: 학교, 여행, 가족") },
                                    singleLine = true,
                                    isError = isDuplicate,
                                    textStyle = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Text(
                                text = if (isDuplicate) "이미 있는 그룹이에요" else "이모지를 누르면 바꿀 수 있어요",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isDuplicate) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = {
                                    isAdding = false
                                    newName = ""
                                    newEmoji = "📁"
                                }) { Text("취소") }
                                Button(
                                    onClick = {
                                        onAddGroup(trimmedNewName, newEmoji)
                                        isAdding = false
                                        newName = ""
                                        newEmoji = "📁"
                                    },
                                    enabled = trimmedNewName.isNotEmpty() && !isDuplicate
                                ) { Text("만들기") }
                            }
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = { isAdding = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("새 그룹 만들기")
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                if (groups.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "아직 그룹이 없어요\n그룹을 만들면 D-Day를 묶어서 볼 수 있어요",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(groups, key = { "${it}_$emojiVersion" }) { groupName ->
                            val isEditingThis = editingGroup == groupName
                            val renameCheck = if (isEditingThis) GroupNames.checkRename(groupName, editingName, groups) else null
                            GroupManageItem(
                                groupName = groupName,
                                isEditing = isEditingThis,
                                editingName = if (isEditingThis) editingName else groupName,
                                errorText = if (renameCheck == GroupNames.RenameCheck.Duplicate) "이미 있는 그룹이에요" else null,
                                onEditStart = {
                                    editingGroup = groupName
                                    editingName = groupName
                                },
                                onEditChange = { editingName = it },
                                onEditConfirm = {
                                    when (renameCheck) {
                                        is GroupNames.RenameCheck.Ok -> {
                                            onRenameGroup(groupName, renameCheck.newName)
                                            editingGroup = null
                                            editingName = ""
                                        }
                                        GroupNames.RenameCheck.Unchanged -> {
                                            editingGroup = null
                                            editingName = ""
                                        }
                                        // 중복·빈 이름은 입력칸을 연 채로 둠 (오류 문구 표시)
                                        else -> Unit
                                    }
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

    // 새 그룹 이모지 피커
    if (showNewEmojiPicker) {
        EmojiPickerDialog(
            currentEmoji = newEmoji,
            categoryColor = MaterialTheme.colorScheme.primary,
            onEmojiSelected = { newEmoji = it },
            onDismiss = { showNewEmojiPicker = false }
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
    onEmojiClick: () -> Unit = {},
    errorText: String? = null
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
                EmojiText(groupEmoji, fontSize = 18.sp)
            }
            Spacer(modifier = Modifier.width(8.dp))

            if (isEditing) {
                // 편집 모드
                OutlinedTextField(
                    value = editingName,
                    onValueChange = onEditChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    isError = errorText != null,
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
        // 오류 문구는 줄 전체 폭으로 (입력칸 아래에 두면 좁아서 줄이 바뀜)
        if (isEditing && errorText != null) {
            Text(
                text = errorText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 52.dp, end = 12.dp, bottom = 8.dp)
            )
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
    onDeleteTemplate: (TodoTemplate) -> Unit,
    onUpdateEmoji: (TodoTemplate, String) -> Unit = { _, _ -> },
    onEmojiChanged: () -> Unit = {}
) {
    var editingTemplate by remember { mutableStateOf<TodoTemplate?>(null) }
    var editingName by remember { mutableStateOf("") }
    var deleteConfirmTemplate by remember { mutableStateOf<TodoTemplate?>(null) }
    var emojiPickerTemplate by remember { mutableStateOf<TodoTemplate?>(null) }
    var emojiVersion by remember { mutableStateOf(0) }

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
                        items(templates, key = { "${it.id}_$emojiVersion" }) { template ->
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
                                },
                                onEmojiClick = {
                                    emojiPickerTemplate = template
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

    // 템플릿 이모지 피커
    if (emojiPickerTemplate != null) {
        EmojiPickerDialog(
            currentEmoji = emojiPickerTemplate!!.iconName,
            categoryColor = MaterialTheme.colorScheme.primary,
            onEmojiSelected = { emoji ->
                emojiPickerTemplate?.let { onUpdateEmoji(it, emoji) }
                emojiVersion++
                onEmojiChanged()
            },
            onDismiss = { emojiPickerTemplate = null }
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
    onDeleteClick: () -> Unit,
    onEmojiClick: () -> Unit = {}
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
            // 템플릿 아이콘 (클릭하여 변경)
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onEmojiClick() },
                contentAlignment = Alignment.Center
            ) {
                EmojiText(template.iconName, fontSize = 18.sp)
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

/**
 * 신규 사용자용 빈 상태 온보딩. 예시 카드 3개와 안내 문구를 보여준다.
 * - 클릭 가능한 영역은 없음 (예시는 시각적 가이드용)
 * - 사용자는 우측 하단 + FAB로 실제 항목을 추가
 */
@Composable
private fun OnboardingEmptyState(itemType: ItemType) {
    val examples = if (itemType == ItemType.DDAY) {
        listOf(
            Triple("📚", "수능", "D-100"),
            Triple("🎂", "엄마 생신", "D-30"),
            Triple("✈️", "제주도 여행", "D-14"),
        )
    } else {
        listOf(
            Triple("💪", "스쿼트 30회", null),
            Triple("📖", "독서 30쪽", null),
            Triple("💧", "물 1리터 마시기", null),
        )
    }
    val title = if (itemType == ItemType.DDAY) "첫 D-Day를 추가해보세요"
                else "첫 할 일을 추가해보세요"
    val subtitle = if (itemType == ItemType.DDAY)
        "중요한 날까지 며칠 남았는지 한눈에 확인할 수 있어요"
    else
        "오늘 할 일을 정리하고 위젯에서 바로 체크하세요"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "이런 식으로 만들 수 있어요",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))

        examples.forEach { (emoji, text, dday) ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EmojiText(emoji, fontSize = 22.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.weight(1f)
                    )
                    if (dday != null) {
                        Text(
                            text = dday,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "오른쪽 아래 + 버튼으로 추가하세요",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
        )
    }
}
