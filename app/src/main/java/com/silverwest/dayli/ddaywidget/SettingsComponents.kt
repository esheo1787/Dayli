package com.silverwest.dayli.ddaywidget

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Date
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// ─── 묶음 카드 ───

@Composable
fun SettingsSectionLabel(title: String) {
    Text(
        text = title,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 6.dp, top = 18.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        content = content
    )
}

// ─── 색상 변환 (순수 함수, 단위 테스트 대상) ───

object ColorMath {
    /** HSV(h 0..360, s 0..1, v 0..1) → "#RRGGBB" */
    fun hsvToHex(h: Float, s: Float, v: Float): String {
        val sat = s.coerceIn(0f, 1f)
        val value = v.coerceIn(0f, 1f)
        val hh = ((h % 360f) + 360f) % 360f / 60f
        val c = value * sat
        val x = c * (1 - kotlin.math.abs(hh % 2 - 1))
        val (r1, g1, b1) = when (hh.toInt()) {
            0 -> Triple(c, x, 0f); 1 -> Triple(x, c, 0f); 2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c); 4 -> Triple(x, 0f, c); else -> Triple(c, 0f, x)
        }
        val m = value - c
        fun ch(f: Float) = Math.round((f + m) * 255).coerceIn(0, 255)
        return String.format("#%02X%02X%02X", ch(r1), ch(g1), ch(b1))
    }

    /** "#RRGGBB" → [h, s, v], 잘못된 값이면 null */
    fun hexToHsv(hex: String): FloatArray? {
        val c = DayliUi.parseHex(hex) ?: return null
        val r = c.red; val g = c.green; val b = c.blue
        val max = maxOf(r, g, b); val min = minOf(r, g, b); val d = max - min
        val h = when {
            d == 0f -> 0f
            max == r -> 60f * (((g - b) / d) % 6f)
            max == g -> 60f * ((b - r) / d + 2f)
            else -> 60f * ((r - g) / d + 4f)
        }.let { if (it < 0) it + 360f else it }
        val s = if (max == 0f) 0f else d / max
        return floatArrayOf(h, s, max)
    }

    /** 색상환 위 좌표 → (hue, saturation). 중심이 채도 0, 가장자리가 1, 위쪽이 빨강(0°)에서 시계방향 */
    fun wheelPick(x: Float, y: Float, radius: Float): Pair<Float, Float> {
        val dx = x - radius
        val dy = y - radius
        val sat = min(1f, sqrt(dx * dx + dy * dy) / radius)
        var hue = Math.toDegrees(atan2(dx.toDouble(), (-dy).toDouble())).toFloat()
        if (hue < 0) hue += 360f
        return hue to sat
    }
}

// ─── 가장 가까운 일정 카드 설정 ───

/** 프리셋 (흰 글자 대비 4.5:1 이상) */
private val HERO_SWATCHES = listOf(
    DdaySettings.HERO_COLOR_AUTO to "항목 색 따라가기",
    "#4A7390" to "딥블루",
    "#45423F" to "차콜",
    "#4E7A5E" to "세이지",
    "#A9533F" to "코랄",
    "#6B5891" to "플럼"
)

private fun isPreset(color: String) = HERO_SWATCHES.any { it.first.equals(color, ignoreCase = true) }

