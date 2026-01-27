package com.silverwest.dayli.ddaywidget

import android.content.Intent
import android.widget.RemoteViewsService

class DdayWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val appWidgetId = intent.getIntExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        val mode = intent.getStringExtra(DdayOnlyWidgetProvider.EXTRA_WIDGET_MODE) ?: "null"
        val factory = RemoteViewsFactory(applicationContext, intent)
        android.util.Log.d("WIDGET_FACTORY", "onGetViewFactory: ${factory::class.java.simpleName}, appWidgetId=$appWidgetId, mode=$mode, data=${intent.data}")
        return factory
    }
}

