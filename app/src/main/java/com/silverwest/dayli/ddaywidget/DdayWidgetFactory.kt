package com.silverwest.dayli.ddaywidget

import java.util.Date
import java.util.Calendar
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.silverwest.dayli.R
import kotlinx.coroutines.runBlocking

class DdayWidgetFactory(
    private val context: Context,
    intent: Intent
) : RemoteViewsService.RemoteViewsFactory {

    private var items: List<DdayItem> = emptyList()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        val db = DdayDatabase.getDatabase(context)
        val dao = db.ddayDao()

        // 오늘 00:00:00 타임스탬프 계산
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        runBlocking {
            items = dao.getAllForWidget(todayStart)
        }
    }

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews {
        val item = items[position]
        val views = RemoteViews(context.packageName, R.layout.item_dday_widget)

        views.setTextViewText(R.id.item_title, item.title)
        views.setTextViewText(R.id.item_dday, item.date?.let { calculateDdayText(it) } ?: "")
        views.setTextViewText(R.id.item_memo, item.memo)

        return views
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = items[position].id.toLong()
    override fun hasStableIds(): Boolean = true
    override fun onDestroy() {}

    private fun calculateDdayText(date: Date): String {
        val today = java.util.Calendar.getInstance().time
        val diffMillis = date.time - today.time
        val days = (diffMillis / (1000 * 60 * 60 * 24)).toInt()

        return when {
            days > 0 -> "D-$days"
            days == 0 -> "D-DAY"
            else -> "D+${-days}"
        }
    }
}

