package ru.whattowear.domain.outfit

import ru.whattowear.domain.model.Season
import java.time.LocalDate
import kotlin.math.sqrt

enum class ComfortRating(val value: Int, val title: String) {
    COLD(-1, "Было холодно"),
    OK(0, "Нормально"),
    HOT(1, "Было жарко"),
}

/**
 * Личная поправка по отзывам «холодно / нормально / жарко».
 *
 * Поправка (°C) вычитается из ощущаемой температуры: +2 — человеку на 2° холоднее, чем «в среднем».
 * Шаг убывает с числом отзывов (η = 1.5 / √(1 + n/5)), чтобы первые отзывы быстро подстраивали
 * алгоритм, а потом единичные дни его не раскачивали. «Нормально» поправку не меняет, но уменьшает
 * следующие шаги. Если человек сам надел комплект заметно холоднее/теплее рекомендованного,
 * отзыв почти не учитывается: дело не в его чувствительности. Поправка ограничена ±6 °C.
 */
object ComfortCalibrator {
    const val MAX_OFFSET = 6.0
    private const val BASE_STEP = 1.5

    data class Result(val offset: Double, val feedbackCount: Int)

    /**
     * @param warmthDelta насколько комплект дня был теплее (+) или холоднее (−) нужного, в баллах теплоты корпуса.
     */
    fun update(currentOffset: Double, feedbackCount: Int, rating: ComfortRating, warmthDelta: Double = 0.0): Result {
        val step = BASE_STEP / sqrt(1.0 + feedbackCount / 5.0)
        val signal = when (rating) {
            ComfortRating.COLD -> 1.0
            ComfortRating.HOT -> -1.0
            ComfortRating.OK -> 0.0
        }
        val trust = when {
            rating == ComfortRating.COLD && warmthDelta <= -1.5 -> 0.25 // был одет легче рекомендованного
            rating == ComfortRating.HOT && warmthDelta >= 1.5 -> 0.25 // был одет теплее рекомендованного
            else -> 1.0
        }
        val offset = (currentOffset + signal * step * trust).coerceIn(-MAX_OFFSET, MAX_OFFSET)
        return Result((offset * 10).let { kotlin.math.round(it) } / 10.0, feedbackCount + 1)
    }
}

object SeasonResolver {
    /** Сезон по дате с учётом полушария. */
    fun seasonOf(date: LocalDate, latitude: Double?): Season {
        val north = when (date.monthValue) {
            12, 1, 2 -> Season.WINTER
            3, 4, 5 -> Season.SPRING
            6, 7, 8 -> Season.SUMMER
            else -> Season.AUTUMN
        }
        if (latitude == null || latitude >= 0) return north
        return when (north) {
            Season.WINTER -> Season.SUMMER
            Season.SUMMER -> Season.WINTER
            Season.SPRING -> Season.AUTUMN
            Season.AUTUMN -> Season.SPRING
        }
    }
}
