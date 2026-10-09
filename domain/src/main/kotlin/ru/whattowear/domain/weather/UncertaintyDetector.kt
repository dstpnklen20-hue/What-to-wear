package ru.whattowear.domain.weather

import ru.whattowear.domain.model.HourlyWeather

/** Насколько модели расходятся на заданном отрезке времени. */
data class Uncertainty(val temperature: Boolean, val precipitation: Boolean) {
    val any: Boolean get() = temperature || precipitation

    companion object {
        val NONE = Uncertainty(temperature = false, precipitation = false)
    }
}

/**
 * Прогноз считается неуверенным, если:
 *  - температура у моделей расходится больше чем на [TEMP_SPREAD_C] хотя бы 2 часа подряд
 *    (одиночный выброс не считается);
 *  - или по осадкам нет согласия: хотя бы 2 часа, когда осадки дают от 30 до 70 % веса моделей,
 *    при этом осадки заметные (по максимуму у моделей ≥ 0.3 мм/ч);
 *  - или суммарный разброс осадков за отрезок ≥ [PRECIP_SPREAD_MM].
 */
object UncertaintyDetector {
    const val TEMP_SPREAD_C = 3.0
    const val PRECIP_SPREAD_MM = 3.0

    fun evaluate(hours: List<HourlyWeather>): Uncertainty {
        val multi = hours.filter { it.modelCount >= 2 }
        if (multi.isEmpty()) return Uncertainty.NONE

        var run = 0
        var tempUncertain = false
        for (h in multi.sortedBy { it.time }) {
            run = if (h.tempSpread > TEMP_SPREAD_C) run + 1 else 0
            if (run >= 2) {
                tempUncertain = true
                break
            }
        }

        val disputedHours = multi.count { h ->
            h.wetShare in 0.3..0.7 && (h.precipitationMm + h.precipSpread / 2) >= 0.3
        }
        val precipUncertain = disputedHours >= 2 || multi.sumOf { it.precipSpread } >= PRECIP_SPREAD_MM

        return Uncertainty(tempUncertain, precipUncertain)
    }
}