@Composable
fun HeroCardSettings(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    color: String,
    onColorCommit: (String) -> Unit,
    fontScale: Float
) {
    var current by remember(color) { mutableStateOf(color) }
    var showWheel by remember { mutableStateOf(!isPreset(color)) }

    SettingsSwitchRow(
        title = "가장 가까운 일정 카드",
        subtitle = "D-Day 목록 맨 위에 제일 급한 일정을 크게 보여줘요",
        checked = enabled,
        onCheckedChange = onEnabledChange
    )
    if (!enabled) return

    Text(
        "카드 색 · " + (HERO_SWATCHES.firstOrNull { it.first.equals(current, true) }?.second ?: "직접 고른 색 $current"),
        fontSize = 13.5.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        HERO_SWATCHES.forEach { (value, label) ->
            val selected = !showWheel && current.equals(value, ignoreCase = true)
            ColorSwatch(
                brush = if (value == DdaySettings.HERO_COLOR_AUTO)
                    Brush.sweepGradient(listOf(Color(0xFF3E6E8E), Color(0xFF4E7A5E), Color(0xFFA9533F), Color(0xFF3E6E8E)))
                else SolidBrush(DayliUi.heroColor(value, null)),
                label = label,
                text = if (value == DdaySettings.HERO_COLOR_AUTO) "자동" else null,
                selected = selected,
                onClick = {
                    showWheel = false
                    current = value
                    onColorCommit(value)
                }
            )
        }
        ColorSwatch(
            brush = Brush.sweepGradient(listOf(Color(0xFFFF5B5B), Color(0xFFFFD84D), Color(0xFF5BD36B), Color(0xFF4DC3FF), Color(0xFF6B6BFF), Color(0xFFE05BFF), Color(0xFFFF5B5B))),
            label = "직접 고르기",
            text = "+",
            selected = showWheel,
            onClick = {
                showWheel = true
                if (isPreset(current)) current = if (current == DdaySettings.HERO_COLOR_AUTO) DdaySettings.DEFAULT_HERO_COLOR else current
            }
        )
    }

    if (showWheel) {
        Spacer(Modifier.height(12.dp))
        HsvColorPicker(
            hex = if (isPreset(current) && current == DdaySettings.HERO_COLOR_AUTO) DdaySettings.DEFAULT_HERO_COLOR else current,
            onChange = { current = it },
            onCommit = { onColorCommit(it) }
        )
    }

    Spacer(Modifier.height(12.dp))
    val sample = remember {
        DdayItem(
            title = "회계정보시스템 5주차 퀴즈",
            date = Date(System.currentTimeMillis() + 3L * 86_400_000L),
            iconName = "📝",
            customColor = 0xFF81D4FAL,
            timeHour = 12,
            timeMinute = 0
        )
    }
    HeroCard(item = sample, colorSetting = current, fontScale = fontScale, onClick = {})
    Spacer(Modifier.height(10.dp))
}

private fun SolidBrush(color: Color) = Brush.linearGradient(listOf(color, color))

@Composable
private fun ColorSwatch(brush: Brush, label: String, text: String?, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(brush)
            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        text?.let {
            Text(it, color = Color.White, fontWeight = FontWeight.Bold, fontSize = if (it == "+") 20.sp else 10.sp)
        }
    }
}

