package ru.whattowear.app.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import ru.whattowear.domain.repository.LocationRepository
import ru.whattowear.domain.repository.RefreshResult
import ru.whattowear.domain.usecase.ObserveSelectedLocationUseCase
import ru.whattowear.domain.usecase.RefreshForecastUseCase

/**
 * Фоновое обновление прогноза (раз в ~2 часа при наличии сети): сначала выбранное место, потом остальные.
 * Геолокацию в фоне не запрашиваем — используются последние сохранённые координаты.
 */
@HiltWorker
class ForecastRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val locations: LocationRepository,
    private val observeSelected: ObserveSelectedLocationUseCase,
    private val refreshForecast: RefreshForecastUseCase,
    private val widgetUpdater: WidgetUpdater,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val selected = observeSelected.current() ?: return Result.success()
        val result = refreshForecast(selected, maxAgeMillis = MAX_AGE)
        locations.getLocations()
            .filter { it.id != selected.id }
            .take(MAX_EXTRA_LOCATIONS)
            .forEach { refreshForecast(it, maxAgeMillis = MAX_AGE) }
        widgetUpdater.updateAll()
        return if (result is RefreshResult.Failure && runAttemptCount < 2) Result.retry() else Result.success()
    }

    companion object {
        /** Прогноз младше 90 минут в фоне не перезагружаем. */
        private const val MAX_AGE = 90 * 60 * 1000L
        private const val MAX_EXTRA_LOCATIONS = 5
    }
}
