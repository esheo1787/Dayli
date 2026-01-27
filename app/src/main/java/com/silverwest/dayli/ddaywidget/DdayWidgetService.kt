package com.silverwest.dayli.ddaywidget

import android.content.Intent
import android.widget.RemoteViewsService

class DdayWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        android.util.Log.d("WIDGET_PIPE", "onGetViewFactory")
        return RemoteViewsFactory(applicationContext, intent)
    }
}

