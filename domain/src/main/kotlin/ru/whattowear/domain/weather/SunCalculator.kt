package ru.whattowear.domain.weather

import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin

/**
 * Восход и закат по «уравнению восхода» (точность ±1–2 мин). Нужен, когда Open-Meteo недоступен
 * и прогноз строится только по MET Norway, у которого в прогнозе нет этих данных.
 */
object SunCalculator {

    fun sunTimes(date: LocalDate, zone: ZoneId, latitude: Double, longitude: Double): SunTimes {
        val n = date.toEpochDay() + 2440588.0 - 2451545.0 + 0.0008
        val jStar = n - longitude / 360.0
        val m = Math.toRadians((357.5291 + 0.98560028 * jStar).mod(360.0))
        val c = 1.9148 * sin(m) + 0.02 * sin(2 * m) + 0.0003 * sin(3 * m)
        val lambda = Math.toRadians((Math.toDegrees(m) + c + 180.0 + 102.9372).mod(360.0))
        val jTransit = 2451545.0 + jStar + 0.0053 * sin(m) - 0.0069 * sin(2 * lambda)
        val declination = asin(sin(lambda) * sin(Math.toRadians(23.4397)))
        val phi = Math.toRadians(latitude)
        val cosOmega = (sin(Math.toRadians(-0.833)) - sin(phi) * sin(declination)) / (cos(phi) * cos(declination))

        val dayStart = date.atStartOfDay(zone).toEpochSecond()
        val dayEnd = date.plusDays(1).atStartOfDay(zone).toEpochSecond()
        return when {
            cosOmega > 1 -> SunTimes(null, null) // полярная ночь
            cosOmega < -1 -> SunTimes(dayStart, dayEnd) // полярный день
            else -> {
                val omega = Math.toDegrees(acos(cosOmega))
                SunTimes(
                    sunrise = julianToEpoch(jTransit - omega / 360.0),
                    sunset = julianToEpoch(jTransit + omega / 360.0),
                )
            }
        }
    }

    private fun julianToEpoch(j: Double): Long = ((j - 2440587.5) * 86400.0).roundToLong()
}
