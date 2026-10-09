package ru.whattowear.domain.outfit

/**
 * Сколько «баллов теплоты» нужно при данной эффективной температуре.
 * Корпус — сумма теплоты слоёв (база + средний + внешний, каждый 1..5), низ и обувь — теплота одной вещи.
 * Опорные точки подобраны так: +17 → футболка (1) + лёгкая кофта (2); +5 → футболка + свитер (3) + куртка (3);
 * −10 → термобельё (2) + тёплый свитер (4) + пуховик (5).
 */
object WarmthModel {

    private val torsoPoints = listOf(
        28.0 to 1.0, 22.0 to 2.0, 17.0 to 3.0, 13.0 to 4.0, 9.0 to 5.5, 5.0 to 7.0,
        0.0 to 8.5, -5.0 to 10.0, -10.0 to 11.5, -20.0 to 13.5, -30.0 to 15.0,
    )
    private val legsPoints = listOf(25.0 to 1.0, 15.0 to 2.0, 5.0 to 3.0, -5.0 to 4.0, -15.0 to 5.0)
    private val shoesPoints = listOf(24.0 to 1.0, 16.0 to 2.0, 6.0 to 3.0, -3.0 to 4.0, -12.0 to 5.0)

    fun torso(temp: Double): Double = interpolate(torsoPoints, temp)
    fun legs(temp: Double): Double = interpolate(legsPoints, temp)
    fun shoes(temp: Double): Double = interpolate(shoesPoints, temp)

    /** Нужная теплота шапки: 0 — шапка не нужна. */
    fun head(temp: Double): Int = when {
        temp <= -10 -> 5
        temp <= -3 -> 4
        temp <= 5 -> 3
        else -> 0
    }

    /** Обратная функция для корпуса: при какой температуре [warmth] баллов как раз достаточно. */
    fun torsoComfortTemp(warmth: Double): Double =
        interpolate(torsoInverted, warmth)

    /** Точки (теплота → температура), по убыванию теплоты. */
    private val torsoInverted = torsoPoints.map { (t, w) -> w to t }.sortedByDescending { it.first }

    /** Кусочно-линейная интерполяция; точки отсортированы по x по убыванию. За краями — значение крайней точки. */
    private fun interpolate(points: List<Pair<Double, Double>>, x: Double): Double {
        if (x >= points.first().first) return points.first().second
        if (x <= points.last().first) return points.last().second
        for (i in 0 until points.size - 1) {
            val (x1, y1) = points[i]
            val (x2, y2) = points[i + 1]
            if (x <= x1 && x >= x2) return y1 + (y2 - y1) * (x1 - x) / (x1 - x2)
        }
        return points.last().second
    }
}
