package ru.whattowear.data.remote

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import ru.whattowear.domain.weather.ModelHour
import ru.whattowear.domain.weather.ModelSeries
import ru.whattowear.domain.weather.SunTimes
import ru.whattowear.domain.weather.WeatherModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Разбор ответа Open-Meteo с несколькими моделями: каждое поле приходит с суффиксом модели,
 * например `temperature_2m_icon_seamless`. Если запрошена одна модель, суффикса нет.
 */
object OpenMeteoParser {

    val HOURLY_VARIABLES = listOf(
        "temperature_2m", "apparent_temperature", "relative_humidity_2m", "cloud_cover", "visibility",
        "precipitation_probability", "precipitation", "rain", "showers", "snowfall", "weather_code",
        "wind_speed_10m", "wind_gusts_10m", "wind_direction_10m", "uv_index", "is_day",
    )

    data class Result(val zone: ZoneId, val series: List<ModelSeries>, val sunTimes: Map<LocalDate, SunTimes>)

    fun parse(json: JsonObject, models: List<WeatherModel>): Result {
        val zone = json["timezone"]?.jsonPrimitive?.content?.let { runCatching { ZoneId.of(it) }.getOrNull() }
            ?: ZoneId.of("UTC")
        val hourly = json["hourly"] as? JsonObject ?: error("В ответе нет hourly")
        val times = (hourly["time"] as? JsonArray)?.map { it.jsonPrimitive.long() } ?: error("В ответе нет hourly.time")
        val single = models.size == 1

        val series = models.mapNotNull { model ->
            fun column(name: String): JsonArray? {
                val key = if (single) name else "${name}_${model.apiId}"
                return (hourly[key] as? JsonArray) ?: (hourly[name] as? JsonArray).takeIf { single }
            }
            val temperature = column("temperature_2m") ?: return@mapNotNull null
            val apparent = column("apparent_temperature")
            val humidity = column("relative_humidity_2m")
            val cloud = column("cloud_cover")
            val visibility = column("visibility")
            val probability = column("precipitation_probability")
            val precipitation = column("precipitation")
            val rain = column("rain")
            val showers = column("showers")
            val snowfall = column("snowfall")
            val code = column("weather_code")
            val wind = column("wind_speed_10m")
            val gust = column("wind_gusts_10m")
            val direction = column("wind_direction_10m")
            val uv = column("uv_index")
            val isDay = column("is_day")

            val hours = times.mapIndexed { i, time ->
                // Ливни у Open-Meteo отдельно от обложного дождя — для одежды это одно и то же
                val rainTotal = listOfNotNull(rain.d(i), showers.d(i)).takeIf { it.isNotEmpty() }?.sum()
                ModelHour(
                    time = time,
                    temperature = temperature.d(i),
                    apparentTemperature = apparent.d(i),
                    humidity = humidity.d(i),
                    cloudCover = cloud.d(i),
                    visibility = visibility.d(i),
                    precipProbability = probability.d(i),
                    precipitation = precipitation.d(i),
                    rain = rainTotal,
                    snowfall = snowfall.d(i),
                    weatherCode = code.d(i)?.toInt(),
                    windSpeed = wind.d(i),
                    windGust = gust.d(i),
                    windDirection = direction.d(i),
                    uvIndex = uv.d(i),
                    isDay = isDay.d(i)?.let { it >= 0.5 },
                )
            }
            if (hours.none { it.temperature != null }) null else ModelSeries(model, hours)
        }

        return Result(zone, series, parseSunTimes(json, zone))
    }

    private fun parseSunTimes(json: JsonObject, zone: ZoneId): Map<LocalDate, SunTimes> {
        val daily = json["daily"] as? JsonObject ?: return emptyMap()
        val days = (daily["time"] as? JsonArray)?.map { it.jsonPrimitive.long() } ?: return emptyMap()
        // Восход/закат одинаковы у всех моделей — берём первый найденный столбец
        val sunrise = daily.entries.firstOrNull { it.key.startsWith("sunrise") }?.value as? JsonArray
        val sunset = daily.entries.firstOrNull { it.key.startsWith("sunset") }?.value as? JsonArray
        return days.mapIndexed { i, dayStart ->
            Instant.ofEpochSecond(dayStart).atZone(zone).toLocalDate() to
                SunTimes(sunrise?.l(i), sunset?.l(i))
        }.toMap()
    }

    private fun JsonPrimitive.long(): Long = longOrNull ?: doubleOrNull?.toLong() ?: error("Неверное время: $content")
    private fun JsonArray?.d(i: Int): Double? = (this?.getOrNull(i) as? JsonPrimitive)?.doubleOrNull
    private fun JsonArray.l(i: Int): Long? = (getOrNull(i) as? JsonPrimitive)?.let { it.longOrNull ?: it.intOrNull?.toLong() }
}
