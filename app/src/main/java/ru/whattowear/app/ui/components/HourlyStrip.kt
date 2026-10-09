package ru.whattowear.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ru.whattowear.domain.model.HourlyWeather
import ru.whattowear.domain.text.Fmt
import java.time.ZoneId

/**
 * Горизонтальная почасовая лента. Часы, когда пользователь вне дома, слегка подсвечены фоном.
 */
@Composable
fun HourlyStrip(
    hours: List<HourlyWeather>,
    zone: ZoneId,
    nowHour: Long?,
    isOutside: (HourlyWeather) -> Boolean,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = Dimens.screenPadding),
) {
    LazyRow(
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(hours, key = { it.time }) { h ->
            val outside = isOutside(h)
            val label = if (h.time == nowHour) "Сейчас" else Fmt.hour(h.time, zone)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .width(58.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(if (outside) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent)
                    .padding(vertical = 12.dp)
                    .semantics {
                        contentDescription = "$label: ${Fmt.temp(h.temperature)}, ${weatherTitle(h.kind)}" +
                            if (h.precipProbability >= 20) ", осадки ${h.precipProbability} %" else ""
                    },
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (h.time == nowHour) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                WeatherIcon(h.kind, h.isDay, size = 24.dp)
                Text(Fmt.temp(h.temperature), style = MaterialTheme.typography.titleMedium)
                // Вероятность осадков — единственное место ленты с акцентным цветом
                Text(
                    if (h.precipProbability >= 20) "${h.precipProbability}%" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.height(14.dp),
                )
            }
        }
    }
}
