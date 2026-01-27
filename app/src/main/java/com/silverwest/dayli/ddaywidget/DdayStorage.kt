package com.silverwest.dayli.ddaywidget

import android.content.Context
import java.text.SimpleDateFormat
import java.util.*

object DdayStorage {
    private const val PREF_NAME = "dday_prefs"
    private const val KEY_TITLE = "dday_title"
    private const val KEY_DATE = "dday_date"

    fun saveDday(context: Context, title: String, date: Calendar) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putString(KEY_TITLE, title)
        editor.putLong(KEY_DATE, date.timeInMillis) // ✅ 문자열 말고 Long으로 저장해야 날짜 계산 가능
        editor.apply()
    }

    fun loadDday(context: Context): Pair<String, String>? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val title = prefs.getString(KEY_TITLE, null)
        val date = prefs.getLong(KEY_DATE, -1L)
        return if (title != null && date != -1L) {
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(date))
            title to dateStr
        } else {
            null
        }
    }

    fun getDday(context: Context): Pair<String, Calendar>? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val title = prefs.getString(KEY_TITLE, null)
        val timeMillis = prefs.getLong(KEY_DATE, -1)

        return if (title != null && timeMillis != -1L) {
            val calendar = Calendar.getInstance().apply { timeInMillis = timeMillis }
            title to calendar
        } else {
            null
        }
    }
}
