package ru.whattowear.domain.usecase

import ru.whattowear.domain.model.Location
import ru.whattowear.domain.repository.RefreshResult
import ru.whattowear.domain.repository.WeatherRepository
import javax.inject.Inject

/** Обновить прогноз места, если кеш устарел (или принудительно). */
class RefreshForecastUseCase @Inject constructor(
    private val weather: WeatherRepository,
) {
    suspend operator fun invoke(
        location: Location,
        force: Boolean = false,
        maxAgeMillis: Long = DEFAULT_MAX_AGE,
        now: Long = System.currentTimeMillis(),
    ): RefreshResult {
        if (!force) {
            val cached = weather.getForecast(location.id)
            if (cached != null && now - cached.fetchedAt < maxAgeMillis) return RefreshResult.UpToDate
        }
        return weather.refresh(location)
    }

    companion object {
        /** При открытии приложения прогноз младше 30 минут не перезагружается. */
        const val DEFAULT_MAX_AGE = 30 * 60 * 1000L
    }
}
