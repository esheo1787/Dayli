package com.silverwest.dayli.ddaywidget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class CalendarWidgetModelTest {

    private val seoul = TimeZone.getTimeZone("Asia/Seoul")

    private fun at(y: Int, m1: Int, d: Int, h: Int = 9, min: Int = 0): Calendar =
        Calendar.getInstance(seoul).apply { clear(); set(y, m1 - 1, d, h, min) }

    private fun phone(y: Int, m1: Int, d: Int, h: Int, color: Int = 0xFF3F7FD9.toInt(), allDay: Boolean = false, title: String = "회의"): PhoneEvent {
        val begin = if (allDay) {
            Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { clear(); set(y, m1 - 1, d) }.timeInMillis
        } else at(y, m1, d, h).timeInMillis
        return PhoneEvent(1, 1, title, begin, begin + 3_600_000, allDay, color, null, "구글")
    }

    @Test
    fun october2026_gridStartsThursday_fiveRows() {
        // 2026-10-01은 목요일 → 앞 4칸 빈칸, 31일까지 5주
        val model = CalendarWidgetModelBuilder.build(at(2026, 10, 11), emptyList(), emptyList())
        assertEquals(42, model.cells.size)
        (0..3).forEach { assertNull(model.cells[it].day) }
        assertEquals(1, model.cells[4].day)
        assertEquals(4, model.cells[4].col)
        assertEquals(5, model.rowsUsed)
        assertTrue(model.cells[4 + 10].isToday)
    }

    @Test
    fun dots_dayliAndPhone_onTheirDays() {
        val dayli = listOf(DayliDay(Triple(2026, 10, 20), "과제", "📚", null, null))
        val phone = listOf(phone(2026, 10, 20, 15), phone(2026, 10, 21, 10, color = 0xFF2DB400.toInt()))
        val model = CalendarWidgetModelBuilder.build(at(2026, 10, 11), dayli, phone)
        val day20 = model.cells.first { it.day == 20 }
        assertTrue(day20.dayliDot)
        assertEquals(listOf(0xFF3F7FD9.toInt()), day20.phoneColors)
        assertFalse(model.cells.first { it.day == 21 }.dayliDot)
        // 다른 달 일정은 점 없음
        val nov = CalendarWidgetModelBuilder.build(at(2026, 11, 1), dayli, phone)
        assertTrue(nov.cells.none { it.dayliDot || it.phoneColors.isNotEmpty() })
    }

    @Test
    fun agenda_todayAndTomorrow_sortedUntimedFirst() {
        val dayli = listOf(
            DayliDay(Triple(2026, 10, 11), "퀴즈", "📝", null, null),
            DayliDay(Triple(2026, 10, 12), "치과", "🦷", 14, 0)
        )
        val phone = listOf(phone(2026, 10, 11, 15, title = "조별 회의"), phone(2026, 10, 12, 0, allDay = true, title = "결혼식"))
        val model = CalendarWidgetModelBuilder.build(at(2026, 10, 11), dayli, phone)
        assertEquals(listOf("D-Day", "오후 3:00"), model.todayEntries.map { it.time })
        assertEquals("📝 퀴즈", model.todayEntries[0].title)
        assertEquals(listOf("종일", "오후 2:00"), model.tomorrowEntries.map { it.time })
        assertNull(model.tomorrowEntries[1].color)  // Dayli 줄은 강조색
    }

    @Test
    fun tomorrow_crossesMonthEnd() {
        val model = CalendarWidgetModelBuilder.build(at(2026, 10, 31), emptyList(), listOf(phone(2026, 11, 1, 10)))
        assertEquals(Triple(2026, 11, 1), model.tomorrow)
        assertEquals(1, model.tomorrowEntries.size)
    }

    @Test
    fun timeText_korean12h() {
        assertEquals("오전 12:05", CalendarWidgetModelBuilder.timeText(0, 5))
        assertEquals("오후 12:00", CalendarWidgetModelBuilder.timeText(12, 0))
        assertEquals("오후 9:30", CalendarWidgetModelBuilder.timeText(21, 30))
    }

    @Test
    fun phoneCalendar_allDayUsesUtcDate() {
        // 종일 일정은 UTC 자정으로 저장 → 시간대와 상관없이 그 날짜
        val utcMidnight = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { clear(); set(2026, 9, 14) }.timeInMillis
        assertEquals(Triple(2026, 10, 14), PhoneCalendar.localDay(utcMidnight, allDay = true, zone = TimeZone.getTimeZone("America/Los_Angeles")))
        // 시간 있는 일정은 기기 시간대 기준
        val seoulLate = at(2026, 10, 14, 23).timeInMillis
        assertEquals(Triple(2026, 10, 14), PhoneCalendar.localDay(seoulLate, allDay = false, zone = seoul))
    }

    @Test
    fun phoneCalendar_sourceLabelAndImportKey() {
        assertEquals("구글", PhoneCalendar.sourceLabel("com.google"))
        assertEquals("삼성", PhoneCalendar.sourceLabel("com.osp.app.signin"))
        assertEquals("캘린더", PhoneCalendar.sourceLabel("at.bitfire.davdroid"))
        // 반복 일정은 회차마다 다른 키
        assertTrue(PhoneCalendar.importKey(5, 1000) != PhoneCalendar.importKey(5, 2000))
    }
}
