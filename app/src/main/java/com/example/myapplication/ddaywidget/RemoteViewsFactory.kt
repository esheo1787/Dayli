package com.example.myapplication.ddaywidget

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Paint
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.example.myapplication.R
import com.example.myapplication.ui.theme.isDarkMode
import kotlinx.coroutines.runBlocking
import java.util.*
import java.text.SimpleDateFormat

class RemoteViewsFactory(
    private val context: Context
) : RemoteViewsService.RemoteViewsFactory {

    private var items: List<DdayItem> = emptyList()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        android.util.Log.d("DDAY_WIDGET", "📦 RemoteViewsFactory.onDataSetChanged() 호출됨")

        try {
            runBlocking {
                val db = DdayDatabase.getDatabase(context)
                val dao = db.ddayDao()

                // 오늘 00:00:00 타임스탬프 계산
                val todayStart = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis

                android.util.Log.d("DDAY_WIDGET", "📦 todayStart: $todayStart")

                // 체크 안 됨 OR 오늘 체크한 항목만 가져오기 (D-Day + To-Do)
                items = dao.getAllForWidgetWithTodos(todayStart)

                android.util.Log.d("DDAY_WIDGET", "📦 위젯 items 개수: ${items.size}")
                items.forEach { item ->
                    android.util.Log.d("DDAY_WIDGET", "📦 item: id=${item.id}, title=${item.title}, isChecked=${item.isChecked}, checkedAt=${item.checkedAt}")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("DDAY_WIDGET", "❌ 위젯 데이터 로드 실패", e)
            items = emptyList()
        }
    }

    override fun onDestroy() {
        items = emptyList()
    }

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews {
        if (position < 0 || position >= items.size) {
            return RemoteViews(context.packageName, R.layout.item_dday_widget)
        }

        val item = items[position]
        val views = RemoteViews(context.packageName, R.layout.item_dday_widget)

        // 다크모드 확인
        val isDark = isDarkMode(context)

        // 설정값 읽기
        val backgroundEnabled = DdaySettings.isBackgroundEnabled(context)
        val bgOpacity = DdaySettings.getBackgroundOpacity(context) / 100f
        val iconBgOpacity = DdaySettings.getIconBgOpacity(context) / 100f

        android.util.Log.d("DDAY_WIDGET", "🎨 아이템 배경: enabled=$backgroundEnabled, bgOpacity=$bgOpacity, iconBgOpacity=$iconBgOpacity, isDark=$isDark")
        val fontSizeMultiplier = when (DdaySettings.getWidgetFontSize(context)) {
            0 -> 0.85f  // 작게
            2 -> 1.15f  // 크게
            else -> 1f  // 보통
        }

        // 커스텀 색상 또는 카테고리 기본 색상 가져오기
        val itemColor = item.getColorLong().toInt()

        // 이모지 가져오기 (선택된 이모지 또는 카테고리 기본 이모지)
        val itemEmoji = item.getEmoji()

        // 이모지 설정
        views.setTextViewText(R.id.item_category_icon, itemEmoji)
        views.setTextViewTextSize(R.id.item_category_icon, android.util.TypedValue.COMPLEX_UNIT_SP, 22f * fontSizeMultiplier)

        // 배경 틴트 적용 (카테고리 색상 반투명)
        if (backgroundEnabled && !item.isChecked) {
            val alpha = (bgOpacity * 0.4f * 255).toInt().coerceIn(0, 255)
            val tintColor = (alpha shl 24) or (itemColor and 0x00FFFFFF)
            views.setInt(R.id.item_card, "setBackgroundColor", tintColor)

            // 아이콘 배경도 색상 적용
            val iconAlpha = (iconBgOpacity * 0.5f * 255).toInt().coerceIn(0, 255)
            val iconTintColor = (iconAlpha shl 24) or (itemColor and 0x00FFFFFF)
            views.setInt(R.id.item_icon_card, "setBackgroundColor", iconTintColor)
        } else {
            // 배경 비활성화 또는 체크된 항목
            if (isDark) {
                // 다크모드: 약간 밝은 배경
                views.setInt(R.id.item_card, "setBackgroundColor", 0x00000000)
                views.setInt(R.id.item_icon_card, "setBackgroundColor", 0x20FFFFFF)
            } else {
                // 라이트모드: 약간 어두운 배경
                views.setInt(R.id.item_card, "setBackgroundColor", 0x00000000)
                views.setInt(R.id.item_icon_card, "setBackgroundColor", 0x15000000)
            }
        }

        // 텍스트 설정 + 글씨 크기 (반복 태그 포함 - D-Day와 To-Do 모두)
        val repeatTag = if (item.isRepeating()) {
            if (item.isDday()) {
                item.getRepeatTagText() ?: ""
            } else {
                // To-Do 반복 태그
                when (item.repeatTypeEnum()) {
                    RepeatType.DAILY -> "🔁매일"
                    RepeatType.WEEKLY -> "🔁매주"
                    RepeatType.MONTHLY -> "🔁매월"
                    else -> ""
                }
            }
        } else ""
        val titleText = if (repeatTag.isNotEmpty()) "${item.title} $repeatTag" else item.title
        views.setTextViewText(R.id.item_title, titleText)
        views.setTextViewTextSize(R.id.item_title, android.util.TypedValue.COMPLEX_UNIT_SP, 15f * fontSizeMultiplier)

        // D-Day 또는 To-Do 표시
        val ddayText: String
        val ddayColor: Int

        if (item.date != null) {
            // D-Day 아이템: D-Day 계산
            val daysUntil = calculateDaysUntil(item.date)
            ddayText = when {
                daysUntil > 0 -> "D-$daysUntil"
                daysUntil == 0 -> "D-DAY"
                else -> "D+${-daysUntil}"
            }
            // Soft Pastel D-Day 색상
            // D-2 이상: 슬레이트 블루, D-1 이하(D-DAY, D+N 포함): 코랄/로즈
            ddayColor = if (daysUntil <= 1) {
                if (isDark) 0xFFE8A598.toInt() else 0xFFDBA8B8.toInt()  // 코랄/로즈
            } else {
                if (isDark) 0xFF9BC4D9.toInt() else 0xFF7BA3BD.toInt()  // 스카이/슬레이트
            }
        } else {
            // To-Do 아이템: 빈 텍스트
            ddayText = ""
            ddayColor = itemColor
        }
        views.setTextViewText(R.id.item_dday, ddayText)
        views.setTextViewTextSize(R.id.item_dday, android.util.TypedValue.COMPLEX_UNIT_SP, 16f * fontSizeMultiplier)

        // 메모 표시/숨김
        if (!item.memo.isNullOrBlank()) {
            views.setTextViewText(R.id.item_memo, item.memo)
            views.setViewVisibility(R.id.item_memo, View.VISIBLE)
            views.setTextViewTextSize(R.id.item_memo, android.util.TypedValue.COMPLEX_UNIT_SP, 12f * fontSizeMultiplier)
        } else {
            views.setViewVisibility(R.id.item_memo, View.GONE)
        }

        // 날짜 표시 (D-Day만, To-Do는 빈 값)
        val formattedDate = item.date?.let { SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).format(it) } ?: ""
        views.setTextViewText(R.id.item_date, formattedDate)
        views.setTextViewTextSize(R.id.item_date, android.util.TypedValue.COMPLEX_UNIT_SP, 12f * fontSizeMultiplier)

        // 체크박스 상태 설정
        views.setCompoundButtonChecked(R.id.item_checkbox, item.isChecked)

        // Soft Pastel 테마 텍스트 색상 정의
        // 라이트모드: WarmGray (#4A4A4A) 기반
        // 다크모드: LightGray (#F5F5F0) 기반
        val titleColor = if (isDark) 0xFFF5F5F0.toInt() else 0xFF4A4A4A.toInt()
        val dateColor = if (isDark) 0xFFB8B8B8.toInt() else 0xFF7A7A7A.toInt()
        val checkedTitleColor = if (isDark) 0xFF606060.toInt() else 0xFF9A9A9A.toInt()
        val checkedDateColor = if (isDark) 0xFF505050.toInt() else 0xFFB0B0B0.toInt()
        val memoColor = if (isDark) 0xFFC8C8C8.toInt() else 0xFF6A6A6A.toInt()

        // 체크된 항목은 가로줄 표시 (STRIKE_THRU_TEXT_FLAG)
        if (item.isChecked) {
            // 가로줄 + 기본 스타일
            val strikePaintFlags = Paint.STRIKE_THRU_TEXT_FLAG or Paint.ANTI_ALIAS_FLAG
            views.setInt(R.id.item_title, "setPaintFlags", strikePaintFlags)
            views.setInt(R.id.item_memo, "setPaintFlags", strikePaintFlags)
            views.setInt(R.id.item_dday, "setPaintFlags", strikePaintFlags)
            views.setInt(R.id.item_category_icon, "setPaintFlags", strikePaintFlags)
            // 체크된 항목은 회색으로 표시
            views.setTextColor(R.id.item_title, checkedTitleColor)
            views.setTextColor(R.id.item_dday, checkedTitleColor)
            views.setTextColor(R.id.item_date, checkedDateColor)
            views.setTextColor(R.id.item_memo, checkedTitleColor)
        } else {
            // 가로줄 없음
            val normalPaintFlags = Paint.ANTI_ALIAS_FLAG
            views.setInt(R.id.item_title, "setPaintFlags", normalPaintFlags)
            views.setInt(R.id.item_memo, "setPaintFlags", normalPaintFlags)
            views.setInt(R.id.item_dday, "setPaintFlags", normalPaintFlags)
            views.setInt(R.id.item_category_icon, "setPaintFlags", normalPaintFlags)
            // D-Day 색상 적용 (이미 다크모드 대응됨)
            views.setTextColor(R.id.item_title, titleColor)
            views.setTextColor(R.id.item_dday, ddayColor)
            views.setTextColor(R.id.item_date, dateColor)
            views.setTextColor(R.id.item_memo, memoColor)
        }

        // 체크박스 클릭 시 전달할 인텐트
        val checkboxIntent = Intent().apply {
            putExtra(DdayWidgetProvider.EXTRA_CLICK_TYPE, DdayWidgetProvider.CLICK_TYPE_CHECKBOX)
            putExtra(DdayWidgetProvider.EXTRA_ITEM_ID, item.id)
            putExtra(DdayWidgetProvider.EXTRA_IS_CHECKED, !item.isChecked)
        }
        views.setOnClickFillInIntent(R.id.item_checkbox, checkboxIntent)

        // 아이템(체크박스 외 영역) 클릭 시 앱 실행 인텐트
        val itemIntent = Intent().apply {
            putExtra(DdayWidgetProvider.EXTRA_CLICK_TYPE, DdayWidgetProvider.CLICK_TYPE_ITEM)
        }
        views.setOnClickFillInIntent(R.id.item_card, itemIntent)

        return views
    }

    private fun calculateDaysUntil(date: Date): Int {
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        val targetDate = Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        return ((targetDate.time - today.time) / (1000 * 60 * 60 * 24)).toInt()
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = items.getOrNull(position)?.id?.toLong() ?: position.toLong()
    override fun hasStableIds(): Boolean = true
}
