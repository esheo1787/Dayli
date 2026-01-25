package com.example.myapplication.ddaywidget

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun IconPickerDialog(
    currentIcon: DdayIcon,
    categoryColor: Color,
    onIconSelected: (DdayIcon) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedIcon by remember { mutableStateOf(currentIcon) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // 제목
                Text(
                    text = "아이콘 선택",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // 미리보기
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(categoryColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = selectedIcon.icon,
                            contentDescription = selectedIcon.displayName,
                            modifier = Modifier.size(36.dp),
                            tint = categoryColor
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = selectedIcon.displayName,
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // 아이콘 그리드
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(DdayIcon.entries.toList()) { icon ->
                        IconGridItem(
                            icon = icon,
                            isSelected = icon == selectedIcon,
                            categoryColor = categoryColor,
                            onClick = { selectedIcon = icon }
                        )
                    }
                }

                // 버튼
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("취소")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onIconSelected(selectedIcon)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = categoryColor)
                    ) {
                        Text("선택")
                    }
                }
            }
        }
    }
}

@Composable
private fun IconGridItem(
    icon: DdayIcon,
    isSelected: Boolean,
    categoryColor: Color,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) {
        categoryColor.copy(alpha = 0.2f)
    } else {
        Color.Transparent
    }

    val borderColor = if (isSelected) {
        categoryColor
    } else {
        Color.Gray.copy(alpha = 0.3f)
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon.icon,
            contentDescription = icon.displayName,
            modifier = Modifier.size(24.dp),
            tint = if (isSelected) categoryColor else Color.Gray
        )
    }
}
