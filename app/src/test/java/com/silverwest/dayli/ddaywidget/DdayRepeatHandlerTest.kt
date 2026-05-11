package com.silverwest.dayli.ddaywidget

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date

/**
 * DdayRepeatHandler 단위 테스트.
 *
 * 매트릭스 1순위: 서브태스크 전체 완료 → 반복 To-Do → hidden 처리.
 * 추가: 반복 D-Day 체크, 31일 → 2월 clamp, 미래 회차 보정, fallback 케이스.
 */
class DdayRepeatHandlerTest {

    // ===== 헬퍼 =====

    private fun ymd(year: Int, month: Int, day: Int): Date {
        return Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, 0, 0, 0)
        }.time
    }

    private fun ymdMillis(year: Int, month: Int, day: Int, hour: Int = 12): Long {
        return Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, hour, 0, 0)
        }.timeInMillis
    }

    // ===== getNextRepeatDate 미래 보정 (#4) =====

    @Test
    fun `monthly repeat advances past long-overdue date`() {
        val item = DdayItem(
            id = 1,
            title = "monthly",
            date = ymd(2024, 1, 15),
            repeatType = RepeatType.MONTHLY.name,
            repeatDay = 15
        )
        val now = ymdMillis(2026, 5, 11)
        val next = item.getNextRepeatDate(now)
        assertNotNull(next)
        assertTrue("next must be in future", next!!.time > now)
        // 2024-01-15부터 매월 +1 반복 → now=2026-05-11 직후 첫 미래 회차는 2026-05-15
        val cal = Calendar.getInstance().apply { time = next }
        assertEquals(2026, cal.get(Calendar.YEAR))
        assertEquals(Calendar.MAY, cal.get(Calendar.MONTH))
        assertEquals(15, cal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `monthly repeat day 31 clamps to february last day`() {
        val item = DdayItem(
            id = 1,
            title = "monthly-31",
            date = ymd(2026, 1, 31),
            repeatType = RepeatType.MONTHLY.name,
            repeatDay = 31
        )
        val now = ymdMillis(2026, 2, 1)
        val next = item.getNextRepeatDate(now)
        assertNotNull(next)
        val cal = Calendar.getInstance().apply { time = next!! }
        assertEquals(Calendar.FEBRUARY, cal.get(Calendar.MONTH))
        assertEquals(28, cal.get(Calendar.DAY_OF_MONTH))  // 2026은 평년
    }

    @Test
    fun `daily repeat advances by single period when 1 day overdue`() {
        val item = DdayItem(
            id = 1,
            title = "daily",
            date = ymd(2026, 5, 10),
            repeatType = RepeatType.DAILY.name
        )
        val now = ymdMillis(2026, 5, 11, hour = 12)
        val next = item.getNextRepeatDate(now)
        assertNotNull(next)
        val cal = Calendar.getInstance().apply { time = next!! }
        assertEquals(12, cal.get(Calendar.DAY_OF_MONTH))
    }

    // ===== toggleChecked: 반복 D-Day =====

    @Test
    fun `toggleChecked on repeating dday hides and advances date`() = runBlocking {
        val dao = FakeDdayDao()
        val item = DdayItem(
            id = 1,
            title = "exam",
            date = ymd(2026, 6, 1),
            repeatType = RepeatType.MONTHLY.name,
            repeatDay = 1,
            itemType = ItemType.DDAY.name
        )
        dao.upsert(item)
        val now = ymdMillis(2026, 5, 11)

        val result = DdayRepeatHandler.toggleChecked(dao, item, now)

        assertTrue("expected HiddenUntil, got $result", result is ToggleCheckResult.HiddenUntil)
        val stored = dao.getById(1)!!
        assertTrue("isHidden", stored.isHidden)
        assertEquals("isChecked must reset", false, stored.isChecked)
        val cal = Calendar.getInstance().apply { time = stored.date!! }
        // 다음 발생일은 다음 달 1일 (2026-07-01)
        assertEquals(Calendar.JULY, cal.get(Calendar.MONTH))
    }

    // ===== toggleChecked: 일반 To-Do =====

    @Test
    fun `toggleChecked on non-repeating todo sets isChecked`() = runBlocking {
        val dao = FakeDdayDao()
        val item = DdayItem(
            id = 1,
            title = "shopping",
            date = null,
            repeatType = RepeatType.NONE.name,
            itemType = ItemType.TODO.name
        )
        dao.upsert(item)

        val result = DdayRepeatHandler.toggleChecked(dao, item, 1000L)

        assertTrue(result is ToggleCheckResult.Updated)
        assertEquals(ToggleCheckResult.Reason.NORMAL, (result as ToggleCheckResult.Updated).reason)
        val stored = dao.getById(1)!!
        assertEquals(true, stored.isChecked)
        assertEquals(1000L, stored.checkedAt)
    }

    @Test
    fun `toggleChecked unchecking clears checkedAt`() = runBlocking {
        val dao = FakeDdayDao()
        val item = DdayItem(
            id = 1,
            title = "x",
            date = null,
            isChecked = true,
            checkedAt = 500L,
            itemType = ItemType.TODO.name
        )
        dao.upsert(item)

        val result = DdayRepeatHandler.toggleChecked(dao, item, 1000L)

        assertTrue(result is ToggleCheckResult.Updated)
        val stored = dao.getById(1)!!
        assertEquals(false, stored.isChecked)
        assertEquals(null, stored.checkedAt)
    }

    // ===== toggleSubTask: 1순위 케이스 — 서브태스크 전체 완료 → 반복 To-Do =====

    @Test
    fun `toggleSubTask completes all on repeating todo, hides and resets subtasks`() = runBlocking {
        val dao = FakeDdayDao()
        val subTasksJson = DdayItem.subTasksToJson(
            listOf(SubTask("a", false), SubTask("b", true))
        )
        val item = DdayItem(
            id = 1,
            title = "routine",
            date = null,
            repeatType = RepeatType.DAILY.name,
            itemType = ItemType.TODO.name,
            subTasks = subTasksJson
        )
        dao.upsert(item)
        val now = ymdMillis(2026, 5, 11)

        // 첫 번째 sub-task ("a") 토글 → 모두 완료
        val result = DdayRepeatHandler.toggleSubTask(dao, item, 0, now)

        assertTrue("expected HiddenUntil, got $result", result is ToggleCheckResult.HiddenUntil)
        val stored = dao.getById(1)!!
        assertTrue("isHidden", stored.isHidden)
        assertEquals(false, stored.isChecked)
        // sub-tasks 모두 리셋
        val storedSubs = stored.getSubTaskList()
        assertEquals(2, storedSubs.size)
        assertTrue("all unchecked after reset", storedSubs.all { !it.isChecked })
    }

    // ===== toggleSubTask: 일반 To-Do, 일부만 완료 =====

    @Test
    fun `toggleSubTask partial keeps parent unchecked and sorts completed to bottom`() = runBlocking {
        val dao = FakeDdayDao()
        val subTasksJson = DdayItem.subTasksToJson(
            listOf(SubTask("a", false), SubTask("b", false), SubTask("c", false))
        )
        val item = DdayItem(
            id = 1,
            title = "list",
            date = null,
            repeatType = RepeatType.NONE.name,
            itemType = ItemType.TODO.name,
            subTasks = subTasksJson
        )
        dao.upsert(item)

        // 중간 인덱스 ("b") 토글
        val result = DdayRepeatHandler.toggleSubTask(dao, item, 1, 1000L)

        assertTrue(result is ToggleCheckResult.Updated)
        assertEquals(
            ToggleCheckResult.Reason.SUBTASK_PROGRESS,
            (result as ToggleCheckResult.Updated).reason
        )
        val stored = dao.getById(1)!!
        assertEquals(false, stored.isChecked)
        val storedSubs = stored.getSubTaskList()
        // 완료된 "b"가 마지막으로 정렬됨
        assertEquals("a", storedSubs[0].title)
        assertEquals("c", storedSubs[1].title)
        assertEquals("b", storedSubs[2].title)
        assertEquals(true, storedSubs[2].isChecked)
    }

    // ===== toggleSubTask: 일반 To-Do, 모두 완료 =====

    @Test
    fun `toggleSubTask completes all on non-repeating todo sets parent isChecked`() = runBlocking {
        val dao = FakeDdayDao()
        val subTasksJson = DdayItem.subTasksToJson(
            listOf(SubTask("a", true), SubTask("b", false))
        )
        val item = DdayItem(
            id = 1,
            title = "list",
            date = null,
            repeatType = RepeatType.NONE.name,
            itemType = ItemType.TODO.name,
            subTasks = subTasksJson
        )
        dao.upsert(item)

        val result = DdayRepeatHandler.toggleSubTask(dao, item, 1, 2000L)

        assertTrue(result is ToggleCheckResult.Updated)
        val stored = dao.getById(1)!!
        assertEquals(true, stored.isChecked)
        assertEquals(2000L, stored.checkedAt)
    }

    // ===== Invalid index =====

    @Test
    fun `toggleSubTask invalid index is NoOp`() = runBlocking {
        val dao = FakeDdayDao()
        val item = DdayItem(
            id = 1,
            title = "x",
            date = null,
            itemType = ItemType.TODO.name,
            subTasks = DdayItem.subTasksToJson(listOf(SubTask("a", false)))
        )
        dao.upsert(item)

        val result = DdayRepeatHandler.toggleSubTask(dao, item, 99, 1000L)

        assertTrue(result is ToggleCheckResult.NoOp)
        assertEquals(
            ToggleCheckResult.Reason.INVALID_SUBTASK_INDEX,
            (result as ToggleCheckResult.NoOp).reason
        )
        // DB 변경 없음
        val stored = dao.getById(1)!!
        assertEquals(item, stored)
    }

    // ===== Effective show date (회귀 #1 — Codex finding) =====

    @Test
    fun `effective show date falls back to nextDate when showDate is already past`() {
        // 다음 회차가 내일, advanceDays=2 → showDate=어제 → effective는 내일.time
        val item = DdayItem(
            id = 1,
            title = "weekly",
            date = ymd(2026, 5, 4),
            repeatType = RepeatType.WEEKLY.name,
            advanceDisplayDays = 2,
            itemType = ItemType.DDAY.name
        )
        val now = ymdMillis(2026, 5, 11)
        val nextDate = ymd(2026, 5, 12)

        val effective = DdayRepeatHandler.computeEffectiveShowDate(item, nextDate, now)
        // showDate = 5/12 - 2일 = 5/10 (now=5/11 12시 이전) → 과거이므로 nextDate.time 사용
        assertEquals(nextDate.time, effective)
    }

    @Test
    fun `effective show date uses showDate when in future`() {
        val item = DdayItem(
            id = 1,
            title = "monthly",
            date = ymd(2026, 5, 1),
            repeatType = RepeatType.MONTHLY.name,
            advanceDisplayDays = 5,
            itemType = ItemType.DDAY.name
        )
        val now = ymdMillis(2026, 5, 11)
        val nextDate = ymd(2026, 6, 30)  // 충분히 미래

        val effective = DdayRepeatHandler.computeEffectiveShowDate(item, nextDate, now)
        // showDate = 6/30 - 5일 = 6/25 → 미래이므로 showDate 사용
        val cal = Calendar.getInstance().apply { timeInMillis = effective }
        assertEquals(Calendar.JUNE, cal.get(Calendar.MONTH))
        assertEquals(25, cal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `toggleChecked stores effective show date that survives unhide check`() = runBlocking {
        // 회귀 시나리오 (Codex finding #1): advanceDays=2, 다음 회차 내일
        // → effective=내일.time이어야 함 (어제가 아니라). 그래야 즉시 unhide되지 않음.
        val dao = FakeDdayDao()
        val item = DdayItem(
            id = 1,
            title = "weekly",
            date = ymd(2026, 5, 4),  // 1주 전 월요일
            repeatType = RepeatType.WEEKLY.name,
            advanceDisplayDays = 2,
            itemType = ItemType.DDAY.name
        )
        dao.upsert(item)
        val now = ymdMillis(2026, 5, 11)  // 이번 주 월요일

        val result = DdayRepeatHandler.toggleChecked(dao, item, now)

        assertTrue(result is ToggleCheckResult.HiddenUntil)
        val nextShowDate = (result as ToggleCheckResult.HiddenUntil).nextShowDate
        // nextShowDate가 과거가 아닌지 확인 (이게 핵심 회귀 방지)
        assertTrue(
            "nextShowDate must be in future to avoid immediate unhide",
            nextShowDate > now
        )
    }

    // ===== Long-overdue jump (Codex finding #2) =====

    @Test
    fun `daily repeat advances correctly even 10 years overdue`() {
        val item = DdayItem(
            id = 1,
            title = "daily",
            date = ymd(2016, 1, 1),
            repeatType = RepeatType.DAILY.name
        )
        val now = ymdMillis(2026, 5, 11, hour = 12)

        val next = item.getNextRepeatDate(now)
        assertNotNull("must compute without guard-cap fallback", next)
        assertTrue("next must be future", next!!.time > now)
        // 차이가 24시간 이내인지 (일 단위 jump가 정확하다는 증거)
        assertTrue(
            "next must be within 1 day of now",
            next.time - now < 24L * 60 * 60 * 1000
        )
    }

    @Test
    fun `monthly repeat advances correctly when 5 years overdue`() {
        val item = DdayItem(
            id = 1,
            title = "monthly",
            date = ymd(2021, 3, 15),
            repeatType = RepeatType.MONTHLY.name,
            repeatDay = 15
        )
        val now = ymdMillis(2026, 5, 11)

        val next = item.getNextRepeatDate(now)
        assertNotNull(next)
        assertTrue("next must be future", next!!.time > now)
        val cal = Calendar.getInstance().apply { time = next }
        assertEquals(2026, cal.get(Calendar.YEAR))
        assertEquals(Calendar.MAY, cal.get(Calendar.MONTH))
        assertEquals(15, cal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `yearly repeat advances correctly when 50 years overdue`() {
        val item = DdayItem(
            id = 1,
            title = "yearly",
            date = ymd(1976, 6, 15),
            repeatType = RepeatType.YEARLY.name
        )
        val now = ymdMillis(2026, 5, 11)

        val next = item.getNextRepeatDate(now)
        assertNotNull(next)
        assertTrue("next must be future", next!!.time > now)
        val cal = Calendar.getInstance().apply { time = next }
        assertEquals(2026, cal.get(Calendar.YEAR))
        assertEquals(Calendar.JUNE, cal.get(Calendar.MONTH))
        assertEquals(15, cal.get(Calendar.DAY_OF_MONTH))
    }

    // ===== Fallback: 반복인데 다음 회차 못 구함 =====

    @Test
    fun `toggleChecked repeating item with NONE repeat type still works as normal`() = runBlocking {
        // isRepeating()이 false인 케이스는 일반 경로로 빠지므로 직접 검증
        val dao = FakeDdayDao()
        val item = DdayItem(
            id = 1,
            title = "normal",
            date = null,
            repeatType = RepeatType.NONE.name,
            itemType = ItemType.TODO.name
        )
        dao.upsert(item)

        val result = DdayRepeatHandler.toggleChecked(dao, item, 1000L)

        assertTrue(result is ToggleCheckResult.Updated)
        assertEquals(ToggleCheckResult.Reason.NORMAL, (result as ToggleCheckResult.Updated).reason)
    }
}

/**
 * In-memory fake [DdayDao]. 핸들러 테스트에 필요한 메서드만 구현.
 */
private class FakeDdayDao : DdayDao {
    private val store = mutableMapOf<Int, DdayItem>()

    fun upsert(item: DdayItem) { store[item.id] = item }

    override suspend fun getById(id: Int): DdayItem? = store[id]

    override suspend fun update(item: DdayItem) { store[item.id] = item }

    override suspend fun updateChecked(id: Int, checked: Boolean, checkedAt: Long?) {
        store[id]?.let { store[id] = it.copy(isChecked = checked, checkedAt = checkedAt) }
    }

    override suspend fun updateSubTasks(id: Int, subTasks: String?) {
        store[id]?.let { store[id] = it.copy(subTasks = subTasks) }
    }

    // 핸들러에서 안 쓰는 메서드들 — 호출되면 테스트 실패
    override suspend fun getAll(): List<DdayItem> = error("not used")
    override suspend fun getAllByLatest(): List<DdayItem> = error("not used")
    override suspend fun getAllByDday(): List<DdayItem> = error("not used")
    override suspend fun insert(item: DdayItem): Long = error("not used")
    override suspend fun delete(item: DdayItem) = error("not used")
    override suspend fun getAllForWidget(todayStart: Long): List<DdayItem> = error("not used")
    override suspend fun getByCategory(category: String): List<DdayItem> = error("not used")
    override suspend fun getByCategoryDday(category: String): List<DdayItem> = error("not used")
    override suspend fun getAllDdays(): List<DdayItem> = error("not used")
    override suspend fun getDdaysForNotification(): List<DdayItem> = error("not used")
    override suspend fun getAllDdaysSorted(): List<DdayItem> = error("not used")
    override suspend fun getAllDdaysByDateAsc(): List<DdayItem> = error("not used")
    override suspend fun getAllDdaysByDateDesc(): List<DdayItem> = error("not used")
    override suspend fun getAllTodos(): List<DdayItem> = error("not used")
    override suspend fun getAllTodosSorted(): List<DdayItem> = error("not used")
    override suspend fun getAllTodosIncompleteFirst(): List<DdayItem> = error("not used")
    override suspend fun updateSortOrder(id: Int, sortOrder: Int) = error("not used")
    override suspend fun getAllForWidgetWithTodos(cutoffTime: Long): List<DdayItem> = error("not used")
    override suspend fun getHiddenDdays(): List<DdayItem> = error("not used")
    override suspend fun getHiddenTodos(): List<DdayItem> = error("not used")
    override suspend fun unhideReadyItems(today: Long) = error("not used")
    override suspend fun getDistinctGroupNames(): List<String> = error("not used")
    override suspend fun renameGroup(oldName: String, newName: String) = error("not used")
    override suspend fun deleteGroup(groupName: String) = error("not used")
    override suspend fun getGroupItemCount(groupName: String): Int = error("not used")
}
