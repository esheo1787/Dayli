package com.silverwest.dayli.ddaywidget

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
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

// 이모지 카테고리 정의
data class EmojiCategory(val icon: String, val name: String, val emojis: List<String>)

val emojiCategories = listOf(
    EmojiCategory("😀", "표정", listOf(
        "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂",
        "🙂", "🙃", "😉", "😊", "😇", "🥰", "😍", "🤩",
        "😘", "😗", "😋", "😛", "😜", "🤪", "😝", "🤑",
        "🤗", "🤭", "🤫", "🤔", "😐", "😑", "😶", "😏",
        "😒", "🙄", "😬", "😌", "😔", "😪", "🤤", "😴",
        "😷", "🤒", "🤕", "🤢", "🤮", "🥵", "🥶", "🤯"
    )),
    EmojiCategory("🐶", "동물", listOf(
        "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼",
        "🐨", "🐯", "🦁", "🐮", "🐷", "🐸", "🐵", "🙈",
        "🙉", "🙊", "🐔", "🐧", "🐦", "🐤", "🦆", "🦅",
        "🦉", "🦇", "🐺", "🐗", "🐴", "🦄", "🐝", "🐛",
        "🦋", "🐌", "🐞", "🐜", "🐢", "🐍", "🦎", "🦖",
        "🐙", "🦑", "🦐", "🦀", "🐠", "🐟", "🐡", "🐬"
    )),
    EmojiCategory("🍎", "음식", listOf(
        "🍎", "🍐", "🍊", "🍋", "🍌", "🍉", "🍇", "🍓",
        "🍈", "🍒", "🍑", "🥭", "🍍", "🥥", "🥝", "🍅",
        "🍆", "🥑", "🥦", "🥬", "🥒", "🌶", "🌽", "🥕",
        "🥔", "🍠", "🍞", "🧀", "🍖", "🍗", "🥩", "🌭",
        "🍔", "🍟", "🍕", "🥪", "🌮", "🌯", "🥙", "🍣",
        "🍰", "🍩", "🍪", "🎂", "☕", "🍵", "🍺", "🥤"
    )),
    EmojiCategory("⚽", "활동", listOf(
        "⚽", "🏀", "🏈", "⚾", "🥎", "🎾", "🏐", "🏉",
        "🎱", "🏓", "🏸", "🏒", "🥍", "🏏", "⛳", "🎣",
        "🥊", "🥋", "🎽", "🛹", "🛼", "🛷", "⛸", "🥌",
        "🎿", "🏂", "🏋️", "🤸", "🤺", "🤾", "🏌️", "🏇",
        "🧘", "🏄", "🏊", "🤽", "🧗", "🚴", "🚵", "🏃",
        "💪", "🎮", "🎲", "🎯", "🎳", "🎪", "🎨", "🎬"
    )),
    EmojiCategory("🚗", "여행", listOf(
        "🚗", "🚕", "🚙", "🚌", "🚎", "🏎", "🚓", "🚑",
        "🚒", "🚐", "🚚", "🚛", "🚜", "🛵", "🏍", "🚲",
        "🛴", "🚏", "🚅", "🚆", "🚇", "🚊", "🚉", "✈️",
        "🛫", "🛬", "🚀", "🛸", "🚁", "🛶", "⛵", "🚤",
        "🛥", "🛳", "🚢", "⚓", "🏖", "🏝", "🏔", "⛰",
        "🌋", "🗻", "🏕", "🏠", "🏡", "🏢", "🏣", "🏥"
    )),
    EmojiCategory("💼", "사물", listOf(
        "💼", "📱", "💻", "⌨️", "🖥", "🖨", "💾", "📀",
        "🎥", "📷", "📸", "📹", "🔍", "🔎", "💡", "🔦",
        "📔", "📕", "📖", "📗", "📘", "📙", "📚", "📓",
        "📒", "📃", "📄", "📰", "📑", "🔖", "🏷", "💰",
        "💵", "💳", "🧾", "✉", "📧", "📦", "🔑", "🔒",
        "🔓", "🛒", "💎", "⏰", "⌚", "📌", "📎", "✂️"
    )),
    EmojiCategory("❤️", "기호", listOf(
        "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍",
        "🤎", "💔", "❣", "💕", "💞", "💟", "💗", "💖",
        "💝", "💘", "✅", "❌", "⭕", "❗", "❓", "⚡",
        "🔥", "💥", "✨", "⭐", "🌟", "💫", "🎵", "🎶",
        "🔔", "📣", "📢", "🏁", "☮", "☯", "♻", "⚜",
        "🔰", "💠", "🔷", "🔶", "🔵", "🟢", "🔴", "🟡"
    )),
    EmojiCategory("🚩", "깃발", listOf(
        "🏳", "🏴", "🏁", "🚩", "🎌", "🏴‍☠️", "🇰🇷", "🇺🇸",
        "🇯🇵", "🇨🇳", "🇬🇧", "🇫🇷", "🇩🇪", "🇮🇹", "🇪🇸", "🇷🇺",
        "🇧🇷", "🇦🇺", "🇨🇦", "🇲🇽", "🇮🇳", "🇮🇩", "🇹🇷", "🇸🇦",
        "🇦🇪", "🇹🇭", "🇻🇳", "🇵🇭", "🇲🇾", "🇸🇬", "🇳🇿", "🇨🇭",
        "🇸🇪", "🇳🇴", "🇩🇰", "🇫🇮", "🇳🇱", "🇧🇪", "🇵🇱", "🇦🇹"
    ))
)

// 하위 호환성
val ddayEmojis = emojiCategories.flatMap { it.emojis }

@Composable
fun EmojiPickerDialog(
    currentEmoji: String,
    categoryColor: Color,
    onEmojiSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedEmoji by remember { mutableStateOf(currentEmoji) }
    // 현재 이모지가 속한 카테고리 자동 선택
    var selectedCategoryIndex by remember {
        val index = emojiCategories.indexOfFirst { category ->
            currentEmoji in category.emojis
        }
        mutableStateOf(if (index >= 0) index else 0)
    }

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

                // 카테고리 탭
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    emojiCategories.forEachIndexed { index, category ->
                        val isSelected = index == selectedCategoryIndex
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) categoryColor.copy(alpha = 0.2f)
                                    else Color.Transparent
                                )
                                .border(
                                    width = if (isSelected) 2.dp else 0.dp,
                                    color = if (isSelected) categoryColor else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { selectedCategoryIndex = index },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = category.icon, fontSize = 20.sp)
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // 이모지 그리드
                LazyVerticalGrid(
                    columns = GridCells.Fixed(8),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(emojiCategories[selectedCategoryIndex].emojis) { emoji ->
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
