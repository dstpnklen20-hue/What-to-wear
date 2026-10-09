package ru.whattowear.domain.weather

import ru.whattowear.domain.model.HourlyWeather
import ru.whattowear.domain.model.PrecipType

/**
 * Риск гололёда на отрезке [start, end):
 *  - ледяной дождь или морось;
 *  - осадки при температуре от −2 до +1 °C (мокрый снег и дождь, которые прихватывает);
 *  - за предыдущие 12 часов были жидкие осадки, а потом температура опустилась ниже −0.5 °C.
 */
object IceRiskDetector {

    fun hasIceRisk(hours: List<HourlyWeather>, start: Long, end: Long): Boolean {
        val window = hours.filter { it.time in start until end }
        if (window.any { it.precipType == PrecipType.FREEZING && it.precipitationMm >= 0.05 }) return true
        if (window.any { it.precipitationMm >= 0.1 && it.precipType != PrecipType.SNOW && it.temperature in -2.0..1.0 }) return true

        val lookBack = 12 * 3600L
        val history = hours.filter { it.time in (start - lookBack) until end }.sortedBy { it.time }
        var lastWetTime: Long? = null
        for (h in history) {
            if (h.precipitationMm >= 0.2 && h.temperature > -0.5 && h.precipType != PrecipType.SNOW) lastWetTime = h.time
            val wet = lastWetTime
            if (h.time >= start && h.temperature <= -0.5 && wet != null && h.time - wet <= lookBack) return true
        }
        return false
    }
}
