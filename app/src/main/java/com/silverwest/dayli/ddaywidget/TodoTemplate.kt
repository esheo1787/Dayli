package com.silverwest.dayli.ddaywidget

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONArray
import org.json.JSONObject

/**
 * To-Do 템플릿 엔티티
 * - 자주 사용하는 To-Do 구성을 템플릿으로 저장
 */
@Entity(tableName = "todo_templates")
data class TodoTemplate(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,                    // 템플릿 이름
    val iconName: String = "📋",         // 템플릿 아이콘
    val customColor: Long = 0xFFA8C5DAL, // 기본 색상
    val subTasks: String? = null,        // JSON 형태의 서브태스크 목록
    val createdAt: Long = System.currentTimeMillis()
) {
    // 서브태스크 리스트 파싱
    fun getSubTaskList(): List<SubTask> {
        if (subTasks.isNullOrBlank()) return emptyList()
        return try {
            val jsonArray = JSONArray(subTasks)
            (0 until jsonArray.length()).map { i ->
                val obj = jsonArray.getJSONObject(i)
                SubTask(
                    title = obj.getString("title"),
                    isChecked = obj.optBoolean("isChecked", false)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    companion object {
        fun subTasksToJson(subTasks: List<SubTask>): String? {
            if (subTasks.isEmpty()) return null
            // 템플릿 저장 시 체크 상태 초기화
            val jsonArray = JSONArray()
            subTasks.forEach { subTask ->
                val obj = JSONObject().apply {
                    put("title", subTask.title)
                    put("isChecked", false)  // 항상 false로 저장
                }
                jsonArray.put(obj)
            }
            return jsonArray.toString()
        }
    }
}
