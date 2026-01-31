package com.silverwest.dayli.ddaywidget

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.silverwest.dayli.MainActivity
import com.silverwest.dayli.R

object NotificationHelper {
    private const val CHANNEL_ID = "dday_notifications"
    private const val CHANNEL_NAME = "D-Day 알림"
    private const val CHANNEL_DESCRIPTION = "D-Day 하루 전 및 당일 알림"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESCRIPTION
                enableLights(true)
                enableVibration(true)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showNotification(
        context: Context,
        itemId: Int,
        emoji: String,
        title: String,
        daysUntil: Int
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 알림 클릭 시 앱 실행
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            itemId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 알림 메시지 결정
        val (message, ddayText) = when {
            daysUntil == 1 -> "내일이에요!" to "D-1"
            daysUntil == 0 -> "오늘이에요!" to "D-DAY"
            daysUntil < 0 -> "지났어요!" to "D+${-daysUntil}"
            else -> "${daysUntil}일 남았어요!" to "D-$daysUntil"
        }

        // 설정 확인
        val soundEnabled = DdaySettings.isNotifySoundEnabled(context)
        val vibrateEnabled = DdaySettings.isNotifyVibrateEnabled(context)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$emoji $title")
            .setContentText("$message $ddayText")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)

        // 소리 설정
        if (soundEnabled) {
            builder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
        } else {
            builder.setSilent(true)
        }

        // 진동 설정
        if (vibrateEnabled) {
            builder.setVibrate(longArrayOf(0, 300, 100, 300))
        }

        // 무음 모드 (소리와 진동 둘 다 꺼진 경우)
        if (!soundEnabled && !vibrateEnabled) {
            builder.setSilent(true)
        }

        notificationManager.notify(itemId, builder.build())
    }
}
