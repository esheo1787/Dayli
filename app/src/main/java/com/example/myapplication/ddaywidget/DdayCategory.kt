package com.example.myapplication.ddaywidget

import androidx.compose.ui.graphics.Color

enum class DdayCategory(
    val displayName: String,
    val emoji: String,
    val color: Color,
    val colorLong: Long  // 위젯용 (RemoteViews)
) {
    STUDY("공부/시험", "📚", Color(0xFFE53935), 0xFFE53935),
    APPOINTMENT("약속/일정", "📅", Color(0xFFFB8C00), 0xFFFB8C00),
    ANNIVERSARY("생일/기념일", "🎂", Color(0xFF43A047), 0xFF43A047),
    WORK("업무", "💼", Color(0xFF1E88E5), 0xFF1E88E5),
    PERSONAL("개인", "🏠", Color(0xFF8E24AA), 0xFF8E24AA),
    TRAVEL("여행", "✈️", Color(0xFF00ACC1), 0xFF00ACC1),
    EXERCISE("운동", "💪", Color(0xFFFF7043), 0xFFFF7043),
    HEALTH("건강/병원", "💊", Color(0xFFEC407A), 0xFFEC407A),
    SHOPPING("쇼핑", "🛒", Color(0xFFAB47BC), 0xFFAB47BC),
    FINANCE("금융", "💰", Color(0xFF26A69A), 0xFF26A69A),
    HOBBY("취미", "🎮", Color(0xFF5C6BC0), 0xFF5C6BC0),
    FOOD("음식", "🍽️", Color(0xFFFF8A65), 0xFFFF8A65),
    LOVE("연애", "❤️", Color(0xFFE91E63), 0xFFE91E63),
    FAMILY("가족", "👨‍👩‍👧", Color(0xFF7CB342), 0xFF7CB342),
    OTHER("기타", "📌", Color(0xFF757575), 0xFF757575);

    companion object {
        fun fromName(name: String): DdayCategory {
            return entries.find { it.name == name } ?: OTHER
        }
    }
}
