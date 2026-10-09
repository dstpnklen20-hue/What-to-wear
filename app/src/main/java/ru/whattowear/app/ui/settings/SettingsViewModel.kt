package ru.whattowear.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.whattowear.app.update.AppUpdater
import ru.whattowear.app.update.UpdateState
import ru.whattowear.domain.model.Location
import ru.whattowear.domain.model.Style
import ru.whattowear.domain.model.ThemeMode
import ru.whattowear.domain.model.UserSettings
import ru.whattowear.domain.repository.SettingsRepository
import ru.whattowear.domain.usecase.ObserveSelectedLocationUseCase
import ru.whattowear.domain.usecase.ResetLearningUseCase
import java.time.LocalTime
import javax.inject.Inject

data class SettingsUiState(
    val loaded: Boolean = false,
    val settings: UserSettings = UserSettings(),
    val location: Location? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val resetLearning: ResetLearningUseCase,
    private val updater: AppUpdater,
    observeSelected: ObserveSelectedLocationUseCase,
) : ViewModel() {

    val state: StateFlow<SettingsUiState> = combine(repository.settings, observeSelected()) { s, loc ->
        SettingsUiState(true, s, loc)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    val update: StateFlow<UpdateState> = updater.state
    val versionName: String get() = updater.versionName

    fun checkUpdates() = launch { updater.check(manual = true) }

    private fun current() = state.value.settings

    fun setLeave(time: LocalTime) = launch { repository.setOutingWindow(time, current().returnTime) }
    fun setReturn(time: LocalTime) = launch { repository.setOutingWindow(current().leaveTime, time) }
    fun setNotificationsEnabled(enabled: Boolean) = launch { repository.setNotifications(enabled, current().notificationTime) }
    fun setNotificationTime(time: LocalTime) = launch { repository.setNotifications(current().notificationsEnabled, time) }
    fun setManualOffset(value: Double) = launch { repository.setManualOffset((value * 2).let { kotlin.math.round(it) } / 2) }
    fun setPreferredStyle(style: Style?) = launch { repository.setPreferredStyle(style) }
    fun setTheme(mode: ThemeMode) = launch { repository.setTheme(mode) }
    fun setDynamicColor(enabled: Boolean) = launch { repository.setDynamicColor(enabled) }
    fun resetLearned() = launch { resetLearning() }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
