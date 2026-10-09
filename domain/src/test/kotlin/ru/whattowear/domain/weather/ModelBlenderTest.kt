package ru.whattowear.domain.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.whattowear.domain.model.PrecipType
import ru.whattowear.domain.model.WeatherKind
import java.time.ZoneId

class ModelBlenderTest {

    private val now = 1_800_000_000L
    private val zone = ZoneId.of("Europe/Moscow")

    private fun raw(vararg series: ModelSeries) = RawForecast(zone, series.toList(), emptyMap(), usedFallback = false)
    private fun series(model: WeatherModel, vararg hours: ModelHour) = ModelSeries(model, hours.toList())

    @Test
    fun `temperature is weighted average by model weights`() {
        // ICON 0.30, GFS 0.10 на коротком горизонте: (10·0.3 + 20·0.1) / 0.4 = 12.5
        val result = ModelBlender.blend(
            raw(
                series(WeatherModel.ICON, ModelHour(now, temperature = 10.0)),
                series(WeatherModel.GFS, ModelHour(now, temperature = 20.0)),
            ),
            now,
        ).single()
        assertEquals(12.5, result.temperature, 0.01)
        assertEquals(10.0, result.tempSpread, 0.01)
        assertEquals(2, result.modelCount)
    }

    @Test
    fun `long range shifts weight to ECMWF`() {
        val t = now + 72 * 3600
        // На 72 ч: ECMWF 0.35, ICON 0.20 → (10·0.35 + 20·0.2) / 0.55 ≈ 13.6
        val result = ModelBlender.blend(
            raw(
                series(WeatherModel.ECMWF, ModelHour(t, temperature = 10.0)),
                series(WeatherModel.ICON, ModelHour(t, temperature = 20.0)),
            ),
            now,
        ).single()
        assertEquals(13.6, result.temperature, 0.05)
    }

    @Test
    fun `missing parameter is taken from models that have it`() {
        val result = ModelBlender.blend(
            raw(
                series(WeatherModel.ECMWF, ModelHour(now, temperature = 10.0, uvIndex = null)),
                series(WeatherModel.ICON, ModelHour(now, temperature = 10.0, uvIndex = 4.0)),
            ),
            now,
        ).single()
        assertEquals(4.0, result.uvIndex!!, 0.01)
    }

    @Test
    fun `apparent temperature falls back to air temperature`() {
        val result = ModelBlender.blend(raw(series(WeatherModel.GFS, ModelHour(now, temperature = 7.0))), now).single()
        assertEquals(7.0, result.apparentTemperature, 0.01)
        assertNull(result.uvIndex)
    }

    @Test
    fun `precipitation probability combines api value and model agreement`() {
        // ICON (0.3) мокрый, GFS (0.1) сухой: доля «мокрых» = 0.75; API: (80·0.3 + 20·0.1)/0.4 = 65
        // Итог: 0.5·65 + 0.5·75 = 70
        val result = ModelBlender.blend(
            raw(
                series(WeatherModel.ICON, ModelHour(now, temperature = 10.0, precipitation = 1.0, precipProbability = 80.0)),
                series(WeatherModel.GFS, ModelHour(now, temperature = 10.0, precipitation = 0.0, precipProbability = 20.0)),
            ),
            now,
        ).single()
        assertEquals(70, result.precipProbability)
        assertEquals(0.75, result.wetShare, 0.001)
        assertEquals(1.0, result.precipSpread, 0.001)
        assertEquals(PrecipType.RAIN, result.precipType)
    }

    @Test
    fun `snowfall makes precipitation snow`() {
        // 1 мм воды ≈ 0.7 см снега
        val result = ModelBlender.blend(
            raw(series(WeatherModel.ICON, ModelHour(now, temperature = -3.0, precipitation = 1.0, snowfall = 0.7, rain = 0.0, weatherCode = 73))),
            now,
        ).single()
        assertEquals(PrecipType.SNOW, result.precipType)
        assertEquals(WeatherKind.SNOW, result.kind)
    }

    @Test
    fun `mixed rain and snow is sleet`() {
        val result = ModelBlender.blend(
            raw(series(WeatherModel.ICON, ModelHour(now, temperature = 0.5, precipitation = 2.0, snowfall = 0.7, rain = 1.0))),
            now,
        ).single()
        assertEquals(PrecipType.SLEET, result.precipType)
    }

    @Test
    fun `freezing rain wins when enough models agree`() {
        val result = ModelBlender.blend(
            raw(
                series(WeatherModel.ICON, ModelHour(now, temperature = -1.0, precipitation = 0.8, weatherCode = 66)),
                series(WeatherModel.ECMWF, ModelHour(now, temperature = -1.0, precipitation = 0.6, weatherCode = 61)),
            ),
            now,
        ).single()
        assertEquals(PrecipType.FREEZING, result.precipType)
        assertEquals(WeatherKind.FREEZING_RAIN, result.kind)
    }

    @Test
    fun `dry hour sky follows cloud cover not a rainy code`() {
        val result = ModelBlender.blend(
            raw(series(WeatherModel.ICON, ModelHour(now, temperature = 15.0, precipitation = 0.0, cloudCover = 10.0, weatherCode = 61))),
            now,
        ).single()
        assertEquals(PrecipType.NONE, result.precipType)
        assertEquals(WeatherKind.CLEAR, result.kind)
    }

    @Test
    fun `hours without temperature in any model are dropped`() {
        val result = ModelBlender.blend(
            raw(series(WeatherModel.ICON, ModelHour(now, temperature = 5.0), ModelHour(now + 3600, temperature = null))),
            now,
        )
        assertEquals(1, result.size)
    }

    @Test
    fun `gusts are never lower than mean wind`() {
        val result = ModelBlender.blend(
            raw(series(WeatherModel.ICON, ModelHour(now, temperature = 5.0, windSpeed = 6.0, windGust = 4.0))),
            now,
        ).single()
        assertTrue(result.windGust >= result.windSpeed)
    }

    @Test
    fun `wind direction is averaged on a circle`() {
        val result = ModelBlender.blend(
            raw(
                series(WeatherModel.ICON, ModelHour(now, temperature = 5.0, windDirection = 350.0)),
                series(WeatherModel.ECMWF, ModelHour(now, temperature = 5.0, windDirection = 10.0)),
            ),
            now,
        ).single()
        // Среднее 350° и 10° — около 0°, а не 180°
        val d = result.windDirection!!
        assertTrue("direction=$d", d <= 10 || d >= 350)
    }
}
