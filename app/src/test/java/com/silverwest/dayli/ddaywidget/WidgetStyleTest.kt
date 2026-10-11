package com.silverwest.dayli.ddaywidget

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
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
    fun itemColors_customColors_textStaysReadable() {
        // "더 많은 색"의 모든 진하기(중간 밝기 포함)·무채색을, 라이트·다크와 카드 진하기별로
        // 투명도를 합친 최종 카드 배경 기준 대비 4.5:1 이상
        val custom = (0 until 360 step 15).flatMap { hue ->
            toneLightness.map { l -> Color.hsl(hue.toFloat(), TONE_SATURATION, l) }
        } + neutralColors.map { Color(it) }
        for (dark in listOf(false, true)) for (opacity in listOf(15, 35, 60, 80, 100)) for (color in custom) {
            val c = WidgetStyle.itemColors(color, dark, tinted = true, opacity, 20, checked = false, ddayDiff = 10)
            assertTrue("title $color dark=$dark $opacity%", DayliUi.contrast(c.title, c.card) >= 4.5f)
            assertTrue("sub $color dark=$dark $opacity%", DayliUi.contrast(c.sub, c.card) >= 4.5f)
            // D-Day 배지: 반투명 배지 바탕에 카드 색이 비친 최종 색 기준
            for (diff in listOf(10, 3, 1)) {
                val p = WidgetStyle.itemColors(color, dark, tinted = true, opacity, 20, checked = false, ddayDiff = diff)
                assertTrue("pill $color dark=$dark $opacity% D-$diff", DayliUi.contrast(p.pillText, p.pillBg) >= 4.5f)
            }
        }
    }

    @Test
    fun pill_midToneCardInDark_isCorrected() {
        // 리뷰에서 찾은 경우: 다크 모드 · #7986CB · 카드 100% → 기존 글자 약 2.29:1
        val c = WidgetStyle.itemColors(Color(0xFF7986CB), dark = true, tinted = true, 100, 20, checked = false, ddayDiff = 9)
        assertTrue(DayliUi.contrast(c.pillText, c.pillBg) >= 4.5f)
    }

    @Test
    fun header_lowOpacity_usesWallpaperStyle_highOpacity_usesThemeText() {
        // 기본 위젯 바탕(20%)은 배경화면이 비침 → 흰 글자 + 그림자
        for (dark in listOf(false, true)) {
            assertTrue("dark=$dark 20%", WidgetStyle.headerOnWallpaper(dark, 20))
            assertEquals(Color.White, WidgetStyle.headerText(dark, onWallpaper = true))
            assertTrue("dark=$dark 100%", !WidgetStyle.headerOnWallpaper(dark, 100))
        }
    }

    @Test
    fun header_themeText_readableOnAnyWallpaperWhenChosen() {
        // 테마 글자를 쓰는 투명도에서는 배경화면이 검정이든 흰색이든 4.5:1 이상
        for (dark in listOf(false, true)) for (opacity in 0..100 step 5) {
            if (WidgetStyle.headerOnWallpaper(dark, opacity)) continue
            val text = WidgetStyle.headerText(dark, onWallpaper = false)
            val shown = WidgetStyle.containerBase(dark).copy(alpha = WidgetStyle.containerAlpha(opacity) / 255f)
            for (wallpaper in listOf(Color.Black, Color.White)) {
                assertTrue("dark=$dark $opacity%", DayliUi.contrast(text, shown.compositeOver(wallpaper)) >= 4.5f)
            }
        }
    }

    @Test
    fun headerWallpaperLayouts_whiteTextWithShadow() {
        // 위젯은 그림자를 실행 중에 못 바꿔 레이아웃에 적어 둠 → 코드의 그림자 색과 어긋나지 않게
        for (file in listOf("item_widget_section_header_wallpaper.xml", "item_widget_group_header_wallpaper.xml")) {
            val xml = java.io.File("src/main/res/layout", file).readText()
            val shadows = Regex("""android:shadowColor="#([0-9A-Fa-f]{8})"""").findAll(xml).map { it.groupValues[1].toLong(16) }.toList()
            assertEquals("$file: TextView마다 그림자", Regex("<TextView").findAll(xml).count(), shadows.size)
            shadows.forEach { assertEquals(file, WidgetStyle.HEADER_WALLPAPER_SHADOW, Color(it)) }
        }
    }

    @Test
    fun readableText_keepsDefaultWhenEnough_correctsMidTones() {
        // 밝은 파스텔은 기존 진회색 유지
        assertEquals(DayliUi.DarkText, DayliUi.readableText(Color(0xFFFFF59D)))
        // 진한 색은 기존 밝은 회색 유지
        assertEquals(DayliUi.LightText, DayliUi.readableText(Color(0xFF0D47A1)))
        // 중간 밝기(기존 두 색 모두 4.5:1 미만)는 검정·흰색으로 보정
        val mid = Color(0xFF7986CB)
        val text = DayliUi.readableText(mid)
        assertTrue(text == Color.Black || text == Color.White)
        assertTrue(DayliUi.contrast(text, mid) >= 4.5f)
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
