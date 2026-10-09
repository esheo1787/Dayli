package com.silverwest.dayli.ddaywidget

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * 정확한 알람 권한 (Android 12+ SCHEDULE_EXACT_ALARM)
 * Android 14부터 새로 설치한 앱은 기본으로 꺼져 있어, 없으면 알림이 정해진 시각보다
 * 늦게(절전 제한이 없을 때 최대 약 1시간, 절전 중에는 더) 올 수 있음 → 사용자에게 켜도록 안내
 */
object ExactAlarms {

    fun canSchedule(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
        return am.canScheduleExactAlarms()
    }

    /** 권한 안내가 의미 있는 기기인지 (Android 12 미만은 항상 정확한 알람 가능) */
    fun isPermissionNeeded(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /** 시스템 설정의 "알람 및 리마인더" 화면 열기 */
    fun openSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            // 일부 제조사 기기: 앱 상세 설정으로 대체
            try {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) { }
        }
    }

    /** 권한이 켜진 뒤 기존 알람을 정확한 알람으로 다시 등록 */
    fun rescheduleAll(context: Context) {
        NotificationScheduler.updateSchedule(context)
        NotificationScheduler.rescheduleAllItemNotifications(context)
    }
}

/** 사용자가 시스템 설정에서 정확한 알람을 허용하면 시스템이 보내는 신호 → 알람 재등록 */
class ExactAlarmPermissionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            intent.action == AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED &&
            ExactAlarms.canSchedule(context)
        ) {
            android.util.Log.d("DDAY_NOTIFICATION", "⏰ 정확한 알람 허용됨 - 알람 재등록")
            ExactAlarms.rescheduleAll(context)
        }
    }
}
