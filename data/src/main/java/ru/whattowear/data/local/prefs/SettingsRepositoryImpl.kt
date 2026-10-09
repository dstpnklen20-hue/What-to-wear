package ru.whattowear.data.local.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import ru.whattowear.domain.model.Style
import ru.whattowear.domain.model.ThemeMode
import ru.whattowear.domain.model.UserSettings
import ru.whattowear.domain.repository.SettingsRepository
import java.io.IOException
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : SettingsRepository {

    private object Keys {
        val leave = intPreferencesKey("leave_minutes")
        val back = intPreferencesKey("return_minutes")
        val notifyEnabled = booleanPreferencesKey("notify_enabled")
        val notifyTime = intPreferencesKey("notify_minutes")
        val location = longPreferencesKey("selected_location")
        val manualOffset = doublePreferencesKey("manual_offset")
        val learnedOffset = doublePreferencesKey("learned_offset")
        val feedbackCount = intPreferencesKey("feedback_count")
        val style = stringPreferencesKey("preferred_style")
        val theme = stringPreferencesKey("theme")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val onboarding = booleanPreferencesKey("onboarding_done")
    }

    private val store get() = context.settingsStore

    override val settings: Flow<UserSettings> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { p ->
            val defaults = UserSettings()
            UserSettings(
                leaveTime = p[Keys.leave]?.toTime() ?: defaults.leaveTime,
                returnTime = p[Keys.back]?.toTime() ?: defaults.returnTime,
                notificationsEnabled = p[Keys.notifyEnabled] ?: defaults.notificationsEnabled,
                notificationTime = p[Keys.notifyTime]?.toTime() ?: defaults.notificationTime,
                selectedLocationId = p[Keys.location],
                manualOffset = p[Keys.manualOffset] ?: 0.0,
                learnedOffset = p[Keys.learnedOffset] ?: 0.0,
                feedbackCount = p[Keys.feedbackCount] ?: 0,
                preferredStyle = p[Keys.style]?.let { runCatching { Style.valueOf(it) }.getOrNull() },
                theme = p[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
                dynamicColor = p[Keys.dynamicColor] ?: false,
                onboardingDone = p[Keys.onboarding] ?: false,
            )
        }
        .distinctUntilChanged()

    override suspend fun setOutingWindow(leave: LocalTime, back: LocalTime) {
        store.edit {
            it[Keys.leave] = leave.toMinutes()
            it[Keys.back] = back.toMinutes()
        }
    }

    override suspend fun setNotifications(enabled: Boolean, time: LocalTime) {
        store.edit {
            it[Keys.notifyEnabled] = enabled
            it[Keys.notifyTime] = time.toMinutes()
        }
    }

    override suspend fun setSelectedLocation(id: Long?) {
        store.edit { if (id == null) it.remove(Keys.location) else it[Keys.location] = id }
    }

    override suspend fun setManualOffset(offset: Double) {
        store.edit { it[Keys.manualOffset] = offset }
    }

    override suspend fun setLearnedOffset(offset: Double, feedbackCount: Int) {
        store.edit {
            it[Keys.learnedOffset] = offset
            it[Keys.feedbackCount] = feedbackCount
        }
    }

    override suspend fun setPreferredStyle(style: Style?) {
        store.edit { if (style == null) it.remove(Keys.style) else it[Keys.style] = style.name }
    }

    override suspend fun setTheme(mode: ThemeMode) {
        store.edit { it[Keys.theme] = mode.name }
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        store.edit { it[Keys.dynamicColor] = enabled }
    }

    override suspend fun setOnboardingDone() {
        store.edit { it[Keys.onboarding] = true }
    }

    private fun Int.toTime(): LocalTime = LocalTime.of((this / 60).coerceIn(0, 23), (this % 60).coerceIn(0, 59))
    private fun LocalTime.toMinutes(): Int = hour * 60 + minute
}
