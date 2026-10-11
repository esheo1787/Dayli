package com.silverwest.dayli.ddaywidget

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

// 색상 팔레트 정의
data class PaletteColor(
    val name: String,
    val color: Color,
    val colorLong: Long
)

// 색상 팔레트 (14색)
val ddayColors = listOf(
    // 1열
    PaletteColor("코랄", Color(0xFFFF8A80), 0xFFFF8A80),
    PaletteColor("피치", Color(0xFFFFCC80), 0xFFFFCC80),
    PaletteColor("레몬", Color(0xFFFFF59D), 0xFFFFF59D),
    PaletteColor("민트", Color(0xFFA5D6A7), 0xFFA5D6A7),
    PaletteColor("스카이블루", Color(0xFF81D4FA), 0xFF81D4FA),
    PaletteColor("라벤더", Color(0xFFCE93D8), 0xFFCE93D8),
    PaletteColor("아이보리", Color(0xFFFFF8E1), 0xFFFFF8E1),
    // 2열
    PaletteColor("로즈핑크", Color(0xFFF48FB1), 0xFFF48FB1),
    PaletteColor("살몬", Color(0xFFFFAB91), 0xFFFFAB91),
    PaletteColor("연두", Color(0xFFC5E1A5), 0xFFC5E1A5),
    PaletteColor("틸", Color(0xFF80CBC4), 0xFF80CBC4),
    PaletteColor("퍼플블루", Color(0xFF9FA8DA), 0xFF9FA8DA),
    PaletteColor("모브", Color(0xFFB39DDB), 0xFFB39DDB),
    PaletteColor("차콜", Color(0xFF90A4AE), 0xFF90A4AE)
)

// 직접 고르기: 진하기 단계 (밝음 → 진함)
private val toneLightness = listOf(0.90f, 0.82f, 0.74f, 0.65f, 0.55f, 0.45f, 0.35f)
private const val TONE_SATURATION = 0.7f

// 직접 고르기: 무채색
private val neutralColors = listOf(
    0xFFFAFAFAL, 0xFFE0E0E0L, 0xFFBDBDBDL, 0xFF9E9E9EL, 0xFF757575L, 0xFF546E7AL, 0xFF37474FL
)

private fun Color.toColorLong(): Long = toArgb().toLong() and 0xFFFFFFFFL

@Composable
fun ColorPalette(
    selectedColor: Long?,
    onColorSelected: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showCustomDialog by remember { mutableStateOf(false) }
    var recentVersion by remember { mutableStateOf(0) }
    // 직접 고른 색: 최근 사용 + 지금 선택된 색이 기본 팔레트에 없으면 맨 앞에
    val customColors = remember(selectedColor, recentVersion) {
        val recents = DdaySettings.getRecentColors(context)
        val presetLongs = ddayColors.map { it.colorLong }
        (listOfNotNull(selectedColor?.takeIf { it !in presetLongs }) + recents)
            .filter { it !in presetLongs }
            .distinct()
            .take(7)
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "색상",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { showCustomDialog = true }) {
                Icon(
                    imageVector = Icons.Outlined.Palette,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("더 많은 색")
            }
        }

        // 기본 14색 (7개씩 2줄) + 직접 고른 색 줄
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ddayColors.chunked(7).forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { paletteColor ->
                        ColorCircle(
                            color = paletteColor.color,
                            isSelected = selectedColor == paletteColor.colorLong,
                            onClick = { onColorSelected(paletteColor.colorLong) }
                        )
                    }
                }
            }
            if (customColors.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    customColors.forEach { colorLong ->
                        ColorCircle(
                            color = colorLong.toComposeColor(),
                            isSelected = selectedColor == colorLong,
                            onClick = { onColorSelected(colorLong) }
                        )
                    }
                }
            }
        }
    }

    if (showCustomDialog) {
        CustomColorDialog(
            initialColor = selectedColor ?: ddayColors[4].colorLong,
            onDismiss = { showCustomDialog = false },
            onConfirm = { color ->
                DdaySettings.addRecentColor(context, color)
                recentVersion++
                onColorSelected(color)
                showCustomDialog = false
            }
        )
    }
}

