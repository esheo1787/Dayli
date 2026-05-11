package com.silverwest.dayli.ddaywidget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.RemoteViews
import com.silverwest.dayli.MainActivity
import com.silverwest.dayli.R
import com.silverwest.dayli.ui.theme.isDarkMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class DdayWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
        // 위젯이 추가되면 자정 알람 설정
        scheduleMidnightUpdate(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        // 첫 위젯 추가 시 자정 알람 설정
        scheduleMidnightUpdate(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        // 모든 위젯 제거 시 알람 취소
        cancelMidnightUpdate(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            ACTION_CHECKBOX_CLICK -> {
                val clickType = intent.getIntExtra(EXTRA_CLICK_TYPE, 0)

                when (clickType) {
                    CLICK_TYPE_CHECKBOX -> {
                        val itemId = intent.getIntExtra(EXTRA_ITEM_ID, -1)
                        // 인텐트의 isChecked는 토글 의도값이지만, 핸들러가 item.isChecked로 판단하므로 사용하지 않음
                        android.util.Log.d("DDAY_WIDGET", "☑️ 체크박스 클릭: id=$itemId")

                        if (itemId != -1) {
                            val pendingResult = goAsync()
                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    val dao = DdayDatabase.getDatabase(context).ddayDao()
                                    val item = dao.getById(itemId)
                                    if (item != null) {
                                        val result = DdayRepeatHandler.toggleChecked(
                                            dao, item, System.currentTimeMillis()
                                        )
                                        android.util.Log.d("DDAY_WIDGET", "✅ 체크박스 처리: $result")
                                        if (result !is ToggleCheckResult.NoOp) {
                                            refreshAllWidgets(context)
                                        }
                                    } else {
                                        android.util.Log.w("DDAY_WIDGET", "⚠️ 항목 없음: id=$itemId")
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("DDAY_WIDGET", "❌ 체크박스 처리 실패", e)
                                } finally {
                                    pendingResult.finish()
                                }
                            }
                        }
                    }
                    CLICK_TYPE_ITEM -> {
                        android.util.Log.d("DDAY_WIDGET", "📱 아이템 클릭 → 앱 실행")
                        val launchIntent = context.packageManager
                            .getLaunchIntentForPackage(context.packageName)
                            ?.apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            }
                        launchIntent?.let { context.startActivity(it) }
                    }
                    CLICK_TYPE_SUBTASK -> {
                        val itemId = intent.getIntExtra(EXTRA_ITEM_ID, -1)
                        val subTaskIndex = intent.getIntExtra(EXTRA_SUBTASK_INDEX, -1)
                        // 인텐트의 isChecked는 토글 의도값. 핸들러가 현재 상태 기준으로 반전 처리.
                        android.util.Log.d("DDAY_WIDGET", "☑️ 서브태스크 클릭: itemId=$itemId, index=$subTaskIndex")

                        if (itemId != -1 && subTaskIndex != -1) {
                            val pendingResult = goAsync()
                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    val dao = DdayDatabase.getDatabase(context).ddayDao()
                                    val item = dao.getById(itemId)
                                    if (item != null) {
                                        val result = DdayRepeatHandler.toggleSubTask(
                                            dao, item, subTaskIndex, System.currentTimeMillis()
                                        )
                                        android.util.Log.d("DDAY_WIDGET", "✅ 서브태스크 처리: $result")
                                        if (result !is ToggleCheckResult.NoOp) {
                                            refreshAllWidgets(context)
                                        }
                                    } else {
                                        android.util.Log.w("DDAY_WIDGET", "⚠️ 항목 없음: id=$itemId")
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("DDAY_WIDGET", "❌ 서브태스크 처리 실패", e)
                                } finally {
                                    pendingResult.finish()
                                }
                            }
                        }
                    }
                    CLICK_TYPE_TODO_TOGGLE -> {
                        // To-Do 헤더 접기/펼치기
                        val itemId = intent.getIntExtra(EXTRA_ITEM_ID, -1)
                        if (itemId != -1) {
                            DdaySettings.toggleTodoCollapsed(context, itemId)
                            refreshAllWidgets(context)
                        }
                    }
                }
            }
            ACTION_MIDNIGHT_UPDATE -> {
                android.util.Log.d("DDAY_WIDGET", "🌙 자정 업데이트 실행")
                refreshAllWidgets(context)
                scheduleMidnightUpdate(context)
            }
        }
    }

    companion object {
        const val ACTION_CHECKBOX_CLICK = "com.silverwest.dayli.ACTION_CHECKBOX_CLICK"
        const val ACTION_ITEM_CLICK = "com.silverwest.dayli.ACTION_ITEM_CLICK"
        const val ACTION_MIDNIGHT_UPDATE = "com.silverwest.dayli.ACTION_MIDNIGHT_UPDATE"
        const val EXTRA_ITEM_ID = "extra_item_id"
        const val EXTRA_IS_CHECKED = "extra_is_checked"
        const val EXTRA_CLICK_TYPE = "extra_click_type"
        const val EXTRA_SUBTASK_INDEX = "extra_subtask_index"
        const val CLICK_TYPE_CHECKBOX = 1
        const val CLICK_TYPE_ITEM = 2
        const val CLICK_TYPE_SUBTASK = 3
        const val CLICK_TYPE_TODO_TOGGLE = 4

        fun refreshAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)

            // 데이터만 갱신 (스크롤 위치 유지)
            val mainIds = manager.getAppWidgetIds(ComponentName(context, DdayWidgetProvider::class.java))
            if (mainIds.isNotEmpty()) {
                manager.notifyAppWidgetViewDataChanged(mainIds, R.id.widgetListView)
            }

            val ddayOnlyIds = manager.getAppWidgetIds(ComponentName(context, DdayOnlyWidgetProvider::class.java))
            if (ddayOnlyIds.isNotEmpty()) {
                manager.notifyAppWidgetViewDataChanged(ddayOnlyIds, R.id.widgetListView)
            }

            val todoOnlyIds = manager.getAppWidgetIds(ComponentName(context, TodoOnlyWidgetProvider::class.java))
            if (todoOnlyIds.isNotEmpty()) {
                manager.notifyAppWidgetViewDataChanged(todoOnlyIds, R.id.widgetListView)
            }

            android.util.Log.d("DDAY_WIDGET", "🔁 refreshAllWidgets() 호출됨: 통합=${mainIds.size}, D-Day=${ddayOnlyIds.size}, To-Do=${todoOnlyIds.size}")
        }

        // 설정 변경 시 전체 위젯 레이아웃 재구성 (배경 등 반영, 스크롤 초기화됨)
        fun refreshAllWidgetsFull(context: Context) {
            val manager = AppWidgetManager.getInstance(context)

            val mainIds = manager.getAppWidgetIds(ComponentName(context, DdayWidgetProvider::class.java))
            if (mainIds.isNotEmpty()) {
                mainIds.forEach { updateAppWidget(context, manager, it) }
                manager.notifyAppWidgetViewDataChanged(mainIds, R.id.widgetListView)
            }

            val ddayOnlyIds = manager.getAppWidgetIds(ComponentName(context, DdayOnlyWidgetProvider::class.java))
            if (ddayOnlyIds.isNotEmpty()) {
                ddayOnlyIds.forEach { DdayOnlyWidgetProvider.updateAppWidget(context, manager, it) }
                manager.notifyAppWidgetViewDataChanged(ddayOnlyIds, R.id.widgetListView)
            }

            val todoOnlyIds = manager.getAppWidgetIds(ComponentName(context, TodoOnlyWidgetProvider::class.java))
            if (todoOnlyIds.isNotEmpty()) {
                todoOnlyIds.forEach { TodoOnlyWidgetProvider.updateAppWidget(context, manager, it) }
                manager.notifyAppWidgetViewDataChanged(todoOnlyIds, R.id.widgetListView)
            }

            android.util.Log.d("DDAY_WIDGET", "🔁 refreshAllWidgetsFull() 호출됨")
        }

        private fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val intent = Intent(context, DdayWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }

            // 위젯 배경 투명도 설정 읽기
            val widgetBgOpacity = DdaySettings.getWidgetBgOpacity(context)
            val bgAlpha = (widgetBgOpacity * 2.55f).toInt().coerceIn(0, 255)  // 0~100 → 0~255

            // Soft Pastel 테마 배경색
            // 라이트: CreamWhite (#FFFDF5), 다크: DarkSurface (#2A2A3E)
            val isDark = isDarkMode(context)
            val baseColor = if (isDark) 0x002A2A3E else 0x00FFFDF5
            val widgetBgColor = (bgAlpha shl 24) or baseColor

            android.util.Log.d("DDAY_WIDGET", "🎨 위젯 배경 업데이트: opacity=$widgetBgOpacity, alpha=$bgAlpha, isDark=$isDark")

            val views = RemoteViews(context.packageName, R.layout.widget_dday_scrollable).apply {
                setRemoteAdapter(R.id.widgetListView, intent)
                setEmptyView(R.id.widgetListView, R.id.emptyTextView)
                setTextViewText(R.id.emptyTextView, "항목을 추가해보세요")

                // 위젯 컨테이너 배경색 적용
                setInt(R.id.widget_container, "setBackgroundColor", widgetBgColor)

                // 빈 텍스트 색상 (Soft Pastel 테마)
                val emptyTextColor = if (isDark) 0x80B8B8B8.toInt() else 0x807A7A7A.toInt()
                setTextColor(R.id.emptyTextView, emptyTextColor)

                // 체크박스 클릭을 위한 PendingIntent 템플릿
                val clickIntent = Intent(context, DdayWidgetProvider::class.java).apply {
                    action = ACTION_CHECKBOX_CLICK
                }
                val clickPendingIntent = PendingIntent.getBroadcast(
                    context,
                    0,
                    clickIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
                setPendingIntentTemplate(R.id.widgetListView, clickPendingIntent)

                // 위젯 전체 클릭 시 앱 실행 PendingIntent
                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val launchPendingIntent = PendingIntent.getActivity(
                    context,
                    appWidgetId,
                    launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                setOnClickPendingIntent(R.id.widget_container, launchPendingIntent)
                setOnClickPendingIntent(R.id.emptyTextView, launchPendingIntent)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun scheduleMidnightUpdate(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            val intent = Intent(context, DdayWidgetProvider::class.java).apply {
                action = ACTION_MIDNIGHT_UPDATE
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // 다음 자정 시간 계산
            val calendar = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            // 정확한 시간에 알람 설정 (API 31+ 권한 확인)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                    // 권한 없으면 비정확 알람으로 대체
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                    android.util.Log.d("DDAY_WIDGET", "⏰ 자정 알람 설정 (비정확): ${calendar.time}")
                } else {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                    android.util.Log.d("DDAY_WIDGET", "⏰ 자정 알람 설정 (정확): ${calendar.time}")
                }
            } catch (e: SecurityException) {
                // Fallback: 비정확 알람
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
                android.util.Log.w("DDAY_WIDGET", "⏰ 정확 알람 권한 없음, 비정확 알람 사용: ${calendar.time}")
            }
        }

        private fun cancelMidnightUpdate(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, DdayWidgetProvider::class.java).apply {
                action = ACTION_MIDNIGHT_UPDATE
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
    }
}
