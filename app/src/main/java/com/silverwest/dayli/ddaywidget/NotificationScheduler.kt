package com.silverwest.dayli.ddaywidget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

object NotificationScheduler {

    /**
     * 매일 알림 체크를 위한 알람 설정
     * 설정된 시간에 NotificationReceiver가 호출되어 D-1, D-Day 아이템을 체크
     */
    fun scheduleDailyCheck(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            if (alarmManager == null) {
                android.util.Log.e("DDAY_NOTIFICATION", "❌ AlarmManager를 가져올 수 없음")
                return
            }

            val intent = Intent(context, NotificationReceiver::class.java).apply {
                action = NotificationReceiver.ACTION_DAILY_CHECK
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_DAILY_CHECK,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // 설정된 알림 시간 가져오기
            val hour = DdaySettings.getNotifyHour(context)
            val minute = DdaySettings.getNotifyMinute(context)

            // 다음 알림 시간 계산
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)

                // 이미 지난 시간이면 다음 날로 설정
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            // 정확한 시간에 알람 설정
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            calendar.timeInMillis,
                            pendingIntent
                        )
                    } else {
                        // 정확한 알람 권한이 없으면 부정확한 알람 사용
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            calendar.timeInMillis,
                            pendingIntent
                        )
                    }
                } catch (e: SecurityException) {
                    // 권한 없으면 부정확한 알람 사용
                    android.util.Log.w("DDAY_NOTIFICATION", "⚠️ 정확한 알람 권한 없음, 부정확한 알람 사용")
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }

            android.util.Log.d("DDAY_NOTIFICATION", "⏰ 매일 알림 알람 설정: ${calendar.time}")
        } catch (e: Exception) {
            android.util.Log.e("DDAY_NOTIFICATION", "❌ 알람 설정 실패", e)
        }
    }

    /**
     * 매일 알림 체크 알람 취소
     */
    fun cancelDailyCheck(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            if (alarmManager == null) {
                android.util.Log.e("DDAY_NOTIFICATION", "❌ AlarmManager를 가져올 수 없음")
                return
            }

            val intent = Intent(context, NotificationReceiver::class.java).apply {
                action = NotificationReceiver.ACTION_DAILY_CHECK
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_DAILY_CHECK,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            alarmManager.cancel(pendingIntent)
            android.util.Log.d("DDAY_NOTIFICATION", "⏰ 매일 알림 알람 취소됨")
        } catch (e: Exception) {
            android.util.Log.e("DDAY_NOTIFICATION", "❌ 알람 취소 실패", e)
        }
    }

    /**
     * 알림이 활성화되어 있는지 확인하고 알람 설정/취소
     */
    fun updateSchedule(context: Context) {
        try {
            val dayBeforeEnabled = DdaySettings.isNotifyDayBeforeEnabled(context)
            val sameDayEnabled = DdaySettings.isNotifySameDayEnabled(context)

            if (dayBeforeEnabled || sameDayEnabled) {
                // 알림이 하나라도 켜져있으면 알람 설정
                scheduleDailyCheck(context)
            } else {
                // 모두 꺼져있으면 알람 취소
                cancelDailyCheck(context)
            }
        } catch (e: Exception) {
            android.util.Log.e("DDAY_NOTIFICATION", "❌ 알람 스케줄 업데이트 실패", e)
        }
    }

    private const val REQUEST_CODE_DAILY_CHECK = 1001
}
