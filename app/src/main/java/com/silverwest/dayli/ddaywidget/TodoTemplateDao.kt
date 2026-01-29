package com.silverwest.dayli.ddaywidget

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface TodoTemplateDao {
    // 모든 템플릿 조회 (최신순)
    @Query("SELECT * FROM todo_templates ORDER BY createdAt DESC")
    suspend fun getAll(): List<TodoTemplate>

    // 템플릿 이름으로 조회
    @Query("SELECT * FROM todo_templates WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): TodoTemplate?

    // 템플릿 ID로 조회
    @Query("SELECT * FROM todo_templates WHERE id = :id")
    suspend fun getById(id: Int): TodoTemplate?

    @Insert
    suspend fun insert(template: TodoTemplate)

    @Update
    suspend fun update(template: TodoTemplate)

    @Delete
    suspend fun delete(template: TodoTemplate)

    // 템플릿 이름 변경
    @Query("UPDATE todo_templates SET name = :newName WHERE id = :id")
    suspend fun rename(id: Int, newName: String)

    // 템플릿 개수
    @Query("SELECT COUNT(*) FROM todo_templates")
    suspend fun getCount(): Int
}
