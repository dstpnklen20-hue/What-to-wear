package ru.whattowear.app.ui.locations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import ru.whattowear.domain.model.GeoPlace
import ru.whattowear.domain.model.Location
import ru.whattowear.domain.repository.DeviceLocationSource
import ru.whattowear.domain.repository.LocationRepository
import ru.whattowear.domain.repository.SettingsRepository
import ru.whattowear.domain.usecase.DeviceLocationUpdate
import ru.whattowear.domain.usecase.LOCATE_TIMEOUT_MS
import ru.whattowear.domain.usecase.ObserveSelectedLocationUseCase
import ru.whattowear.domain.usecase.RefreshForecastUseCase
import ru.whattowear.domain.usecase.UpdateDeviceLocationUseCase
import java.io.IOException
import javax.inject.Inject

sealed interface SearchState {
    data object Idle : SearchState
    data object Loading : SearchState
    data class Results(val places: List<GeoPlace>) : SearchState
    data class Error(val noNetwork: Boolean) : SearchState
}

data class LocationsUiState(
    val locations: List<Location> = emptyList(),
    val selectedId: Long? = null,
    val query: String = "",
    val search: SearchState = SearchState.Idle,
    val hasPermission: Boolean = false,
    val locating: Boolean = false,
    /** Почему не удалось определить местоположение по кнопке; null — не пробовали или всё хорошо. */
    val locateProblem: DeviceLocationUpdate? = null,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class LocationsViewModel @Inject constructor(
    private val repository: LocationRepository,
    private val settings: SettingsRepository,
    private val deviceLocation: DeviceLocationSource,
    private val updateDeviceLocation: UpdateDeviceLocationUseCase,
    private val refreshForecast: RefreshForecastUseCase,
    observeSelected: ObserveSelectedLocationUseCase,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val search = MutableStateFlow<SearchState>(SearchState.Idle)
    private val locating = MutableStateFlow(false)
    private val locateProblem = MutableStateFlow<DeviceLocationUpdate?>(null)
    private val permission = MutableStateFlow(deviceLocation.hasPermission())

    val state: StateFlow<LocationsUiState> = combine(
        combine(repository.observeLocations(), observeSelected()) { list, selected -> list to selected?.id },
        query,
        search,
        combine(permission, locating, locateProblem) { p, l, f -> Triple(p, l, f) },
    ) { (list, selectedId), q, s, (p, l, f) ->
        LocationsUiState(list, selectedId, q, s, p, l, f)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocationsUiState())

    init {
        viewModelScope.launch {
            query.debounce(350).distinctUntilChanged().collect { q -> runSearch(q) }
        }
    }

    fun onQueryChange(value: String) {
        query.value = value
        if (value.isBlank()) search.value = SearchState.Idle
    }

    private suspend fun runSearch(q: String) {
        if (q.trim().length < 2) {
            search.value = SearchState.Idle
            return
        }
        search.value = SearchState.Loading
        search.value = try {
            SearchState.Results(repository.search(q))
        } catch (e: IOException) {
            SearchState.Error(noNetwork = true)
        } catch (e: Exception) {
            SearchState.Error(noNetwork = false)
        }
    }

    fun addPlace(place: GeoPlace) {
        viewModelScope.launch {
            val id = repository.addPlace(place)
            query.value = ""
            search.value = SearchState.Idle
            select(id)
        }
    }

    fun select(id: Long) {
        viewModelScope.launch {
            settings.setSelectedLocation(id)
            // Прогноз нового места грузим сразу, чтобы на «Сегодня» он уже был
            repository.getLocation(id)?.let { refreshForecast(it) }
        }
    }

    fun delete(location: Location) {
        viewModelScope.launch {
            repository.delete(location.id)
            if (settings.settings.first().selectedLocationId == location.id) settings.setSelectedLocation(null)
        }
    }

    fun onPermissionResult(granted: Boolean) {
        permission.value = granted
        if (granted) locateNow() else locateProblem.value = DeviceLocationUpdate.NO_PERMISSION
    }

    /** Вернулись из настроек — разрешение или геолокация могли измениться. */
    fun onResume() {
        val granted = deviceLocation.hasPermission()
        val changed = granted != permission.value
        permission.value = granted
        val problem = locateProblem.value
        if (granted && (changed || problem == DeviceLocationUpdate.NO_PERMISSION ||
                (problem == DeviceLocationUpdate.DISABLED && deviceLocation.isLocationEnabled()))
        ) {
            locateNow()
        }
    }

    fun locateNow() {
        if (locating.value) return
        viewModelScope.launch {
            locating.value = true
            locateProblem.value = null
            try {
                val result = withTimeoutOrNull(LOCATE_TIMEOUT_MS) { updateDeviceLocation() } ?: DeviceLocationUpdate.NOT_FOUND
                val device = repository.getLocations().firstOrNull { it.isDeviceLocation }
                if (result.failed || device == null) {
                    locateProblem.value = if (result.failed) result else DeviceLocationUpdate.NOT_FOUND
                } else {
                    settings.setSelectedLocation(device.id)
                    refreshForecast(device, force = result == DeviceLocationUpdate.MOVED)
                }
            } finally {
                locating.value = false
            }
        }
    }
}
