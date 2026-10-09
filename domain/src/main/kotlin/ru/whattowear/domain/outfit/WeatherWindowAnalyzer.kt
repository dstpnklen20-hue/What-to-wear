package ru.whattowear.domain.outfit

import ru.whattowear.domain.model.HourlyWeather
import ru.whattowear.domain.model.PrecipType
import ru.whattowear.domain.weather.DaySummary
import ru.whattowear.domain.weather.IceRiskDetector
import ru.whattowear.domain.weather.UncertaintyDetector
import java.time.ZoneId

/** Сводит почасовой прогноз за время вне дома в условия для подбора одежды. */
object WeatherWindowAnalyzer {

    /** null — прогноз не покрывает окно. */
    fun analyze(
        allHours: List<HourlyWeather>,
        window: OutingWindow,
        personalOffset: Double,
        zone: ZoneId,
    ): WindowConditions? {
        val hours = window.hoursOf(allHours).sortedBy { it.time }
        if (hours.isEmpty()) return null

        // Положительная поправка = человеку холоднее, значит погода для него «ощущается» ниже
        val effective = hours.map { it.time to it.apparentTemperature - personalOffset }
        val minEntry = effective.minBy { it.second }
        val maxEntry = effective.maxBy { it.second }

        val wet = hours.filter { DaySummary.isWet(it) }
        val rain = wet.any { it.precipType == PrecipType.RAIN || it.precipType == PrecipType.SLEET || it.precipType == PrecipType.FREEZING }
        val snow = wet.any { it.precipType == PrecipType.SNOW || it.precipType == PrecipType.SLEET }
        val daylight = hours.filter { it.isDay }

        return WindowConditions(
            window = window,
            feelsMin = hours.minOf { it.apparentTemperature },
            feelsMax = hours.maxOf { it.apparentTemperature },
            effMin = minEntry.second,
            effMax = maxEntry.second,
            effMinTime = minEntry.first,
            effMaxTime = maxEntry.first,
            effective = effective,
            rain = rain,
            snow = snow,
            precipPhrase = DaySummary.precipitationPhrase(hours, zone),
            maxWind = hours.maxOf { it.windSpeed },
            maxGust = hours.maxOf { it.windGust },
            uvMax = daylight.mapNotNull { it.uvIndex }.maxOrNull(),
            iceRisk = IceRiskDetector.hasIceRisk(allHours, window.start, window.end),
            uncertain = UncertaintyDetector.evaluate(hours).any,
            personalOffset = personalOffset,
        )
    }
}
