package com.silverwest.dayli.ddaywidget

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.Calendar
import java.util.TimeZone

/** 폰 캘린더(구글·삼성 등)에 있는 캘린더 하나 */
data class PhoneCalendarInfo(
    val id: Long,
    val name: String,
    val accountName: String,
    val accountType: String,
    val color: Int
) {
    val sourceLabel: String get() = PhoneCalendar.sourceLabel(accountType)
}

/** 폰 캘린더의 일정 한 번 (반복 일정은 회차마다 하나) */
data class PhoneEvent(
    val eventId: Long,
    val calendarId: Long,
    val title: String,
    val beginMs: Long,
    val endMs: Long,
    val allDay: Boolean,
    val color: Int,
    val location: String?,
    val sourceLabel: String
) {
    /** 이 일정이 걸친 날짜(로컬). 종일 일정은 UTC 자정 기준으로 저장돼 있어 따로 계산 */
    val startDay: Triple<Int, Int, Int> get() = PhoneCalendar.localDay(beginMs, allDay)

    /** 가져오기 중복 방지 키 (같은 반복 일정이라도 회차마다 다름) */
    val importKey: String get() = PhoneCalendar.importKey(eventId, beginMs)
}

/**
 * 폰 캘린더 읽기 전용 (CalendarContract). 구글 캘린더는 폰의 구글 계정 동기화로 여기에 들어와 있음.
 * 네이버 기본 캘린더는 폰 캘린더에 들어오지 않아 읽을 수 없음.
 * 일정은 기기 밖으로 보내지 않음.
 */
object PhoneCalendar {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** 계정 종류 → 화면에 보여줄 출처 이름 */
    fun sourceLabel(accountType: String): String = when {
        accountType == "com.google" -> "구글"
        accountType.contains("samsung", ignoreCase = true) || accountType == "com.osp.app.signin" -> "삼성"
        accountType == CalendarContract.ACCOUNT_TYPE_LOCAL || accountType.equals("local", ignoreCase = true) -> "휴대폰"
        else -> "캘린더"
    }

    /** 종일 일정은 UTC 자정으로 저장됨 → UTC 기준 날짜, 아니면 기기 시간대 기준 날짜 (연, 월(1~12), 일) */
    fun localDay(timeMs: Long, allDay: Boolean, zone: TimeZone = TimeZone.getDefault()): Triple<Int, Int, Int> {
        val cal = Calendar.getInstance(if (allDay) TimeZone.getTimeZone("UTC") else zone).apply { timeInMillis = timeMs }
        return Triple(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
    }

    fun importKey(eventId: Long, beginMs: Long): String = "$eventId@$beginMs"

    /** 보이는 캘린더 목록 (권한 없으면 빈 목록) */
    fun calendars(context: Context): List<PhoneCalendarInfo> {
        if (!hasPermission(context)) return emptyList()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.CALENDAR_COLOR,
            CalendarContract.Calendars.VISIBLE
        )
        return try {
            context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, projection, null, null, null)?.use { c ->
                buildList {
                    while (c.moveToNext()) {
                        if (c.getInt(5) == 0) continue  // 캘린더 앱에서 숨긴 캘린더
                        add(
                            PhoneCalendarInfo(
                                id = c.getLong(0),
                                name = c.getString(1) ?: "캘린더",
                                accountName = c.getString(2) ?: "",
                                accountType = c.getString(3) ?: "",
                                color = c.getInt(4)
                            )
                        )
                    }
                }
            } ?: emptyList()
        } catch (e: SecurityException) {
            emptyList()
        } catch (e: Exception) {
            Log.w("DDAY_WIDGET", "캘린더 목록 읽기 실패", e)
            emptyList()
        }
    }

    /**
     * [fromMs, toMs) 사이의 일정 (반복 일정은 회차별로 펼쳐서). calendarIds가 비면 빈 목록.
     * Instances 테이블을 써야 반복 일정이 날짜마다 나옴
     */
    fun events(context: Context, fromMs: Long, toMs: Long, calendarIds: Set<Long>): List<PhoneEvent> {
        if (!hasPermission(context) || calendarIds.isEmpty()) return emptyList()
        val labels = calendars(context).associate { it.id to it.sourceLabel }
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, fromMs)
            ContentUris.appendId(it, toMs)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.DISPLAY_COLOR,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.STATUS
        )
        val selection = "${CalendarContract.Instances.CALENDAR_ID} IN (${calendarIds.joinToString(",")})"
        return try {
            context.contentResolver.query(uri, projection, selection, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                buildList {
                    while (c.moveToNext()) {
                        if (c.getInt(8) == CalendarContract.Instances.STATUS_CANCELED) continue
                        val calendarId = c.getLong(1)
                        add(
                            PhoneEvent(
                                eventId = c.getLong(0),
                                calendarId = calendarId,
                                title = c.getString(2)?.takeIf { it.isNotBlank() } ?: "(제목 없음)",
                                beginMs = c.getLong(3),
                                endMs = c.getLong(4),
                                allDay = c.getInt(5) == 1,
                                color = c.getInt(6),
                                location = c.getString(7)?.takeIf { it.isNotBlank() },
                                sourceLabel = labels[calendarId] ?: "캘린더"
                            )
                        )
                    }
                }
            } ?: emptyList()
        } catch (e: SecurityException) {
            emptyList()
        } catch (e: Exception) {
            Log.w("DDAY_WIDGET", "캘린더 일정 읽기 실패", e)
            emptyList()
        }
    }

    /** 설정에서 고른 캘린더의 일정 (연동이 꺼졌거나 프리미엄이 아니면 빈 목록) */
    fun enabledEvents(context: Context, fromMs: Long, toMs: Long): List<PhoneEvent> {
        if (!isActive(context)) return emptyList()
        return events(context, fromMs, toMs, selectedCalendarIds(context))
    }

    /** 연동 켜짐 + 권한 있음 + 프리미엄 */
    fun isActive(context: Context): Boolean =
        DdaySettings.isPhoneCalendarEnabled(context) && hasPermission(context) && Premium.isUnlocked(context)

    /** 고른 캘린더. 따로 고른 적 없으면 보이는 캘린더 전부 */
    fun selectedCalendarIds(context: Context): Set<Long> {
        val all = calendars(context).map { it.id }.toSet()
        val saved = DdaySettings.getPhoneCalendarIds(context) ?: return all
        return saved intersect all
    }
}
