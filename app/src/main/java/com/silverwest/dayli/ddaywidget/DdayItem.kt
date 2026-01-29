package com.silverwest.dayli.ddaywidget

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.*

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
    val repeatDay: Int? = null,  // 반복 기준 (매주: 요일 1-7, 매월: 날짜 1-31)
    val itemType: String = ItemType.DDAY.name,  // 아이템 타입 (DDAY / TODO)
    val sortOrder: Int = 0  // To-Do 드래그 순서 (0 = 기본, 작을수록 위)
) {
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

    // 반복 아이콘 포함 이모지 가져오기
    fun getDisplayEmoji(): String {
        val emoji = getEmoji()
        return if (isRepeating()) "🔁$emoji" else emoji
    }

    // 반복 태그 텍스트 가져오기 (예: [매주 월], [매월 15일])
    fun getRepeatTagText(): String? {
        val type = repeatTypeEnum()
        if (type == RepeatType.NONE) return null

        return when (type) {
            RepeatType.DAILY -> "[매일]"
            RepeatType.WEEKLY -> {
                val dayName = repeatDay?.let { day ->
                    when (day) {
                        Calendar.SUNDAY -> "일"
                        Calendar.MONDAY -> "월"
                        Calendar.TUESDAY -> "화"
                        Calendar.WEDNESDAY -> "수"
                        Calendar.THURSDAY -> "목"
                        Calendar.FRIDAY -> "금"
                        Calendar.SATURDAY -> "토"
                        else -> ""
                    }
                } ?: ""
                "[매주 $dayName]"
            }
            RepeatType.MONTHLY -> {
                val dayOfMonth = repeatDay ?: date?.let { d ->
                    Calendar.getInstance().apply { time = d }.get(Calendar.DAY_OF_MONTH)
                } ?: 1
                "[매월 ${dayOfMonth}일]"
            }
            RepeatType.YEARLY -> "[매년]"
            RepeatType.NONE -> null
        }
    }
}
