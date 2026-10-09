package com.silverwest.dayli.ddaywidget

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silverwest.dayli.ui.theme.*
import java.util.*

// ─── 순수 헬퍼 (단위 테스트 대상) ───

object DayliUi {

    private val WEEKDAYS = arrayOf("일", "월", "화", "수", "목", "금", "토")

    /** "10월 14일 (수)" */
    fun koreanDate(date: Date): String {
        val cal = Calendar.getInstance().apply { time = date }
        return "${cal.get(Calendar.MONTH) + 1}월 ${cal.get(Calendar.DAY_OF_MONTH)}일 (${WEEKDAYS[cal.get(Calendar.DAY_OF_WEEK) - 1]})"
    }

    /** 오늘 이후(오늘 포함) 미완료 D-Day 중 가장 가까운 것. 같은 날이면 시간 이른 순, 시간 없는 항목은 뒤로 */
    fun nearestUpcoming(items: List<DdayItem>, diffOf: (Date) -> Int = ::calculateDdayDiff): DdayItem? =
        items.asSequence()
            .filter { !it.isChecked && !it.isHidden && it.date != null && diffOf(it.date) >= 0 }
            .minWithOrNull(
                compareBy<DdayItem> { diffOf(it.date!!) }
                    .thenBy { if (it.hasTime()) it.timeHour!! * 60 + it.timeMinute!! else Int.MAX_VALUE }
            )

    /** "#RRGGBB" → Color, 잘못된 값이면 null */
    fun parseHex(hex: String): Color? {
        val h = hex.trim().removePrefix("#")
        if (h.length != 6) return null
        val v = h.toLongOrNull(16) ?: return null
        return Color(0xFF000000 or v)
    }

    /** 항목 색을 카드 배경용으로 진하게 (흰 글자가 읽히도록 밝기를 낮춤) */
    fun deepen(color: Color): Color {
        var c = color
        repeat(8) {
            if (contrastWithWhite(c) >= 4.5f) return c
            c = Color(c.red * 0.85f, c.green * 0.85f, c.blue * 0.85f, 1f)
        }
        return c
    }

    fun contrastWithWhite(color: Color): Float = 1.05f / (color.luminance() + 0.05f)

    val DarkText = Color(0xFF2F2D2B)
    val LightText = Color(0xFFEDE9E6)

    /** 두 색의 WCAG 대비 */
    fun contrast(a: Color, b: Color): Float {
        val la = a.luminance(); val lb = b.luminance()
        return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
    }

    /** 이 배경에서 진한 글자가 밝은 글자보다 잘 읽히면 true */
    fun prefersDarkText(bg: Color): Boolean = contrast(DarkText, bg) >= contrast(LightText, bg)

    /** 배경 위에서 읽히는 글자색: 흰 글자 대비 4.5:1 이상이면 흰색, 아니면 진한 색 */
    fun readableOn(color: Color): Color =
        if (contrastWithWhite(color) >= 4.5f) Color.White else Color(0xFF2F2D2B)

    /** 가장 가까운 일정 카드 배경색 */
    fun heroColor(setting: String, item: DdayItem?): Color =
        heroColorFor(setting, item?.getColorLong()?.toComposeColor())

    /** 항목 색으로 계산 (설정 화면 위젯 미리보기처럼 실제 항목이 없을 때) */
    fun heroColorFor(setting: String, itemColor: Color?): Color = when {
        setting == DdaySettings.HERO_COLOR_AUTO && itemColor != null -> deepen(itemColor)
        else -> parseHex(setting) ?: DeepBlue
    }

    /** 항목 카드 색 (앱 목록·위젯·설정 미리보기 공통) */
    data class CardColors(val card: Color, val tile: Color, val text: Color, val subText: Color, val lightCard: Boolean)

    /**
     * 항목 배경 방식: tinted(켜짐) = 카드 전체에 항목 색 / 꺼짐 = 흰 카드 + 이모지 칸에만 항목 색
     * 투명도는 0~1. 결과 색은 모두 불투명 (surface 위에 합성)
     */
    fun cardColors(
        itemColor: Color,
        surface: Color,
        dark: Boolean,
        tinted: Boolean,
        bgOpacity: Float,
        iconBgOpacity: Float,
        checked: Boolean
    ): CardColors {
        val card = if (tinted) {
            itemColor.copy(alpha = if (checked) bgOpacity * 0.5f else bgOpacity).compositeOver(surface)
        } else surface
        val tile = if (tinted) {
            Color.White.copy(alpha = if (dark) 0.10f else 0.65f).compositeOver(card)
        } else {
            itemColor.copy(alpha = if (checked) iconBgOpacity * 0.6f else iconBgOpacity).compositeOver(surface)
        }
        // 카드 배경과의 대비를 비교해 더 잘 읽히는 글자색 선택 (항목 색을 진하게 칠한 경우 포함)
        val lightCard = prefersDarkText(card)
        val text = if (lightCard) DarkText else LightText
        // 보조 글자도 대비 4.5:1 미만이면 본문 글자색으로 (진한 항목 색에서 날짜가 흐려지지 않게)
        val subText = (if (lightCard) Color(0xFF5D5A58) else Color(0xFFCFCAC6))
            .let { if (contrast(it, card) >= 4.5f) it else text }
        return CardColors(card, tile, text, subText, lightCard)
    }

