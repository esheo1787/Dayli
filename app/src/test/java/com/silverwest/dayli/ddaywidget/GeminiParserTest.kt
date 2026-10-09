package com.silverwest.dayli.ddaywidget

import com.silverwest.dayli.ddaywidget.GeminiParser.ParsedEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiParserTest {

    // ===== 자정 마감 보정 =====

    @Test
    fun midnightDeadline_movesToPreviousDay2359() {
        val event = ParsedEvent(title = "생활법률 5주차 퀴즈", date = "2026-10-13", timeHour = 0, timeMinute = 0, deadline = true)
        val result = GeminiParser.normalizeDeadline(event)
        assertEquals("2026-10-12", result.date)
        assertEquals(23, result.timeHour)
        assertEquals(59, result.timeMinute)
    }

    @Test
    fun midnightDeadline_crossesMonthBoundary() {
        val event = ParsedEvent(date = "2026-11-01", timeHour = 0, timeMinute = 0, deadline = true)
        assertEquals("2026-10-31", GeminiParser.normalizeDeadline(event).date)
    }

    @Test
    fun noonDeadline_unchanged() {
        val event = ParsedEvent(date = "2026-10-12", timeHour = 12, timeMinute = 0, deadline = true)
        assertEquals(event, GeminiParser.normalizeDeadline(event))
    }

    @Test
    fun midnightNonDeadline_unchanged() {
        // 마감이 아닌 자정 일정(예: 새해 카운트다운)은 그대로
        val event = ParsedEvent(date = "2027-01-01", timeHour = 0, timeMinute = 0, deadline = false)
        assertEquals(event, GeminiParser.normalizeDeadline(event))
    }

    @Test
    fun deadlineWithoutTime_unchanged() {
        val event = ParsedEvent(date = "2026-10-13", deadline = true)
        assertEquals(event, GeminiParser.normalizeDeadline(event))
    }

    // ===== 그룹 이름 매칭 =====

    @Test
    fun resolveGroup_exactExistingMatch() {
        assertEquals("대학교" to false, GeminiParser.resolveGroupName("대학교", listOf("회사", "대학교")))
    }

    @Test
    fun resolveGroup_spacingDifference_usesExistingSpelling() {
        assertEquals("2학기 수업" to false, GeminiParser.resolveGroupName("2학기수업", listOf("2학기 수업")))
    }

    @Test
    fun resolveGroup_newGroup() {
        assertEquals("학교" to true, GeminiParser.resolveGroupName("학교", listOf("회사")))
    }

    @Test
    fun resolveGroup_blank_returnsNull() {
        val (name, isNew) = GeminiParser.resolveGroupName("  ", listOf("학교"))
        assertNull(name)
        assertFalse(isNew)
    }

    // ===== 응답 파싱 =====

    @Test
    fun parseResponse_readsGroupAndDeadlineFields() {
        val json = """
            ```json
            [
              {"title":"회계정보시스템 5주차 퀴즈","date":"2026-10-12","timeHour":12,"timeMinute":0,"emoji":"📝","memo":"퀴즈 | 이원주 교수","groupName":"학교","groupEmoji":"🏫","deadline":true},
              {"title":"정보보호시스템 과제(레포트) 공지","date":"2026-11-23","timeHour":0,"timeMinute":0,"emoji":"📄","memo":"과제 | 이승원 교수","groupName":"학교","groupEmoji":null,"deadline":true}
            ]
            ```
        """.trimIndent()
        val events = GeminiParser.parseResponse(json)
        assertEquals(2, events.size)
        assertEquals("학교", events[0].groupName)
        assertEquals("🏫", events[0].groupEmoji)
        assertTrue(events[0].deadline)
        assertNull(events[1].groupEmoji)
    }

    @Test
    fun parseResponse_missingNewFields_defaults() {
        val events = GeminiParser.parseResponse("""{"title":"치과","date":"2026-10-20"}""")
        assertNull(events[0].groupName)
        assertFalse(events[0].deadline)
    }
}
