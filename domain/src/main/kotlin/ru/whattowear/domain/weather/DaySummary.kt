package ru.whattowear.domain.weather

import ru.whattowear.domain.model.HourlyWeather
import ru.whattowear.domain.model.PrecipType
import ru.whattowear.domain.model.WeatherKind
import ru.whattowear.domain.text.Fmt
import java.time.ZoneId

/** Короткая текстовая сводка по набору часов: небо, температура, осадки, ветер. */
object DaySummary {

    fun summarize(hours: List<HourlyWeather>, zone: ZoneId): String? {
        if (hours.isEmpty()) return null
        val parts = mutableListOf<String>()

        val sky = dominantSky(hours)
        val maxT = hours.maxBy { it.temperature }
        val minT = hours.minBy { it.temperature }
        val first = hours.first().temperature
        parts += when {
            maxT.temperature - first >= 3 -> "$sky, к ${Fmt.hour(maxT.time, zone)} до ${Fmt.temp(maxT.temperature)}"
            first - minT.temperature >= 3 -> "$sky, к ${Fmt.hour(minT.time, zone)} похолодает до ${Fmt.temp(minT.temperature)}"
            else -> "$sky, ${Fmt.temp(minT.temperature)}…${Fmt.temp(maxT.temperature)}"
        }

        precipitationPhrase(hours, zone)?.let { parts += it }

        val gust = hours.maxOf { it.windGust }
        if (gust >= 10) parts += "порывы ветра до ${Fmt.number(gust)} м/с"

        val uv = hours.mapNotNull { it.uvIndex }.maxOrNull()
        if (uv != null && uv >= 6) parts += "высокий УФ-индекс (${Fmt.number(uv)})"

        return parts.joinToString(". ") { it.replaceFirstChar(Char::uppercase) } + "."
    }

    /** «Дождь с 15:00 до 18:00, до 70 %» — по часам, где осадки вероятны. */
    fun precipitationPhrase(hours: List<HourlyWeather>, zone: ZoneId): String? {
        val wet = hours.filter { isWet(it) }
        if (wet.isEmpty()) return null
        val type = wet.groupBy { it.precipType }.maxBy { (_, list) -> list.sumOf { it.precipitationMm } }.key
        val name = when (type) {
            PrecipType.SNOW -> "снег"
            PrecipType.SLEET -> "мокрый снег"
            PrecipType.FREEZING -> "ледяной дождь"
            else -> if (wet.all { it.kind == WeatherKind.DRIZZLE }) "морось" else "дождь"
        }
        val start = wet.first().time
        val end = wet.last().time + 3600
        val probability = wet.maxOf { it.precipProbability }
        val span = when {
            start <= hours.first().time && end >= hours.last().time + 3600 -> "весь день"
            start <= hours.first().time -> "до ${Fmt.hour(end, zone)}"
            end >= hours.last().time + 3600 -> "с ${Fmt.hour(start, zone)}"
            else -> "с ${Fmt.hour(start, zone)} до ${Fmt.hour(end, zone)}"
        }
        return "$name $span, вероятность до $probability %"
    }

    /** Осадки в этот час стоит учитывать. */
    fun isWet(h: HourlyWeather): Boolean =
        h.precipType != PrecipType.NONE && (h.precipitationMm >= 0.5 || (h.precipitationMm >= 0.1 && h.precipProbability >= 40))

    private fun dominantSky(hours: List<HourlyWeather>): String {
        if (hours.any { it.kind == WeatherKind.THUNDER }) return "возможна гроза"
        val counts = hours.groupingBy {
            when (it.kind) {
                WeatherKind.CLEAR -> WeatherKind.CLEAR
                WeatherKind.PARTLY_CLOUDY -> WeatherKind.PARTLY_CLOUDY
                WeatherKind.FOG -> WeatherKind.FOG
                else -> WeatherKind.CLOUDY
            }
        }.eachCount()
        return when (counts.maxBy { it.value }.key) {
            WeatherKind.CLEAR -> "ясно"
            WeatherKind.PARTLY_CLOUDY -> "переменная облачность"
            WeatherKind.FOG -> "туман"
            else -> "пасмурно"
        }
    }
}
