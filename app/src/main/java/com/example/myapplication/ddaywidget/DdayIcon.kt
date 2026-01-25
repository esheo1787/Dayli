package com.example.myapplication.ddaywidget

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * D-Day/To-Do 용도에 맞는 Material Icons
 * 위젯에서 사용할 유니코드 fallback 포함
 */
enum class DdayIcon(
    val icon: ImageVector,
    val displayName: String,
    val unicodeFallback: String  // 위젯용 유니코드
) {
    // 시험/공부
    SCHOOL(Icons.Filled.School, "학교", "🎓"),
    MENU_BOOK(Icons.AutoMirrored.Filled.MenuBook, "책", "📖"),
    EDIT_NOTE(Icons.Filled.EditNote, "필기", "📝"),
    QUIZ(Icons.Filled.Quiz, "퀴즈", "❓"),

    // 약속/일정
    EVENT(Icons.Filled.Event, "일정", "📅"),
    SCHEDULE(Icons.Filled.Schedule, "시간", "⏰"),
    PEOPLE(Icons.Filled.People, "모임", "👥"),
    RESTAURANT(Icons.Filled.Restaurant, "식사", "🍽️"),
    COFFEE(Icons.Filled.Coffee, "카페", "☕"),

    // 기념일/생일
    CAKE(Icons.Filled.Cake, "생일", "🎂"),
    CELEBRATION(Icons.Filled.Celebration, "축하", "🎉"),
    FAVORITE(Icons.Filled.Favorite, "하트", "❤️"),
    CARD_GIFTCARD(Icons.Filled.CardGiftcard, "선물", "🎁"),

    // 업무
    WORK(Icons.Filled.Work, "업무", "💼"),
    BUSINESS_CENTER(Icons.Filled.BusinessCenter, "비즈니스", "🏢"),
    ASSIGNMENT(Icons.AutoMirrored.Filled.Assignment, "과제", "📋"),
    COMPUTER(Icons.Filled.Computer, "컴퓨터", "💻"),

    // 개인
    HOME(Icons.Filled.Home, "집", "🏠"),
    PERSON(Icons.Filled.Person, "개인", "👤"),
    SELF_IMPROVEMENT(Icons.Filled.SelfImprovement, "명상", "🧘"),

    // 여행
    FLIGHT(Icons.Filled.Flight, "비행기", "✈️"),
    LUGGAGE(Icons.Filled.Luggage, "여행", "🧳"),
    BEACH_ACCESS(Icons.Filled.BeachAccess, "휴가", "🏖️"),

    // 운동/건강
    FITNESS_CENTER(Icons.Filled.FitnessCenter, "운동", "🏋️"),
    DIRECTIONS_RUN(Icons.AutoMirrored.Filled.DirectionsRun, "달리기", "🏃"),
    MEDICAL_SERVICES(Icons.Filled.MedicalServices, "병원", "🏥"),
    HEALING(Icons.Filled.Healing, "건강", "💊"),

    // 쇼핑/금융
    SHOPPING_CART(Icons.Filled.ShoppingCart, "쇼핑", "🛒"),
    PAYMENTS(Icons.Filled.Payments, "결제", "💳"),
    ACCOUNT_BALANCE(Icons.Filled.AccountBalance, "금융", "🏦"),

    // 취미
    SPORTS_ESPORTS(Icons.Filled.SportsEsports, "게임", "🎮"),
    MUSIC_NOTE(Icons.Filled.MusicNote, "음악", "🎵"),
    MOVIE(Icons.Filled.Movie, "영화", "🎬"),
    BRUSH(Icons.Filled.Brush, "예술", "🎨"),

    // 기타
    STAR(Icons.Filled.Star, "중요", "⭐"),
    FLAG(Icons.Filled.Flag, "목표", "🚩"),
    PUSH_PIN(Icons.Filled.PushPin, "핀", "📌"),
    NOTIFICATIONS(Icons.Filled.Notifications, "알림", "🔔");

    companion object {
        fun fromName(name: String): DdayIcon {
            return entries.find { it.name == name } ?: PUSH_PIN
        }

        // 카테고리별 추천 아이콘 그룹
        val examIcons = listOf(SCHOOL, MENU_BOOK, EDIT_NOTE, QUIZ)
        val appointmentIcons = listOf(EVENT, SCHEDULE, PEOPLE, RESTAURANT, COFFEE)
        val anniversaryIcons = listOf(CAKE, CELEBRATION, FAVORITE, CARD_GIFTCARD)
        val workIcons = listOf(WORK, BUSINESS_CENTER, ASSIGNMENT, COMPUTER)
        val personalIcons = listOf(HOME, PERSON, SELF_IMPROVEMENT)
        val travelIcons = listOf(FLIGHT, LUGGAGE, BEACH_ACCESS)
        val healthIcons = listOf(FITNESS_CENTER, DIRECTIONS_RUN, MEDICAL_SERVICES, HEALING)
        val shoppingIcons = listOf(SHOPPING_CART, PAYMENTS, ACCOUNT_BALANCE)
        val hobbyIcons = listOf(SPORTS_ESPORTS, MUSIC_NOTE, MOVIE, BRUSH)
        val otherIcons = listOf(STAR, FLAG, PUSH_PIN, NOTIFICATIONS)
    }
}
