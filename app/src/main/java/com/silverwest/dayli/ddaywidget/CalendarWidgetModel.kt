package com.silverwest.dayli.ddaywidget

import java.util.Calendar
import java.util.Locale

/** 위젯 달력의 한 칸 (일요일 시작 6주 = 42칸) */
data class CalCell(
    val day: Int?,               // 이번 달이 아니면 null (빈칸)
    val col: Int,                // 0=일 … 6=토
    val isToday: Boolean,
    val dayliDot: Boolean,       // 그날 끝나지 않은 Dayli D-Day가 있음
    val phoneColors: List<Int>   // 폰 캘린더 일정 색 (최대 2개)
)

/** 오늘·내일 목록의 한 줄 */
data class AgendaEntry(val time: String, val title: String, val color: Int?, val sortMinutes: Int)

data class CalendarWidgetModel(
    val year: Int,
    val month1: Int,             // 1~12
    val cells: List<CalCell>,
    val rowsUsed: Int,           // 이번 달이 차지하는 주 수 (4~6)
    val today: Triple<Int, Int, Int>,
    val tomorrow: Triple<Int, Int, Int>,
    val todayEntries: List<AgendaEntry>,
    val tomorrowEntries: List<AgendaEntry>
)

/** Dayli D-Day를 위젯용으로 줄인 것 (DB 객체와 분리해 단위 테스트) */
data class DayliDay(
    val day: Triple<Int, Int, Int>,  // 연, 월(1~12), 일
    val title: String,
    val emoji: String,
    val hour: Int?,
    val minute: Int?
)

/** 캘린더 위젯 데이터 계산 (순수 함수). color=null인 줄은 Dayli 강조색으로 그림 */
object CalendarWidgetModelBuilder {

    fun build(now: Calendar, dayli: List<DayliDay>, phone: List<PhoneEvent>): CalendarWidgetModel {
        val year = now.get(Calendar.YEAR)
        val month0 = now.get(Calendar.MONTH)
        val today = Triple(year, month0 + 1, now.get(Calendar.DAY_OF_MONTH))
        val tomorrowCal = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }
        val tomorrow = Triple(tomorrowCal.get(Calendar.YEAR), tomorrowCal.get(Calendar.MONTH) + 1, tomorrowCal.get(Calendar.DAY_OF_MONTH))

        val first = (now.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
        val firstCol = first.get(Calendar.DAY_OF_WEEK) - 1
        val daysInMonth = first.getActualMaximum(Calendar.DAY_OF_MONTH)

        val dayliDays = dayli.filter { it.day.first == year && it.day.second == month0 + 1 }.map { it.day.third }.toSet()
        val phoneByDay = phone.filter { it.startDay.first == year && it.startDay.second == month0 + 1 }
            .groupBy({ it.startDay.third }, { it.color })

        val cells = (0 until 42).map { i ->
            val day = i - firstCol + 1
            if (day in 1..daysInMonth) {
                CalCell(
                    day = day,
                    col = i % 7,
                    isToday = day == today.third,
                    dayliDot = day in dayliDays,
                    phoneColors = phoneByDay[day].orEmpty().distinct().take(2)
                )
            } else CalCell(null, i % 7, false, false, emptyList())
        }
        val rowsUsed = (firstCol + daysInMonth + 6) / 7

        return CalendarWidgetModel(
            year = year,
            month1 = month0 + 1,
            cells = cells,
            rowsUsed = rowsUsed,
            today = today,
            tomorrow = tomorrow,
            todayEntries = entriesFor(today, dayli, phone, dayLabel = "D-Day"),
            tomorrowEntries = entriesFor(tomorrow, dayli, phone, dayLabel = "D-1")
        )
    }

    /** 그날의 Dayli D-Day + 폰 캘린더 일정. 종일·시간 없는 것이 먼저, 그다음 시간순 */
    internal fun entriesFor(
        day: Triple<Int, Int, Int>,
        dayli: List<DayliDay>,
        phone: List<PhoneEvent>,
        dayLabel: String
    ): List<AgendaEntry> {
        val fromDayli = dayli.filter { it.day == day }.map {
            val timed = it.hour != null
            AgendaEntry(
                time = if (timed) timeText(it.hour!!, it.minute ?: 0) else dayLabel,
                title = "${it.emoji} ${it.title}".trim(),
                color = null,
                sortMinutes = if (timed) it.hour!! * 60 + (it.minute ?: 0) else -1
            )
        }
        val fromPhone = phone.filter { it.startDay == day }.map {
            if (it.allDay) {
                AgendaEntry("종일", it.title, it.color, -1)
            } else {
                val c = Calendar.getInstance().apply { timeInMillis = it.beginMs }
                val h = c.get(Calendar.HOUR_OF_DAY)
                val m = c.get(Calendar.MINUTE)
                AgendaEntry(timeText(h, m), it.title, it.color, h * 60 + m)
            }
        }
        return (fromDayli + fromPhone).sortedBy { it.sortMinutes }
    }

    /** 15:00 → "오후 3:00" */
    fun timeText(hour: Int, minute: Int): String {
        val ampm = if (hour < 12) "오전" else "오후"
        val h12 = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        return "%s %d:%02d".format(Locale.KOREAN, ampm, h12, minute)
    }
}
