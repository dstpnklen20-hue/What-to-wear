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
        // ICON 0.25, GFS 0.06 на коротком горизонте: (10·0.25 + 20·0.06) / 0.31 ≈ 11.9
        val result = ModelBlender.blend(
            raw(
                series(WeatherModel.ICON, ModelHour(now, temperature = 10.0)),
                series(WeatherModel.GFS, ModelHour(now, temperature = 20.0)),
            ),
            now,
        ).single()
        assertEquals(11.9, result.temperature, 0.01)
        assertEquals(10.0, result.tempSpread, 0.01)
        assertEquals(2, result.modelCount)
    }

    @Test
    fun `long range shifts weight to ECMWF`() {
        val t = now + 72 * 3600
        // На 72 ч: ECMWF 0.27, ICON 0.15 → (10·0.27 + 20·0.15) / 0.42 ≈ 13.6
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
    fun `AI model weighs more at long range than at short range`() {
        assertTrue(WeatherModel.AIFS.weightAt(96.0) > WeatherModel.AIFS.weightAt(6.0) * 2)
        // Веса нормированы: сумма по всем моделям — 1 на любом горизонте
        listOf(0.0, 48.0, 96.0).forEach { h -> assertEquals(1.0, WeatherModel.entries.sumOf { it.weightAt(h) }, 1e-9) }
    }

    @Test
    fun `single outlier model does not inflate spread when there are many models`() {
        val temps = listOf(10.0, 10.5, 11.0, 9.8, 16.0)
        val result = ModelBlender.blend(
            raw(
                series(WeatherModel.ICON, ModelHour(now, temperature = temps[0])),
                series(WeatherModel.ECMWF, ModelHour(now, temperature = temps[1])),
                series(WeatherModel.UKMO, ModelHour(now, temperature = temps[2])),
                series(WeatherModel.BEST_MATCH, ModelHour(now, temperature = temps[3])),
                series(WeatherModel.AIFS, ModelHour(now, temperature = temps[4])),
            ),
            now,
        ).single()
        // Без AIFS-«выброса» разброс 9.8…11.0
        assertEquals(1.2, result.tempSpread, 0.01)
        // Двух моделей мало, чтобы что-то отбрасывать
        assertEquals(6.0, ModelBlender.spread(listOf(10.0, 16.0), 13.0), 0.01)
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
        // ICON (0.25) мокрый, ECMWF (0.25) и GFS (0.06) сухие: доля «мокрых» = 0.25/0.56 ≈ 0.446;
        // API: (90·0.25 + 20·0.25 + 20·0.06)/0.56 ≈ 51.25. Итог: 0.5·51.25 + 0.5·44.6 ≈ 48
        val result = ModelBlender.blend(
            raw(
                series(WeatherModel.ICON, ModelHour(now, temperature = 10.0, precipitation = 1.0, precipProbability = 90.0)),
                series(WeatherModel.ECMWF, ModelHour(now, temperature = 10.0, precipitation = 0.0, precipProbability = 20.0)),
                series(WeatherModel.GFS, ModelHour(now, temperature = 10.0, precipitation = 0.0, precipProbability = 20.0)),
            ),
            now,
        ).single()
        assertEquals(48, result.precipProbability)
        assertEquals(0.446, result.wetShare, 0.001)
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
