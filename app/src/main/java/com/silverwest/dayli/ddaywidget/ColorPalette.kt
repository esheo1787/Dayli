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

// Soft Pastel 색상 팔레트
val ddayColors = listOf(
    // 첫 번째 줄 - 파스텔 기본
    PaletteColor("코랄", Color(0xFFE8A598), 0xFFE8A598),
    PaletteColor("피치", Color(0xFFF5D5C8), 0xFFF5D5C8),
    PaletteColor("레몬", Color(0xFFF2E2A0), 0xFFF2E2A0),
    PaletteColor("민트", Color(0xFF9FCEC4), 0xFF9FCEC4),
    PaletteColor("스카이", Color(0xFF9BC4D9), 0xFF9BC4D9),
    PaletteColor("블루", Color(0xFFA8C5DA), 0xFFA8C5DA),
    PaletteColor("라벤더", Color(0xFFC4B5D4), 0xFFC4B5D4),
    // 두 번째 줄 - 파스텔 딥
    PaletteColor("로즈", Color(0xFFDBA8B8), 0xFFDBA8B8),
    PaletteColor("살몬", Color(0xFFE8B5A2), 0xFFE8B5A2),
    PaletteColor("올리브", Color(0xFFB5C4A8), 0xFFB5C4A8),
    PaletteColor("틸", Color(0xFF8CBAB2), 0xFF8CBAB2),
    PaletteColor("슬레이트", Color(0xFF7BA3BD), 0xFF7BA3BD),
    PaletteColor("모브", Color(0xFFB8A5C8), 0xFFB8A5C8),
    PaletteColor("그레이", Color(0xFF9A9896), 0xFF9A9896)
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
