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

    // D-Day 아이템만 (최신순)
    @Query("SELECT * FROM dday_items WHERE itemType = 'DDAY' ORDER BY id DESC")
    suspend fun getAllDdays(): List<DdayItem>

    // D-Day 아이템만 (D-Day 임박순)
    @Query("SELECT * FROM dday_items WHERE itemType = 'DDAY' ORDER BY date ASC")
    suspend fun getAllDdaysByDate(): List<DdayItem>

    // To-Do 아이템만 (최신순)
    @Query("SELECT * FROM dday_items WHERE itemType = 'TODO' ORDER BY id DESC")
    suspend fun getAllTodos(): List<DdayItem>

    // To-Do 아이템만 (체크 안 된 것 먼저, 그 다음 최신순)
    @Query("SELECT * FROM dday_items WHERE itemType = 'TODO' ORDER BY isChecked ASC, id DESC")
    suspend fun getAllTodosSorted(): List<DdayItem>

    // 위젯용: D-Day + To-Do 함께
    // D-Day: 체크 즉시 숨김
    // To-Do: 체크 후 24시간 유지
    // :cutoffTime = 현재 시간 - 24시간
    @Query("""
        SELECT * FROM dday_items
        WHERE
            (itemType = 'DDAY' AND isChecked = 0)
            OR
            (itemType = 'TODO' AND (isChecked = 0 OR (isChecked = 1 AND checkedAt > :cutoffTime)))
        ORDER BY
            isChecked ASC,
            CASE WHEN itemType = 'DDAY' AND date IS NOT NULL THEN 0 ELSE 1 END,
            date ASC,
            id DESC
    """)
    suspend fun getAllForWidgetWithTodos(cutoffTime: Long): List<DdayItem>
}

