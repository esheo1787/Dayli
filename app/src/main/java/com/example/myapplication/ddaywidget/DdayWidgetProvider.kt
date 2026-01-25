package com.example.myapplication.ddaywidget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.example.myapplication.R
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
                        val isChecked = intent.getBooleanExtra(EXTRA_IS_CHECKED, false)

                        android.util.Log.d("DDAY_WIDGET", "☑️ 체크박스 클릭: id=$itemId, checked=$isChecked")

                        if (itemId != -1) {
                            CoroutineScope(Dispatchers.IO).launch {
                                val db = DdayDatabase.getDatabase(context)
                                val checkedAt = if (isChecked) System.currentTimeMillis() else null
                                db.ddayDao().updateChecked(itemId, isChecked, checkedAt)

                                android.util.Log.d("DDAY_WIDGET", "✅ DB 업데이트 완료 (checkedAt=$checkedAt)")
                                refreshAllWidgets(context)
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
        const val ACTION_CHECKBOX_CLICK = "com.example.myapplication.ACTION_CHECKBOX_CLICK"
        const val ACTION_ITEM_CLICK = "com.example.myapplication.ACTION_ITEM_CLICK"
        const val ACTION_MIDNIGHT_UPDATE = "com.example.myapplication.ACTION_MIDNIGHT_UPDATE"
        const val EXTRA_ITEM_ID = "extra_item_id"
        const val EXTRA_IS_CHECKED = "extra_is_checked"
        const val EXTRA_CLICK_TYPE = "extra_click_type"
        const val CLICK_TYPE_CHECKBOX = 1
        const val CLICK_TYPE_ITEM = 2

        fun refreshAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, DdayWidgetProvider::class.java))

            android.util.Log.d("DDAY_WIDGET", "🔁 refreshAllWidgets() 호출됨, 위젯 수 = ${ids.size}")

            // 위젯 전체 업데이트 (PendingIntent 포함)
            for (id in ids) {
                updateAppWidget(context, manager, id)
            }

            // 데이터 갱신
            manager.notifyAppWidgetViewDataChanged(ids, R.id.widgetListView)
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
            val widgetBgColor = (bgAlpha shl 24) or 0x00FFFFFF  // 흰색 + 알파

            android.util.Log.d("DDAY_WIDGET", "🎨 위젯 배경 업데이트: opacity=$widgetBgOpacity, alpha=$bgAlpha")

            val views = RemoteViews(context.packageName, R.layout.widget_dday_scrollable).apply {
                setRemoteAdapter(R.id.widgetListView, intent)
                setEmptyView(R.id.widgetListView, R.id.emptyTextView)

                // 위젯 컨테이너 배경색 적용
                setInt(R.id.widget_container, "setBackgroundColor", widgetBgColor)

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

            // 정확한 시간에 알람 설정
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )

            android.util.Log.d("DDAY_WIDGET", "⏰ 자정 알람 설정: ${calendar.time}")
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
