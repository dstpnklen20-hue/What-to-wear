package ru.whattowear.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.whattowear.app.update.AppUpdater
import ru.whattowear.app.update.UpdateState
import ru.whattowear.domain.model.UserSettings
import ru.whattowear.domain.repository.SettingsRepository
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val updater: AppUpdater,
) : ViewModel() {

    /** null — настройки ещё не прочитаны (чтобы не мигать темой и не спрашивать разрешения зря). */
    val settings: StateFlow<UserSettings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val update: StateFlow<UpdateState> = updater.state

    init {
        // Тихая проверка новой сборки из GitHub при запуске (только для сборок из CI)
        viewModelScope.launch { updater.check(manual = false) }
    }

    fun installUpdate() {
        updater.install()
    }

    fun postponeUpdate() = updater.dismiss()

    /** Первый запуск пройден; уведомления включены, только если пользователь их разрешил. */
    fun completeOnboarding(notificationsAllowed: Boolean) {
        viewModelScope.launch {
            val s = settingsRepository.settings.first()
            settingsRepository.setNotifications(notificationsAllowed && s.notificationsEnabled, s.notificationTime)
            settingsRepository.setOnboardingDone()
        }
    }
}
