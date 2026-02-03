package com.silverwest.dayli.ddaywidget

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

// SubTask 데이터 클래스 (체크리스트 하위 항목)
data class SubTask(
    val title: String,
    val isChecked: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("title", title)
        put("checked", isChecked)
    }

    companion object {
        fun fromJson(json: JSONObject): SubTask = SubTask(
            title = json.optString("title", ""),
            isChecked = json.optBoolean("checked", false)
        )
    }
}

@Entity(tableName = "dday_items")
data class DdayItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val date: Date? = null,  // null이면 To-Do (날짜 없음)
    val memo: String? = null,
    val isChecked: Boolean = false,
    val checkedAt: Long? = null,  // 체크한 시간 (null = 체크 안 함)
    val category: String = DdayCategory.OTHER.name,  // 카테고리
    val iconName: String? = null,  // 커스텀 이모지 (null = 카테고리 기본 이모지 사용)
    val customColor: Long? = null,  // 커스텀 색상 (null = 카테고리 기본 색상 사용)
    val repeatType: String = RepeatType.NONE.name,  // 반복 타입 (NONE/DAILY/WEEKLY/MONTHLY/YEARLY)
    val repeatDay: Int? = null,  // WEEKLY: 요일 비트마스크, MONTHLY: 날짜 1-31
    val itemType: String = ItemType.DDAY.name,  // 아이템 타입 (DDAY / TODO)
    val sortOrder: Int = 0,  // To-Do 드래그 순서 (0 = 기본, 작을수록 위)
    @ColumnInfo(name = "sub_tasks")
    val subTasks: String? = null,  // 체크리스트 하위 항목 (JSON 형식)
    val isHidden: Boolean = false,  // 매년 반복: 체크 시 숨김
    val nextShowDate: Long? = null,  // 반복: 다시 보여줄 날짜 (epoch ms)
    val advanceDisplayDays: Int? = null,  // 미리 표시 일수 (null = 기본값 사용)
    @ColumnInfo(name = "group_name")
    val groupName: String? = null  // D-Day 그룹 이름
) {
    // SubTask 리스트로 변환
    fun getSubTaskList(): List<SubTask> {
        if (subTasks.isNullOrBlank()) return emptyList()
        return try {
            val jsonArray = JSONArray(subTasks)
            (0 until jsonArray.length()).map { i ->
                SubTask.fromJson(jsonArray.getJSONObject(i))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // SubTask 리스트를 JSON 문자열로 변환
    companion object {
        fun subTasksToJson(subTasks: List<SubTask>): String? {
            if (subTasks.isEmpty()) return null
            val jsonArray = JSONArray()
            subTasks.forEach { jsonArray.put(it.toJson()) }
            return jsonArray.toString()
        }

        // 매주 요일 비트마스크 변환
        private val DAY_NAMES = mapOf(
            Calendar.MONDAY to "월", Calendar.TUESDAY to "화",
            Calendar.WEDNESDAY to "수", Calendar.THURSDAY to "목",
            Calendar.FRIDAY to "금", Calendar.SATURDAY to "토",
            Calendar.SUNDAY to "일"
        )
        private val DAY_ORDER = listOf(
            Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
            Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY
        )

        fun weeklyDaysToBitmask(days: Set<Int>): Int {
            var mask = 0
            days.forEach { day -> mask = mask or (1 shl (day - 1)) }
            return mask
        }

        fun bitmaskToWeeklyDays(mask: Int): Set<Int> {
            return (1..7).filter { day -> mask and (1 shl (day - 1)) != 0 }.toSet()
        }

        fun bitmaskToDayNames(mask: Int): String {
            val days = bitmaskToWeeklyDays(mask)
            return DAY_ORDER.filter { it in days }.mapNotNull { DAY_NAMES[it] }.joinToString(",")
        }
    }
    // To-Do 여부 확인
    fun isTodo(): Boolean = itemType == ItemType.TODO.name

    // D-Day 여부 확인
    fun isDday(): Boolean = itemType == ItemType.DDAY.name

    // 이모지 가져오기 (커스텀 이모지 또는 카테고리 기본 이모지)
    fun getEmoji(): String {
        return iconName ?: DdayCategory.fromName(category).emoji
    }

    // 색상 가져오기 (커스텀 색상 또는 카테고리 기본 색상)
    fun getColorLong(): Long {
        return customColor ?: DdayCategory.fromName(category).colorLong
    }

    // 반복 타입 Enum으로 가져오기
    fun repeatTypeEnum(): RepeatType {
        return RepeatType.fromName(repeatType)
    }

    // 반복 여부 확인
    fun isRepeating(): Boolean {
        return repeatTypeEnum() != RepeatType.NONE
    }

    // 미리 표시 일수 (항목별 설정값 또는 기본값)
    fun getAdvanceDays(): Int {
        advanceDisplayDays?.let { return it }
        return when (repeatTypeEnum()) {
            RepeatType.WEEKLY -> 2
            RepeatType.MONTHLY -> 14
            RepeatType.YEARLY -> 30
            else -> 0
        }
    }

    // 다음 반복 날짜 계산
    fun getNextRepeatDate(): Date? {
        val type = repeatTypeEnum()
        if (type == RepeatType.NONE || date == null) return null

        val calendar = Calendar.getInstance().apply {
            time = date
        }

        when (type) {
            RepeatType.DAILY -> {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }
            RepeatType.WEEKLY -> {
                calendar.add(Calendar.WEEK_OF_YEAR, 1)
            }
            RepeatType.MONTHLY -> {
                calendar.add(Calendar.MONTH, 1)
                // 매월 반복 시 날짜 유지 (repeatDay가 있으면 해당 날짜로)
                repeatDay?.let { day ->
                    val maxDay = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
                    calendar.set(Calendar.DAY_OF_MONTH, minOf(day, maxDay))
                }
            }
            RepeatType.YEARLY -> {
                calendar.add(Calendar.YEAR, 1)
            }
            RepeatType.NONE -> return null
        }

        return calendar.time
    }

    // 다음 반복 발생일 계산 (D-Day + To-Do 통합)
    fun getNextOccurrenceDate(): Date? {
        if (!isRepeating()) return null
        if (date != null) return getNextRepeatDate()
        // To-Do: 오늘 기준 다음 발생일 계산
        val cal = Calendar.getInstance()
        when (repeatTypeEnum()) {
            RepeatType.DAILY -> cal.add(Calendar.DAY_OF_YEAR, 1)
            RepeatType.WEEKLY -> {
                val mask = repeatDay ?: 0
                if (mask != 0) {
                    val todayDow = cal.get(Calendar.DAY_OF_WEEK)
                    var found = false
                    for (i in 1..7) {
                        val checkDay = ((todayDow - 1 + i) % 7) + 1
                        if (mask and (1 shl (checkDay - 1)) != 0) {
                            cal.add(Calendar.DAY_OF_YEAR, i)
                            found = true
                            break
                        }
                    }
                    if (!found) cal.add(Calendar.WEEK_OF_YEAR, 1)
                } else {
                    cal.add(Calendar.WEEK_OF_YEAR, 1)
                }
            }
            RepeatType.MONTHLY -> cal.add(Calendar.MONTH, 1)
            RepeatType.YEARLY -> cal.add(Calendar.YEAR, 1)
            RepeatType.NONE -> return null
        }
        return cal.time
    }

    // 반복 아이콘 포함 이모지 가져오기
    fun getDisplayEmoji(): String {
        val emoji = getEmoji()
        return if (isRepeating()) "🔁$emoji" else emoji
    }

    // 반복 태그 텍스트 가져오기 (예: [매주 월,수,금], [매월 15일])
    fun getRepeatTagText(): String? {
        val type = repeatTypeEnum()
        if (type == RepeatType.NONE) return null

        return when (type) {
            RepeatType.DAILY -> "[매일]"
            RepeatType.WEEKLY -> {
                val names = repeatDay?.let { bitmaskToDayNames(it) } ?: ""
                if (names.isEmpty()) "[매주]" else "[매주 $names]"
            }
            RepeatType.MONTHLY -> {
                if (isTodo()) {
                    "[매월]"
                } else {
                    val dayOfMonth = repeatDay ?: date?.let { d ->
                        Calendar.getInstance().apply { time = d }.get(Calendar.DAY_OF_MONTH)
                    } ?: 1
                    "[매월 ${dayOfMonth}일]"
                }
            }
            RepeatType.YEARLY -> "[매년]"
            RepeatType.NONE -> null
        }
    }
}
