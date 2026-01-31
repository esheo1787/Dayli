package com.silverwest.dayli.ddaywidget

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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

@Composable
fun ColorPalette(
    selectedColor: Long?,
    onColorSelected: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "색상",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // 2줄로 색상 표시
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 첫 번째 줄 (7개)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ddayColors.take(7).forEach { paletteColor ->
                    ColorCircle(
                        color = paletteColor.color,
                        colorLong = paletteColor.colorLong,
                        isSelected = selectedColor == paletteColor.colorLong,
                        onClick = { onColorSelected(paletteColor.colorLong) }
                    )
                }
            }

            // 두 번째 줄 (7개)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ddayColors.drop(7).forEach { paletteColor ->
                    ColorCircle(
                        color = paletteColor.color,
                        colorLong = paletteColor.colorLong,
                        isSelected = selectedColor == paletteColor.colorLong,
                        onClick = { onColorSelected(paletteColor.colorLong) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorCircle(
    color: Color,
    colorLong: Long,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
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
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// Long 색상값을 Color로 변환
fun Long.toComposeColor(): Color = Color(this)
