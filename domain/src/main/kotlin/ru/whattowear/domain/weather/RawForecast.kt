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
    /** DWD ICON: в Европе и европейской части России — ICON-EU (7 км) и ICON-D2, дальше — глобальная (11 км). */
    ICON("icon_seamless", "ICON", 0.25, 0.15),
    /** ECMWF IFS HRES 9 км (открытые данные с октября 2025 г.). */
    ECMWF("ecmwf_ifs", "ECMWF", 0.25, 0.27),
    /**
     * ECMWF AIFS Single — ИИ-модель ECMWF, по оценкам ECMWF точнее IFS по температуре у земли и ветру,
     * но шаг у неё 6 часов (часы между ними Open-Meteo интерполирует), поэтому вес растёт к дальним дням.
     */
    AIFS("ecmwf_aifs025_single", "ECMWF AIFS", 0.05, 0.17),
    BEST_MATCH("best_match", "Open-Meteo", 0.15, 0.13),
    /** UK Met Office Global 10 км, до 7 дней. Независимая от ECMWF и DWD физическая модель. */
    UKMO("ukmo_seamless", "UK Met Office", 0.12, 0.10),
    GFS("gfs_seamless", "GFS", 0.06, 0.06),
    MET_NO(null, "MET Norway", 0.12, 0.12);

    /** Вес на заданном горизонте (часы от текущего момента): до 36 ч — краткосрочный, после 60 ч — долгосрочный. */
    fun weightAt(hoursAhead: Double): Double {
        val t = ((hoursAhead - 36.0) / 24.0).coerceIn(0.0, 1.0)
        return shortRangeWeight + (longRangeWeight - shortRangeWeight) * t
    }

    companion object {
        val openMeteoModels = listOf(ICON, ECMWF, AIFS, BEST_MATCH, UKMO, GFS)
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
