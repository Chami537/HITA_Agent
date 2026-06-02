package cn.limpu.hita.ui.widgets

import android.content.Context
import android.content.Intent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import cn.limpu.hita.ui.widgets.today.normal.TodayWidget
import cn.limpu.hita.ui.widgets.today.slim.TodayWidgetSlim

object WidgetUtils {
    val widgets = listOf(TodayWidget::class.java, TodayWidgetSlim::class.java)
    const val EVENT_REFRESH = "cn.limpu.hita.WIDGET_EVENT_REFRESH"

    fun hasAnyWidget(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        return widgets.any { wid ->
            manager.getAppWidgetIds(ComponentName(context, wid)).isNotEmpty()
        }
    }

    fun sendRefreshToAll(context: Context){
        val manager = AppWidgetManager.getInstance(context)
        for(wid in widgets){
            if (manager.getAppWidgetIds(ComponentName(context, wid)).isEmpty()) continue
            val btIntent = Intent().setAction(EVENT_REFRESH)
            btIntent.setClass(context, wid)
            context.sendBroadcast(btIntent)
        }
    }
}
