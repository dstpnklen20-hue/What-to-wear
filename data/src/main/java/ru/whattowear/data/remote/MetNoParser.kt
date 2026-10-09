package ru.whattowear.data.remote

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ru.whattowear.domain.weather.ModelHour
import ru.whattowear.domain.weather.ModelSeries
import ru.whattowear.domain.weather.WeatherCodes
import ru.whattowear.domain.weather.WeatherModel
import java.time.Instant

/**
 * Разбор MET Norway Locationforecast (complete). Первые ~60 часов идут с шагом 1 час,
 * дальше — 6 часов; такие шаги раскладываются на часы (температура — линейно, осадки — поровну),
 * чтобы MET мог заменить Open-Meteo на всю неделю.
 */
object MetNoParser {

    fun parse(json: JsonObject): ModelSeries {
        val timeseries = json["properties"]?.jsonObject?.get("timeseries") as? JsonArray ?: error("В ответе нет timeseries")
        val points = timeseries.mapNotNull { runCatching { point(it.jsonObject) }.getOrNull() }.sortedBy { it.time }

        val hours = mutableListOf<ModelHour>()
        points.forEachIndexed { index, p ->
            val next = points.getOrNull(index + 1)
            val step = if (next != null) ((next.time - p.time) / 3600).toInt() else 1
            if (step <= 1 || p.precip6h == null) {
                hours += p.toHour(p.time, p.temperature, p.precip1h, p.symbol1h ?: p.symbol6h)
            } else {
                // Шаг 6 часов: раскладываем на часы до следующей точки
                val span = step.coerceAtMost(6)
                for (k in 0 until span) {
                    val t = p.time + k * 3600L
                    val temperature = if (next?.temperature != null && p.temperature != null) {
                        p.temperature + (next.temperature - p.temperature) * k / step
                    } else p.temperature
                    hours += p.toHour(t, temperature, p.precip6h / span, p.symbol6h)
                }
            }
        }
        return ModelSeries(WeatherModel.MET_NO, hours.distinctBy { it.time })
    }

    private class Point(
        val time: Long,
        val temperature: Double?,
        val apparent: Double?,
        val humidity: Double?,
        val cloud: Double?,
        val wind: Double?,
        val gust: Double?,
        val direction: Double?,
        val uvClearSky: Double?,
        val precip1h: Double?,
        val probability1h: Double?,
        val symbol1h: String?,
        val precip6h: Double?,
        val symbol6h: String?,
    ) {
        fun toHour(t: Long, temp: Double?, precipitation: Double?, symbol: String?): ModelHour {
            val code = WeatherCodes.fromMetSymbol(symbol)
            val precip = precipitation ?: 0.0
            // Снега в MET нет отдельным полем — оцениваем по символу (1 мм воды ≈ 0.7 см снега)
            val snowShare = when {
                symbol == null -> 0.0
                "sleet" in symbol -> 0.5
                "snow" in symbol -> 1.0
                else -> 0.0
            }
            // UV у MET — для ясного неба; ослабляем облачностью, чтобы не завышать
            val uv = uvClearSky?.let { it * (1.0 - 0.6 * ((cloud ?: 0.0) / 100.0)) }
            return ModelHour(
                time = t,
                temperature = temp,
                apparentTemperature = if (t == time) apparent else null,
                humidity = humidity,
                cloudCover = cloud,
                visibility = null,
                precipProbability = if (t == time) probability1h else null,
                precipitation = precip,
                rain = precip * (1 - snowShare),
                snowfall = precip * snowShare * 0.7,
                weatherCode = code,
                windSpeed = wind,
                windGust = gust,
                windDirection = direction,
                uvIndex = uv,
                isDay = symbol?.let { if (it.endsWith("_night") || it.endsWith("_polartwilight")) false else if (it.endsWith("_day")) true else null },
            )
        }
    }

    private fun point(obj: JsonObject): Point {
        val time = Instant.parse(obj["time"]!!.jsonPrimitive.content).epochSecond
        val data = obj["data"]!!.jsonObject
        val instant = data["instant"]?.jsonObject?.get("details")?.jsonObject
        val next1 = data["next_1_hours"]?.jsonObject
        val next6 = data["next_6_hours"]?.jsonObject
        return Point(
            time = time,
            temperature = instant.num("air_temperature"),
            apparent = instant.num("apparent_air_temperature"),
            humidity = instant.num("relative_humidity"),
            cloud = instant.num("cloud_area_fraction"),
            wind = instant.num("wind_speed"),
            gust = instant.num("wind_speed_of_gust"),
            direction = instant.num("wind_from_direction"),
            uvClearSky = instant.num("ultraviolet_index_clear_sky"),
            precip1h = next1?.get("details")?.jsonObject.num("precipitation_amount"),
            probability1h = next1?.get("details")?.jsonObject.num("probability_of_precipitation"),
            symbol1h = next1?.get("summary")?.jsonObject?.get("symbol_code")?.jsonPrimitive?.content,
            precip6h = next6?.get("details")?.jsonObject.num("precipitation_amount"),
            symbol6h = next6?.get("summary")?.jsonObject?.get("symbol_code")?.jsonPrimitive?.content,
        )
    }

    private fun JsonObject?.num(key: String): Double? = (this?.get(key) as? JsonPrimitive)?.doubleOrNull
}
