package ru.whattowear.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.whattowear.app.R
import ru.whattowear.domain.model.WeatherKind

@DrawableRes
fun weatherIconRes(kind: WeatherKind, isDay: Boolean = true): Int = when (kind) {
    WeatherKind.CLEAR -> if (isDay) R.drawable.ic_wx_clear_day else R.drawable.ic_wx_clear_night
    WeatherKind.PARTLY_CLOUDY -> if (isDay) R.drawable.ic_wx_partly_day else R.drawable.ic_wx_partly_night
    WeatherKind.CLOUDY -> R.drawable.ic_wx_cloudy
    WeatherKind.FOG -> R.drawable.ic_wx_fog
    WeatherKind.DRIZZLE -> R.drawable.ic_wx_drizzle
    WeatherKind.RAIN -> R.drawable.ic_wx_rain
    WeatherKind.SLEET, WeatherKind.FREEZING_RAIN -> R.drawable.ic_wx_sleet
    WeatherKind.SNOW -> R.drawable.ic_wx_snow
    WeatherKind.THUNDER -> R.drawable.ic_wx_thunder
}

fun weatherTitle(kind: WeatherKind): String = when (kind) {
    WeatherKind.CLEAR -> "Ясно"
    WeatherKind.PARTLY_CLOUDY -> "Переменная облачность"
    WeatherKind.CLOUDY -> "Пасмурно"
    WeatherKind.FOG -> "Туман"
    WeatherKind.DRIZZLE -> "Морось"
    WeatherKind.RAIN -> "Дождь"
    WeatherKind.SLEET -> "Мокрый снег"
    WeatherKind.SNOW -> "Снег"
    WeatherKind.FREEZING_RAIN -> "Ледяной дождь"
    WeatherKind.THUNDER -> "Гроза"
}

@Composable
fun WeatherIcon(
    kind: WeatherKind,
    isDay: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = LocalContentColor.current,
) {
    Icon(
        painter = painterResource(weatherIconRes(kind, isDay)),
        contentDescription = weatherTitle(kind),
        tint = tint,
        modifier = modifier.size(size),
    )
}
