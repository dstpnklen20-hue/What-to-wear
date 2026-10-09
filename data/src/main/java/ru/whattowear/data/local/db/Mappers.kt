package ru.whattowear.data.local.db

import ru.whattowear.domain.model.Category
import ru.whattowear.domain.model.ClothingColor
import ru.whattowear.domain.model.ClothingItem
import ru.whattowear.domain.model.DailyWeather
import ru.whattowear.domain.model.HourlyWeather
import ru.whattowear.domain.model.Layer
import ru.whattowear.domain.model.Location
import ru.whattowear.domain.model.PrecipType
import ru.whattowear.domain.model.Season
import ru.whattowear.domain.model.Style
import ru.whattowear.domain.model.Subtype
import ru.whattowear.domain.model.WeatherKind
import java.time.LocalDate

internal inline fun <reified E : Enum<E>> enumOr(name: String, default: E): E =
    runCatching { enumValueOf<E>(name) }.getOrDefault(default)

fun LocationEntity.toDomain() = Location(
    id = id,
    name = name,
    region = region,
    country = country,
    latitude = latitude,
    longitude = longitude,
    timezone = timezone,
    isDeviceLocation = isDeviceLocation,
    sortOrder = sortOrder,
)

fun HourlyWeather.toEntity(locationId: Long) = ForecastHourEntity(
    locationId = locationId,
    time = time,
    temperature = temperature,
    apparentTemperature = apparentTemperature,
    humidity = humidity,
    cloudCover = cloudCover,
    visibilityM = visibilityM,
    precipProbability = precipProbability,
    precipitationMm = precipitationMm,
    rainMm = rainMm,
    snowfallCm = snowfallCm,
    precipType = precipType.name,
    windSpeed = windSpeed,
    windGust = windGust,
    windDirection = windDirection,
    uvIndex = uvIndex,
    kind = kind.name,
    isDay = isDay,
    tempSpread = tempSpread,
    precipSpread = precipSpread,
    wetShare = wetShare,
    modelCount = modelCount,
)

fun ForecastHourEntity.toDomain() = HourlyWeather(
    time = time,
    temperature = temperature,
    apparentTemperature = apparentTemperature,
    humidity = humidity,
    cloudCover = cloudCover,
    visibilityM = visibilityM,
    precipProbability = precipProbability,
    precipitationMm = precipitationMm,
    rainMm = rainMm,
    snowfallCm = snowfallCm,
    precipType = enumOr(precipType, PrecipType.NONE),
    windSpeed = windSpeed,
    windGust = windGust,
    windDirection = windDirection,
    uvIndex = uvIndex,
    kind = enumOr(kind, WeatherKind.CLOUDY),
    isDay = isDay,
    tempSpread = tempSpread,
    precipSpread = precipSpread,
    wetShare = wetShare,
    modelCount = modelCount,
)

fun DailyWeather.toEntity(locationId: Long) = ForecastDayEntity(
    locationId = locationId,
    date = date.toString(),
    tempMin = tempMin,
    tempMax = tempMax,
    feelsMin = feelsMin,
    feelsMax = feelsMax,
    precipSumMm = precipSumMm,
    precipProbMax = precipProbMax,
    precipType = precipType.name,
    windMax = windMax,
    gustMax = gustMax,
    uvMax = uvMax,
    sunrise = sunrise,
    sunset = sunset,
    kind = kind.name,
    isUncertain = isUncertain,
)

fun ForecastDayEntity.toDomain() = DailyWeather(
    date = LocalDate.parse(date),
    tempMin = tempMin,
    tempMax = tempMax,
    feelsMin = feelsMin,
    feelsMax = feelsMax,
    precipSumMm = precipSumMm,
    precipProbMax = precipProbMax,
    precipType = enumOr(precipType, PrecipType.NONE),
    windMax = windMax,
    gustMax = gustMax,
    uvMax = uvMax,
    sunrise = sunrise,
    sunset = sunset,
    kind = enumOr(kind, WeatherKind.CLOUDY),
    isUncertain = isUncertain,
)

fun Set<Season>.toMask(): Int = fold(0) { mask, s -> mask or (1 shl s.ordinal) }
fun Int.toSeasons(): Set<Season> = Season.entries.filter { this and (1 shl it.ordinal) != 0 }.toSet()

fun ClothingItemEntity.toDomain() = ClothingItem(
    id = id,
    name = name,
    category = enumOr(category, Category.ACCESSORIES),
    subtype = enumOr(subtype, Subtype.NONE),
    photoPath = photoPath,
    warmth = warmth.coerceIn(1, 5),
    waterproof = waterproof,
    windproof = windproof,
    layer = enumOr(layer, Layer.OUTER),
    style = enumOr(style, Style.CASUAL),
    color = enumOr(color, ClothingColor.MULTI),
    seasons = seasons.toSeasons(),
    isAvailable = isAvailable,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun ClothingItem.toEntity() = ClothingItemEntity(
    id = id,
    name = name,
    category = category.name,
    subtype = subtype.name,
    photoPath = photoPath,
    warmth = warmth.coerceIn(1, 5),
    waterproof = waterproof,
    windproof = windproof,
    layer = layer.name,
    style = style.name,
    color = color.name,
    seasons = seasons.toMask(),
    isAvailable = isAvailable,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
