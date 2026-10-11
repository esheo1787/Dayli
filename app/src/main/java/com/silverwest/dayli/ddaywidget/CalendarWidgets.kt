package com.silverwest.dayli.ddaywidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.silverwest.dayli.MainActivity
import com.silverwest.dayli.R
import com.silverwest.dayli.ui.theme.isDarkMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * 캘린더 위젯 3종 (프리미엄): 월간 달력 / 오늘·내일 / 둘을 합친 큰 위젯.
 * Dayli D-Day + (연동을 켰으면) 폰 캘린더(구글·삼성) 일정. 30분마다, 그리고 앱에서 데이터가 바뀔 때 갱신
 */
enum class CalendarWidgetKind(
    val layout: Int, val hasGrid: Boolean, val hasAgenda: Boolean, val requestBase: Int,
    val todayRows: Int = 0, val tomorrowRows: Int = 0  // 오늘·내일 최대 줄 수 (넘치면 "+N개 더")
) {
    MONTH(R.layout.widget_calendar_month, true, false, 30000),
    AGENDA(R.layout.widget_calendar_agenda, false, true, 40000, todayRows = 4, tomorrowRows = 3),
    COMBINED(R.layout.widget_calendar_combined, true, true, 50000, todayRows = 2, tomorrowRows = 2)  // 달력과 같이 있어 줄여서
}

abstract class CalendarWidgetProviderBase(private val kind: CalendarWidgetKind) : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        CalendarWidgets.scope.launch {
            try {
                CalendarWidgets.update(context, manager, kind, appWidgetIds)
            } finally {
                pending.finish()
            }
        }
    }
}

class CalendarMonthWidgetProvider : CalendarWidgetProviderBase(CalendarWidgetKind.MONTH)
class CalendarAgendaWidgetProvider : CalendarWidgetProviderBase(CalendarWidgetKind.AGENDA)
class CalendarCombinedWidgetProvider : CalendarWidgetProviderBase(CalendarWidgetKind.COMBINED)

object CalendarWidgets {

    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val providers = listOf(
        CalendarMonthWidgetProvider::class.java to CalendarWidgetKind.MONTH,
        CalendarAgendaWidgetProvider::class.java to CalendarWidgetKind.AGENDA,
        CalendarCombinedWidgetProvider::class.java to CalendarWidgetKind.COMBINED
    )

    /** 설치된 캘린더 위젯 전부 다시 그림 (DdayWidgetProvider.refreshAllWidgets에서 호출) */
    fun refreshAll(context: Context) {
        val app = context.applicationContext
        val manager = AppWidgetManager.getInstance(app)
        val targets = providers.map { (cls, kind) -> kind to manager.getAppWidgetIds(ComponentName(app, cls)) }
            .filter { it.second.isNotEmpty() }
        if (targets.isEmpty()) return
        scope.launch {
            targets.forEach { (kind, ids) -> update(app, manager, kind, ids) }
        }
    }

