package ru.whattowear.domain.weather

import ru.whattowear.domain.model.HourlyWeather
import ru.whattowear.domain.model.PrecipType
import ru.whattowear.domain.model.WeatherKind
import java.time.Instant
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Усреднение почасового прогноза нескольких моделей.
 *
 * Каждый параметр часа — взвешенное среднее по моделям, у которых он есть; вес отсутствующей
 * модели перераспределяется на остальные. Вероятность осадков = половина из API (где модель её даёт)
 * + половина из доли моделей, у которых осадки есть. Тип осадков и состояние неба выбираются
 * взвешенным голосованием и согласуются с итоговым количеством осадков.
 */
object ModelBlender {

    /** Порог «есть осадки» для одной модели, мм/ч. */
    const val WET_THRESHOLD_MM = 0.1

    /** Снег в см → вода в мм (Open-Meteo: 1 мм воды ≈ 0.7 см снега). */
    private const val SNOW_CM_PER_MM = 0.7

    fun blend(raw: RawForecast, nowEpochSec: Long): List<HourlyWeather> {
        val byTime = sortedMapOf<Long, MutableList<Pair<WeatherModel, ModelHour>>>()
        raw.series.forEach { series ->
            series.hours.forEach { h -> byTime.getOrPut(h.time) { mutableListOf() } += series.model to h }
        }
        return byTime.mapNotNull { (time, entries) -> blendHour(time, entries, nowEpochSec, raw) }
    }

    private class Weighted(val hour: ModelHour, val weight: Double)

    private fun blendHour(
        time: Long,
        entries: List<Pair<WeatherModel, ModelHour>>,
        now: Long,
        raw: RawForecast,
    ): HourlyWeather? {
        val hoursAhead = ((time - now) / 3600.0).coerceAtLeast(0.0)
        val items = entries
            .filter { it.second.temperature != null }
            .map { (model, h) -> Weighted(h, model.weightAt(hoursAhead)) }
        if (items.isEmpty()) return null

        val temperature = items.avg { it.temperature }!!
        val apparent = items.avg { it.apparentTemperature } ?: temperature

        // Осадки
        val precipItems = items.filter { it.hour.precipitation != null }
        val precipitation = precipItems.avg { it.precipitation } ?: 0.0
        val wetShare = if (precipItems.isEmpty()) 0.0 else
            precipItems.filter { it.hour.precipitation!! >= WET_THRESHOLD_MM }.sumOf { it.weight } / precipItems.sumOf { it.weight }
        val apiProbability = items.avg { it.precipProbability }
        val probability = (if (apiProbability != null) 0.5 * apiProbability + 0.5 * wetShare * 100 else wetShare * 100)
            .roundToInt().coerceIn(0, 100)
        val snowfall = items.avg { it.snowfall } ?: 0.0
        val snowWater = snowfall / SNOW_CM_PER_MM
        val rain = items.avg { it.rain } ?: (precipitation - snowWater).coerceAtLeast(0.0)

        val codeVotes = vote(items)
        val precipType = precipType(precipitation, snowWater, codeVotes)

        val cloudCover = items.avg { it.cloudCover }
        val kind = kind(precipitation, precipType, codeVotes, cloudCover)

        val temps = items.map { it.hour.temperature!! }
        val precips = precipItems.map { it.hour.precipitation!! }

        val windSpeed = items.avg { it.windSpeed } ?: 0.0
        val windGust = (items.avg { it.windGust } ?: (windSpeed * 1.5)).coerceAtLeast(windSpeed)

        return HourlyWeather(
            time = time,
            temperature = temperature.round1(),
            apparentTemperature = apparent.round1(),
            humidity = items.avg { it.humidity }?.roundToInt(),
            cloudCover = cloudCover?.roundToInt(),
            visibilityM = items.avg { it.visibility },
            precipProbability = probability,
            precipitationMm = precipitation.round2(),
            rainMm = rain.round2(),
            snowfallCm = snowfall.round2(),
            precipType = precipType,
            windSpeed = windSpeed.round1(),
            windGust = windGust.round1(),
            windDirection = circularMean(items),
            uvIndex = items.avg { it.uvIndex }?.round1(),
            kind = kind,
            isDay = isDay(time, items, raw),
            tempSpread = (temps.max() - temps.min()).round1(),
            precipSpread = if (precips.isEmpty()) 0.0 else (precips.max() - precips.min()).round2(),
            wetShare = wetShare,
            modelCount = items.size,
        )
    }

