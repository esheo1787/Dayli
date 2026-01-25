package com.example.myapplication.ddaywidget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class NotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        android.util.Log.d("DDAY_NOTIFICATION", "📬 NotificationReceiver.onReceive() 호출됨")

        when (intent.action) {
            ACTION_SHOW_NOTIFICATION -> {
                // 단일 아이템 알림
                val itemId = intent.getIntExtra(EXTRA_ITEM_ID, -1)
                val emoji = intent.getStringExtra(EXTRA_EMOJI) ?: "📅"
                val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
                val daysUntil = intent.getIntExtra(EXTRA_DAYS_UNTIL, 0)

                android.util.Log.d("DDAY_NOTIFICATION", "📬 알림 표시: id=$itemId, title=$title, daysUntil=$daysUntil")

                if (itemId != -1 && title.isNotEmpty()) {
                    NotificationHelper.showNotification(context, itemId, emoji, title, daysUntil)
                }
            }
            ACTION_DAILY_CHECK -> {
                // 매일 알림 체크 (설정된 시간에 실행)
                android.util.Log.d("DDAY_NOTIFICATION", "📬 매일 알림 체크 시작")
                checkAndShowNotifications(context)
                // 다음 날 알람 재설정
                NotificationScheduler.scheduleDailyCheck(context)
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                // 기기 재부팅 후 알람 재설정
                android.util.Log.d("DDAY_NOTIFICATION", "📬 부팅 완료 - 알람 재설정")
                NotificationScheduler.scheduleDailyCheck(context)
            }
        }
    }

    private fun checkAndShowNotifications(context: Context) {
        val notifyDayBefore = DdaySettings.isNotifyDayBeforeEnabled(context)
        val notifySameDay = DdaySettings.isNotifySameDayEnabled(context)

        if (!notifyDayBefore && !notifySameDay) {
            android.util.Log.d("DDAY_NOTIFICATION", "📬 알림 설정이 모두 꺼져있음")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = DdayDatabase.getDatabase(context)
                val items = db.ddayDao().getAll()

                val today = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.time

                items.forEach { item ->
                    // 체크된 항목은 알림 제외
                    if (item.isChecked) return@forEach

                    val targetDate = Calendar.getInstance().apply {
                        time = item.date
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.time

                    val daysUntil = ((targetDate.time - today.time) / (1000 * 60 * 60 * 24)).toInt()

                    android.util.Log.d("DDAY_NOTIFICATION", "📬 아이템 체크: ${item.title}, daysUntil=$daysUntil")

                    when {
                        daysUntil == 1 && notifyDayBefore -> {
                            // D-1 알림
                            android.util.Log.d("DDAY_NOTIFICATION", "📬 D-1 알림 표시: ${item.title}")
                            NotificationHelper.showNotification(
                                context,
                                item.id,
                                item.getEmoji(),
                                item.title,
                                daysUntil
                            )
                        }
                        daysUntil == 0 && notifySameDay -> {
                            // D-Day 알림
                            android.util.Log.d("DDAY_NOTIFICATION", "📬 D-Day 알림 표시: ${item.title}")
                            NotificationHelper.showNotification(
                                context,
                                item.id,
                                item.getEmoji(),
                                item.title,
                                daysUntil
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("DDAY_NOTIFICATION", "❌ 알림 체크 실패", e)
            }
        }
    }

    companion object {
        const val ACTION_SHOW_NOTIFICATION = "com.example.myapplication.ACTION_SHOW_NOTIFICATION"
        const val ACTION_DAILY_CHECK = "com.example.myapplication.ACTION_DAILY_CHECK"

        const val EXTRA_ITEM_ID = "extra_item_id"
        const val EXTRA_EMOJI = "extra_emoji"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_DAYS_UNTIL = "extra_days_until"
    }
}
