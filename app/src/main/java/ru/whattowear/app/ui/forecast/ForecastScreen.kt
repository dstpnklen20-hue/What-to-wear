package ru.whattowear.app.ui.forecast

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.whattowear.app.ui.components.Dimens
import ru.whattowear.app.ui.components.EmptyState
import ru.whattowear.app.ui.components.HourlyStrip
import ru.whattowear.app.ui.components.NoteBanner
import ru.whattowear.app.ui.components.ScreenHeader
import ru.whattowear.app.ui.components.ThinDivider
import ru.whattowear.app.ui.components.WeatherIcon
import ru.whattowear.app.ui.components.weatherTitle
import ru.whattowear.app.ui.navigation.MotionSpec
import ru.whattowear.domain.model.DailyWeather
import ru.whattowear.domain.model.Forecast
import ru.whattowear.domain.model.PrecipType
import ru.whattowear.domain.repository.FailureReason
import ru.whattowear.domain.text.Fmt
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForecastScreen(onOpenLocations: () -> Unit, viewModel: ForecastViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh(force = false) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader(
            title = "7 дней",
            subtitle = state.location?.name,
            actions = { TextButton(onClick = onOpenLocations) { Text("Место") } },
        )
        val forecast = state.forecast
        when {
            !state.initialized -> Unit
            state.location == null -> EmptyState(
                Icons.Outlined.CalendarMonth,
                "Место не выбрано",
                "Выберите город или разрешите определение местоположения на экране «Сегодня».",
                primaryAction = "Выбрать город" to onOpenLocations,
            )
            forecast == null || forecast.days.isEmpty() -> EmptyState(
                Icons.Outlined.CloudOff,
                if (state.isRefreshing) "Загружаем прогноз…" else "Прогноза пока нет",
                if (state.error == FailureReason.NO_NETWORK) "Нет подключения к интернету." else "Потяните вниз или попробуйте позже.",
                primaryAction = if (state.isRefreshing) null else "Обновить" to { viewModel.refresh(force = true) },
            )
            else -> PullToRefreshBox(
                isRefreshing = state.showPullIndicator,
                onRefresh = { viewModel.refresh(force = true) },
                modifier = Modifier.fillMaxSize(),
            ) {
                DaysList(forecast, state.error)
            }
        }
    }
}

@Composable
private fun DaysList(forecast: Forecast, error: FailureReason?) {
    val days = forecast.days.take(7)
    val weekMin = days.minOf { it.tempMin }
    val weekMax = days.maxOf { it.tempMax }
    val today = Instant.ofEpochMilli(System.currentTimeMillis()).atZone(forecast.zone).toLocalDate()
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = Dimens.contentMaxWidth).fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            if (error != null) {
                item {
                    NoteBanner(
                        Icons.Outlined.CloudOff,
                        "Нет связи с сервисом. Прогноз от ${Fmt.time(forecast.fetchedAt)}.",
                        modifier = Modifier.padding(horizontal = Dimens.screenPadding, vertical = 8.dp),
                    )
                }
            }
            itemsIndexed(days, key = { _, d -> d.date.toString() }) { index, day ->
                if (index > 0) ThinDivider(Modifier.padding(horizontal = Dimens.screenPadding))
                val isExpanded = expanded == day.date.toString()
                DayRow(
                    day = day,
                    today = today,
                    weekMin = weekMin,
                    weekMax = weekMax,
                    expanded = isExpanded,
                    onClick = { expanded = if (isExpanded) null else day.date.toString() },
                )
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically(MotionSpec.content()) + fadeIn(MotionSpec.content()),
                    exit = shrinkVertically(MotionSpec.content()) + fadeOut(MotionSpec.exit()),
                ) {
                    DayDetails(day, forecast)
                }
            }
            item {
                Text(
                    "Источники: ${forecast.sources.joinToString(", ")}. Итог — взвешенное среднее моделей.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Dimens.screenPadding, vertical = 20.dp),
                )
            }
        }
    }
}

