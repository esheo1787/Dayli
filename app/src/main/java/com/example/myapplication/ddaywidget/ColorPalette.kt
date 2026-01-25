package com.example.myapplication.ddaywidget

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

val ddayColors = listOf(
    PaletteColor("빨강", Color(0xFFE53935), 0xFFE53935),
    PaletteColor("주황", Color(0xFFFB8C00), 0xFFFB8C00),
    PaletteColor("노랑", Color(0xFFFFB300), 0xFFFFB300),
    PaletteColor("연두", Color(0xFF7CB342), 0xFF7CB342),
    PaletteColor("초록", Color(0xFF43A047), 0xFF43A047),
    PaletteColor("청록", Color(0xFF00ACC1), 0xFF00ACC1),
    PaletteColor("파랑", Color(0xFF1E88E5), 0xFF1E88E5),
    PaletteColor("남색", Color(0xFF5C6BC0), 0xFF5C6BC0),
    PaletteColor("보라", Color(0xFF8E24AA), 0xFF8E24AA),
    PaletteColor("핑크", Color(0xFFEC407A), 0xFFEC407A),
    PaletteColor("분홍", Color(0xFFF48FB1), 0xFFF48FB1),
    PaletteColor("갈색", Color(0xFF8D6E63), 0xFF8D6E63),
    PaletteColor("회색", Color(0xFF757575), 0xFF757575),
    PaletteColor("검정", Color(0xFF424242), 0xFF424242)
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
