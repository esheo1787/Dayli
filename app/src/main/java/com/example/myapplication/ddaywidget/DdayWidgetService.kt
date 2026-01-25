package com.example.myapplication.ddaywidget

import android.content.Intent
import android.widget.RemoteViewsService

class DdayWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return RemoteViewsFactory(applicationContext)
    }
}

