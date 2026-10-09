package ru.whattowear.domain.weather

import java.time.LocalDate
import java.time.ZoneId

/**
 * Модели прогноза и их веса. Вес плавно меняется с горизонтом: в первые двое суток
 * сильнее региональные модели (ICON с вложенными ICON-D2/EU, best_match), дальше — ECMWF.
 * MET Norway участвует как дополнительная модель для сверки (или как единственный источник при сбое Open-Meteo).
 */
enum class WeatherModel(
    val apiId: String?,
    val label: String,
    private val shortRangeWeight: Double,
    private val longRangeWeight: Double,
) {
    ICON("icon_seamless", "ICON", 0.30, 0.20),
    ECMWF("ecmwf_ifs", "ECMWF", 0.25, 0.35),
    BEST_MATCH("best_match", "Open-Meteo", 0.20, 0.20),
    GFS("gfs_seamless", "GFS", 0.10, 0.10),
    MET_NO(null, "MET Norway", 0.15, 0.15);

    /** Вес на заданном горизонте (часы от текущего момента): до 36 ч — краткосрочный, после 60 ч — долгосрочный. */
    fun weightAt(hoursAhead: Double): Double {
        val t = ((hoursAhead - 36.0) / 24.0).coerceIn(0.0, 1.0)
        return shortRangeWeight + (longRangeWeight - shortRangeWeight) * t
    }

    companion object {
        val openMeteoModels = listOf(ICON, ECMWF, BEST_MATCH, GFS)
    }
}

/** Один час одной модели. Любое поле может отсутствовать (не все модели считают все параметры). */
data class ModelHour(
    val time: Long,
    val temperature: Double? = null,
    val apparentTemperature: Double? = null,
    val humidity: Double? = null,
    val cloudCover: Double? = null,
    val visibility: Double? = null,
    val precipProbability: Double? = null,
    val precipitation: Double? = null,
    val rain: Double? = null,
    val snowfall: Double? = null,
    val weatherCode: Int? = null,
    val windSpeed: Double? = null,
    val windGust: Double? = null,
    val windDirection: Double? = null,
    val uvIndex: Double? = null,
    val isDay: Boolean? = null,
)

data class ModelSeries(val model: WeatherModel, val hours: List<ModelHour>)

data class SunTimes(val sunrise: Long?, val sunset: Long?)

/** Сырые данные всех моделей до усреднения. */
data class RawForecast(
    val zone: ZoneId,
    val series: List<ModelSeries>,
    val sunTimes: Map<LocalDate, SunTimes>,
    val usedFallback: Boolean,
)
