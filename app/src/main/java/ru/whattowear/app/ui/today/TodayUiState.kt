package ru.whattowear.app.ui.today

import ru.whattowear.domain.model.DailyWeather
import ru.whattowear.domain.model.Forecast
import ru.whattowear.domain.model.HourlyWeather
import ru.whattowear.domain.model.Location
import ru.whattowear.domain.model.UserSettings
import ru.whattowear.domain.outfit.ComfortRating
import ru.whattowear.domain.outfit.OutfitRecommendation
import ru.whattowear.domain.outfit.OutingWindow
import ru.whattowear.domain.repository.FailureReason
import ru.whattowear.domain.weather.DaySummary
import ru.whattowear.domain.weather.Uncertainty
import ru.whattowear.domain.weather.UncertaintyDetector
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class TodayUiState(
    /** false — первые данные ещё не пришли (показываем пустой экран без мигания пустых состояний). */
    val initialized: Boolean = false,
    val location: Location? = null,
    val weather: WeatherSnapshot? = null,
    val recommendation: OutfitRecommendation? = null,
    val wardrobeEmpty: Boolean = false,
    val settings: UserSettings = UserSettings(),
    val isRefreshing: Boolean = false,
    val showPullIndicator: Boolean = false,
    val error: FailureReason? = null,
    val hasLocationPermission: Boolean = false,
    val now: Long = 0,
    /** Не null — пора спросить «как вам было сегодня?» за эту дату. */
    val feedbackDate: LocalDate? = null,
    /** Отзыв за сегодня уже есть. */
    val feedbackGiven: ComfortRating? = null,
)

/** Всё, что экран «Сегодня» показывает о погоде, — посчитано заранее из кеша прогноза. */
data class WeatherSnapshot(
    val zone: ZoneId,
    val fetchedAt: Long,
    val usedFallback: Boolean,
    val sources: List<String>,
    val current: HourlyWeather,
    val today: DailyWeather?,
    /** 24 часа начиная с текущего. */
    val strip: List<HourlyWeather>,
    val summary: String?,
    val window: OutingWindow,
    val windowHours: List<HourlyWeather>,
    val windowUncertainty: Uncertainty,
    /** Прогноз старше 3 часов — показываем время обновления заметнее. */
    val isStale: Boolean,
    val forecast: Forecast,
) {
    companion object {
        fun build(forecast: Forecast, settings: UserSettings, now: Long): WeatherSnapshot? {
            val nowSec = now / 1000
            val hours = forecast.hours
            if (hours.isEmpty()) return null
            val current = hours.lastOrNull { it.time <= nowSec } ?: hours.first()
            val strip = hours.filter { it.time >= current.time }.take(24)
            val zone = forecast.zone
            val today = Instant.ofEpochSecond(current.time).atZone(zone).toLocalDate()

            // Сводка: остаток сегодняшнего дня, а поздно вечером — завтрашний день
            val restOfToday = hours.filter { it.time >= current.time && Instant.ofEpochSecond(it.time).atZone(zone).toLocalDate() == today }
            val summaryHours = if (restOfToday.size >= 4) restOfToday else hours.filter {
                Instant.ofEpochSecond(it.time).atZone(zone).toLocalDate() == today.plusDays(1) &&
                    Instant.ofEpochSecond(it.time).atZone(zone).hour in 7..22
            }

            val window = OutingWindow.resolve(settings.leaveTime, settings.returnTime, zone, now)
            val windowHours = window.hoursOf(hours)
            return WeatherSnapshot(
                zone = zone,
                fetchedAt = forecast.fetchedAt,
                usedFallback = forecast.usedFallback,
                sources = forecast.sources,
                current = current,
                today = forecast.days.firstOrNull { it.date == today },
                strip = strip,
                summary = DaySummary.summarize(summaryHours, zone),
                window = window,
                windowHours = windowHours,
                windowUncertainty = UncertaintyDetector.evaluate(windowHours),
                isStale = now - forecast.fetchedAt > 3 * 3600 * 1000L,
                forecast = forecast,
            )
        }
    }
}
