package ru.whattowear.app.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.whattowear.app.widget.WeatherWidget
import javax.inject.Inject
import javax.inject.Singleton

/** Перерисовка виджетов после обновления данных. */
@Singleton
class WidgetUpdater @Inject constructor(@ApplicationContext private val context: Context) {
    suspend fun updateAll() {
        runCatching { WeatherWidget().updateAll(context) }
    }
}

/** После обновления приложения Glance-виджеты могут остаться на «загрузке» — перерисовываем их. */
class AppUpdatedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                runCatching { WeatherWidget().updateAll(context.applicationContext) }
            } finally {
                pending.finish()
            }
        }
    }
}
