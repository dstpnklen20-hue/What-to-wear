package ru.whattowear.app.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkScheduler @Inject constructor(@ApplicationContext private val context: Context) {

    private val workManager get() = WorkManager.getInstance(context)

    /** Периодическое обновление прогноза: каждые 2 часа (система может сдвинуть в пределах 30 минут). */
    fun schedulePeriodicRefresh() {
        val request = PeriodicWorkRequestBuilder<ForecastRefreshWorker>(2, TimeUnit.HOURS, 30, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniquePeriodicWork(REFRESH_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /**
     * Утреннее уведомление: разовая задача на ближайшее заданное время; после показа воркер сам
     * планирует следующую. Так точнее, чем периодическая задача (у неё плавающее начало).
     */
    fun scheduleMorningNotification(enabled: Boolean, time: LocalTime, fromWorker: Boolean = false) {
        if (!enabled) {
            workManager.cancelUniqueWork(MORNING_WORK)
            return
        }
        val now = ZonedDateTime.now()
        var next = now.with(time).withSecond(0).withNano(0)
        if (!next.isAfter(now.plusMinutes(1))) next = next.plusDays(1)
        val delay = Duration.between(now, next).toMillis()
        val request = OneTimeWorkRequestBuilder<MorningNotificationWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        // Из самого воркера — дописываем следом, чтобы не отменить текущий запуск; из настроек — заменяем
        val policy = if (fromWorker) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE
        workManager.enqueueUniqueWork(MORNING_WORK, policy, request)
    }

    companion object {
        const val REFRESH_WORK = "forecast-refresh"
        const val MORNING_WORK = "morning-notification"
    }
}