    /** D-Day 배지 (배경, 글자). 배경은 반투명일 수 있음 → 위젯처럼 불투명이 필요하면 카드 색 위에 합성 */
    fun pillColors(
        diff: Int?,
        checked: Boolean,
        onTinted: Boolean,
        dark: Boolean,
        surfaceVariant: Color,
        onSurface: Color,
        onSurfaceVariant: Color
    ): Pair<Color, Color> = when {
        checked -> surfaceVariant to onSurfaceVariant
        diff != null && diff <= 1 -> (if (dark) PillUrgentBgDark else PillUrgentBg) to (if (dark) PillUrgentTextDark else PillUrgentText)
        diff == 2 || diff == 3 -> (if (dark) PillSoonBgDark else PillSoonBg) to (if (dark) PillSoonTextDark else PillSoonText)
        onTinted -> Color.White.copy(alpha = if (dark) 0.12f else 0.75f) to onSurface
        else -> surfaceVariant to onSurfaceVariant
    }
}

// ─── 컴포넌트 ───

/** D-Day 배지: 기존 글자색 규칙(D-3~D-2 파랑, D-1 이하 빨강)을 배지 배경으로 */
@Composable
fun DdayPill(
    label: String,
    diff: Int?,
    checked: Boolean,
    onTinted: Boolean,
    fontScale: Float
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val (bg, fg) = DayliUi.pillColors(
        diff, checked, onTinted, dark,
        MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.onSurfaceVariant
    )
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(label, fontSize = (13 * fontScale).sp, fontWeight = FontWeight.Bold, color = fg, maxLines = 1)
    }
}

/** 둥근 사각형 체크박스 (터치 영역 40dp) */
@Composable
fun DayliCheckbox(
    checked: Boolean,
    color: Color,
    onClick: () -> Unit,
    size: Int = 24,
    description: String = "완료"
) {
    val fill = DayliUi.deepen(color)
    Box(
        modifier = Modifier
            .size((size + 16).dp)
            .clip(RoundedCornerShape(8.dp))
            // toggleable: 화면 낭독기에 체크됨/해제됨 상태까지 전달
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onClick() })
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size.dp)
                .clip(RoundedCornerShape((size * 0.3f).dp))
                .then(
                    if (checked) Modifier.background(fill)
                    else Modifier.border(2.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape((size * 0.3f).dp))
                ),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(Icons.Default.Check, contentDescription = null, tint = DayliUi.readableOn(fill), modifier = Modifier.size((size * 0.7f).dp))
            }
        }
    }
}

/** D-Day / To-Do 전환 (개수 표시) */
@Composable
fun SegmentedTabs(
    labels: List<String>,
    counts: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(3.dp)
    ) {
        labels.forEachIndexed { i, label ->
            val isSel = i == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (isSel) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(i) },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        label,
                        fontSize = 15.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSel) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "${counts.getOrElse(i) { 0 }}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** 그룹·섹션 머리글 옆 개수 배지 */
@Composable
fun CountBadge(count: Int, fontScale: Float) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 7.dp, vertical = 1.dp)
    ) {
        Text("$count", fontSize = (12 * fontScale).sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 가장 가까운 일정 카드 */
@Composable
fun HeroCard(
    item: DdayItem,
    colorSetting: String,
    fontScale: Float,
    onClick: () -> Unit
) {
    val bg = DayliUi.heroColor(colorSetting, item)
    val fg = DayliUi.readableOn(bg)
    val diff = item.date?.let { calculateDdayDiff(it) }
    val meta = buildString {
        item.date?.let { append(DayliUi.koreanDate(it)) }
        item.getTimeString()?.let { append(" "); append(it) }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(fg.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            EmojiText(item.getEmoji(), fontSize = 24.sp)
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("가장 가까운 일정", fontSize = (12 * fontScale).sp, color = fg.copy(alpha = 0.85f))
            Text(
                item.title,
                fontSize = (16 * fontScale).sp,
                fontWeight = FontWeight.Bold,
                color = fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (meta.isNotEmpty()) {
                Text(meta, fontSize = (12.5f * fontScale).sp, color = fg.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(10.dp))
        diff?.let {
            Text(
                item.date?.let { d -> calculateDday(d) } ?: "",
                fontSize = (32 * fontScale).sp,
                fontWeight = FontWeight.Bold,
                color = fg,
                maxLines = 1
            )
        }
    }
}
