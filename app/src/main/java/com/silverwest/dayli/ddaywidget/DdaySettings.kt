package com.silverwest.dayli.ddaywidget

import android.content.Context
import android.content.SharedPreferences

/**
 * D-Day 위젯 설정 관리
 * SharedPreferences를 사용하여 앱과 위젯에서 공유
 */
object DdaySettings {
    private const val PREFS_NAME = "dday_widget_settings"

    // 배경 색상 표시 여부
    private const val KEY_BACKGROUND_ENABLED = "background_enabled"
    private const val DEFAULT_BACKGROUND_ENABLED = true

    // 아이템 배경 투명도 (0~100, 기본 15% - 글래스모피즘)
    private const val KEY_BACKGROUND_OPACITY = "background_opacity"
    private const val DEFAULT_BACKGROUND_OPACITY = 15

    // 아이콘 배경 투명도 (0~100, 기본 20%)
    private const val KEY_ICON_BG_OPACITY = "icon_bg_opacity"
    private const val DEFAULT_ICON_BG_OPACITY = 20

    // 위젯 배경 투명도 (0~100, 기본 20% - 글래스모피즘)
    private const val KEY_WIDGET_BG_OPACITY = "widget_bg_opacity"
    private const val DEFAULT_WIDGET_BG_OPACITY = 20

    // 위젯 글씨 크기 (0=작게, 1=보통, 2=크게)
    private const val KEY_WIDGET_FONT_SIZE = "widget_font_size"
    private const val DEFAULT_WIDGET_FONT_SIZE = 1  // 보통

    // 알림 설정
    private const val KEY_NOTIFY_DAY_BEFORE = "notify_day_before"
    private const val DEFAULT_NOTIFY_DAY_BEFORE = true

    private const val KEY_NOTIFY_SAME_DAY = "notify_same_day"
    private const val DEFAULT_NOTIFY_SAME_DAY = true

    private const val KEY_NOTIFY_HOUR = "notify_hour"
    private const val DEFAULT_NOTIFY_HOUR = 9  // 오전 9시

    private const val KEY_NOTIFY_MINUTE = "notify_minute"
    private const val DEFAULT_NOTIFY_MINUTE = 0

    private const val KEY_NOTIFY_SOUND = "notify_sound"
    private const val DEFAULT_NOTIFY_SOUND = true

    private const val KEY_NOTIFY_VIBRATE = "notify_vibrate"
    private const val DEFAULT_NOTIFY_VIBRATE = true

