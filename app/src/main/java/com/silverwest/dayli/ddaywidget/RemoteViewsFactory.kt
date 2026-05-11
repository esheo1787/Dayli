package com.silverwest.dayli.ddaywidget

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.silverwest.dayli.R
import com.silverwest.dayli.ui.theme.isDarkMode
import kotlinx.coroutines.runBlocking
import java.util.*
import java.text.SimpleDateFormat

// 위젯 행 타입 (헤더 / 그룹헤더 / 아이템 / To-Do헤더 / 서브태스크)
sealed class WidgetRow {
    data class Header(val title: String) : WidgetRow()
    data class GroupHeader(val groupName: String, val isCollapsed: Boolean = false) : WidgetRow()  // D-Day 그룹 헤더
    data class Item(val item: DdayItem, val showProgress: Boolean = false) : WidgetRow()  // showProgress: To-Do 진행률 표시
    data class TodoHeader(val item: DdayItem, val completedCount: Int, val totalCount: Int, val isCollapsed: Boolean = false) : WidgetRow()  // To-Do 위젯 헤더
    data class SubTaskItem(val parentItem: DdayItem, val subTask: SubTask, val subTaskIndex: Int) : WidgetRow()  // To-Do 서브태스크
}

class RemoteViewsFactory(
    private val context: Context,
    private val intent: Intent? = null
) : RemoteViewsService.RemoteViewsFactory {

    private var items: List<DdayItem> = emptyList()
    private var displayRows: List<WidgetRow> = emptyList()

    // Widget mode from intent (MODE_ALL, MODE_DDAY, MODE_TODO)
    private val mode: String = intent?.getStringExtra(DdayOnlyWidgetProvider.EXTRA_WIDGET_MODE)
        ?: DdayOnlyWidgetProvider.MODE_ALL

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_ITEM = 1
        private const val VIEW_TYPE_GROUP_HEADER = 2
        private const val VIEW_TYPE_TODO_HEADER = 3
        private const val VIEW_TYPE_SUBTASK = 4
    }

    override fun onCreate() {
        android.util.Log.d("DDAY_WIDGET", "📦 RemoteViewsFactory created with mode: $mode")
    }

    override fun onDataSetChanged() {
        android.util.Log.d("WIDGET_PIPE", "onDataSetChanged")
        android.util.Log.d("DDAY_WIDGET", "📦 RemoteViewsFactory.onDataSetChanged() 호출됨 (mode=$mode)")

        try {
            runBlocking {
                val db = DdayDatabase.getDatabase(context)
                val dao = db.ddayDao()

                // 숨겨진 반복 항목: stale nextShowDate 재계산 후 자동 표시
                // 핸들러와 동일한 effective 식을 사용 (advanceDays는 item.getAdvanceDays() 기반)
                val now = System.currentTimeMillis()
                dao.getHiddenDdays().forEach { item ->
                    val date = item.date ?: return@forEach
                    if (item.repeatTypeEnum() == RepeatType.NONE) return@forEach
                    val effectiveShowDate = DdayRepeatHandler.computeEffectiveShowDate(item, date, now)
                    if (item.nextShowDate != effectiveShowDate) {
                        dao.update(item.copy(nextShowDate = effectiveShowDate))
                    }
                }
                dao.unhideReadyItems(System.currentTimeMillis())

                // 24시간 전 타임스탬프 계산 (To-Do 체크 후 24시간 유지용)
                val cutoffTime = System.currentTimeMillis() - 24 * 60 * 60 * 1000

                // D-Day: 체크 즉시 숨김 / To-Do: 체크 후 24시간 유지
                val allItems = dao.getAllForWidgetWithTodos(cutoffTime)

                // mode에 따라 필터링
                items = when (mode) {
                    DdayOnlyWidgetProvider.MODE_DDAY -> allItems.filter { it.isDday() }
                    DdayOnlyWidgetProvider.MODE_TODO -> allItems.filter { it.isTodo() }
                    else -> allItems  // MODE_ALL: 전체 표시
                }

                // 통합 위젯(MODE_ALL)일 때만 섹션 헤더 삽입
                // 위젯은 앱 정렬 설정과 무관하게 항상 사용자 순서로 표시
                // D-Day: 임박순 (date ASC), To-Do: 드래그 순서 (sortOrder ASC)
                // 앱 정렬 설정 읽기
                val savedGroupOrder = DdaySettings.getGroupOrder(context)
                val ddaySortOption = DdaySettings.getDdaySort(context)

                displayRows = if (mode == DdayOnlyWidgetProvider.MODE_ALL) {
                    val ddayItems = items.filter { it.isDday() && !it.isChecked }
                    // To-Do: DB에서 이미 24시간 이내 체크된 항목만 포함되므로 추가 필터 불필요
                    val todoItems = items.filter { it.isTodo() }
                        .sortedWith(compareBy<DdayItem> { it.isChecked }.thenBy { it.sortOrder }.thenByDescending { it.id })
                    buildList {
                        // D-Day 섹션: 그룹별로 2개씩
                        if (ddayItems.isNotEmpty()) {
                            add(WidgetRow.Header("D-Day"))

                            // 그룹별로 묶기
                            val groupedDdays = ddayItems.groupBy { it.groupName ?: "미분류" }

                            // 앱 드래그 순서로 그룹 정렬
                            val orderedGroups = mutableListOf<String>()
                            savedGroupOrder.forEach { name -> if (name in groupedDdays) orderedGroups.add(name) }
                            groupedDdays.keys.forEach { name -> if (name !in orderedGroups) orderedGroups.add(name) }

                            orderedGroups.forEach { groupName ->
                                val groupItems = groupedDdays[groupName] ?: return@forEach
                                // 그룹 헤더 추가
                                add(WidgetRow.GroupHeader(groupName))
                                // 정렬 옵션에 따라 그룹 내 아이템 정렬 후 최대 2개만
                                val sortedItems = if (ddaySortOption == "FARTHEST") {
                                    groupItems.sortedByDescending { it.date }
                                } else {
                                    groupItems.sortedBy { it.date }
                                }
                                sortedItems.take(2).forEach { item ->
                                    add(WidgetRow.Item(item))
                                }
                            }
                        }
                        // To-Do 섹션: 진행현황 표시
                        if (todoItems.isNotEmpty()) {
                            add(WidgetRow.Header("To-Do"))
                            todoItems.forEach { item ->
                                add(WidgetRow.Item(item, showProgress = true))
                            }
                        }
                    }
                } else if (mode == DdayOnlyWidgetProvider.MODE_DDAY) {
                    // D-Day 전용 위젯: 그룹별로 표시 (접기/펼치기 지원)
                    val ddayItems = items.filter { !it.isChecked }
                    if (ddayItems.isEmpty()) {
                        emptyList()  // emptyView에서 안내 문구 표시
                    } else {
                    val collapsedGroups = DdaySettings.getCollapsedGroups(context)
                    buildList {
                        // D-Day 전용 위젯 헤더
                        add(WidgetRow.Header("D-Day"))
                        // 그룹별로 묶기
                        val groupedDdays = ddayItems.groupBy { it.groupName ?: "미분류" }

                        // 앱 드래그 순서로 그룹 정렬
                        val orderedGroups = mutableListOf<String>()
                        savedGroupOrder.forEach { name -> if (name in groupedDdays) orderedGroups.add(name) }
                        groupedDdays.keys.forEach { name -> if (name !in orderedGroups) orderedGroups.add(name) }

                        orderedGroups.forEach { groupName ->
                            val groupItems = groupedDdays[groupName] ?: return@forEach
                            val isCollapsed = collapsedGroups.contains(groupName)
                            // 그룹 헤더 추가 (접힘 상태 포함)
                            add(WidgetRow.GroupHeader(groupName, isCollapsed))
                            // 접혀있지 않으면 항목들 표시 (정렬 옵션 적용)
                            if (!isCollapsed) {
                                val sortedItems = if (ddaySortOption == "FARTHEST") {
                                    groupItems.sortedByDescending { it.date }
                                } else {
                                    groupItems.sortedBy { it.date }
                                }
                                sortedItems.forEach { item ->
                                    add(WidgetRow.Item(item))
                                }
                            }
                        }
                    }
                    }
                } else {
                    // To-Do 전용 위젯: 체크리스트 표시 (드래그 순서)
                    val sortedItems = items.sortedWith(
                        compareBy<DdayItem> { it.isChecked }.thenBy { it.sortOrder }.thenByDescending { it.id }
                    )
                    if (sortedItems.isEmpty()) {
                        emptyList()  // emptyView에서 안내 문구 표시
                    } else {
                    val collapsedTodos = DdaySettings.getCollapsedTodos(context)
                    buildList {
                        // To-Do 전용 위젯 헤더
                        add(WidgetRow.Header("To-Do"))
                        sortedItems.forEach { item ->
                            val subTasks = item.getSubTaskList()
                            if (subTasks.isNotEmpty()) {
                                // 체크리스트가 있는 To-Do: TodoHeader + SubTaskItem들
                                val completedCount = subTasks.count { it.isChecked }
                                val isCollapsed = collapsedTodos.contains(item.id.toString())
                                add(WidgetRow.TodoHeader(item, completedCount, subTasks.size, isCollapsed))
                                // 접혀있지 않으면 서브태스크 표시
                                if (!isCollapsed) {
                                    subTasks.withIndex().sortedBy { it.value.isChecked }.forEach { (originalIndex, subTask) ->
                                        add(WidgetRow.SubTaskItem(item, subTask, originalIndex))
                                    }
                                }
                            } else {
                                // 체크리스트가 없는 To-Do: 일반 아이템으로 표시
                                add(WidgetRow.Item(item))
                            }
                        }
                    }
                    }
                }

                android.util.Log.d("DDAY_WIDGET", "📦 위젯 items 개수: ${items.size}, displayRows: ${displayRows.size} (전체: ${allItems.size})")
            }
        } catch (e: Exception) {
            android.util.Log.e("DDAY_WIDGET", "❌ 위젯 데이터 로드 실패", e)
            items = emptyList()
            displayRows = emptyList()
        }
    }

    override fun onDestroy() {
        items = emptyList()
        displayRows = emptyList()
    }

    override fun getCount(): Int = displayRows.size

    override fun getViewTypeCount(): Int = 5

    fun getItemViewType(position: Int): Int {
        return when (displayRows.getOrNull(position)) {
            is WidgetRow.Header -> VIEW_TYPE_HEADER
            is WidgetRow.GroupHeader -> VIEW_TYPE_GROUP_HEADER
            is WidgetRow.TodoHeader -> VIEW_TYPE_TODO_HEADER
            is WidgetRow.SubTaskItem -> VIEW_TYPE_SUBTASK
            is WidgetRow.Item -> VIEW_TYPE_ITEM
            else -> VIEW_TYPE_ITEM
        }
    }

    override fun getViewAt(position: Int): RemoteViews {
        if (position < 0 || position >= displayRows.size) {
            return RemoteViews(context.packageName, R.layout.item_dday_widget)
        }

        val row = displayRows[position]

        // 헤더 행 처리
        if (row is WidgetRow.Header) {
            return createHeaderView(row.title)
        }

        // 그룹 헤더 행 처리
        if (row is WidgetRow.GroupHeader) {
            return createGroupHeaderView(row.groupName, row.isCollapsed)
        }

        // To-Do 헤더 행 처리
        if (row is WidgetRow.TodoHeader) {
            return createTodoHeaderView(row.item, row.completedCount, row.totalCount, row.isCollapsed)
        }

        // 서브태스크 행 처리
        if (row is WidgetRow.SubTaskItem) {
            return createSubTaskView(row.parentItem, row.subTask, row.subTaskIndex)
        }

        // 아이템 행 처리
        val widgetItem = row as WidgetRow.Item
        val item = widgetItem.item
        val showProgress = widgetItem.showProgress
        val views = RemoteViews(context.packageName, R.layout.item_dday_widget)

        // 다크모드 확인
        val isDark = isDarkMode(context)

        // 설정값 읽기
        val backgroundEnabled = DdaySettings.isBackgroundEnabled(context)
        val bgOpacity = DdaySettings.getBackgroundOpacity(context) / 100f
        val iconBgOpacity = DdaySettings.getIconBgOpacity(context) / 100f

        android.util.Log.d("DDAY_WIDGET", "🎨 아이템 배경: enabled=$backgroundEnabled, bgOpacity=$bgOpacity, iconBgOpacity=$iconBgOpacity, isDark=$isDark")
        val fontSizeMultiplier = when (DdaySettings.getWidgetFontSize(context)) {
            0 -> 0.85f  // 작게
            2 -> 1.15f  // 크게
            else -> 1f  // 보통
        }

        // 커스텀 색상 또는 카테고리 기본 색상 가져오기
        val itemColor = item.getColorLong().toInt()

        // 이모지 가져오기 (선택된 이모지 또는 카테고리 기본 이모지)
        val itemEmoji = item.getEmoji()

        // 이모지 설정
        views.setTextViewText(R.id.item_category_icon, itemEmoji)
        views.setTextViewTextSize(R.id.item_category_icon, android.util.TypedValue.COMPLEX_UNIT_SP, 22f * fontSizeMultiplier)

        // 배경 틴트 적용 (앱과 동일한 투명도 공식)
        if (backgroundEnabled && !item.isChecked) {
            val alpha = (bgOpacity * 255).toInt().coerceIn(0, 255)
            val tintColor = (alpha shl 24) or (itemColor and 0x00FFFFFF)
            views.setInt(R.id.item_card, "setBackgroundColor", tintColor)

            // 아이콘 배경도 색상 적용
            val iconAlpha = (iconBgOpacity * 255).toInt().coerceIn(0, 255)
            val iconTintColor = (iconAlpha shl 24) or (itemColor and 0x00FFFFFF)
            views.setInt(R.id.item_icon_card, "setBackgroundColor", iconTintColor)
        } else {
            // 배경 비활성화 또는 체크된 항목
            if (isDark) {
                // 다크모드: 약간 밝은 배경
                views.setInt(R.id.item_card, "setBackgroundColor", 0x00000000)
                views.setInt(R.id.item_icon_card, "setBackgroundColor", 0x20FFFFFF)
            } else {
                // 라이트모드: 약간 어두운 배경
                views.setInt(R.id.item_card, "setBackgroundColor", 0x00000000)
                views.setInt(R.id.item_icon_card, "setBackgroundColor", 0x15000000)
            }
        }

        // 텍스트 설정 + 글씨 크기 (반복 태그 포함 - D-Day와 To-Do 모두)
        val repeatTag = if (item.isRepeating()) item.getRepeatTagText() ?: "" else ""
        val titleText = if (repeatTag.isNotEmpty()) "${item.title} $repeatTag" else item.title
        views.setTextViewText(R.id.item_title, titleText)
        views.setTextViewTextSize(R.id.item_title, android.util.TypedValue.COMPLEX_UNIT_SP, 15f * fontSizeMultiplier)

        // D-Day 또는 To-Do 표시
        val ddayText: String
        val ddayColor: Int

        if (item.date != null) {
            // D-Day 아이템: D-Day 계산
            val daysUntil = calculateDaysUntil(item.date)
            ddayText = when {
                daysUntil > 0 -> "D-$daysUntil"
                daysUntil == 0 -> "D-DAY"
                else -> "D+${-daysUntil}"
            }
            // D-Day 숫자 색상 규칙:
            // D-3 ~ D-2: 파란색, D-1/D-Day/D+N: 빨간색, D-4 이상: 기본 검정
            ddayColor = when {
                daysUntil == 2 || daysUntil == 3 -> 0xFF2F6BFF.toInt()  // 파란색 (D-2, D-3)
                daysUntil <= 1 -> 0xFFE53935.toInt()  // 빨간색 (D-1, D-Day, D+N)
                else -> if (isDark) 0xFFF5F5F0.toInt() else 0xFF4A4A4A.toInt()  // 기본 (D-4 이상)
            }
        } else if (showProgress) {
            // To-Do 아이템 (혼합 위젯): 체크리스트 진행현황 표시
            val subTasks = item.getSubTaskList()
            if (subTasks.isNotEmpty()) {
                val completedCount = subTasks.count { it.isChecked }
                val totalCount = subTasks.size
                ddayText = "$completedCount/$totalCount"
                ddayColor = if (isDark) 0xFFB8B8B8.toInt() else 0xFF7A7A7A.toInt()
            } else {
                ddayText = ""
                ddayColor = itemColor
            }
        } else {
            // To-Do 아이템 (전용 위젯): 빈 텍스트
            ddayText = ""
            ddayColor = itemColor
        }
        views.setTextViewText(R.id.item_dday, ddayText)
        views.setTextViewTextSize(R.id.item_dday, android.util.TypedValue.COMPLEX_UNIT_SP, 16f * fontSizeMultiplier)
        // D-Day 텍스트가 비어있으면 숨김 (To-Do 체크박스 오른쪽 정렬)
        views.setViewVisibility(R.id.item_dday, if (ddayText.isEmpty()) View.GONE else View.VISIBLE)

        // 메모 표시/숨김
        if (!item.memo.isNullOrBlank()) {
            views.setTextViewText(R.id.item_memo, item.memo)
            views.setViewVisibility(R.id.item_memo, View.VISIBLE)
            views.setTextViewTextSize(R.id.item_memo, android.util.TypedValue.COMPLEX_UNIT_SP, 12f * fontSizeMultiplier)
        } else {
            views.setViewVisibility(R.id.item_memo, View.GONE)
        }

        // 날짜 표시 (D-Day만, To-Do는 빈 값)
        val formattedDate = item.date?.let { SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).format(it) } ?: ""
        views.setTextViewText(R.id.item_date, formattedDate)
        views.setTextViewTextSize(R.id.item_date, android.util.TypedValue.COMPLEX_UNIT_SP, 12f * fontSizeMultiplier)

        // 체크박스: D-Day는 숨김, 하위 체크리스트 있는 To-Do도 숨김 (자동 완료)
        if (item.isDday() || item.getSubTaskList().isNotEmpty()) {
            views.setViewVisibility(R.id.item_checkbox, View.GONE)
        } else {
            views.setViewVisibility(R.id.item_checkbox, View.VISIBLE)
            views.setImageViewResource(
                R.id.item_checkbox,
                if (item.isChecked) R.drawable.widget_checkbox_checked
                else R.drawable.widget_checkbox_unchecked
            )
            views.setContentDescription(
                R.id.item_checkbox,
                context.getString(
                    if (item.isChecked) R.string.cd_checkbox_checked
                    else R.string.cd_checkbox_unchecked
                )
            )
        }

        // Soft Pastel 테마 텍스트 색상 정의
        // 라이트모드: WarmGray (#4A4A4A) 기반
        // 다크모드: LightGray (#F5F5F0) 기반
        val titleColor = if (isDark) 0xFFF5F5F0.toInt() else 0xFF4A4A4A.toInt()
        val dateColor = if (isDark) 0xFFB8B8B8.toInt() else 0xFF7A7A7A.toInt()
        val checkedTitleColor = if (isDark) 0xFF606060.toInt() else 0xFF9A9A9A.toInt()
        val checkedDateColor = if (isDark) 0xFF505050.toInt() else 0xFFB0B0B0.toInt()
        val memoColor = if (isDark) 0xFFC8C8C8.toInt() else 0xFF6A6A6A.toInt()

        // 체크된 항목은 가로줄 표시 (STRIKE_THRU_TEXT_FLAG)
        if (item.isChecked) {
            // 가로줄 + 기본 스타일
            val strikePaintFlags = Paint.STRIKE_THRU_TEXT_FLAG or Paint.ANTI_ALIAS_FLAG
            views.setInt(R.id.item_title, "setPaintFlags", strikePaintFlags)
            views.setInt(R.id.item_memo, "setPaintFlags", strikePaintFlags)
            views.setInt(R.id.item_dday, "setPaintFlags", strikePaintFlags)
            views.setInt(R.id.item_category_icon, "setPaintFlags", strikePaintFlags)
            // 체크된 항목은 회색으로 표시
            views.setTextColor(R.id.item_title, checkedTitleColor)
            views.setTextColor(R.id.item_dday, checkedTitleColor)
            views.setTextColor(R.id.item_date, checkedDateColor)
            views.setTextColor(R.id.item_memo, checkedTitleColor)
        } else {
            // 가로줄 없음
            val normalPaintFlags = Paint.ANTI_ALIAS_FLAG
            views.setInt(R.id.item_title, "setPaintFlags", normalPaintFlags)
            views.setInt(R.id.item_memo, "setPaintFlags", normalPaintFlags)
            views.setInt(R.id.item_dday, "setPaintFlags", normalPaintFlags)
            views.setInt(R.id.item_category_icon, "setPaintFlags", normalPaintFlags)
            // D-Day 색상 적용 (이미 다크모드 대응됨)
            views.setTextColor(R.id.item_title, titleColor)
            views.setTextColor(R.id.item_dday, ddayColor)
            views.setTextColor(R.id.item_date, dateColor)
            views.setTextColor(R.id.item_memo, memoColor)
        }

        // 앱 실행 인텐트
        val itemIntent = Intent().apply {
            putExtra(DdayWidgetProvider.EXTRA_CLICK_TYPE, DdayWidgetProvider.CLICK_TYPE_ITEM)
        }

        when (mode) {
            DdayOnlyWidgetProvider.MODE_DDAY -> {
                // D-Day 전용: 모든 영역 → 앱 열기 (접기/펼치기는 그룹 헤더에서만)
                views.setOnClickFillInIntent(R.id.item_card, itemIntent)
            }
            else -> {
                // 혼합/To-Do 위젯: 형제 레벨 클릭 (부모-자식 충돌 방지)
                // 왼쪽 영역 → 앱 열기
                views.setOnClickFillInIntent(R.id.item_icon_card, itemIntent)
                views.setOnClickFillInIntent(R.id.item_content_area, itemIntent)
                views.setOnClickFillInIntent(R.id.item_dday, itemIntent)
                // 오른쪽 체크박스 → 체크 토글
                val checkboxIntent = Intent().apply {
                    putExtra(DdayWidgetProvider.EXTRA_CLICK_TYPE, DdayWidgetProvider.CLICK_TYPE_CHECKBOX)
                    putExtra(DdayWidgetProvider.EXTRA_ITEM_ID, item.id)
                    putExtra(DdayWidgetProvider.EXTRA_IS_CHECKED, !item.isChecked)
                }
                views.setOnClickFillInIntent(R.id.item_checkbox, checkboxIntent)
            }
        }

        return views
    }

    private fun createHeaderView(title: String): RemoteViews {
        val isDark = isDarkMode(context)
        val views = RemoteViews(context.packageName, R.layout.item_widget_section_header)
        val fontSizeMultiplier = when (DdaySettings.getWidgetFontSize(context)) {
            0 -> 0.85f; 2 -> 1.15f; else -> 1f
        }
        // 바 프리픽스 스타일: ▎D-Day / ▎To-Do
        views.setTextViewText(R.id.header_title, "▎$title")
        views.setTextViewTextSize(R.id.header_title, android.util.TypedValue.COMPLEX_UNIT_SP, 16f * fontSizeMultiplier)
        val headerColor = if (isDark) 0xDDF5F5F0.toInt() else 0xDD4A4A4A.toInt()
        views.setTextColor(R.id.header_title, headerColor)
        views.setInt(R.id.header_title, "setBackgroundColor", 0x00000000)
        // 혼합 위젯: 헤더 클릭 시 앱 열기
        val itemIntent = Intent().apply {
            putExtra(DdayWidgetProvider.EXTRA_CLICK_TYPE, DdayWidgetProvider.CLICK_TYPE_ITEM)
        }
        views.setOnClickFillInIntent(R.id.header_title, itemIntent)
        return views
    }

    private fun createGroupHeaderView(groupName: String, isCollapsed: Boolean): RemoteViews {
        val isDark = isDarkMode(context)

        // D-Day 전용 위젯에서만 접기/펼치기 사용 (새 레이아웃 사용)
        if (mode == DdayOnlyWidgetProvider.MODE_DDAY) {
            val views = RemoteViews(context.packageName, R.layout.item_widget_group_header)
            // 그룹 헤더: "이모지 그룹명" 형식
            val groupEmoji = DdaySettings.getGroupEmoji(context, groupName)
            views.setTextViewText(R.id.group_header_title, "$groupEmoji $groupName")
            // 접기/펼치기 아이콘
            views.setTextViewText(R.id.group_header_indicator, if (isCollapsed) "▼" else "▲")
            // 다크모드 대응 색상
            val groupHeaderColor = if (isDark) 0xCCD0D0D0.toInt() else 0xAA3A3A3A.toInt()
            views.setTextColor(R.id.group_header_title, groupHeaderColor)
            views.setTextColor(R.id.group_header_indicator, groupHeaderColor)
            // 오른쪽 영역(화살표) → 접기/펼치기
            val toggleIntent = Intent().apply {
                putExtra(DdayOnlyWidgetProvider.EXTRA_GROUP_NAME, groupName)
            }
            views.setOnClickFillInIntent(R.id.group_header_toggle_area, toggleIntent)
            // 나머지 영역(그룹명) → 앱 열기 (형제 레벨, 부모 클릭 제거)
            val itemIntent = Intent().apply {
                putExtra(DdayWidgetProvider.EXTRA_CLICK_TYPE, DdayWidgetProvider.CLICK_TYPE_ITEM)
            }
            views.setOnClickFillInIntent(R.id.group_header_title, itemIntent)
            return views
        }

        // 혼합 위젯에서는 기존 레이아웃 사용 (접기 없음, 클릭 시 앱 열기)
        val views = RemoteViews(context.packageName, R.layout.item_widget_section_header)
        val groupEmoji = DdaySettings.getGroupEmoji(context, groupName)
        views.setTextViewText(R.id.header_title, "$groupEmoji $groupName")
        views.setTextViewTextSize(R.id.header_title, android.util.TypedValue.COMPLEX_UNIT_SP, 13f)
        val groupHeaderColor = if (isDark) 0xCCD0D0D0.toInt() else 0xAA3A3A3A.toInt()
        views.setTextColor(R.id.header_title, groupHeaderColor)
        // 그룹 헤더 클릭 시 앱 열기
        val itemIntent = Intent().apply {
            putExtra(DdayWidgetProvider.EXTRA_CLICK_TYPE, DdayWidgetProvider.CLICK_TYPE_ITEM)
        }
        views.setOnClickFillInIntent(R.id.header_title, itemIntent)
        return views
    }

    private fun createTodoHeaderView(item: DdayItem, completedCount: Int, totalCount: Int, isCollapsed: Boolean = false): RemoteViews {
        val isDark = isDarkMode(context)
        val views = RemoteViews(context.packageName, R.layout.item_widget_todo_header)

        // 설정값 읽기
        val backgroundEnabled = DdaySettings.isBackgroundEnabled(context)
        val bgOpacity = DdaySettings.getBackgroundOpacity(context) / 100f
        val iconBgOpacity = DdaySettings.getIconBgOpacity(context) / 100f
        val fontSizeMultiplier = when (DdaySettings.getWidgetFontSize(context)) {
            0 -> 0.85f  // 작게
            2 -> 1.15f  // 크게
            else -> 1f  // 보통
        }

        // 이모지 설정 (📋 대신 아이템의 이모지 사용)
        val itemEmoji = item.getEmoji()
        views.setTextViewText(R.id.todo_header_icon, itemEmoji)
        views.setTextViewTextSize(R.id.todo_header_icon, android.util.TypedValue.COMPLEX_UNIT_SP, 18f * fontSizeMultiplier)

        // 제목 (반복 태그 포함)
        val repeatTag = if (item.isRepeating()) item.getRepeatTagText() ?: "" else ""
        val titleText = if (repeatTag.isNotEmpty()) "${item.title} $repeatTag" else item.title
        views.setTextViewText(R.id.todo_header_title, titleText)
        views.setTextViewTextSize(R.id.todo_header_title, android.util.TypedValue.COMPLEX_UNIT_SP, 14f * fontSizeMultiplier)

        // 진행현황 (2/5)
        views.setTextViewText(R.id.todo_header_progress, "($completedCount/$totalCount)")
        views.setTextViewTextSize(R.id.todo_header_progress, android.util.TypedValue.COMPLEX_UNIT_SP, 13f * fontSizeMultiplier)

        // 커스텀 색상 또는 카테고리 기본 색상
        val itemColor = item.getColorLong().toInt()

        // 배경 색상 적용 (앱과 동일한 투명도 공식)
        if (backgroundEnabled && !item.isChecked) {
            val alpha = (bgOpacity * 255).toInt().coerceIn(0, 255)
            val tintColor = (alpha shl 24) or (itemColor and 0x00FFFFFF)
            views.setInt(R.id.todo_header_root, "setBackgroundColor", tintColor)

            val iconAlpha = (iconBgOpacity * 255).toInt().coerceIn(0, 255)
            val iconTintColor = (iconAlpha shl 24) or (itemColor and 0x00FFFFFF)
            views.setInt(R.id.todo_header_icon_card, "setBackgroundColor", iconTintColor)
        } else {
            views.setInt(R.id.todo_header_root, "setBackgroundColor", 0x00000000)
            if (isDark) {
                views.setInt(R.id.todo_header_icon_card, "setBackgroundColor", 0x20FFFFFF)
            } else {
                views.setInt(R.id.todo_header_icon_card, "setBackgroundColor", 0x15000000)
            }
        }

        // 텍스트 색상 + 체크 시 가로줄/회색 처리
        if (item.isChecked) {
            val checkedColor = if (isDark) 0xFF606060.toInt() else 0xFF9A9A9A.toInt()
            views.setTextColor(R.id.todo_header_title, checkedColor)
            views.setTextColor(R.id.todo_header_progress, checkedColor)
            views.setInt(R.id.todo_header_title, "setPaintFlags", Paint.STRIKE_THRU_TEXT_FLAG or Paint.ANTI_ALIAS_FLAG)
        } else {
            val titleColor = if (isDark) 0xFFF5F5F0.toInt() else 0xFF4A4A4A.toInt()
            val progressColor = if (isDark) 0xFFB8B8B8.toInt() else 0xFF7A7A7A.toInt()
            views.setTextColor(R.id.todo_header_title, titleColor)
            views.setTextColor(R.id.todo_header_progress, progressColor)
            views.setInt(R.id.todo_header_title, "setPaintFlags", Paint.ANTI_ALIAS_FLAG)
        }

        // 접기/펼치기 표시
        views.setTextViewText(R.id.todo_header_indicator, if (isCollapsed) "▼" else "▲")
        val indicatorColor = if (item.isChecked) {
            if (isDark) 0xFF606060.toInt() else 0xFF9A9A9A.toInt()
        } else {
            if (isDark) 0xFFB8B8B8.toInt() else 0xFF7A7A7A.toInt()
        }
        views.setTextColor(R.id.todo_header_indicator, indicatorColor)

        // 하위 체크리스트가 있으므로 상위 체크박스 숨김 (자동 완료로 처리)
        views.setViewVisibility(R.id.todo_header_checkbox, View.GONE)

        // 접기/펼치기 영역 (넓은 터치) → 접기/펼치기 토글
        val toggleIntent = Intent().apply {
            putExtra(DdayWidgetProvider.EXTRA_CLICK_TYPE, DdayWidgetProvider.CLICK_TYPE_TODO_TOGGLE)
            putExtra(DdayWidgetProvider.EXTRA_ITEM_ID, item.id)
        }
        views.setOnClickFillInIntent(R.id.todo_header_toggle_area, toggleIntent)

        // 나머지 영역(아이콘+제목) → 앱 열기 (형제 레벨, 부모 클릭 제거)
        val itemIntent = Intent().apply {
            putExtra(DdayWidgetProvider.EXTRA_CLICK_TYPE, DdayWidgetProvider.CLICK_TYPE_ITEM)
        }
        views.setOnClickFillInIntent(R.id.todo_header_icon_card, itemIntent)
        views.setOnClickFillInIntent(R.id.todo_header_title, itemIntent)

        return views
    }

    private fun createSubTaskView(parentItem: DdayItem, subTask: SubTask, subTaskIndex: Int): RemoteViews {
        val isDark = isDarkMode(context)
        val views = RemoteViews(context.packageName, R.layout.item_widget_subtask)

        val fontSizeMultiplier = when (DdaySettings.getWidgetFontSize(context)) {
            0 -> 0.85f  // 작게
            2 -> 1.15f  // 크게
            else -> 1f  // 보통
        }

        // 서브태스크 제목
        views.setTextViewText(R.id.subtask_title, subTask.title)
        views.setTextViewTextSize(R.id.subtask_title, android.util.TypedValue.COMPLEX_UNIT_SP, 13f * fontSizeMultiplier)

        // 체크박스 상태 (ImageView)
        views.setImageViewResource(
            R.id.subtask_checkbox,
            if (subTask.isChecked) R.drawable.widget_checkbox_checked
            else R.drawable.widget_checkbox_unchecked
        )
        views.setContentDescription(
            R.id.subtask_checkbox,
            context.getString(
                if (subTask.isChecked) R.string.cd_checkbox_checked
                else R.string.cd_checkbox_unchecked
            )
        )

        // 텍스트 색상 (체크 여부에 따라)
        val titleColor: Int
        val paintFlags: Int
        if (subTask.isChecked) {
            titleColor = if (isDark) 0xFF606060.toInt() else 0xFF9A9A9A.toInt()
            paintFlags = Paint.STRIKE_THRU_TEXT_FLAG or Paint.ANTI_ALIAS_FLAG
        } else {
            titleColor = if (isDark) 0xFFD0D0D0.toInt() else 0xFF5A5A5A.toInt()
            paintFlags = Paint.ANTI_ALIAS_FLAG
        }
        views.setTextColor(R.id.subtask_title, titleColor)
        views.setInt(R.id.subtask_title, "setPaintFlags", paintFlags)

        // 서브태스크 체크박스 클릭 시 인텐트
        val checkboxIntent = Intent().apply {
            putExtra(DdayWidgetProvider.EXTRA_CLICK_TYPE, DdayWidgetProvider.CLICK_TYPE_SUBTASK)
            putExtra(DdayWidgetProvider.EXTRA_ITEM_ID, parentItem.id)
            putExtra(DdayWidgetProvider.EXTRA_SUBTASK_INDEX, subTaskIndex)
            putExtra(DdayWidgetProvider.EXTRA_IS_CHECKED, !subTask.isChecked)
        }
        views.setOnClickFillInIntent(R.id.subtask_root, checkboxIntent)

        return views
    }

    private fun calculateDaysUntil(date: Date): Int {
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        val targetDate = Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        return ((targetDate.time - today.time) / (1000 * 60 * 60 * 24)).toInt()
    }

    override fun getLoadingView(): RemoteViews? = null
    // getViewTypeCount()는 위에서 이미 override 했으므로 제거
    override fun getItemId(position: Int): Long {
        return when (val row = displayRows.getOrNull(position)) {
            is WidgetRow.Header -> -row.title.hashCode().toLong()  // 헤더는 음수 ID
            is WidgetRow.GroupHeader -> -(row.groupName.hashCode().toLong() + 10000)  // 그룹 헤더는 다른 범위의 음수 ID
            is WidgetRow.TodoHeader -> row.item.id.toLong() * 1000  // To-Do 헤더: item.id * 1000
            is WidgetRow.SubTaskItem -> row.parentItem.id.toLong() * 1000 + row.subTaskIndex + 1  // 서브태스크: parentId * 1000 + index + 1
            is WidgetRow.Item -> row.item.id.toLong()
            else -> position.toLong()
        }
    }
    override fun hasStableIds(): Boolean = true
}
