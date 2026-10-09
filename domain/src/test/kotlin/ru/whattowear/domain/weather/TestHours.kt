package ru.whattowear.domain.weather

import ru.whattowear.domain.model.HourlyWeather
import ru.whattowear.domain.model.PrecipType
import ru.whattowear.domain.model.WeatherKind

/** Удобный конструктор усреднённого часа для тестов. */
fun hour(
    time: Long,
    temp: Double = 10.0,
    feels: Double = temp,
    precip: Double = 0.0,
    probability: Int = if (precip > 0) 80 else 0,
    type: PrecipType = if (precip > 0) PrecipType.RAIN else PrecipType.NONE,
    wind: Double = 2.0,
    gust: Double = wind * 1.5,
    uv: Double? = 1.0,
    tempSpread: Double = 0.5,
    precipSpread: Double = 0.0,
    wetShare: Double = if (precip > 0) 1.0 else 0.0,
    models: Int = 5,
    kind: WeatherKind = if (precip > 0) WeatherKind.RAIN else WeatherKind.CLOUDY,
) = HourlyWeather(
    time = time,
    temperature = temp,
    apparentTemperature = feels,
    humidity = 70,
    cloudCover = 60,
    visibilityM = 20000.0,
    precipProbability = probability,
    precipitationMm = precip,
    rainMm = if (type == PrecipType.SNOW) 0.0 else precip,
    snowfallCm = if (type == PrecipType.SNOW) precip * 0.7 else 0.0,
    precipType = type,
    windSpeed = wind,
    windGust = gust,
    windDirection = 180,
    uvIndex = uv,
    kind = kind,
    isDay = true,
    tempSpread = tempSpread,
    precipSpread = precipSpread,
    wetShare = wetShare,
    modelCount = models,
)
