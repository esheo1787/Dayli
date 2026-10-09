package com.silverwest.dayli.ddaywidget

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Date

class DayliUiTest {

    private fun date(y: Int, m: Int, d: Int): Date =
        Calendar.getInstance().apply { clear(); set(y, m - 1, d) }.time

    // 오늘 = 2026-10-09 로 고정한 diff 계산
    private val today = date(2026, 10, 9)
    private val diffOf: (Date) -> Int = { ((it.time - today.time) / 86_400_000L).toInt() }

    private fun dday(id: Int, d: Date?, checked: Boolean = false, hidden: Boolean = false, hour: Int? = null) =
        DdayItem(id = id, title = "t$id", date = d, isChecked = checked, isHidden = hidden, timeHour = hour, timeMinute = hour?.let { 0 })

    @Test
    fun koreanDate_formatsMonthDayWeekday() {
        assertEquals("10월 14일 (수)", DayliUi.koreanDate(date(2026, 10, 14)))
        assertEquals("11월 22일 (일)", DayliUi.koreanDate(date(2026, 11, 22)))
    }

    @Test
    fun nearestUpcoming_picksSmallestNonNegativeDiff_skipsDoneHiddenPast() {
        val items = listOf(
            dday(1, date(2026, 10, 8)),                 // 지난 일정
            dday(2, date(2026, 10, 10), checked = true), // 완료
            dday(3, date(2026, 10, 11), hidden = true),  // 숨김
            dday(4, date(2026, 10, 14)),
            dday(5, date(2026, 10, 12)),
            dday(6, null)                                // To-Do성
        )
        assertEquals(5, DayliUi.nearestUpcoming(items, diffOf)?.id)
    }

    @Test
    fun nearestUpcoming_sameDay_earlierTimeFirst_untimedLast() {
        val d = date(2026, 10, 12)
        val items = listOf(dday(1, d), dday(2, d, hour = 15), dday(3, d, hour = 9))
        assertEquals(3, DayliUi.nearestUpcoming(items, diffOf)?.id)
    }

    @Test
    fun nearestUpcoming_todayCounts_emptyReturnsNull() {
        assertEquals(7, DayliUi.nearestUpcoming(listOf(dday(7, today)), diffOf)?.id)
        assertNull(DayliUi.nearestUpcoming(emptyList(), diffOf))
    }

    @Test
    fun parseHex_validAndInvalid() {
        assertEquals(Color(0xFF4A7390), DayliUi.parseHex("#4A7390"))
        assertEquals(Color(0xFF4A7390), DayliUi.parseHex("4a7390"))
        assertNull(DayliUi.parseHex("#12345"))
        assertNull(DayliUi.parseHex("#GGGGGG"))
    }

    @Test
    fun deepen_makesPastelReadableWithWhiteText() {
        listOf(0xFFFFF59D, 0xFFA5D6A7, 0xFF81D4FA, 0xFFFFF8E1).forEach { c ->
            assertTrue(DayliUi.contrastWithWhite(DayliUi.deepen(Color(c))) >= 4.5f)
        }
    }

    @Test
    fun readableOn_switchesToDarkTextOnLightColor() {
        assertEquals(Color.White, DayliUi.readableOn(Color(0xFF4A7390)))
        assertEquals(Color(0xFF2F2D2B), DayliUi.readableOn(Color(0xFFF8C8DC)))
    }

    @Test
    fun cardText_picksReadableSide_forEveryPaletteColorAtFullStrength() {
        // 팔레트 14색 × 진하기 100% (흰 배경 위): 선택된 글자색 대비가 4.5:1 이상
        val palette = listOf(0xFFFF8A80, 0xFFFFCC80, 0xFFFFF59D, 0xFFA5D6A7, 0xFF81D4FA, 0xFFCE93D8, 0xFFFFF8E1,
            0xFFF48FB1, 0xFFFFAB91, 0xFFC5E1A5, 0xFF80CBC4, 0xFF9FA8DA, 0xFFB39DDB, 0xFF90A4AE)
        palette.forEach { c ->
            val bg = Color(c)
            val text = if (DayliUi.prefersDarkText(bg)) DayliUi.DarkText else DayliUi.LightText
            assertTrue("0x${c.toString(16)} 대비 ${DayliUi.contrast(text, bg)}", DayliUi.contrast(text, bg) >= 4.5f)
        }
        // 다크 모드 카드(진한 배경)는 밝은 글자
        assertTrue(!DayliUi.prefersDarkText(Color(0xFF252423)))
    }

    @Test
    fun groupNameFromKey_onlyGroupItems() {
        assertEquals("학교", groupNameFromKey("group_학교"))
        assertEquals("group_x", groupNameFromKey("group_group_x"))
        assertNull(groupNameFromKey("hero_card"))
        assertNull(groupNameFromKey(42))
        assertNull(groupNameFromKey(null))
    }

    @Test
    fun heroColor_presetHexAndAutoAndFallback() {
        assertEquals(Color(0xFF6B5891), DayliUi.heroColor("#6B5891", null))
        assertEquals(DayliUi.heroColor(DdaySettings.DEFAULT_HERO_COLOR, null), DayliUi.heroColor("broken", null))
        val item = DdayItem(title = "x", customColor = 0xFFA5D6A7L)
        assertTrue(DayliUi.contrastWithWhite(DayliUi.heroColor(DdaySettings.HERO_COLOR_AUTO, item)) >= 4.5f)
    }
}