    /** Взвешенное среднее по моделям, где значение есть; null — если его нет ни у одной. */
    private inline fun List<Weighted>.avg(selector: (ModelHour) -> Double?): Double? {
        var sum = 0.0
        var weights = 0.0
        for (w in this) {
            val v = selector(w.hour) ?: continue
            sum += v * w.weight
            weights += w.weight
        }
        return if (weights > 0) sum / weights else null
    }

    /** Доля веса моделей за каждую группу кода погоды. */
    private fun vote(items: List<Weighted>): Map<CodeGroup, Double> {
        val withCode = items.filter { it.hour.weatherCode != null }
        val total = withCode.sumOf { it.weight }
        if (total <= 0) return emptyMap()
        return withCode.groupBy { WeatherCodes.group(it.hour.weatherCode!!) }
            .mapValues { (_, list) -> list.sumOf { it.weight } / total }
    }

    private fun precipType(precipitation: Double, snowWater: Double, votes: Map<CodeGroup, Double>): PrecipType {
        if (precipitation < 0.05) return PrecipType.NONE
        if ((votes[CodeGroup.FREEZING] ?: 0.0) >= 0.3) return PrecipType.FREEZING
        val snowRatio = (snowWater / precipitation).coerceIn(0.0, 1.0)
        return when {
            snowRatio >= 0.75 -> PrecipType.SNOW
            snowRatio >= 0.25 -> PrecipType.SLEET
            else -> PrecipType.RAIN
        }
    }

    private fun kind(
        precipitation: Double,
        type: PrecipType,
        votes: Map<CodeGroup, Double>,
        cloudCover: Double?,
    ): WeatherKind {
        val thunder = votes[CodeGroup.THUNDER] ?: 0.0
        if (thunder >= 0.5 || (thunder >= 0.3 && precipitation >= WET_THRESHOLD_MM)) return WeatherKind.THUNDER
        if (precipitation >= WET_THRESHOLD_MM) {
            return when (type) {
                PrecipType.SNOW -> WeatherKind.SNOW
                PrecipType.SLEET -> WeatherKind.SLEET
                PrecipType.FREEZING -> WeatherKind.FREEZING_RAIN
                else -> if (precipitation < 0.5 && (votes[CodeGroup.DRIZZLE] ?: 0.0) >= 0.5) WeatherKind.DRIZZLE else WeatherKind.RAIN
            }
        }
        if ((votes[CodeGroup.FOG] ?: 0.0) >= 0.5) return WeatherKind.FOG
        if (cloudCover != null) {
            return when {
                cloudCover < 20 -> WeatherKind.CLEAR
                cloudCover < 70 -> WeatherKind.PARTLY_CLOUDY
                else -> WeatherKind.CLOUDY
            }
        }
        val sky = listOf(CodeGroup.CLEAR, CodeGroup.PARTLY, CodeGroup.CLOUDY).maxByOrNull { votes[it] ?: 0.0 }
        return when (sky) {
            CodeGroup.CLEAR -> WeatherKind.CLEAR
            CodeGroup.PARTLY -> WeatherKind.PARTLY_CLOUDY
            else -> WeatherKind.CLOUDY
        }
    }

    private fun circularMean(items: List<Weighted>): Int? {
        var x = 0.0
        var y = 0.0
        var any = false
        for (w in items) {
            val d = w.hour.windDirection ?: continue
            val r = Math.toRadians(d)
            x += cos(r) * w.weight
            y += sin(r) * w.weight
            any = true
        }
        if (!any) return null
        return ((Math.toDegrees(atan2(y, x)) + 360) % 360).roundToInt()
    }

    private fun isDay(time: Long, items: List<Weighted>, raw: RawForecast): Boolean {
        // Середина часа между восходом и закатом — по данным API или по расчёту
        val mid = time + 1800
        val date = Instant.ofEpochSecond(time).atZone(raw.zone).toLocalDate()
        val sun = raw.sunTimes[date]
        if (sun?.sunrise != null && sun.sunset != null) return mid in sun.sunrise until sun.sunset
        val votes = items.mapNotNull { it.hour.isDay }
        if (votes.isNotEmpty()) return votes.count { it } * 2 >= votes.size
        return Instant.ofEpochSecond(mid).atZone(raw.zone).hour in 7..19
    }

    private fun Double.round1() = (this * 10).roundToInt() / 10.0
    private fun Double.round2() = (this * 100).roundToInt() / 100.0
}
