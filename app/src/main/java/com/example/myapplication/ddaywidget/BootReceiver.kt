package com.example.myapplication.ddaywidget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            android.util.Log.d("DDAY_WIDGET", "📱 부팅 완료 - 알람 재설정")

            // 자정 알람 재설정
            DdayWidgetProvider.scheduleMidnightUpdate(context)

            // 위젯 갱신 (부팅 후 D-Day 업데이트)
            DdayWidgetProvider.refreshAllWidgets(context)

            // 알림 알람 재설정
            NotificationScheduler.updateSchedule(context)
        }
    }
}
