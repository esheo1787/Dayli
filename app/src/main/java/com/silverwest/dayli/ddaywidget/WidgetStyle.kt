package com.silverwest.dayli.ddaywidget

import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import com.silverwest.dayli.ui.theme.*
import kotlin.math.roundToInt

/**
 * 위젯 색 (RemoteViewsFactory · 위젯 Provider · 설정의 위젯 미리보기 공통)
 * 앱 테마 색과 DayliUi.cardColors를 그대로 써서 앱 목록 카드와 같은 색이 나오게 함
 */
object WidgetStyle {

    fun surface(dark: Boolean): Color = if (dark) DarkSurface else SoftWhite
    fun containerBase(dark: Boolean): Color = if (dark) DarkBackground else CreamWhite
    private fun surfaceVariant(dark: Boolean): Color = if (dark) DarkSurfaceVariant else PastelGrayLight
    fun onSurface(dark: Boolean): Color = if (dark) LightGray else WarmGray
    fun onSurfaceVariant(dark: Boolean): Color = if (dark) LightGrayDim else WarmGrayLight

    /** 위젯 전체 배경 투명도 0~100 → 알파 0~255 */
    fun containerAlpha(opacityPercent: Int): Int = (opacityPercent * 2.55f).roundToInt().coerceIn(0, 255)

    /** 항목 카드 색. 모두 불투명 (위젯은 뷰 알파를 줄 수 없어 미리 합성) */
    data class ItemColors(
        val card: Color,
        val tile: Color,
        val title: Color,
        val sub: Color,
        val pillBg: Color,
        val pillText: Color
    )

    fun itemColors(
        itemColor: Color,
        dark: Boolean,
        tinted: Boolean,
        bgOpacityPercent: Int,
        iconBgOpacityPercent: Int,
        checked: Boolean,
        ddayDiff: Int?
    ): ItemColors {
        val c = DayliUi.cardColors(
            itemColor, surface(dark), dark, tinted,
            bgOpacityPercent / 100f, iconBgOpacityPercent / 100f, checked
        )
        val (pillBg, pillText) = DayliUi.pillColors(
            ddayDiff, checked, tinted, dark, surfaceVariant(dark), onSurface(dark), onSurfaceVariant(dark), c.card
        )
        // 완료 항목은 앱처럼 내용 전체를 60%로 흐리게
        fun dim(x: Color) = if (checked) x.copy(alpha = 0.6f).compositeOver(c.card) else x
        return ItemColors(
            card = c.card,
            tile = dim(c.tile),
            title = dim(c.text),
            sub = dim(c.subText),
            pillBg = dim(pillBg.compositeOver(c.card)),
            pillText = dim(pillText)
        )
    }

    /**
     * 머리글(D-Day·To-Do·그룹 이름)은 카드 없이 위젯 바탕 위에 바로 놓여 배경화면이 비침.
     * 바탕이 충분히 진해서 배경화면이 검정이든 흰색이든 테마 글자가 4.5:1 이상이면 false(테마 글자),
     * 아니면 true → 홈 화면 앱 이름처럼 흰 글자 + 어두운 그림자 (item_widget_*_header_wallpaper.xml)
     */
    fun headerOnWallpaper(dark: Boolean, containerOpacityPercent: Int): Boolean {
        val base = containerBase(dark)
        val shown = base.copy(alpha = containerAlpha(containerOpacityPercent) / 255f)
        val text = DayliUi.readableText(base)
        return listOf(Color.Black, Color.White).any { DayliUi.contrast(text, shown.compositeOver(it)) < 4.5f }
    }

    fun headerText(dark: Boolean, onWallpaper: Boolean): Color =
        if (onWallpaper) Color.White else DayliUi.readableText(containerBase(dark))

    /** 배경화면용 머리글 그림자 (레이아웃 *_wallpaper.xml의 shadowColor와 같아야 함) */
    val HEADER_WALLPAPER_SHADOW = Color(0xB3000000)

    /** 가장 가까운 일정 카드 색 (배경, 이모지 칸, 글자, 보조 글자) */
    data class HeroColors(val bg: Color, val tile: Color, val text: Color, val sub: Color)

    fun heroColors(setting: String, itemColor: Color): HeroColors {
        val bg = DayliUi.heroColorFor(setting, itemColor)
        val fg = DayliUi.readableOn(bg)
        return HeroColors(
            bg = bg,
            tile = fg.copy(alpha = 0.16f).compositeOver(bg),
            text = fg,
            sub = fg.copy(alpha = 0.85f).compositeOver(bg)
        )
    }

    /**
     * 흰색 둥근 모양 ImageView에 색 입히기.
     * setBackgroundColor는 둥근 모서리 drawable을 덮어써 각지게 만들기 때문에 쓰지 않음
     */
    fun paint(views: RemoteViews, imageId: Int, color: Color, alpha: Int = 255) {
        views.setInt(imageId, "setColorFilter", color.copy(alpha = 1f).toArgb())
        views.setInt(imageId, "setImageAlpha", alpha)
        views.setViewVisibility(imageId, if (alpha == 0) View.INVISIBLE else View.VISIBLE)
    }
}