@Composable
private fun DayRow(
    day: DailyWeather,
    today: LocalDate,
    weekMin: Double,
    weekMax: Double,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.screenPadding, vertical = 16.dp)
            .semantics {
                contentDescription = "${Fmt.dayName(day.date, today)}: ${weatherTitle(day.kind)}, от ${Fmt.temp(day.tempMin)} до ${Fmt.temp(day.tempMax)}"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(Fmt.dayName(day.date, today), style = MaterialTheme.typography.titleMedium)
            Text(Fmt.shortDayMonth(day.date), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        WeatherIcon(day.kind, isDay = true, size = 26.dp)
        Text(
            if (day.precipProbMax >= 20 && day.precipType != PrecipType.NONE) "${day.precipProbMax}%" else "",
            style = MaterialTheme.typography.labelMedium,
            color = colors.primary,
            modifier = Modifier.width(44.dp).padding(start = 8.dp),
        )
        Text(Fmt.temp(day.tempMin), style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant, modifier = Modifier.width(40.dp))
        TemperatureRange(day.tempMin, day.tempMax, weekMin, weekMax, Modifier.width(64.dp).height(4.dp))
        Text(Fmt.temp(day.tempMax), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(44.dp).padding(start = 8.dp))
        if (day.isUncertain && !expanded) {
            Icon(Icons.Outlined.Info, contentDescription = "Прогноз неуверенный", tint = colors.primary, modifier = Modifier.size(14.dp))
        } else {
            Spacer(Modifier.width(14.dp))
        }
    }
}

/** Отрезок температур дня на шкале всей недели — нейтральная линия без градиентов. */
@Composable
private fun TemperatureRange(min: Double, max: Double, weekMin: Double, weekMax: Double, modifier: Modifier) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val fill = MaterialTheme.colorScheme.onSurface
    Canvas(modifier) {
        val span = (weekMax - weekMin).coerceAtLeast(1.0)
        val y = size.height / 2
        drawLine(track, Offset(0f, y), Offset(size.width, y), strokeWidth = size.height, cap = StrokeCap.Round)
        val start = ((min - weekMin) / span * size.width).toFloat()
        val end = ((max - weekMin) / span * size.width).toFloat().coerceAtLeast(start + 1f)
        drawLine(fill, Offset(start, y), Offset(end, y), strokeWidth = size.height, cap = StrokeCap.Round)
    }
}

@Composable
private fun DayDetails(day: DailyWeather, forecast: Forecast) {
    val colors = MaterialTheme.colorScheme
    val dayHours = forecast.hours.filter {
        Instant.ofEpochSecond(it.time).atZone(forecast.zone).toLocalDate() == day.date &&
            Instant.ofEpochSecond(it.time).atZone(forecast.zone).hour % 3 == 0
    }
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HourlyStrip(
            hours = dayHours,
            zone = forecast.zone,
            nowHour = null,
            isOutside = { false },
            contentPadding = PaddingValues(horizontal = Dimens.screenPadding - 4.dp),
        )
        val facts = buildList {
            add("Ощущается" to "${Fmt.temp(day.feelsMin)}…${Fmt.temp(day.feelsMax)}")
            add("Осадки" to if (day.precipSumMm > 0) "${Fmt.number(day.precipSumMm)} мм, до ${day.precipProbMax} %" else "нет")
            add("Ветер" to "до ${Fmt.number(day.windMax)} м/с, порывы ${Fmt.number(day.gustMax)}")
            add("УФ-индекс" to (day.uvMax?.let { Fmt.number(it) } ?: "—"))
            val sunrise = day.sunrise
            val sunset = day.sunset
            if (sunrise != null && sunset != null) {
                add("Восход и закат" to "${Fmt.hour(sunrise, forecast.zone)} · ${Fmt.hour(sunset, forecast.zone)}")
            }
        }
        Column(Modifier.padding(horizontal = Dimens.screenPadding), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            facts.forEach { (label, value) ->
                Row {
                    Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text(value, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (day.isUncertain) {
            NoteBanner(
                Icons.Outlined.Info,
                "Модели расходятся — прогноз на этот день неуверенный.",
                emphasized = true,
                modifier = Modifier.padding(horizontal = Dimens.screenPadding),
            )
        }
    }
}
