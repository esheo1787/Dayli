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
 * D-Day 전용 위젯 Provider
 * - D-Day 항목만 표시 (To-Do 제외)
 * - 기존 DdayWidgetService/RemoteViewsFactory 재사용
 * - MODE_DDAY를 Intent로 전달하여 Factory에서 필터링
 */
class DdayOnlyWidgetProvider : AppWidgetProvider() {

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
        // Widget mode constants (shared with RemoteViewsFactory)
        const val EXTRA_WIDGET_MODE = "widget_mode"
        const val MODE_ALL = "ALL"
        const val MODE_DDAY = "DDAY"
        const val MODE_TODO = "TODO"  // Step 8C에서 사용

        private fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            // RemoteViewsService Intent with MODE_DDAY
            val intent = Intent(context, DdayWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtra(EXTRA_WIDGET_MODE, MODE_DDAY)
                // Unique URI to prevent cache collision (include mode)
                data = Uri.parse("dayli://widget/$appWidgetId?mode=$MODE_DDAY")
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
                    appWidgetId + 10000,  // Offset to avoid collision with main widget
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
