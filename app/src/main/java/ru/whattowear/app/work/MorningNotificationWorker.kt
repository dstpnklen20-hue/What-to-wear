package ru.whattowear.app.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import ru.whattowear.app.notification.Notifications
import ru.whattowear.domain.repository.SettingsRepository
import ru.whattowear.domain.usecase.GetCurrentRecommendationUseCase
import ru.whattowear.domain.usecase.ObserveSelectedLocationUseCase
import ru.whattowear.domain.usecase.RefreshForecastUseCase

/** Утреннее уведомление: свежий прогноз (если есть сеть), подбор комплекта, показ и планирование следующего. */
@HiltWorker
class MorningNotificationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val settings: SettingsRepository,
    private val observeSelected: ObserveSelectedLocationUseCase,
    private val refreshForecast: RefreshForecastUseCase,
    private val getRecommendation: GetCurrentRecommendationUseCase,
    private val notifications: Notifications,
    private val scheduler: WorkScheduler,
    private val widgetUpdater: WidgetUpdater,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val s = settings.settings.first()
        try {
            if (s.notificationsEnabled) {
                // Без сети покажем прогноз из кеша — это лучше, чем ничего
                observeSelected.current()?.let { location ->
                    withTimeoutOrNull(25_000) { refreshForecast(location, maxAgeMillis = 20 * 60 * 1000L) }
                }
                getRecommendation()?.let { notifications.showMorning(it) }
                widgetUpdater.updateAll()
            }
        } finally {
            // Настройки могли поменяться, пока воркер работал
            val fresh = settings.settings.first()
            if (fresh.notificationsEnabled) scheduler.scheduleMorningNotification(true, fresh.notificationTime, fromWorker = true)
        }
        return Result.success()
    }
}
