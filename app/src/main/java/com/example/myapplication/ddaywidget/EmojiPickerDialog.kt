package com.example.myapplication.ddaywidget

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

// D-Day/To-Do 용도에 맞는 이모지 목록
val ddayEmojis = listOf(
    // 공부/시험
    "📚", "📖", "📝", "✏️", "🎓", "📕", "📗", "📘",
    // 일정/약속
    "📅", "📆", "⏰", "🕐", "📋", "✅", "☑️", "📌",
    // 기념일/축하
    "🎂", "🎉", "🎊", "🎁", "🎈", "🥳", "🎀", "🏆",
    // 업무
    "💼", "🏢", "💻", "⌨️", "📊", "📈", "📁", "🗂️",
    // 개인/집
    "🏠", "🏡", "🛋️", "🛏️", "🧹", "🧺", "📦", "🔑",
    // 여행
    "✈️", "🚗", "🚆", "🚢", "🏖️", "🏔️", "🗺️", "🧳",
    // 운동
    "💪", "🏃", "🚴", "🏊", "⚽", "🏀", "🎾", "🏋️",
    // 건강
    "💊", "🏥", "💉", "🩺", "🦷", "👁️", "❤️‍🩹", "🧘",
    // 쇼핑/금융
    "🛒", "🛍️", "💰", "💳", "🏦", "💵", "🧾", "💎",
    // 취미
    "🎮", "🎬", "🎵", "🎨", "📷", "🎸", "🎤", "🎧",
    // 음식
    "🍽️", "🍕", "🍔", "🍣", "🍰", "☕", "🍺", "🥗",
    // 사람/관계
    "❤️", "💕", "👨‍👩‍👧", "👪", "👫", "🤝", "💑", "👶",
    // 기타
    "⭐", "🔥", "💡", "🎯", "🚀", "🌟", "✨", "🔔"
)

@Composable
fun EmojiPickerDialog(
    currentEmoji: String,
    categoryColor: Color,
    onEmojiSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedEmoji by remember { mutableStateOf(currentEmoji) }

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
                    text = "이모지 선택",
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
                        Text(
                            text = selectedEmoji,
                            fontSize = 32.sp
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // 이모지 그리드
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(ddayEmojis) { emoji ->
                        EmojiGridItem(
                            emoji = emoji,
                            isSelected = emoji == selectedEmoji,
                            categoryColor = categoryColor,
                            onClick = { selectedEmoji = emoji }
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
                            onEmojiSelected(selectedEmoji)
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
private fun EmojiGridItem(
    emoji: String,
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
        Color.Gray.copy(alpha = 0.2f)
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
        Text(
            text = emoji,
            fontSize = 22.sp
        )
    }
}
