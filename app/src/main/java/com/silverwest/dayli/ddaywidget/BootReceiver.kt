package com.silverwest.dayli.ddaywidget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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

            // 개별 아이템 알림 재스케줄링 — DB 조회가 끝날 때까지 프로세스 유지 (부팅 직후엔 앱 화면이 없음)
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    NotificationScheduler.rescheduleAllItemNotificationsNow(context)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