    // 테마 모드 (0=시스템, 1=라이트, 2=다크)
    private const val KEY_THEME_MODE = "theme_mode"
    private const val DEFAULT_THEME_MODE = 0  // 시스템 설정 따라가기

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // 배경 색상 표시 여부
    fun isBackgroundEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_BACKGROUND_ENABLED, DEFAULT_BACKGROUND_ENABLED)
    }

    fun setBackgroundEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_BACKGROUND_ENABLED, enabled).apply()
    }

    // 배경 투명도 (0~100)
    fun getBackgroundOpacity(context: Context): Int {
        return getPrefs(context).getInt(KEY_BACKGROUND_OPACITY, DEFAULT_BACKGROUND_OPACITY)
    }

    fun setBackgroundOpacity(context: Context, opacity: Int) {
        getPrefs(context).edit().putInt(KEY_BACKGROUND_OPACITY, opacity.coerceIn(0, 100)).apply()
    }

    // 아이콘 배경 투명도 (0~100)
    fun getIconBgOpacity(context: Context): Int {
        return getPrefs(context).getInt(KEY_ICON_BG_OPACITY, DEFAULT_ICON_BG_OPACITY)
    }

    fun setIconBgOpacity(context: Context, opacity: Int) {
        getPrefs(context).edit().putInt(KEY_ICON_BG_OPACITY, opacity.coerceIn(0, 100)).apply()
    }

    // 위젯 배경 투명도 (0~100)
    fun getWidgetBgOpacity(context: Context): Int {
        return getPrefs(context).getInt(KEY_WIDGET_BG_OPACITY, DEFAULT_WIDGET_BG_OPACITY)
    }

    fun setWidgetBgOpacity(context: Context, opacity: Int) {
        getPrefs(context).edit().putInt(KEY_WIDGET_BG_OPACITY, opacity.coerceIn(0, 100)).apply()
    }

    // 위젯 글씨 크기 (0=작게, 1=보통, 2=크게)
    fun getWidgetFontSize(context: Context): Int {
        return getPrefs(context).getInt(KEY_WIDGET_FONT_SIZE, DEFAULT_WIDGET_FONT_SIZE)
    }

    fun setWidgetFontSize(context: Context, size: Int) {
        getPrefs(context).edit().putInt(KEY_WIDGET_FONT_SIZE, size.coerceIn(0, 2)).apply()
    }

    // 글씨 크기 → sp 값 변환
    fun getFontSizeSp(context: Context, baseSize: Float): Float {
        return when (getWidgetFontSize(context)) {
            0 -> baseSize * 0.85f  // 작게
            2 -> baseSize * 1.15f  // 크게
            else -> baseSize       // 보통
        }
    }

    // 투명도 % → 알파 float (0.0 ~ 1.0)
    fun opacityToAlpha(opacity: Int): Float {
        return opacity / 100f
    }

    // ===== 알림 설정 =====

    // 하루 전 알림
    fun isNotifyDayBeforeEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_NOTIFY_DAY_BEFORE, DEFAULT_NOTIFY_DAY_BEFORE)
    }

    fun setNotifyDayBeforeEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_NOTIFY_DAY_BEFORE, enabled).apply()
    }

    // 당일 알림
    fun isNotifySameDayEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_NOTIFY_SAME_DAY, DEFAULT_NOTIFY_SAME_DAY)
    }

    fun setNotifySameDayEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_NOTIFY_SAME_DAY, enabled).apply()
    }

    // 알림 시간 (시)
    fun getNotifyHour(context: Context): Int {
        return getPrefs(context).getInt(KEY_NOTIFY_HOUR, DEFAULT_NOTIFY_HOUR)
    }

    fun setNotifyHour(context: Context, hour: Int) {
        getPrefs(context).edit().putInt(KEY_NOTIFY_HOUR, hour.coerceIn(0, 23)).apply()
    }

    // 알림 시간 (분)
    fun getNotifyMinute(context: Context): Int {
        return getPrefs(context).getInt(KEY_NOTIFY_MINUTE, DEFAULT_NOTIFY_MINUTE)
    }

    fun setNotifyMinute(context: Context, minute: Int) {
        getPrefs(context).edit().putInt(KEY_NOTIFY_MINUTE, minute.coerceIn(0, 59)).apply()
    }

    // 알림 소리
    fun isNotifySoundEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_NOTIFY_SOUND, DEFAULT_NOTIFY_SOUND)
    }

    fun setNotifySoundEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_NOTIFY_SOUND, enabled).apply()
    }

    // 알림 진동
    fun isNotifyVibrateEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_NOTIFY_VIBRATE, DEFAULT_NOTIFY_VIBRATE)
    }

    fun setNotifyVibrateEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_NOTIFY_VIBRATE, enabled).apply()
    }

    // 알림 시간 포맷 문자열
    fun getNotifyTimeString(context: Context): String {
        val hour = getNotifyHour(context)
        val minute = getNotifyMinute(context)
        val amPm = if (hour < 12) "오전" else "오후"
        val displayHour = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
        return if (minute == 0) {
            "$amPm ${displayHour}시"
        } else {
            "$amPm ${displayHour}시 ${minute}분"
        }
    }

    // ===== 테마 설정 =====

    // 테마 모드 (0=시스템, 1=라이트, 2=다크)
    fun getThemeMode(context: Context): Int {
        return getPrefs(context).getInt(KEY_THEME_MODE, DEFAULT_THEME_MODE)
    }

    fun setThemeMode(context: Context, mode: Int) {
        getPrefs(context).edit().putInt(KEY_THEME_MODE, mode.coerceIn(0, 2)).apply()
    }

    // 테마 모드 enum
    enum class ThemeMode(val value: Int, val displayName: String) {
        SYSTEM(0, "시스템 설정"),
        LIGHT(1, "라이트 모드"),
        DARK(2, "다크 모드");

        companion object {
            fun fromValue(value: Int): ThemeMode {
                return entries.find { it.value == value } ?: SYSTEM
            }
        }
    }

    fun getThemeModeEnum(context: Context): ThemeMode {
        return ThemeMode.fromValue(getThemeMode(context))
    }

    fun setThemeModeEnum(context: Context, mode: ThemeMode) {
        setThemeMode(context, mode.value)
    }

    // ===== 정렬 설정 (앱 ↔ 위젯 공유) =====

    // D-Day 정렬 (MY_ORDER / NEAREST / FARTHEST)
    private const val KEY_DDAY_SORT = "dday_sort"
    private const val DEFAULT_DDAY_SORT = "MY_ORDER"

    fun getDdaySort(context: Context): String {
        return getPrefs(context).getString(KEY_DDAY_SORT, DEFAULT_DDAY_SORT) ?: DEFAULT_DDAY_SORT
    }

    fun setDdaySort(context: Context, sort: String) {
        getPrefs(context).edit().putString(KEY_DDAY_SORT, sort).apply()
    }

    // To-Do 정렬 (MY_ORDER / INCOMPLETE_FIRST / LATEST)
    private const val KEY_TODO_SORT = "todo_sort"
    private const val DEFAULT_TODO_SORT = "MY_ORDER"

    fun getTodoSort(context: Context): String {
        return getPrefs(context).getString(KEY_TODO_SORT, DEFAULT_TODO_SORT) ?: DEFAULT_TODO_SORT
    }

    fun setTodoSort(context: Context, sort: String) {
        getPrefs(context).edit().putString(KEY_TODO_SORT, sort).apply()
    }

    // ===== D-Day 위젯 그룹 접기/펼치기 =====

    private const val KEY_COLLAPSED_GROUPS = "collapsed_groups"

    // 접힌 그룹 목록 가져오기
    fun getCollapsedGroups(context: Context): Set<String> {
        return getPrefs(context).getStringSet(KEY_COLLAPSED_GROUPS, emptySet()) ?: emptySet()
    }

    // 그룹 접기/펼치기 토글
    fun toggleGroupCollapsed(context: Context, groupName: String) {
        val collapsed = getCollapsedGroups(context).toMutableSet()
        if (collapsed.contains(groupName)) {
            collapsed.remove(groupName)
        } else {
            collapsed.add(groupName)
        }
        getPrefs(context).edit().putStringSet(KEY_COLLAPSED_GROUPS, collapsed).apply()
    }

    // 그룹이 접혀있는지 확인
    fun isGroupCollapsed(context: Context, groupName: String): Boolean {
        return getCollapsedGroups(context).contains(groupName)
    }
}
