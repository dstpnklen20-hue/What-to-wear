package ru.whattowear.app.ui.today

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.whattowear.app.ui.components.Dimens
import ru.whattowear.app.ui.components.EmptyState
import ru.whattowear.app.ui.components.HourlyStrip
import ru.whattowear.app.ui.components.NoteBanner
import ru.whattowear.app.ui.components.SectionLabel
import ru.whattowear.app.ui.components.WeatherIcon
import ru.whattowear.app.ui.components.weatherTitle
import ru.whattowear.domain.repository.FailureReason
import ru.whattowear.domain.text.Fmt

@Composable
fun TodayScreen(
    onOpenWardrobe: () -> Unit,
    onAddItem: () -> Unit,
    onOpenLocations: () -> Unit,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        viewModel.onLocationPermissionResult(result.values.any { it })
    }
    val requestLocation = { permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION)) }

    Box(Modifier.fillMaxSize().statusBarsPadding()) {
        when {
            !state.initialized -> Unit
            state.location == null -> EmptyState(
                icon = Icons.Outlined.LocationOn,
                title = "Где вы находитесь?",
                message = when {
                    !state.hasLocationPermission ->
                        "Чтобы показать погоду, разрешите доступ к приблизительному местоположению или выберите город вручную."
                    state.isRefreshing -> "Определяем местоположение…"
                    else -> "Не удалось определить местоположение. Проверьте, что геолокация включена, или выберите город вручную."
                },
                primaryAction = when {
                    !state.hasLocationPermission -> "Определить местоположение" to requestLocation
                    state.isRefreshing -> null
                    else -> "Повторить" to viewModel::onPullToRefresh
                },
                secondaryAction = "Выбрать город" to onOpenLocations,
            )
            state.weather == null -> when {
                state.isRefreshing -> Loading()
                else -> EmptyState(
                    icon = Icons.Outlined.CloudOff,
                    title = if (state.error == FailureReason.NO_NETWORK) "Нет подключения" else "Прогноз недоступен",
                    message = if (state.error == FailureReason.NO_NETWORK) {
                        "Подключитесь к интернету, чтобы загрузить прогноз. После первой загрузки приложение работает и офлайн."
                    } else {
                        "Сервисы погоды сейчас не отвечают. Попробуйте ещё раз чуть позже."
                    },
                    primaryAction = "Повторить" to viewModel::onPullToRefresh,
                    secondaryAction = "Выбрать другой город" to onOpenLocations,
                )
            }
            else -> TodayContent(
                state = state,
                onRefresh = viewModel::onPullToRefresh,
                onOpenLocations = onOpenLocations,
                outfit = { modifier ->
                    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (state.feedbackDate != null) {
                            FeedbackCard(onRate = viewModel::onFeedback)
                        } else if (state.feedbackGiven != null) {
                            Text(
                                "Спасибо за отзыв («${state.feedbackGiven!!.title.lowercase()}») — подбор подстроен.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        OutfitCard(
                            recommendation = state.recommendation,
                            wardrobeEmpty = state.wardrobeEmpty,
                            onNextVariant = viewModel::onNextVariant,
                            onOpenWardrobe = onOpenWardrobe,
                            onAddItem = onAddItem,
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(32.dp))
            Text("Загружаем прогноз…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodayContent(
    state: TodayUiState,
    onRefresh: () -> Unit,
    onOpenLocations: () -> Unit,
    outfit: @Composable (Modifier) -> Unit,
) {
    val weather = state.weather ?: return
    PullToRefreshBox(isRefreshing = state.showPullIndicator, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            if (maxWidth >= 840.dp) {
                // Планшет: погода слева, комплект справа
                Row(Modifier.fillMaxSize()) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
                        WeatherSection(state, weather, onOpenLocations)
                        DetailsGrid(weather, Modifier.padding(horizontal = Dimens.screenPadding, vertical = 24.dp))
                    }
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState())
                            .padding(top = 16.dp, end = Dimens.screenPadding, bottom = 32.dp),
                    ) {
                        outfit(Modifier.fillMaxWidth())
                    }
                }
            } else {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Column(Modifier.widthIn(max = Dimens.contentMaxWidth)) {
                        WeatherSection(state, weather, onOpenLocations)
                        outfit(Modifier.fillMaxWidth().padding(start = Dimens.screenPadding, end = Dimens.screenPadding, top = 24.dp))
                        DetailsGrid(weather, Modifier.padding(horizontal = Dimens.screenPadding, vertical = 24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherSection(state: TodayUiState, weather: WeatherSnapshot, onOpenLocations: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val current = weather.current
    val pad = Modifier.padding(horizontal = Dimens.screenPadding)

    // Место и время обновления
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = Dimens.screenPadding, end = Dimens.screenPadding, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).clickable(onClick = onOpenLocations).padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.location?.isDeviceLocation == true) {
                Icon(Icons.Outlined.LocationOn, contentDescription = "Текущее местоположение", modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(state.location?.name.orEmpty(), style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Icon(Icons.Outlined.ExpandMore, contentDescription = "Сменить место", modifier = Modifier.size(20.dp))
        }
        Text(
            "Обновлено ${Fmt.time(weather.fetchedAt)}",
            style = MaterialTheme.typography.labelSmall,
            color = if (weather.isStale) colors.primary else colors.onSurfaceVariant,
        )
    }

    // Крупно: текущая температура и ощущаемая
    Row(pad.padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(Fmt.tempPlain(current.temperature), style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.width(16.dp))
        WeatherIcon(current.kind, current.isDay, size = 44.dp, tint = colors.onSurface)
    }
    Text(
        "Ощущается как ${Fmt.tempPlain(current.apparentTemperature)} · ${weatherTitle(current.kind)}",
        style = MaterialTheme.typography.bodyLarge,
        color = colors.onSurfaceVariant,
        modifier = pad,
    )
    weather.today?.let { day ->
        Text(
            "Сегодня ${Fmt.temp(day.tempMin)}…${Fmt.temp(day.tempMax)}",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            modifier = pad.padding(top = 2.dp),
        )
    }

    weather.summary?.let {
        Text(it, style = MaterialTheme.typography.bodyLarge, modifier = pad.padding(top = 20.dp))
    }

    // Пометки: неуверенный прогноз, нет сети, резервный источник
    Column(pad.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AnimatedVisibility(weather.windowUncertainty.any, enter = fadeIn()) {
            val what = when {
                weather.windowUncertainty.temperature && weather.windowUncertainty.precipitation -> "по температуре и осадкам"
                weather.windowUncertainty.temperature -> "по температуре"
                else -> "по осадкам"
            }
            NoteBanner(
                Icons.Outlined.Info,
                "Прогноз неуверенный: модели расходятся $what. Возьмите вещь «на всякий случай».",
                emphasized = true,
            )
        }
        state.error?.let { error ->
            val text = if (error == FailureReason.NO_NETWORK) "Нет сети." else "Сервис погоды не отвечает."
            NoteBanner(Icons.Outlined.CloudOff, "$text Показан прогноз от ${Fmt.time(weather.fetchedAt)}.")
        }
        if (weather.usedFallback) {
            NoteBanner(Icons.Outlined.Info, "Open-Meteo недоступен — прогноз по данным MET Norway.")
        }
    }

    SectionLabel("По часам", pad.padding(top = 24.dp, bottom = 8.dp))
    HourlyStrip(
        hours = weather.strip,
        zone = weather.zone,
        nowHour = current.time,
        isOutside = { weather.window.contains(it) },
        contentPadding = PaddingValues(horizontal = Dimens.screenPadding - 4.dp),
    )
    Text(
        "Подсвечены часы вне дома: ${Fmt.hour(weather.window.start, weather.zone)}–${Fmt.hour(weather.window.end, weather.zone)}" +
            if (weather.window.isTomorrow) " (завтра)" else "",
        style = MaterialTheme.typography.bodySmall,
        color = colors.onSurfaceVariant,
        modifier = pad.padding(top = 6.dp),
    )

}

@Composable
private fun DetailsGrid(weather: WeatherSnapshot, modifier: Modifier) {
    val current = weather.current
    val day = weather.today
    val items = buildList {
        add("Ветер" to "${Fmt.number(current.windSpeed)} м/с, порывы ${Fmt.number(current.windGust)}")
        add("Осадки" to if (current.precipitationMm > 0) "${Fmt.number(current.precipitationMm)} мм · ${current.precipProbability} %" else "${current.precipProbability} %")
        current.humidity?.let { add("Влажность" to "$it %") }
        add("УФ-индекс" to ((day?.uvMax ?: current.uvIndex)?.let { "${Fmt.number(it)} макс." } ?: "—"))
        val sunrise = day?.sunrise
        val sunset = day?.sunset
        if (sunrise != null && sunset != null) {
            add("Восход" to Fmt.hour(sunrise, weather.zone))
            add("Закат" to Fmt.hour(sunset, weather.zone))
        }
        current.visibilityM?.let { add("Видимость" to if (it >= 10_000) "более 10 км" else "${Fmt.number(it / 1000)} км") }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { (label, value) ->
                    Column(Modifier.weight(1f)) {
                        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(2.dp))
                        Text(value, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
