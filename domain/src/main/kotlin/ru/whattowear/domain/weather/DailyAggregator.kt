package ru.whattowear.domain.weather

import ru.whattowear.domain.model.DailyWeather
import ru.whattowear.domain.model.HourlyWeather
import ru.whattowear.domain.model.PrecipType
import ru.whattowear.domain.model.WeatherKind
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/** Дневные сводки из уже усреднённых часов — поэтому они всегда согласованы с почасовой лентой. */
object DailyAggregator {

    /** Минимум часов, чтобы день считался полным (иначе мин/макс неполные). */
    private const val MIN_HOURS = 18

    fun aggregate(
        hours: List<HourlyWeather>,
        zone: ZoneId,
        sunTimes: Map<LocalDate, SunTimes>,
        fromDate: LocalDate,
    ): List<DailyWeather> = hours
        .groupBy { Instant.ofEpochSecond(it.time).atZone(zone).toLocalDate() }
        .filterKeys { it >= fromDate }
        .filterValues { it.size >= MIN_HOURS }
        .toSortedMap()
        .map { (date, dayHours) -> day(date, dayHours, zone, sunTimes[date]) }

    private fun day(date: LocalDate, hours: List<HourlyWeather>, zone: ZoneId, sun: SunTimes?): DailyWeather {
        // Сводка и неуверенность — по светлой части дня, когда человек обычно на улице
        val daytime = hours.filter { Instant.ofEpochSecond(it.time).atZone(zone).hour in 8..21 }.ifEmpty { hours }
        val precipSum = hours.sumOf { it.precipitationMm }
        val wetByType = hours.filter { it.precipType != PrecipType.NONE }
            .groupBy { it.precipType }
            .mapValues { (_, list) -> list.sumOf { it.precipitationMm } }
        val dominantType = wetByType.maxByOrNull { it.value }?.key ?: PrecipType.NONE

        return DailyWeather(
            date = date,
            tempMin = hours.minOf { it.temperature },
            tempMax = hours.maxOf { it.temperature },
            feelsMin = hours.minOf { it.apparentTemperature },
            feelsMax = hours.maxOf { it.apparentTemperature },
            precipSumMm = (precipSum * 10).roundToInt() / 10.0,
            precipProbMax = hours.maxOf { it.precipProbability },
            precipType = if (precipSum >= 0.2) dominantType else PrecipType.NONE,
            windMax = hours.maxOf { it.windSpeed },
            gustMax = hours.maxOf { it.windGust },
            uvMax = hours.mapNotNull { it.uvIndex }.maxOrNull(),
            sunrise = sun?.sunrise,
            sunset = sun?.sunset,
            kind = dayKind(daytime, precipSum, dominantType),
            isUncertain = UncertaintyDetector.evaluate(daytime).any,
        )
    }

    private fun dayKind(daytime: List<HourlyWeather>, precipSum: Double, type: PrecipType): WeatherKind {
        if (daytime.count { it.kind == WeatherKind.THUNDER } >= 1) return WeatherKind.THUNDER
        if (precipSum >= 1.0) {
            return when (type) {
                PrecipType.SNOW -> WeatherKind.SNOW
                PrecipType.SLEET -> WeatherKind.SLEET
                PrecipType.FREEZING -> WeatherKind.FREEZING_RAIN
                else -> WeatherKind.RAIN
            }
        }
        if (precipSum >= 0.3) return if (type == PrecipType.SNOW) WeatherKind.SNOW else WeatherKind.DRIZZLE
        val sky = daytime.map {
            when (it.kind) {
                WeatherKind.CLEAR, WeatherKind.PARTLY_CLOUDY, WeatherKind.FOG -> it.kind
                else -> WeatherKind.CLOUDY
            }
        }
        return sky.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: WeatherKind.CLOUDY
    }
}