/** 더 많은 색: 무지개 막대로 색을 정하고 진하기 단계를 고름 */
@Composable
private fun CustomColorDialog(
    initialColor: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    val initialHue = remember(initialColor) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(initialColor.toInt(), hsv)
        hsv[0]
    }
    var hue by remember { mutableStateOf(initialHue) }
    var toneIndex by remember { mutableStateOf<Int?>(null) }  // null = 무채색·처음 색
    var picked by remember { mutableStateOf(initialColor) }

    fun toneColor(index: Int): Long = Color.hsl(hue, TONE_SATURATION, toneLightness[index]).toColorLong()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("색 고르기") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // 미리보기
                val previewColor = picked.toComposeColor()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(previewColor)
                        .border(1.dp, Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "미리보기",
                        fontWeight = FontWeight.Medium,
                        color = if (previewColor.luminance() > 0.45f) Color(0xFF1D2329) else Color.White
                    )
                }

                // 색 (무지개 막대)
                Text("색", style = MaterialTheme.typography.labelLarge)
                HueBar(
                    hue = hue,
                    onHueChange = { newHue ->
                        hue = newHue
                        val index = toneIndex ?: 2
                        toneIndex = index
                        picked = Color.hsl(newHue, TONE_SATURATION, toneLightness[index]).toColorLong()
                    }
                )

                // 진하기
                Text("진하기", style = MaterialTheme.typography.labelLarge)
                SwatchRow(
                    colors = toneLightness.indices.map { toneColor(it) },
                    selected = picked,
                    onClick = { color ->
                        toneIndex = toneLightness.indices.first { toneColor(it) == color }
                        picked = color
                    }
                )

                // 무채색
                Text("무채색", style = MaterialTheme.typography.labelLarge)
                SwatchRow(
                    colors = neutralColors,
                    selected = picked,
                    onClick = { color ->
                        toneIndex = null
                        picked = color
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(picked) }) { Text("선택") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    )
}

@Composable
private fun HueBar(hue: Float, onHueChange: (Float) -> Unit) {
    val density = LocalDensity.current
    val thumbSize = 28.dp
    val thumbPx = with(density) { thumbSize.toPx() }
    val rainbow = remember { (0..6).map { Color.hsl(it * 60f % 360f, 0.85f, 0.6f) } }
    var widthPx by remember { mutableStateOf(0f) }

    fun hueAt(x: Float): Float {
        val track = (widthPx - thumbPx).coerceAtLeast(1f)
        return ((x - thumbPx / 2) / track).coerceIn(0f, 1f) * 359.9f
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(thumbSize)
            .onSizeChanged { widthPx = it.width.toFloat() }
            .pointerInput(Unit) {
                detectTapGestures { onHueChange(hueAt(it.x)) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    onHueChange(hueAt(change.position.x))
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .padding(horizontal = thumbSize / 2)
                .clip(RoundedCornerShape(9.dp))
                .background(Brush.horizontalGradient(rainbow))
        )
        Box(
            modifier = Modifier
                .offset { IntOffset((hue / 360f * (widthPx - thumbPx)).toInt(), 0) }
                .size(thumbSize)
                .clip(CircleShape)
                .background(Color.hsl(hue, 0.85f, 0.6f))
                .border(3.dp, Color.White, CircleShape)
                .border(4.dp, Color.Gray.copy(alpha = 0.4f), CircleShape)
        )
    }
}

@Composable
private fun SwatchRow(colors: List<Long>, selected: Long, onClick: (Long) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        colors.forEach { colorLong ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
            ) {
                ColorCircle(
                    color = colorLong.toComposeColor(),
                    isSelected = colorLong == selected,
                    onClick = { onClick(colorLong) },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun ColorCircle(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.size(36.dp)
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(color)
            .then(
                if (isSelected) {
                    Modifier.border(3.dp, Color.White, CircleShape)
                        .border(4.dp, Color.DarkGray, CircleShape)
                } else {
                    Modifier.border(1.dp, Color.Gray.copy(alpha = 0.3f), CircleShape)
                }
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "선택됨",
                // 밝은 색 위에서도 체크가 보이게
                tint = if (color.luminance() > 0.6f) Color(0xFF37474F) else Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// Long 색상값을 Color로 변환
fun Long.toComposeColor(): Color = Color(this)
