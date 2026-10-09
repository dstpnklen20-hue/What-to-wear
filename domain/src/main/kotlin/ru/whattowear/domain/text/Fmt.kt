package ru.whattowear.domain.text

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Форматирование для интерфейса на русском: температуры, время, даты, числа. */
object Fmt {
    private val ru = Locale.forLanguageTag("ru")
    private val hourFormat = DateTimeFormatter.ofPattern("HH:mm", ru)

    /** «+12°», «−3°», «0°». Используется настоящий знак минуса. */
    fun temp(value: Double): String {
        val r = value.roundToInt()
        return when {
            r > 0 -> "+$r°"
            r < 0 -> "−${abs(r)}°"
            else -> "0°"
        }
    }

    /** Температура без знака плюс (для крупного вывода): «12°», «−3°». */
    fun tempPlain(value: Double): String {
        val r = value.roundToInt()
        return if (r < 0) "−${abs(r)}°" else "$r°"
    }

    fun hour(epochSec: Long, zone: ZoneId): String = Instant.ofEpochSecond(epochSec).atZone(zone).format(hourFormat)

    fun time(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).format(hourFormat)

    /** Число с одним знаком после запятой, без «,0»: 3 → «3», 2.5 → «2,5». */
    fun number(value: Double): String {
        val r = (value * 10).roundToInt() / 10.0
        return if (r % 1.0 == 0.0) r.toInt().toString() else r.toString().replace('.', ',')
    }

    fun dayName(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "Сегодня"
        today.plusDays(1) -> "Завтра"
        else -> date.dayOfWeek.getDisplayName(TextStyle.FULL, ru).replaceFirstChar(Char::uppercase)
    }

    /** «12 октября» */
    fun dayMonth(date: LocalDate): String = "${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.FULL, ru)}"

    fun shortDayMonth(date: LocalDate): String =
        "${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.SHORT, ru).trimEnd('.')}"

    /** Русские формы множественного числа: plural(5, "вещь", "вещи", "вещей"). */
    fun plural(n: Int, one: String, few: String, many: String): String {
        val mod100 = abs(n) % 100
        val mod10 = mod100 % 10
        return when {
            mod100 in 11..14 -> many
            mod10 == 1 -> one
            mod10 in 2..4 -> few
            else -> many
        }
    }
}
