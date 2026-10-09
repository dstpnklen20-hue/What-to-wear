package ru.whattowear.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.whattowear.domain.weather.WeatherModel

class OpenMeteoParserTest {

    private val json = """
        {"timezone":"Europe/Moscow",
         "hourly":{"time":[1791493200,1791496800],
           "temperature_2m_icon_seamless":[9.4,9.1],
           "apparent_temperature_icon_seamless":[7.5,7.1],
           "precipitation_icon_seamless":[0.0,1.2],
           "rain_icon_seamless":[0.0,1.0],
           "showers_icon_seamless":[0.0,0.2],
           "uv_index_icon_seamless":[0.0,null],
           "temperature_2m_ecmwf_ifs":[9.0,8.8],
           "uv_index_ecmwf_ifs":[null,null],
           "weather_code_ecmwf_ifs":[3,61]},
         "daily":{"time":[1791493200],"sunrise_icon_seamless":[1791517600],"sunset_icon_seamless":[1791556000]}}
    """.trimIndent()

    @Test
    fun `parses suffixed columns per model`() {
        val result = OpenMeteoParser.parse(Json.parseToJsonElement(json).jsonObject, listOf(WeatherModel.ICON, WeatherModel.ECMWF, WeatherModel.GFS))
        assertEquals("Europe/Moscow", result.zone.id)
        // GFS в ответе нет — модель пропускается
        assertEquals(listOf(WeatherModel.ICON, WeatherModel.ECMWF), result.series.map { it.model })

        val icon = result.series.first { it.model == WeatherModel.ICON }
        assertEquals(9.1, icon.hours[1].temperature!!, 0.001)
        assertEquals(1.2, icon.hours[1].rain!!, 0.001) // дождь + ливни
        assertNull(icon.hours[1].uvIndex)

        val ecmwf = result.series.first { it.model == WeatherModel.ECMWF }
        assertEquals(61, ecmwf.hours[1].weatherCode)
        assertNull(ecmwf.hours[0].precipitation)

        val sun = result.sunTimes.values.single()
        assertEquals(1791517600L, sun.sunrise)
        assertEquals(1791556000L, sun.sunset)
    }
}

class MetNoParserTest {

    private val json = """
        {"properties":{"timeseries":[
          {"time":"2026-10-10T00:00:00Z","data":{"instant":{"details":{"air_temperature":4.0,"apparent_air_temperature":2.0,"wind_speed":3.0,"cloud_area_fraction":100.0,"ultraviolet_index_clear_sky":2.0}},
            "next_1_hours":{"summary":{"symbol_code":"lightsnow_night"},"details":{"precipitation_amount":0.5,"probability_of_precipitation":60.0}},
            "next_6_hours":{"summary":{"symbol_code":"snow"},"details":{"precipitation_amount":3.0}}}},
          {"time":"2026-10-10T01:00:00Z","data":{"instant":{"details":{"air_temperature":2.0,"wind_speed":3.0}},
            "next_6_hours":{"summary":{"symbol_code":"rain"},"details":{"precipitation_amount":6.0}}}},
          {"time":"2026-10-10T07:00:00Z","data":{"instant":{"details":{"air_temperature":8.0,"wind_speed":3.0}}}}
        ]}}
    """.trimIndent()

    @Test
    fun `hourly steps are kept and six hour steps are spread over hours`() {
        val series = MetNoParser.parse(Json.parseToJsonElement(json).jsonObject)
        assertEquals(WeatherModel.MET_NO, series.model)

        val first = series.hours.first()
        assertEquals(0.5, first.precipitation!!, 0.001)
        assertEquals(0.35, first.snowfall!!, 0.001) // снег по символу: 0.5 мм × 0.7
        assertEquals(60.0, first.precipProbability!!, 0.001)
        assertEquals(false, first.isDay)
        // UV ясного неба ослаблен сплошной облачностью
        assertTrue(first.uvIndex!! < 2.0)

        // Шаг 6 часов (01:00 → 07:00) разложен на 6 часов: осадки поровну, температура линейно
        val spread = series.hours.filter { it.time in (first.time + 3600) until (first.time + 7 * 3600) }
        assertEquals(6, spread.size)
        spread.forEach { assertEquals(1.0, it.precipitation!!, 0.001) }
        assertEquals(2.0, spread.first().temperature!!, 0.001)
        assertEquals(7.0, spread.last().temperature!!, 0.001)
    }
}
