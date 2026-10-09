package ru.whattowear.domain.model

import java.time.LocalTime

enum class ThemeMode(val title: String) { SYSTEM("Как в системе"), LIGHT("Светлая"), DARK("Тёмная") }

data class UserSettings(
    val leaveTime: LocalTime = LocalTime.of(8, 0),
    val returnTime: LocalTime = LocalTime.of(18, 0),
    val notificationsEnabled: Boolean = true,
    val notificationTime: LocalTime = LocalTime.of(7, 0),
    val selectedLocationId: Long? = null,
    /** Ручная поправка «мерзлявости», °C: +3 = «мне холоднее, чем всем». */
    val manualOffset: Double = 0.0,
    /** Поправка, выученная по отзывам «холодно / нормально / жарко», °C. */
    val learnedOffset: Double = 0.0,
    val feedbackCount: Int = 0,
    val preferredStyle: Style? = null,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val onboardingDone: Boolean = false,
) {
    /** Итоговая личная поправка: на столько градусов пользователю холоднее «среднего человека». */
    val personalOffset: Double get() = manualOffset + learnedOffset
}