/** 색상환(색상·채도) + 밝기 슬라이더. 드래그 중에는 onChange, 손을 떼면 onCommit */
@Composable
fun HsvColorPicker(hex: String, onChange: (String) -> Unit, onCommit: (String) -> Unit) {
    val initial = remember(Unit) { ColorMath.hexToHsv(hex) ?: floatArrayOf(200f, 0.5f, 0.55f) }
    var hue by remember { mutableFloatStateOf(initial[0]) }
    var sat by remember { mutableFloatStateOf(initial[1]) }
    var value by remember { mutableFloatStateOf(initial[2]) }
    val currentHex = ColorMath.hsvToHex(hue, sat, value)

    fun pick(offset: Offset, radius: Float) {
        val (h, s) = ColorMath.wheelPick(offset.x, offset.y, radius)
        hue = h; sat = s
        onChange(ColorMath.hsvToHex(hue, sat, value))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.background)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(
            modifier = Modifier
                .size(140.dp)
                .semantics { contentDescription = "색상환: 눌러서 색 고르기" }
                .pointerInput(Unit) {
                    detectTapGestures { pick(it, size.width / 2f); onCommit(ColorMath.hsvToHex(hue, sat, value)) }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = { onCommit(ColorMath.hsvToHex(hue, sat, value)) }
                    ) { change, _ -> pick(change.position, size.width / 2f) }
                }
        ) {
            val r = size.minDimension / 2f
            // 위쪽(빨강)에서 시계방향으로 도는 색상 링 → 가운데로 갈수록 흰색(채도 0)
            val hues = (0..6).map { Color.hsv((it * 60f) % 360f, 1f, 1f) }
            rotate270 { drawCircle(Brush.sweepGradient(hues, center), r) }
            drawCircle(Brush.radialGradient(listOf(Color.White, Color.White.copy(alpha = 0f)), center, r), r)
            // 밝기 반영 (어둡게)
            drawCircle(Color.Black.copy(alpha = 1f - value), r)
            val kx = center.x + sin(Math.toRadians(hue.toDouble())).toFloat() * sat * r
            val ky = center.y - cos(Math.toRadians(hue.toDouble())).toFloat() * sat * r
            drawCircle(DayliUi.parseHex(currentHex) ?: Color.Black, 10.dp.toPx(), Offset(kx, ky))
            drawCircle(Color.White, 10.dp.toPx(), Offset(kx, ky), style = Stroke(3.dp.toPx()))
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("밝기", fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Slider(
                value = value,
                onValueChange = { value = it; onChange(ColorMath.hsvToHex(hue, sat, value)) },
                onValueChangeFinished = { onCommit(ColorMath.hsvToHex(hue, sat, value)) },
                valueRange = 0.15f..1f
            )
            Text(currentHex, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val fg = DayliUi.readableOn(DayliUi.parseHex(currentHex) ?: Color.Black)
            Text(
                "글자 색 자동: " + if (fg == Color.White) "흰색" else "진한 색",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** sweepGradient 시작점(오른쪽)을 위쪽으로 돌림 */
private inline fun androidx.compose.ui.graphics.drawscope.DrawScope.rotate270(block: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) {
    drawContext.transform.rotate(-90f, center)
    block()
    drawContext.transform.rotate(90f, center)
}

// ─── 항목 배경 방식 ───

/**
 * 흰 카드 / 항목 색 선택 + 색 진하기 슬라이더 1개
 * (기존 설정값 재사용: 항목 색 = backgroundOpacity, 흰 카드 = iconBgOpacity)
 */
@Composable
fun ItemBackgroundSettings(
    tinted: Boolean,
    onTintedChange: (Boolean) -> Unit,
    strength: Int,
    onStrengthChange: (Int) -> Unit,
    onStrengthCommit: () -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 10.dp)) {
        Text("항목 배경", style = MaterialTheme.typography.bodyLarge)
        Text(
            if (tinted) "카드 전체에 항목 색을 깔아요" else "흰 카드에 이모지 칸만 색이 들어가요",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BackgroundModeOption("흰 카드", selected = !tinted, tintedSample = false, onClick = { onTintedChange(false) }, modifier = Modifier.weight(1f))
            BackgroundModeOption("항목 색", selected = tinted, tintedSample = true, onClick = { onTintedChange(true) }, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (tinted) "카드 색 진하기" else "이모지 칸 색 진하기", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text("$strength%", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = strength.toFloat(),
            onValueChange = { onStrengthChange((it / 5f).roundToInt() * 5) },  // 5% 단위
            onValueChangeFinished = onStrengthCommit,
            valueRange = 0f..100f
        )
    }
}

@Composable
private fun BackgroundModeOption(label: String, selected: Boolean, tintedSample: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .border(if (selected) 2.dp else 1.dp, if (selected) accent else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = "항목 배경 $label" }
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val sampleColor = Color(0xFF81D4FA)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(if (tintedSample) sampleColor.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(if (tintedSample) Color.White.copy(alpha = 0.7f) else sampleColor.copy(alpha = 0.5f))
            )
            Spacer(Modifier.width(6.dp))
            Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.outlineVariant))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

/** 미리보기: 실제 목록 카드(DdayListItem)를 그대로 그림. 설정값이 바뀌면 key로 다시 그림 */
@Composable
fun SettingsListPreview(refreshKey: Any) {
    val samples = remember {
        val day = 86_400_000L
        val now = System.currentTimeMillis()
        listOf(
            DdayItem(id = -1, title = "회계정보시스템 퀴즈", date = Date(now + 3 * day), iconName = "📝", customColor = 0xFF81D4FAL, timeHour = 12, timeMinute = 0),
            DdayItem(id = -2, title = "치과 예약", date = Date(now + 5 * day), iconName = "🦷", customColor = 0xFFA5D6A7L, timeHour = 14, timeMinute = 0)
        )
    }
    Text("미리보기", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
    key(refreshKey) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.background)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            samples.forEach { DdayListItem(item = it, onToggle = {}) }
        }
    }
    Spacer(Modifier.height(10.dp))
}

// ─── 공통 행 ───

@Composable
fun SettingsSwitchRow(title: String, subtitle: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

// ─── 오픈소스 라이선스 ───

@Composable
fun OpenSourceLicensesDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val text = remember { readLicenses(context) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("오픈소스 라이선스") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text("Microsoft Fluent Emoji (플랫 이모지)", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(text, fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } }
    )
}

private fun readLicenses(context: Context): String = try {
    context.assets.open("licenses/fluent-emoji-LICENSE.txt").bufferedReader().use { it.readText().trimIndent() }
} catch (_: Exception) {
    "MIT License — Copyright (c) Microsoft Corporation."
}

/** 설정용 선택 버튼 (여러 개 중 하나): 선택 시 강조색 테두리 + 연한 배경 */
@Composable
fun SegmentChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) accent.copy(alpha = 0.10f) else Color.Transparent)
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) accent else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(10.dp)
            )
            .clickable(role = androidx.compose.ui.semantics.Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        leadingIcon?.let { it(); Spacer(Modifier.width(6.dp)) }
        CompositionLocalProvider(
            LocalContentColor provides if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        ) {
            ProvideTextStyle(MaterialTheme.typography.bodyMedium.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)) {
                label()
            }
        }
    }
}
