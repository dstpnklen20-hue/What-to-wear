package ru.whattowear.app.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import ru.whattowear.domain.model.Forecast
import ru.whattowear.domain.model.Location
import ru.whattowear.domain.model.UserSettings
import ru.whattowear.domain.outfit.ComfortRating
import ru.whattowear.domain.outfit.OutfitRecommendation
import ru.whattowear.domain.outfit.OutingWindow
import ru.whattowear.domain.repository.DeviceLocationSource
import ru.whattowear.domain.repository.FailureReason
import ru.whattowear.domain.repository.FeedbackEntry
import ru.whattowear.domain.repository.OutfitHistoryRepository
import ru.whattowear.domain.repository.OutfitLogEntry
import ru.whattowear.domain.repository.RefreshResult
import ru.whattowear.domain.repository.SettingsRepository
import ru.whattowear.domain.repository.WardrobeRepository
import ru.whattowear.domain.repository.WeatherRepository
import ru.whattowear.domain.usecase.BuildRecommendationUseCase
import ru.whattowear.domain.usecase.DeviceLocationUpdate
import ru.whattowear.domain.usecase.NextVariantUseCase
import ru.whattowear.domain.usecase.ObserveSelectedLocationUseCase
import ru.whattowear.domain.usecase.RecordOutfitUseCase
import ru.whattowear.domain.usecase.RefreshForecastUseCase
import ru.whattowear.domain.usecase.SubmitFeedbackUseCase
import ru.whattowear.domain.usecase.UpdateDeviceLocationUseCase
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/** Состояние загрузки, общее для экранов с погодой. */
data class RefreshStatus(
    val isRefreshing: Boolean = false,
    /** Обновление запущено жестом «потянуть вниз» — только тогда показываем индикатор сверху. */
    val manual: Boolean = false,
    val error: FailureReason? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodayViewModel @Inject constructor(
    private val observeSelectedLocation: ObserveSelectedLocationUseCase,
    private val weatherRepository: WeatherRepository,
    private val refreshForecast: RefreshForecastUseCase,
    private val updateDeviceLocation: UpdateDeviceLocationUseCase,
    private val deviceLocation: DeviceLocationSource,
    private val history: OutfitHistoryRepository,
    private val buildRecommendation: BuildRecommendationUseCase,
    private val recordOutfit: RecordOutfitUseCase,
    private val nextVariant: NextVariantUseCase,
    private val submitFeedback: SubmitFeedbackUseCase,
    wardrobe: WardrobeRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val status = MutableStateFlow(RefreshStatus())
    private val clock = MutableStateFlow(System.currentTimeMillis())
    private val permission = MutableStateFlow(deviceLocation.hasPermission())

    private val settings = settingsRepository.settings
    private val location = observeSelectedLocation()
    private val forecast = location.flatMapLatest { loc ->
        if (loc == null) flowOf(null) else weatherRepository.observeForecast(loc.id)
    }
    private val items = wardrobe.observeItems()

    /** Дата окна «вне дома» (сегодня или завтра) и «сегодня» — в часовом поясе места. */
    private val dates: Flow<Pair<LocalDate, LocalDate>?> = combine(forecast, settings, clock) { fc, s, now ->
        fc ?: return@combine null
        val window = OutingWindow.resolve(s.leaveTime, s.returnTime, fc.zone, now)
        window.date to Instant.ofEpochMilli(now).atZone(fc.zone).toLocalDate()
    }.distinctUntilChanged()

    private val windowLog: Flow<OutfitLogEntry?> = dates.flatMapLatest { d ->
        if (d == null) flowOf(null) else history.observeLog(d.first)
    }
    private val recentLogs: Flow<List<OutfitLogEntry>> = dates.flatMapLatest { d ->
        if (d == null) flowOf(emptyList()) else flow { emit(history.logsSince(d.first.minusDays(3))) }
    }
    private val todayFeedback: Flow<Pair<OutfitLogEntry?, FeedbackEntry?>> = dates.flatMapLatest { d ->
        if (d == null) flowOf(null to null)
        else combine(history.observeLog(d.second), history.observeFeedback(d.second)) { log, fb -> log to fb }
    }

    private val recommendation: Flow<OutfitRecommendation?> = combine(
        combine(location, forecast) { l, f -> l to f },
        settings,
        items,
        combine(windowLog.map { it?.variantIndex ?: 0 }.distinctUntilChanged(), recentLogs) { v, logs -> v to logs },
        clock,
    ) { (loc, fc), s, list, (variant, logs), now ->
        fc ?: return@combine null
        buildRecommendation(fc, loc, list, s, logs, variant, now)
    }.flowOn(Dispatchers.Default)

    val state: StateFlow<TodayUiState> = combine(
        combine(location, forecast, recommendation) { l, f, r -> Triple(l, f, r) },
        combine(settings, items.map { list -> list.isEmpty() }.distinctUntilChanged()) { s, empty -> s to empty },
        status,
        combine(clock, permission) { now, p -> now to p },
        todayFeedback,
    ) { (loc, fc, rec), (s, wardrobeEmpty), st, (now, hasPermission), (todayLog, feedback) ->
        buildState(loc, fc, rec, s, wardrobeEmpty, st, now, hasPermission, todayLog, feedback)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    init {
        // Выбрали другое место — подтянуть его прогноз (первую загрузку делает onResume вместе с GPS)
        viewModelScope.launch {
            location.filterNotNull().distinctUntilChangedBy { it.id }.drop(1).collect { refresh(force = false, updateGps = false) }
        }
        // Запоминаем комплект дня: нужен для «давно не надевали» и вечернего отзыва
        viewModelScope.launch {
            combine(recommendation, location) { r, l -> r to l }.collect { (r, l) -> if (r != null) recordOutfit(r, l?.id) }
        }
    }

    fun onResume() {
        clock.value = System.currentTimeMillis()
        permission.value = deviceLocation.hasPermission()
        refresh(force = false)
    }

    fun onPullToRefresh() = refresh(force = true)

    fun onLocationPermissionResult(granted: Boolean) {
        permission.value = granted
        if (granted) refresh(force = false)
    }

    fun onNextVariant() {
        val rec = state.value.recommendation ?: return
        viewModelScope.launch { nextVariant(rec, state.value.location?.id) }
    }

    fun onFeedback(rating: ComfortRating) {
        val date = state.value.feedbackDate ?: return
        viewModelScope.launch { submitFeedback(date, rating) }
    }

    private fun refresh(force: Boolean, updateGps: Boolean = true) {
        if (status.value.isRefreshing) return
        viewModelScope.launch {
            status.update { it.copy(isRefreshing = true, manual = force) }
            var result: RefreshResult = RefreshResult.UpToDate
            try {
                // Сначала прогноз для уже известного места — не ждём GPS (он может искать до 10 секунд)
                val before = observeSelectedLocation.current()
                result = before?.let { refreshForecast(it, force) } ?: RefreshResult.UpToDate
                if (updateGps) {
                    val gps = withTimeoutOrNull(20_000) { updateDeviceLocation() } ?: DeviceLocationUpdate.UNAVAILABLE
                    val after = observeSelectedLocation.current()
                    val moved = gps == DeviceLocationUpdate.MOVED && after?.isDeviceLocation == true
                    if (after != null && (after.id != before?.id || moved)) result = refreshForecast(after, force = moved)
                }
            } finally {
                // Флаг сбрасывается всегда, иначе зависшее обновление заблокирует все следующие
                clock.value = System.currentTimeMillis()
                status.value = RefreshStatus(isRefreshing = false, error = (result as? RefreshResult.Failure)?.reason)
            }
        }
    }

    private fun buildState(
        location: Location?,
        forecast: Forecast?,
        recommendation: OutfitRecommendation?,
        settings: UserSettings,
        wardrobeEmpty: Boolean,
        status: RefreshStatus,
        now: Long,
        hasPermission: Boolean,
        todayLog: OutfitLogEntry?,
        feedback: FeedbackEntry?,
    ): TodayUiState {
        val weather = forecast?.let { WeatherSnapshot.build(it, settings, now) }
        // Отзыв за сегодня спрашиваем после времени возвращения, если сегодня был комплект
        val today = todayLog?.date
        val askFeedback = forecast != null && today != null && feedback == null &&
            now >= today.atTime(settings.returnTime).atZone(forecast.zone).toInstant().toEpochMilli()
        return TodayUiState(
            initialized = true,
            location = location,
            weather = weather,
            recommendation = recommendation,
            wardrobeEmpty = wardrobeEmpty,
            settings = settings,
            isRefreshing = status.isRefreshing,
            showPullIndicator = status.isRefreshing && status.manual,
            error = status.error,
            hasLocationPermission = hasPermission,
            now = now,
            feedbackDate = if (askFeedback) today else null,
            feedbackGiven = feedback?.rating,
        )
    }
}
