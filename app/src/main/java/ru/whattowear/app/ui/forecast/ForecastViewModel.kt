package ru.whattowear.app.ui.forecast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.whattowear.domain.model.Forecast
import ru.whattowear.domain.model.Location
import ru.whattowear.domain.repository.FailureReason
import ru.whattowear.domain.repository.RefreshResult
import ru.whattowear.domain.repository.WeatherRepository
import ru.whattowear.domain.usecase.ObserveSelectedLocationUseCase
import ru.whattowear.domain.usecase.RefreshForecastUseCase
import javax.inject.Inject

data class ForecastUiState(
    val initialized: Boolean = false,
    val location: Location? = null,
    val forecast: Forecast? = null,
    val isRefreshing: Boolean = false,
    /** Индикатор сверху — только для обновления жестом. */
    val showPullIndicator: Boolean = false,
    val error: FailureReason? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ForecastViewModel @Inject constructor(
    private val observeSelectedLocation: ObserveSelectedLocationUseCase,
    weatherRepository: WeatherRepository,
    private val refreshForecast: RefreshForecastUseCase,
) : ViewModel() {

    private val refreshing = MutableStateFlow(false)
    private val manual = MutableStateFlow(false)
    private val error = MutableStateFlow<FailureReason?>(null)
    private val location = observeSelectedLocation()

    val state: StateFlow<ForecastUiState> = combine(
        location,
        location.flatMapLatest { if (it == null) flowOf(null) else weatherRepository.observeForecast(it.id) },
        combine(refreshing, manual) { r, m -> r to m },
        error,
    ) { loc, forecast, (isRefreshing, isManual), err ->
        ForecastUiState(true, loc, forecast, isRefreshing, isRefreshing && isManual, err)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ForecastUiState())

    fun refresh(force: Boolean) {
        if (refreshing.value) return
        viewModelScope.launch {
            refreshing.value = true
            manual.value = force
            try {
                val loc = observeSelectedLocation.current()
                val result = if (loc != null) refreshForecast(loc, force) else RefreshResult.UpToDate
                error.value = (result as? RefreshResult.Failure)?.reason
            } finally {
                refreshing.value = false
            }
        }
    }
}
