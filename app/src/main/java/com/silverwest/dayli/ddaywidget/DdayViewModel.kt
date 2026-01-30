package com.silverwest.dayli.ddaywidget

import android.app.Application
import android.util.Log
import androidx.lifecycle.*
import kotlinx.coroutines.launch
import java.util.Date

// D-Day 정렬 옵션
enum class SortOption {
    NEAREST,   // 임박순 (가까운 날짜 먼저)
    FARTHEST   // 여유순 (먼 날짜 먼저)
}

// To-Do 정렬 옵션
enum class TodoSortOption {
    MY_ORDER,          // 내 순서 (드래그 순서)
    INCOMPLETE_FIRST,  // 미완료순
    LATEST             // 최근 추가순
}

class DdayViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = DdayDatabase.getDatabase(application).ddayDao()
    private val templateDao = DdayDatabase.getDatabase(application).todoTemplateDao()
    private val _ddayList = MutableLiveData<List<DdayItem>>()
    val ddayList: LiveData<List<DdayItem>> = _ddayList

    // To-Do 리스트
    private val _todoList = MutableLiveData<List<DdayItem>>()
    val todoList: LiveData<List<DdayItem>> = _todoList

    private val _sortOption = MutableLiveData(SortOption.NEAREST)
    val sortOption: LiveData<SortOption> = _sortOption

    // To-Do 정렬
    private val _todoSortOption = MutableLiveData(TodoSortOption.MY_ORDER)
    val todoSortOption: LiveData<TodoSortOption> = _todoSortOption

    // 카테고리 필터 (null = 전체)
    private val _categoryFilter = MutableLiveData<DdayCategory?>(null)
    val categoryFilter: LiveData<DdayCategory?> = _categoryFilter

    // 현재 탭 (DDAY / TODO)
    private val _currentTab = MutableLiveData(ItemType.DDAY)
    val currentTab: LiveData<ItemType> = _currentTab

    // 기존 그룹 목록
    private val _existingGroups = MutableLiveData<List<String>>(emptyList())
    val existingGroups: LiveData<List<String>> = _existingGroups

    init {
        loadAllDdays()
        loadAllTodos()
        loadGroups()
        loadTemplates()
    }

    fun loadGroups() {
        viewModelScope.launch {
            val groups = dao.getDistinctGroupNames()
            _existingGroups.postValue(groups)
        }
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
            val items = when (_todoSortOption.value) {
                TodoSortOption.MY_ORDER -> dao.getAllTodosSorted()
                TodoSortOption.INCOMPLETE_FIRST -> dao.getAllTodosIncompleteFirst()
                TodoSortOption.LATEST -> dao.getAllTodos()
                else -> dao.getAllTodosSorted()
            }
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

    fun setTodoSortOption(option: TodoSortOption) {
        _todoSortOption.value = option
        loadAllTodos()
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
        repeatType: RepeatType = RepeatType.NONE,
        groupName: String? = null
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
                itemType = ItemType.DDAY.name,
                groupName = groupName
            )
            dao.insert(item)
            loadAll()
            loadGroups()  // 그룹 목록 갱신
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
        repeatType: RepeatType = RepeatType.NONE,
        subTasks: List<SubTask> = emptyList()
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
                itemType = ItemType.TODO.name,
                subTasks = DdayItem.subTasksToJson(subTasks)
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

    // 서브태스크 토글 (체크리스트 내 개별 항목)
    fun toggleSubTask(item: DdayItem, subTaskIndex: Int) {
        viewModelScope.launch {
            val currentSubTasks = item.getSubTaskList().toMutableList()
            if (subTaskIndex >= 0 && subTaskIndex < currentSubTasks.size) {
                val subTask = currentSubTasks[subTaskIndex]
                currentSubTasks[subTaskIndex] = subTask.copy(isChecked = !subTask.isChecked)
                val updatedItem = item.copy(
                    subTasks = DdayItem.subTasksToJson(currentSubTasks)
                )
                dao.update(updatedItem)
                loadAll()
                // 위젯 동기화
                DdayWidgetProvider.refreshAllWidgets(getApplication())
            }
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

    // 그룹 이름 변경
    fun renameGroup(oldName: String, newName: String) {
        viewModelScope.launch {
            dao.renameGroup(oldName, newName)
            loadGroups()
            loadAllDdays()
            // 위젯 동기화
            DdayWidgetProvider.refreshAllWidgets(getApplication())
        }
    }

    // 그룹 삭제 (해당 그룹의 D-Day는 미분류로 이동)
    fun deleteGroup(groupName: String) {
        viewModelScope.launch {
            dao.deleteGroup(groupName)
            loadGroups()
            loadAllDdays()
            // 위젯 동기화
            DdayWidgetProvider.refreshAllWidgets(getApplication())
        }
    }

    // 특정 그룹의 D-Day 개수 조회
    suspend fun getGroupItemCount(groupName: String): Int {
        return dao.getGroupItemCount(groupName)
    }

    // === To-Do 템플릿 관련 ===

    // 템플릿 목록
    private val _templates = MutableLiveData<List<TodoTemplate>>(emptyList())
    val templates: LiveData<List<TodoTemplate>> = _templates

    fun loadTemplates() {
        viewModelScope.launch {
            val templateList = templateDao.getAll()
            _templates.postValue(templateList)
        }
    }

    // 템플릿 저장
    fun saveAsTemplate(
        name: String,
        iconName: String,
        customColor: Long,
        subTasks: List<SubTask>
    ) {
        viewModelScope.launch {
            val template = TodoTemplate(
                name = name,
                iconName = iconName,
                customColor = customColor,
                subTasks = TodoTemplate.subTasksToJson(subTasks)
            )
            templateDao.insert(template)
            loadTemplates()
        }
    }

    // 템플릿에서 To-Do 생성
    fun createTodoFromTemplate(template: TodoTemplate, title: String) {
        viewModelScope.launch {
            val subTasks = template.getSubTaskList()
            val item = DdayItem(
                title = title,
                memo = null,
                date = null,
                category = DdayCategory.OTHER.name,
                iconName = template.iconName,
                customColor = template.customColor,
                repeatType = RepeatType.NONE.name,
                itemType = ItemType.TODO.name,
                subTasks = DdayItem.subTasksToJson(subTasks)
            )
            dao.insert(item)
            loadAll()
            DdayWidgetProvider.refreshAllWidgets(getApplication())
        }
    }

    // 템플릿 삭제
    fun deleteTemplate(template: TodoTemplate) {
        viewModelScope.launch {
            templateDao.delete(template)
            loadTemplates()
        }
    }

    // 템플릿 이름 변경
    fun renameTemplate(template: TodoTemplate, newName: String) {
        viewModelScope.launch {
            templateDao.rename(template.id, newName)
            loadTemplates()
        }
    }

    // 템플릿 업데이트
    fun updateTemplate(template: TodoTemplate) {
        viewModelScope.launch {
            templateDao.update(template)
            loadTemplates()
        }
    }

    // 템플릿 ID로 조회
    suspend fun getTemplateById(id: Int): TodoTemplate? {
        return templateDao.getById(id)
    }
}





