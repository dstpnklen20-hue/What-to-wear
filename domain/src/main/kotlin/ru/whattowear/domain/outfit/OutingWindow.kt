package ru.whattowear.domain.outfit

import ru.whattowear.domain.model.HourlyWeather
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Отрезок дня, когда пользователь вне дома. [start] и [end] — секунды эпохи; [start] выровнен на начало часа.
 * Если сегодняшнее время возвращения уже прошло, окно относится к завтрашнему дню ([isTomorrow]).
 */
data class OutingWindow(
    val date: LocalDate,
    val start: Long,
    val end: Long,
    val isTomorrow: Boolean,
) {
    fun contains(hour: HourlyWeather): Boolean = hour.time in start until end
    fun hoursOf(all: List<HourlyWeather>): List<HourlyWeather> = all.filter { contains(it) }

    companion object {
        fun resolve(leave: LocalTime, back: LocalTime, zone: ZoneId, nowMillis: Long): OutingWindow {
            val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
            val today = now.toLocalDate()
            fun build(date: LocalDate, isTomorrow: Boolean): OutingWindow {
                val start = date.atTime(leave.withMinute(0)).atZone(zone).toEpochSecond()
                // Время возвращения раньше выхода — смена через полночь
                val endDate = if (back > leave) date else date.plusDays(1)
                val end = endDate.atTime(back).atZone(zone).toEpochSecond()
                return OutingWindow(date, start, end, isTomorrow)
            }
            val todayWindow = build(today, isTomorrow = false)
            return if (now.toEpochSecond() >= todayWindow.end) build(today.plusDays(1), isTomorrow = true) else todayWindow
        }
    }
}
