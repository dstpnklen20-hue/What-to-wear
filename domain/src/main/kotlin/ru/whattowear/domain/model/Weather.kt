package ru.whattowear.domain.model

import java.time.LocalDate
import java.time.ZoneId

/** Тип осадков за час. */
enum class PrecipType { NONE, RAIN, SNOW, SLEET, FREEZING }

/** Укрупнённое состояние погоды — определяет иконку и формулировку сводки. */
enum class WeatherKind { CLEAR, PARTLY_CLOUDY, CLOUDY, FOG, DRIZZLE, RAIN, SLEET, SNOW, FREEZING_RAIN, THUNDER }

/**
 * Итоговый (усреднённый по моделям) прогноз на один час.
 * Время — начало часа в секундах эпохи (UTC). Все величины метрические: °C, м/с, мм, см.
 */
data class HourlyWeather(
    val time: Long,
    val temperature: Double,
    val apparentTemperature: Double,
    val humidity: Int?,
    val cloudCover: Int?,
    val visibilityM: Double?,
    /** Вероятность осадков, 0..100. */
    val precipProbability: Int,
    val precipitationMm: Double,
    val rainMm: Double,
    val snowfallCm: Double,
    val precipType: PrecipType,
    val windSpeed: Double,
    val windGust: Double,
    val windDirection: Int?,
    val uvIndex: Double?,
    val kind: WeatherKind,
    val isDay: Boolean,
    /** Разброс температуры между моделями (макс − мин), °C. */
    val tempSpread: Double,
    /** Разброс количества осадков между моделями, мм. */
    val precipSpread: Double,
    /** Взвешенная доля моделей, дающих осадки ≥ 0.1 мм, 0..1. */
    val wetShare: Double,
    val modelCount: Int,
)

/** Сводка дня, собранная из усреднённых часов. */
data class DailyWeather(
    val date: LocalDate,
    val tempMin: Double,
    val tempMax: Double,
    val feelsMin: Double,
    val feelsMax: Double,
    val precipSumMm: Double,
    val precipProbMax: Int,
    val precipType: PrecipType,
    val windMax: Double,
    val gustMax: Double,
    val uvMax: Double?,
    val sunrise: Long?,
    val sunset: Long?,
    val kind: WeatherKind,
    val isUncertain: Boolean,
)

data class Forecast(
    val locationId: Long,
    val zone: ZoneId,
    /** Момент загрузки, мс. */
    val fetchedAt: Long,
    /** Какие источники дали данные, например ["ECMWF", "ICON", "GFS", "Open-Meteo", "MET Norway"]. */
    val sources: List<String>,
    /** true — Open-Meteo был недоступен и прогноз построен только по MET Norway. */
    val usedFallback: Boolean,
    val hours: List<HourlyWeather>,
    val days: List<DailyWeather>,
)
