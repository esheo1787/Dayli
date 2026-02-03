package com.silverwest.dayli.ddaywidget

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Delete
import androidx.room.Update

@Dao
interface DdayDao {
    // 기본 (최신 등록순)
    @Query("SELECT * FROM dday_items ORDER BY id DESC")
    suspend fun getAll(): List<DdayItem>

    // 최신 등록순 (id 내림차순)
    @Query("SELECT * FROM dday_items ORDER BY id DESC")
    suspend fun getAllByLatest(): List<DdayItem>

    // D-Day 임박순 (날짜 오름차순)
    @Query("SELECT * FROM dday_items ORDER BY date ASC")
    suspend fun getAllByDday(): List<DdayItem>

    @Insert
    suspend fun insert(item: DdayItem)

    @Update
    suspend fun update(item: DdayItem)

    @Delete
    suspend fun delete(item: DdayItem)

    // 체크 상태와 체크 시간을 함께 업데이트
    @Query("UPDATE dday_items SET isChecked = :checked, checkedAt = :checkedAt WHERE id = :id")
    suspend fun updateChecked(id: Int, checked: Boolean, checkedAt: Long?)

    // ID로 단일 아이템 조회
    @Query("SELECT * FROM dday_items WHERE id = :id")
    suspend fun getById(id: Int): DdayItem?

    // 서브태스크 JSON 업데이트
    @Query("UPDATE dday_items SET sub_tasks = :subTasks WHERE id = :id")
    suspend fun updateSubTasks(id: Int, subTasks: String?)

    // 위젯용 쿼리: 체크 안 됨 OR (체크됨 AND 오늘 체크한 것)
    // :todayStart = 오늘 00:00:00의 타임스탬프
    @Query("""
        SELECT * FROM dday_items
        WHERE isChecked = 0
           OR (isChecked = 1 AND checkedAt >= :todayStart)
        ORDER BY isChecked ASC, date ASC
    """)
    suspend fun getAllForWidget(todayStart: Long): List<DdayItem>

    // 카테고리별 필터 (최신순)
    @Query("SELECT * FROM dday_items WHERE category = :category ORDER BY id DESC")
    suspend fun getByCategory(category: String): List<DdayItem>

    // 카테고리별 필터 (D-Day순)
    @Query("SELECT * FROM dday_items WHERE category = :category ORDER BY date ASC")
    suspend fun getByCategoryDday(category: String): List<DdayItem>

    // D-Day 아이템만 (최신순, 숨김 제외)
    @Query("SELECT * FROM dday_items WHERE itemType = 'DDAY' AND isHidden = 0 ORDER BY id DESC")
    suspend fun getAllDdays(): List<DdayItem>

    // D-Day 아이템만 (내 순서 - sortOrder 기준, 숨김 제외)
    @Query("SELECT * FROM dday_items WHERE itemType = 'DDAY' AND isHidden = 0 ORDER BY isChecked ASC, sortOrder ASC, id DESC")
    suspend fun getAllDdaysSorted(): List<DdayItem>

    // D-Day 아이템만 (임박순 - 가까운 날짜 먼저, 숨김 제외)
    @Query("SELECT * FROM dday_items WHERE itemType = 'DDAY' AND isHidden = 0 ORDER BY date ASC")
    suspend fun getAllDdaysByDateAsc(): List<DdayItem>

    // D-Day 아이템만 (여유순 - 먼 날짜 먼저, 숨김 제외)
    @Query("SELECT * FROM dday_items WHERE itemType = 'DDAY' AND isHidden = 0 ORDER BY date DESC")
    suspend fun getAllDdaysByDateDesc(): List<DdayItem>

    // To-Do 아이템만 (최신순)
    @Query("SELECT * FROM dday_items WHERE itemType = 'TODO' ORDER BY id DESC")
    suspend fun getAllTodos(): List<DdayItem>

    // To-Do 아이템만 (체크 안 된 것 먼저, 그 다음 sortOrder, 최신순)
    @Query("SELECT * FROM dday_items WHERE itemType = 'TODO' ORDER BY isChecked ASC, sortOrder ASC, id DESC")
    suspend fun getAllTodosSorted(): List<DdayItem>

    // To-Do 미완료순 (isChecked ASC, id DESC - sortOrder 무시)
    @Query("SELECT * FROM dday_items WHERE itemType = 'TODO' ORDER BY isChecked ASC, id DESC")
    suspend fun getAllTodosIncompleteFirst(): List<DdayItem>

    // sortOrder 업데이트 (드래그 순서 변경용)
    @Query("UPDATE dday_items SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateSortOrder(id: Int, sortOrder: Int)

    // 여러 아이템의 sortOrder 일괄 업데이트 (트랜잭션)
    @androidx.room.Transaction
    suspend fun updateSortOrders(items: List<Pair<Int, Int>>) {
        items.forEach { (id, order) ->
            updateSortOrder(id, order)
        }
    }

    // 위젯용: D-Day + To-Do 함께
    // D-Day: 체크 즉시 숨김
    // To-Do: 체크 후 24시간 유지
    // :cutoffTime = 현재 시간 - 24시간
    @Query("""
        SELECT * FROM dday_items
        WHERE
            (itemType = 'DDAY' AND isChecked = 0 AND isHidden = 0)
            OR
            (itemType = 'TODO' AND (isChecked = 0 OR (isChecked = 1 AND checkedAt > :cutoffTime)))
        ORDER BY
            isChecked ASC,
            CASE WHEN itemType = 'DDAY' AND date IS NOT NULL THEN 0 ELSE 1 END,
            date ASC,
            id DESC
    """)
    suspend fun getAllForWidgetWithTodos(cutoffTime: Long): List<DdayItem>

    // 매년 반복: 표시 시간이 된 숨겨진 항목을 자동으로 다시 표시
    @Query("UPDATE dday_items SET isHidden = 0, nextShowDate = NULL WHERE isHidden = 1 AND nextShowDate IS NOT NULL AND nextShowDate <= :today")
    suspend fun unhideReadyItems(today: Long)

    // 기존 그룹 이름 목록 (D-Day만, null 제외)
    @Query("SELECT DISTINCT group_name FROM dday_items WHERE itemType = 'DDAY' AND group_name IS NOT NULL ORDER BY group_name ASC")
    suspend fun getDistinctGroupNames(): List<String>

    // 그룹 이름 변경 (해당 그룹의 모든 D-Day 업데이트)
    @Query("UPDATE dday_items SET group_name = :newName WHERE group_name = :oldName AND itemType = 'DDAY'")
    suspend fun renameGroup(oldName: String, newName: String)

    // 그룹 삭제 (해당 그룹의 D-Day를 미분류로 이동)
    @Query("UPDATE dday_items SET group_name = NULL WHERE group_name = :groupName AND itemType = 'DDAY'")
    suspend fun deleteGroup(groupName: String)

    // 특정 그룹의 D-Day 개수
    @Query("SELECT COUNT(*) FROM dday_items WHERE group_name = :groupName AND itemType = 'DDAY'")
    suspend fun getGroupItemCount(groupName: String): Int
}

