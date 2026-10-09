package com.silverwest.dayli.ddaywidget

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar
import java.util.Date

/**
 * 알림 실제 동작 확인 (테스트 에뮬레이터 전용, 실제 AlarmManager 알람이 울릴 때까지 기다림 → 오래 걸림)
 * - 하루 전(D-1) / 당일(D-Day) / 항목별 "N일 전" : 알림 시간을 1분 뒤로 바꿔 매일 확인 알람이 실제로 울리는지
 * - 항목별 "N분 전" : 개별 알람이 실제로 울리는지
 *
 * adb shell am instrument -w -e notificationE2E true -e class com.silverwest.dayli.ddaywidget.NotificationE2ETest  *   com.silverwest.dayli.test/androidx.test.runner.AndroidJUnitRunner
 */
@RunWith(AndroidJUnit4::class)
class NotificationE2ETest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val nm = context.getSystemService(NotificationManager::class.java)
    private val day = 24L * 60 * 60 * 1000

    @Before
    fun setUp() {
        // 실제 알람을 몇 분씩 기다리므로 평소 테스트에서는 건너뜀: -e notificationE2E true 로 실행
        org.junit.Assume.assumeTrue(
            "notificationE2E 인자 없음 → 건너뜀",
            InstrumentationRegistry.getArguments().getString("notificationE2E") == "true"
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        context.getSharedPreferences("dday_widget_settings", Context.MODE_PRIVATE).edit().clear().commit()
        NotificationHelper.createNotificationChannel(context)
        nm.cancelAll()
        DdayDatabase.getDatabase(context).clearAllTables()
    }

    private fun startOfDay(offsetDays: Int): Date = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_MONTH, offsetDays)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.time

    private fun activeTitles(): List<String> =
        nm.activeNotifications.mapNotNull { it.notification.extras.getCharSequence("android.title")?.toString() }

    private fun waitForNotifications(predicate: (List<String>) -> Boolean, timeoutMs: Long): List<String> {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            val titles = activeTitles()
            if (predicate(titles)) return titles
            Thread.sleep(2_000)
        }
        return activeTitles()
    }

    @Test
    fun dailyCheck_firesAtConfiguredTime_showsDayBeforeSameDayAndDaysRule() {
        val dao = DdayDatabase.getDatabase(context).ddayDao()
        runBlocking {
            dao.insert(DdayItem(title = "오늘 일정", date = startOfDay(0)))
            dao.insert(DdayItem(title = "내일 일정", date = startOfDay(1)))
            dao.insert(DdayItem(title = "3일 전 알림 일정", date = startOfDay(3),
                notifications = DdayItem.notificationRulesToJson(listOf(NotificationRule("days", 3)))))
            dao.insert(DdayItem(title = "알림 없는 일정", date = startOfDay(5)))
            dao.insert(DdayItem(title = "완료한 오늘 일정", date = startOfDay(0), isChecked = true))
        }
        // 알림 시간 = 지금으로부터 1~2분 뒤 (설정 화면에서 시간 바꾸는 것과 같은 경로)
        val target = Calendar.getInstance().apply { add(Calendar.MINUTE, 1); if (get(Calendar.SECOND) > 45) add(Calendar.MINUTE, 1) }
        DdaySettings.setNotifyDayBeforeEnabled(context, true)
        DdaySettings.setNotifySameDayEnabled(context, true)
        DdaySettings.setNotifyHour(context, target.get(Calendar.HOUR_OF_DAY))
        DdaySettings.setNotifyMinute(context, target.get(Calendar.MINUTE))
        NotificationScheduler.updateSchedule(context)

        val titles = waitForNotifications({ t -> t.size >= 3 }, timeoutMs = 180_000)
        android.util.Log.i("NotifE2E", "daily titles=$titles")
        assertTrue("당일 알림 $titles", titles.any { it.contains("오늘 일정") && !it.contains("완료") })
        assertTrue("하루 전 알림 $titles", titles.any { it.contains("내일 일정") })
        assertTrue("3일 전 개별 알림 $titles", titles.any { it.contains("3일 전 알림 일정") })
        assertTrue("알림 없는 일정은 안 뜸 $titles", titles.none { it.contains("알림 없는 일정") })
        assertTrue("완료한 일정은 안 뜸 $titles", titles.none { it.contains("완료한") })
    }

    /** 재부팅 점검 준비용: 3시간 뒤 일정 + "1시간 전" 알람을 등록만 함 (adb reboot 후 알람 목록 확인) */
    @Test
    fun prepareFutureItemAlarmForRebootCheck() {
        val dao = DdayDatabase.getDatabase(context).ddayDao()
        val at = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 3) }
        val item = DdayItem(
            title = "재부팅 점검 일정",
            date = startOfDay(if (at.get(Calendar.DAY_OF_YEAR) == Calendar.getInstance().get(Calendar.DAY_OF_YEAR)) 0 else 1),
            timeHour = at.get(Calendar.HOUR_OF_DAY), timeMinute = at.get(Calendar.MINUTE),
            notifications = DdayItem.notificationRulesToJson(listOf(NotificationRule("hours", 1)))
        )
        val id = runBlocking { dao.insert(item) }.toInt()
        NotificationScheduler.scheduleItemNotifications(context, item.copy(id = id))
    }

    @Test
    fun minuteRule_firesItemAlarm() {
        val dao = DdayDatabase.getDatabase(context).ddayDao()
        // 일정 시각 = 2~3분 뒤, 규칙 "1분 전" → 1~2분 뒤 알람
        val at = Calendar.getInstance().apply { add(Calendar.MINUTE, 2); if (get(Calendar.SECOND) > 45) add(Calendar.MINUTE, 1) }
        val item = DdayItem(
            title = "1분 전 알림 일정",
            date = startOfDay(if (at.get(Calendar.DAY_OF_YEAR) == Calendar.getInstance().get(Calendar.DAY_OF_YEAR)) 0 else 1),
            timeHour = at.get(Calendar.HOUR_OF_DAY), timeMinute = at.get(Calendar.MINUTE),
            notifications = DdayItem.notificationRulesToJson(listOf(NotificationRule("minutes", 1)))
        )
        val id = runBlocking { dao.insert(item) }.toInt()
        NotificationScheduler.scheduleItemNotifications(context, item.copy(id = id))

        val titles = waitForNotifications({ t -> t.any { it.contains("1분 전 알림 일정") } }, timeoutMs = 200_000)
        android.util.Log.i("NotifE2E", "minute titles=$titles")
        assertTrue("1분 전 개별 알림 $titles", titles.any { it.contains("1분 전 알림 일정") })
    }
}
