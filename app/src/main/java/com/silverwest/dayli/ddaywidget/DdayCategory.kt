package com.silverwest.dayli.ddaywidget

import androidx.compose.ui.graphics.Color

enum class DdayCategory(
    val displayName: String,
    val emoji: String,
    val color: Color,
    val colorLong: Long  // 위젯용 (RemoteViews)
) {
    // Soft Pastel 카테고리 색상
    STUDY("공부/시험", "📚", Color(0xFFE8A598), 0xFFE8A598),        // Coral
    APPOINTMENT("약속/일정", "📅", Color(0xFFF5D5C8), 0xFFF5D5C8),  // Peach
    ANNIVERSARY("생일/기념일", "🎂", Color(0xFFDBA8B8), 0xFFDBA8B8), // Rose
    WORK("업무", "💼", Color(0xFF7BA3BD), 0xFF7BA3BD),              // Slate Blue
    PERSONAL("개인", "🏠", Color(0xFFC4B5D4), 0xFFC4B5D4),          // Lavender
    TRAVEL("여행", "✈️", Color(0xFF9BC4D9), 0xFF9BC4D9),            // Sky
    EXERCISE("운동", "💪", Color(0xFFE8B5A2), 0xFFE8B5A2),          // Salmon
    HEALTH("건강/병원", "💊", Color(0xFFB8A5C8), 0xFFB8A5C8),        // Mauve
    SHOPPING("쇼핑", "🛒", Color(0xFFF2E2A0), 0xFFF2E2A0),          // Lemon
    FINANCE("금융", "💰", Color(0xFF8CBAB2), 0xFF8CBAB2),           // Teal
    HOBBY("취미", "🎮", Color(0xFFA8C5DA), 0xFFA8C5DA),             // Pastel Blue
    FOOD("음식", "🍽️", Color(0xFFE8A598), 0xFFE8A598),              // Coral
    LOVE("연애", "❤️", Color(0xFFDBA8B8), 0xFFDBA8B8),              // Rose
    FAMILY("가족", "👨‍👩‍👧", Color(0xFF9FCEC4), 0xFF9FCEC4),            // Mint
    OTHER("기타", "📌", Color(0xFFA8C5DA), 0xFFA8C5DA);             // Pastel Blue

    companion object {
        fun fromName(name: String): DdayCategory {
            return entries.find { it.name == name } ?: OTHER
        }
    }
}
