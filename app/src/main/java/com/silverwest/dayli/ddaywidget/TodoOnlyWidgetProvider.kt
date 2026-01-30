package com.silverwest.dayli.ddaywidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.silverwest.dayli.MainActivity
import com.silverwest.dayli.R
import com.silverwest.dayli.ui.theme.isDarkMode

/**
 * To-Do 전용 위젯 Provider
 * - To-Do 항목만 표시 (D-Day 제외)
 * - 기존 DdayWidgetService/RemoteViewsFactory 재사용
 * - MODE_TODO를 Intent로 전달하여 Factory에서 필터링
 */
class TodoOnlyWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            // RemoteViewsService Intent with MODE_TODO
            val intent = Intent(context, DdayWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtra(DdayOnlyWidgetProvider.EXTRA_WIDGET_MODE, DdayOnlyWidgetProvider.MODE_TODO)
                // Unique URI to prevent cache collision (include mode)
                data = Uri.parse("dayli://widget/$appWidgetId?mode=${DdayOnlyWidgetProvider.MODE_TODO}")
            }

            // Widget background opacity
            val widgetBgOpacity = DdaySettings.getWidgetBgOpacity(context)
            val bgAlpha = (widgetBgOpacity * 2.55f).toInt().coerceIn(0, 255)

            // Soft Pastel theme background
            val isDark = isDarkMode(context)
            val baseColor = if (isDark) 0x002A2A3E else 0x00FFFDF5
            val widgetBgColor = (bgAlpha shl 24) or baseColor

            val views = RemoteViews(context.packageName, R.layout.widget_dday_scrollable).apply {
                setRemoteAdapter(R.id.widgetListView, intent)
                setEmptyView(R.id.widgetListView, R.id.emptyTextView)

                // To-Do widget empty text
                setTextViewText(R.id.emptyTextView, "등록된 To-Do 없음")

                // Widget container background
                setInt(R.id.widget_container, "setBackgroundColor", widgetBgColor)

                // Empty text color
                val emptyTextColor = if (isDark) 0x80B8B8B8.toInt() else 0x807A7A7A.toInt()
                setTextColor(R.id.emptyTextView, emptyTextColor)

                // Click template for checkbox (reuse existing action)
                val clickIntent = Intent(context, DdayWidgetProvider::class.java).apply {
                    action = DdayWidgetProvider.ACTION_CHECKBOX_CLICK
                }
                val clickPendingIntent = PendingIntent.getBroadcast(
                    context,
                    0,
                    clickIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
                setPendingIntentTemplate(R.id.widgetListView, clickPendingIntent)

                // Widget click to launch app
                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val launchPendingIntent = PendingIntent.getActivity(
                    context,
                    appWidgetId + 20000,  // Offset to avoid collision with other widgets
                    launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                setOnClickPendingIntent(R.id.widget_container, launchPendingIntent)
                setOnClickPendingIntent(R.id.emptyTextView, launchPendingIntent)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
