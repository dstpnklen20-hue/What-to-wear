package ru.whattowear.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

/** Добавление виджета на рабочий стол прямо из приложения (если лаунчер это поддерживает). */
object WidgetPinning {
    fun isSupported(context: Context): Boolean =
        AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported

    fun request(context: Context) {
        if (!isSupported(context)) return
        AppWidgetManager.getInstance(context)
            .requestPinAppWidget(ComponentName(context, WeatherWidgetReceiver::class.java), null, null)
    }
}
