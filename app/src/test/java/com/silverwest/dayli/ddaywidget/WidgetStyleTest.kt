package com.silverwest.dayli.ddaywidget

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetStyleTest {

    private val pastels = ddayColors.map { it.color }
    private val strong = listOf(Color(0xFF1B5E20), Color(0xFF0D47A1), Color(0xFFB71C1C), Color(0xFF212121))

    @Test
    fun itemColors_cardMatchesAppCard() {
        // 위젯 카드 색 = 앱 목록 카드 색 (같은 테마 surface 위에 같은 공식)
        for (dark in listOf(false, true)) for (tinted in listOf(false, true)) {
            val itemColor = Color(0xFFFF8A80)
            val widget = WidgetStyle.itemColors(itemColor, dark, tinted, 35, 20, checked = false, ddayDiff = 10)
            val app = DayliUi.cardColors(itemColor, WidgetStyle.surface(dark), dark, tinted, 0.35f, 0.20f, checked = false)
            assertEquals(app.card, widget.card)
            assertEquals(app.tile, widget.tile)
            assertEquals(app.text, widget.title)
        }
    }

    @Test
    fun itemColors_allOpaque_evenWhenBadgeIsTranslucent() {
        // 위젯은 뷰 알파를 못 주므로 모든 색이 불투명이어야 함 (항목 배경 켜짐 → 배지가 반투명 흰색)
        val c = WidgetStyle.itemColors(Color(0xFF81D4FA), dark = false, tinted = true, 40, 20, checked = false, ddayDiff = 10)
        listOf(c.card, c.tile, c.title, c.sub, c.pillBg, c.pillText).forEach { assertEquals(1f, it.alpha) }
    }

    @Test
    fun itemColors_strongTint_textStaysReadable() {
        // 카드 색을 진하게(100%) 해도 제목·날짜 글자 대비 4.5:1 이상
        for (dark in listOf(false, true)) for (color in pastels + strong) {
            val c = WidgetStyle.itemColors(color, dark, tinted = true, 100, 20, checked = false, ddayDiff = 10)
            assertTrue("title $color dark=$dark", DayliUi.contrast(c.title, c.card) >= 4.5f)
            assertTrue("sub $color dark=$dark", DayliUi.contrast(c.sub, c.card) >= 4.5f)
        }
    }

    @Test
    fun itemColors_tintedOff_cardIsSurface_tileUsesItemColor() {
        val c = WidgetStyle.itemColors(Color(0xFFFF8A80), dark = false, tinted = false, 35, 30, checked = false, ddayDiff = 10)
        assertEquals(WidgetStyle.surface(false), c.card)
        assertNotEquals(WidgetStyle.surface(false), c.tile)
    }

    @Test
    fun itemColors_badgeFollowsAppRules() {
        val urgent = WidgetStyle.itemColors(Color(0xFF81D4FA), false, false, 15, 20, false, ddayDiff = 1)
        val soon = WidgetStyle.itemColors(Color(0xFF81D4FA), false, false, 15, 20, false, ddayDiff = 3)
        val later = WidgetStyle.itemColors(Color(0xFF81D4FA), false, false, 15, 20, false, ddayDiff = 4)
        assertEquals(Color(0xFFB3261E), urgent.pillText)  // PillUrgentText
        assertEquals(Color(0xFF2B5F8A), soon.pillText)    // PillSoonText
        assertNotEquals(urgent.pillBg, later.pillBg)
    }

    @Test
    fun itemColors_checkedIsDimmed() {
        val normal = WidgetStyle.itemColors(Color(0xFF81D4FA), false, true, 30, 20, checked = false, ddayDiff = null)
        val done = WidgetStyle.itemColors(Color(0xFF81D4FA), false, true, 30, 20, checked = true, ddayDiff = null)
        assertTrue(DayliUi.contrast(done.title, done.card) < DayliUi.contrast(normal.title, normal.card))
    }

    @Test
    fun containerAlpha_mapsPercentTo255() {
        assertEquals(0, WidgetStyle.containerAlpha(0))
        assertEquals(51, WidgetStyle.containerAlpha(20))
        assertEquals(255, WidgetStyle.containerAlpha(100))
        assertEquals(255, WidgetStyle.containerAlpha(150))
    }

    @Test
    fun heroColors_textReadable_forAutoAndPresetColors() {
        for (color in pastels) {
            val auto = WidgetStyle.heroColors(DdaySettings.HERO_COLOR_AUTO, color)
            assertTrue("auto $color", DayliUi.contrast(auto.text, auto.bg) >= 4.5f)
        }
        val preset = WidgetStyle.heroColors(DdaySettings.DEFAULT_HERO_COLOR, Color(0xFFFFF59D))
        assertEquals(DayliUi.parseHex(DdaySettings.DEFAULT_HERO_COLOR), preset.bg)
        assertEquals(Color.White, preset.text)
    }
}
