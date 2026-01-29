package com.silverwest.dayli.ddaywidget

import android.app.Application
import android.util.Log
import androidx.lifecycle.*
import kotlinx.coroutines.launch
import java.util.Date

// 정렬 옵션
enum class SortOption {
    NEAREST,   // 임박순 (가까운 날짜 먼저)
    FARTHEST   // 여유순 (먼 날짜 먼저)
}

class DdayViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = DdayDatabase.getDatabase(application).ddayDao()
    private val _ddayList = MutableLiveData<List<DdayItem>>()
    val ddayList: LiveData<List<DdayItem>> = _ddayList

    // To-Do 리스트
    private val _todoList = MutableLiveData<List<DdayItem>>()
    val todoList: LiveData<List<DdayItem>> = _todoList

    private val _sortOption = MutableLiveData(SortOption.NEAREST)
    val sortOption: LiveData<SortOption> = _sortOption

    // 카테고리 필터 (null = 전체)
    private val _categoryFilter = MutableLiveData<DdayCategory?>(null)
    val categoryFilter: LiveData<DdayCategory?> = _categoryFilter

    // 현재 탭 (DDAY / TODO)
    private val _currentTab = MutableLiveData(ItemType.DDAY)
    val currentTab: LiveData<ItemType> = _currentTab

    init {
        loadAllDdays()
        loadAllTodos()
    }

    fun setCurrentTab(tab: ItemType) {
        _currentTab.value = tab
    }

    fun loadAllDdays() {
        viewModelScope.launch {
            val items = when (_sortOption.value) {
                SortOption.NEAREST -> dao.getAllDdaysByDateAsc()
                SortOption.FARTHEST -> dao.getAllDdaysByDateDesc()
                else -> dao.getAllDdaysByDateAsc()
            }
            _ddayList.postValue(items)
        }
    }

    fun loadAllTodos() {
        viewModelScope.launch {
            val items = dao.getAllTodosSorted()
            _todoList.postValue(items)
        }
    }

    fun loadAll() {
        loadAllDdays()
        loadAllTodos()
    }

    fun setSortOption(option: SortOption) {
        _sortOption.value = option
        loadAllDdays()
    }

    fun setCategoryFilter(category: DdayCategory?) {
        _categoryFilter.value = category
        loadAllDdays()
    }

    fun toggleChecked(item: DdayItem) {
        viewModelScope.launch {
            val newChecked = !item.isChecked

            // 반복 일정이고 체크하는 경우
            if (newChecked && item.isRepeating()) {
                if (item.isDday()) {
                    // D-Day 반복: 다음 날짜로 자동 재생성
                    val nextDate = item.getNextRepeatDate()
                    if (nextDate != null) {
                        val updatedItem = item.copy(
                            date = nextDate,
                            isChecked = false,
                            checkedAt = null
                        )
                        dao.update(updatedItem)
                        Log.d("DDAY_WIDGET", "🔁 반복 D-Day 갱신: ${item.title} → ${nextDate}")
                    }
                } else if (item.isTodo()) {
                    // To-Do 반복: 체크 해제 상태로 재생성 (새 항목 생성)
                    val checkedAt = System.currentTimeMillis()
                    dao.updateChecked(item.id, true, checkedAt)  // 기존 항목 체크
                    // 새로운 To-Do 항목 생성 (반복 유지)
                    val newTodo = item.copy(
                        id = 0,  // 새 ID 자동 생성
                        isChecked = false,
                        checkedAt = null
                    )
                    dao.insert(newTodo)
                    Log.d("DDAY_WIDGET", "🔁 반복 To-Do 재생성: ${item.title}")
                }
            } else {
                // 일반 항목 또는 체크 해제: 기존 로직
                val checkedAt = if (newChecked) System.currentTimeMillis() else null
                dao.updateChecked(item.id, newChecked, checkedAt)
            }

            loadAll()
            // 위젯 동기화
            DdayWidgetProvider.refreshAllWidgets(getApplication())
        }
    }

    fun delete(item: DdayItem) {
        viewModelScope.launch {
            dao.delete(item)
            loadAll()
            // 위젯 동기화
            DdayWidgetProvider.refreshAllWidgets(getApplication())
        }
    }

    fun insertDday(
        title: String,
        memo: String,
        date: Date,
        emoji: String = "📌",
        color: Long = 0xFFA8C5DAL,  // Pastel Blue
        repeatType: RepeatType = RepeatType.NONE
    ) {
        viewModelScope.launch {
            // 반복 기준 날짜 계산 (매주: 요일, 매월: 날짜)
            val calendar = java.util.Calendar.getInstance().apply { time = date }
            val repeatDay = when (repeatType) {
                RepeatType.WEEKLY -> calendar.get(java.util.Calendar.DAY_OF_WEEK)
                RepeatType.MONTHLY -> calendar.get(java.util.Calendar.DAY_OF_MONTH)
                else -> null
            }

            val item = DdayItem(
                title = title,
                memo = memo,
                date = date,
                category = DdayCategory.OTHER.name,
                iconName = emoji,
                customColor = color,
                repeatType = repeatType.name,
                repeatDay = repeatDay,
                itemType = ItemType.DDAY.name
            )
            dao.insert(item)
            loadAll()
            // 위젯 동기화
            DdayWidgetProvider.refreshAllWidgets(getApplication())
        }
    }

    // To-Do 아이템 추가
    fun insertTodo(
        title: String,
        memo: String? = null,
        emoji: String = "✅",
        color: Long = 0xFFA8C5DAL,  // Pastel Blue
        repeatType: RepeatType = RepeatType.NONE
    ) {
        viewModelScope.launch {
            val item = DdayItem(
                title = title,
                memo = memo,
                date = null,  // To-Do는 날짜 없음
                category = DdayCategory.OTHER.name,
                iconName = emoji,
                customColor = color,
                repeatType = repeatType.name,
                itemType = ItemType.TODO.name
            )
            dao.insert(item)
            loadAll()
            // 위젯 동기화
            DdayWidgetProvider.refreshAllWidgets(getApplication())
        }
    }

    fun updateItem(item: DdayItem) {
        viewModelScope.launch {
            dao.update(item)
            loadAll()
            // 위젯 동기화
            DdayWidgetProvider.refreshAllWidgets(getApplication())
        }
    }

    fun restoreItem(item: DdayItem) {
        viewModelScope.launch {
            // 삭제된 항목 복원 (동일 ID로 다시 삽입)
            dao.insert(item)
            loadAll()
            // 위젯 동기화
            DdayWidgetProvider.refreshAllWidgets(getApplication())
        }
    }

    // To-Do 드래그 순서 변경
    fun updateTodoOrder(reorderedItems: List<DdayItem>) {
        viewModelScope.launch {
            // 순서대로 sortOrder 업데이트
            val updates = reorderedItems.mapIndexed { index, item ->
                Pair(item.id, index)
            }
            dao.updateSortOrders(updates)
            loadAllTodos()
            // 위젯 동기화
            DdayWidgetProvider.refreshAllWidgets(getApplication())
        }
    }
}





