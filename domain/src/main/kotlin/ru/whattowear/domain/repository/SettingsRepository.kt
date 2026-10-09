package ru.whattowear.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.whattowear.domain.model.Style
import ru.whattowear.domain.model.ThemeMode
import ru.whattowear.domain.model.UserSettings
import java.time.LocalTime

interface SettingsRepository {
    val settings: Flow<UserSettings>

    suspend fun setOutingWindow(leave: LocalTime, back: LocalTime)
    suspend fun setNotifications(enabled: Boolean, time: LocalTime)
    suspend fun setSelectedLocation(id: Long?)
    suspend fun setManualOffset(offset: Double)
    suspend fun setLearnedOffset(offset: Double, feedbackCount: Int)
    suspend fun setPreferredStyle(style: Style?)
    suspend fun setTheme(mode: ThemeMode)
    suspend fun setDynamicColor(enabled: Boolean)
    suspend fun setOnboardingDone()
}