    /** 홈 화면에 캘린더 위젯 추가 요청 (Android 8+ 런처의 추가 창). 지원 안 하면 안내 */
    fun requestPin(context: Context, kind: CalendarWidgetKind) {
        val manager = AppWidgetManager.getInstance(context)
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O || !manager.isRequestPinAppWidgetSupported) {
            android.widget.Toast.makeText(context, "홈 화면을 길게 눌러 위젯 메뉴에서 Dayli 캘린더 위젯을 추가해 주세요", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        val cls = providers.first { it.second == kind }.first
        manager.requestPinAppWidget(ComponentName(context, cls), null, null)
    }

    fun isAnyInstalled(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        return providers.any { (cls, _) -> manager.getAppWidgetIds(ComponentName(context, cls)).isNotEmpty() }
    }

    internal suspend fun update(context: Context, manager: AppWidgetManager, kind: CalendarWidgetKind, ids: IntArray) {
        val model = if (Premium.isUnlocked(context)) loadModel(context) else null
        ids.forEach { id ->
            try {
                manager.updateAppWidget(id, render(context, kind, model, id))
            } catch (e: Exception) {
                Log.w("DDAY_WIDGET", "캘린더 위젯 갱신 실패", e)
            }
        }
    }

    private suspend fun loadModel(context: Context): CalendarWidgetModel {
        val now = Calendar.getInstance()
        val dao = DdayDatabase.getDatabase(context).ddayDao()
        // 반복 일정을 완료하면 다음 회차가 숨김 상태로 저장됨 → 앱 캘린더처럼 함께 표시
        val dayli = (dao.getAllDdays() + dao.getHiddenDdays())
            .filter { !it.isChecked && it.date != null }
            .map {
                val c = Calendar.getInstance().apply { time = it.date!! }
                DayliDay(
                    day = Triple(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)),
                    title = it.title,
                    emoji = it.getEmoji(),
                    hour = it.timeHour,
                    minute = it.timeMinute
                )
            }
        // 이번 달 전체 + 내일(다음 달로 넘어가는 말일 대비)
        val start = (now.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0); add(Calendar.DAY_OF_MONTH, -1)
        }
        val end = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 34) }
        val phone = PhoneCalendar.enabledEvents(context, start.timeInMillis, end.timeInMillis)
        return CalendarWidgetModelBuilder.build(now, dayli, phone)
    }

    private fun render(context: Context, kind: CalendarWidgetKind, model: CalendarWidgetModel?, widgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, kind.layout)
        val dark = isDarkMode(context)
        val surface = WidgetStyle.surface(dark)
        val text = DayliUi.readableText(surface).toArgb()
        val sub = WidgetStyle.onSurfaceVariant(dark).toArgb()
        val accent = Color(0xFF4A7390)

        // 달력은 숫자가 작아 배경화면이 비치면 읽기 어려움 → 거의 불투명한 바탕
        WidgetStyle.paint(views, R.id.cal_widget_bg, surface, 235)

        // 누르면 앱 열기
        val launch = PendingIntent.getActivity(
            context, kind.requestBase + widgetId,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.cal_widget_root, launch)

        if (model == null) {
            views.setViewVisibility(R.id.cal_widget_content, View.GONE)
            views.setViewVisibility(R.id.cal_widget_locked, View.VISIBLE)
            views.setTextColor(R.id.cal_widget_locked, text)
            return views
        }
        views.setViewVisibility(R.id.cal_widget_content, View.VISIBLE)
        views.setViewVisibility(R.id.cal_widget_locked, View.GONE)

        if (kind.hasGrid) bindGrid(views, model, dark, text, sub, accent, PhoneCalendar.isActive(context))
        if (kind.hasAgenda) bindAgenda(views, model, kind, text, sub, accent)
        if (kind == CalendarWidgetKind.COMBINED) WidgetStyle.paint(views, R.id.combined_divider, Color(sub).copy(alpha = 1f), 60)
        return views
    }

    private fun bindGrid(views: RemoteViews, model: CalendarWidgetModel, dark: Boolean, text: Int, sub: Int, accent: Color, phoneActive: Boolean) {
        val sunday = (if (dark) Color(0xFFF2A49E) else Color(0xFFC2564F)).toArgb()
        val saturday = (if (dark) Color(0xFFA8C3EE) else Color(0xFF3E6FB0)).toArgb()
        views.setTextViewText(R.id.cal_title, "${model.month1}월")
        views.setTextColor(R.id.cal_title, text)
        WidgetStyle.paint(views, R.id.cal_legend_dayli_dot, accent)
        views.setTextColor(R.id.cal_legend_dayli, sub)
        val phoneLegend = if (phoneActive) View.VISIBLE else View.GONE
        views.setViewVisibility(R.id.cal_legend_phone_dot, phoneLegend)
        views.setViewVisibility(R.id.cal_legend_phone, phoneLegend)
        WidgetStyle.paint(views, R.id.cal_legend_phone_dot, Color(0xFF3F7FD9))
        views.setTextColor(R.id.cal_legend_phone, sub)
        CalendarWidgetIds.weekdays.forEachIndexed { col, id ->
            views.setTextColor(id, when (col) { 0 -> sunday; 6 -> saturday; else -> sub })
        }
        CalendarWidgetIds.rows.forEachIndexed { r, id ->
            views.setViewVisibility(id, if (r < model.rowsUsed) View.VISIBLE else View.GONE)
        }
        model.cells.forEachIndexed { i, cell ->
            val num = CalendarWidgetIds.nums[i]
            views.setTextViewText(num, cell.day?.toString() ?: "")
            views.setTextColor(
                num,
                when {
                    cell.isToday -> android.graphics.Color.WHITE
                    cell.col == 0 -> sunday
                    cell.col == 6 -> saturday
                    else -> text
                }
            )
            if (cell.isToday) WidgetStyle.paint(views, CalendarWidgetIds.todayBgs[i], accent)
            else views.setViewVisibility(CalendarWidgetIds.todayBgs[i], View.INVISIBLE)
            val dotColors = (if (cell.dayliDot) listOf(accent.toArgb()) else emptyList()) + cell.phoneColors
            CalendarWidgetIds.dots[i].forEachIndexed { k, dotId ->
                val c = dotColors.getOrNull(k)
                if (c != null) WidgetStyle.paint(views, dotId, Color(c)) else views.setViewVisibility(dotId, View.GONE)
            }
        }
    }

    private fun bindAgenda(views: RemoteViews, model: CalendarWidgetModel, kind: CalendarWidgetKind, text: Int, sub: Int, accent: Color) {
        fun dateText(d: Triple<Int, Int, Int>): String {
            val c = Calendar.getInstance().apply { clear(); set(d.first, d.second - 1, d.third) }
            val dow = "일월화수목금토"[c.get(Calendar.DAY_OF_WEEK) - 1]
            return "${d.second}월 ${d.third}일 ($dow)"
        }

        fun bindDay(label: Int, date: Int, allRows: Array<AgendaRowIds>, maxRows: Int, empty: Int, more: Int, name: String, day: Triple<Int, Int, Int>, entries: List<AgendaEntry>) {
            val rows = allRows.take(maxRows)
            allRows.drop(maxRows).forEach { views.setViewVisibility(it.row, View.GONE) }
            views.setTextViewText(label, name)
            views.setTextColor(label, text)
            views.setTextViewText(date, dateText(day))
            views.setTextColor(date, sub)
            rows.forEachIndexed { i, ids ->
                val e = entries.getOrNull(i)
                if (e == null) {
                    views.setViewVisibility(ids.row, View.GONE)
                } else {
                    views.setViewVisibility(ids.row, View.VISIBLE)
                    WidgetStyle.paint(views, ids.dot, e.color?.let { Color(it) } ?: accent)
                    views.setTextViewText(ids.time, e.time)
                    views.setTextColor(ids.time, sub)
                    views.setTextViewText(ids.title, e.title)
                    views.setTextColor(ids.title, text)
                }
            }
            views.setViewVisibility(empty, if (entries.isEmpty()) View.VISIBLE else View.GONE)
            views.setTextColor(empty, sub)
            val hidden = entries.size - rows.size
            views.setViewVisibility(more, if (hidden > 0) View.VISIBLE else View.GONE)
            views.setTextViewText(more, "+${hidden}개 더")
            views.setTextColor(more, sub)
        }
        bindDay(R.id.today_label, R.id.today_date, CalendarWidgetIds.todayRows, kind.todayRows, R.id.today_empty, R.id.today_more, "오늘", model.today, model.todayEntries)
        bindDay(R.id.tomorrow_label, R.id.tomorrow_date, CalendarWidgetIds.tomorrowRows, kind.tomorrowRows, R.id.tomorrow_empty, R.id.tomorrow_more, "내일", model.tomorrow, model.tomorrowEntries)
        WidgetStyle.paint(views, R.id.agenda_divider, Color(sub).copy(alpha = 1f), 60)
    }
}
