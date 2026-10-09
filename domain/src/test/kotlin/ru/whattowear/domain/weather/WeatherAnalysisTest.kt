package ru.whattowear.domain.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.whattowear.domain.model.PrecipType
import ru.whattowear.domain.model.WeatherKind
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

class UncertaintyDetectorTest {
    private val t0 = 1_800_000_000L

    @Test
    fun `two consecutive hours with large temperature spread are uncertain`() {
        val hours = listOf(hour(t0, tempSpread = 1.0), hour(t0 + 3600, tempSpread = 3.5), hour(t0 + 7200, tempSpread = 4.0))
        val result = UncertaintyDetector.evaluate(hours)
        assertTrue(result.temperature)
        assertTrue(result.any)
    }

    @Test
    fun `single outlier hour is not uncertain`() {
        val hours = listOf(hour(t0, tempSpread = 5.0), hour(t0 + 3600, tempSpread = 1.0), hour(t0 + 7200, tempSpread = 5.0))
        assertFalse(UncertaintyDetector.evaluate(hours).temperature)
    }

    @Test
    fun `models disagreeing about rain are uncertain`() {
        val hours = (0 until 3).map { hour(t0 + it * 3600L, precip = 0.4, wetShare = 0.5, precipSpread = 0.8) }
        assertTrue(UncertaintyDetector.evaluate(hours).precipitation)
    }

    @Test
    fun `large total precipitation spread is uncertain`() {
        val hours = (0 until 4).map { hour(t0 + it * 3600L, precip = 1.0, wetShare = 1.0, precipSpread = 0.9) }
        assertTrue(UncertaintyDetector.evaluate(hours).precipitation)
    }

    @Test
    fun `agreeing models are certain`() {
        val hours = (0 until 6).map { hour(t0 + it * 3600L, precip = 1.0, wetShare = 1.0, precipSpread = 0.2, tempSpread = 1.0) }
        assertFalse(UncertaintyDetector.evaluate(hours).any)
    }

    @Test
    fun `single model cannot be uncertain`() {
        val hours = (0 until 4).map { hour(t0 + it * 3600L, tempSpread = 9.0, models = 1) }
        assertFalse(UncertaintyDetector.evaluate(hours).any)
    }
}

class IceRiskDetectorTest {
    private val t0 = 1_800_000_000L

    @Test
    fun `freezing rain is ice risk`() {
        val hours = listOf(hour(t0, temp = -1.0, precip = 0.5, type = PrecipType.FREEZING))
        assertTrue(IceRiskDetector.hasIceRisk(hours, t0, t0 + 3600))
    }

    @Test
    fun `rain near zero is ice risk`() {
        val hours = listOf(hour(t0, temp = 0.5, precip = 0.4))
        assertTrue(IceRiskDetector.hasIceRisk(hours, t0, t0 + 3600))
    }

    @Test
    fun `evening rain followed by morning frost is ice risk`() {
        val hours = listOf(
            hour(t0 - 6 * 3600, temp = 3.0, precip = 1.0),
            hour(t0 - 3 * 3600, temp = 1.5),
            hour(t0, temp = -2.0),
        )
        assertTrue(IceRiskDetector.hasIceRisk(hours, t0, t0 + 3600))
    }

    @Test
    fun `dry frost is not ice risk`() {
        val hours = (0 until 12).map { hour(t0 - 6 * 3600 + it * 3600L, temp = -5.0) }
        assertFalse(IceRiskDetector.hasIceRisk(hours, t0, t0 + 6 * 3600))
    }

    @Test
    fun `warm rain is not ice risk`() {
        val hours = (0 until 6).map { hour(t0 + it * 3600L, temp = 8.0, precip = 2.0) }
        assertFalse(IceRiskDetector.hasIceRisk(hours, t0, t0 + 6 * 3600))
    }
}

class SunCalculatorTest {
    @Test
    fun `moscow sunrise and sunset match open-meteo within three minutes`() {
        // Open-Meteo для 55.78, 37.58 на 10.10.2026: восход 1791604203, закат 1791643343
        val sun = SunCalculator.sunTimes(LocalDate.of(2026, 10, 10), ZoneId.of("Europe/Moscow"), 55.782, 37.576)
        assertTrue(abs(sun.sunrise!! - 1791604203L) < 180)
        assertTrue(abs(sun.sunset!! - 1791643343L) < 180)
    }

    @Test
    fun `polar night has no sunrise`() {
        val sun = SunCalculator.sunTimes(LocalDate.of(2026, 12, 21), ZoneId.of("Europe/Oslo"), 78.2, 15.6)
        assertEquals(null, sun.sunrise)
    }
}

class DailyAggregatorTest {
    private val zone = ZoneId.of("UTC")
    private val date = LocalDate.of(2026, 10, 10)
    private val start = date.atStartOfDay(zone).toEpochSecond()

    @Test
    fun `day summary is built from blended hours`() {
        val hours = (0 until 24).map { h ->
            hour(start + h * 3600L, temp = 5.0 + h / 2.0, feels = 3.0 + h / 2.0, precip = if (h in 14..16) 1.0 else 0.0, gust = 4.0 + h)
        }
        val day = DailyAggregator.aggregate(hours, zone, emptyMap(), date).single()
        assertEquals(5.0, day.tempMin, 0.01)
        assertEquals(16.5, day.tempMax, 0.01)
        assertEquals(3.0, day.feelsMin, 0.01)
        assertEquals(3.0, day.precipSumMm, 0.01)
        assertEquals(PrecipType.RAIN, day.precipType)
        assertEquals(WeatherKind.RAIN, day.kind)
        assertEquals(27.0, day.gustMax, 0.01)
    }

    @Test
    fun `incomplete days and past days are skipped`() {
        val yesterday = (0 until 24).map { hour(start - 86400 + it * 3600L) }
        val today = (0 until 24).map { hour(start + it * 3600L) }
        val partialTomorrow = (0 until 6).map { hour(start + 86400 + it * 3600L) }
        val days = DailyAggregator.aggregate(yesterday + today + partialTomorrow, zone, emptyMap(), date)
        assertEquals(listOf(date), days.map { it.date })
    }
}
