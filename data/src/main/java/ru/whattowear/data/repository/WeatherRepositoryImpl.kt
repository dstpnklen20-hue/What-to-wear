package ru.whattowear.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import ru.whattowear.data.local.db.ForecastDao
import ru.whattowear.data.local.db.ForecastMetaEntity
import ru.whattowear.data.local.db.toDomain
import ru.whattowear.data.local.db.toEntity
import ru.whattowear.data.remote.MetNoApi
import ru.whattowear.data.remote.MetNoParser
import ru.whattowear.data.remote.OpenMeteoApi
import ru.whattowear.data.remote.OpenMeteoParser
import ru.whattowear.domain.model.Forecast
import ru.whattowear.domain.model.Location
import ru.whattowear.domain.repository.FailureReason
import ru.whattowear.domain.repository.RefreshResult
import ru.whattowear.domain.repository.WeatherRepository
import ru.whattowear.domain.weather.DailyAggregator
import ru.whattowear.domain.weather.ModelBlender
import ru.whattowear.domain.weather.ModelSeries
import ru.whattowear.domain.weather.RawForecast
import ru.whattowear.domain.weather.SunCalculator
import ru.whattowear.domain.weather.SunTimes
import ru.whattowear.domain.weather.WeatherModel
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeatherRepositoryImpl @Inject constructor(
    private val openMeteo: OpenMeteoApi,
    private val metNo: MetNoApi,
    private val dao: ForecastDao,
) : WeatherRepository {

    override fun observeForecast(locationId: Long): Flow<Forecast?> =
        combine(
            dao.observeMeta(locationId),
            dao.observeHours(locationId),
            dao.observeDays(locationId),
        ) { meta, hours, days ->
            meta?.let { toForecast(it, hours.map { h -> h.toDomain() }, days.map { d -> d.toDomain() }) }
        }.distinctUntilChanged()

    override suspend fun getForecast(locationId: Long): Forecast? {
        val meta = dao.getMeta(locationId) ?: return null
        return toForecast(meta, dao.getHours(locationId).map { it.toDomain() }, dao.getDays(locationId).map { it.toDomain() })
    }

    override suspend fun refresh(location: Location): RefreshResult = withContext(Dispatchers.IO) {
        coroutineScope {
            // Оба источника запрашиваются параллельно; MET Norway — для сверки и как резерв
            val openMeteoResult = async { runCatching { loadOpenMeteo(location) } }
            val metResult = async { runCatching { loadMetNo(location) } }
            val om = openMeteoResult.await()
            val met = metResult.await()

            val omData = om.getOrNull()
            val metSeries = met.getOrNull()?.takeIf { it.hours.isNotEmpty() }
            if (omData == null && metSeries == null) {
                val error = om.exceptionOrNull()
                return@coroutineScope RefreshResult.Failure(
                    if (error is IOException) FailureReason.NO_NETWORK else FailureReason.SERVER,
                )
            }

            val zone = omData?.zone ?: location.timezone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()
            val series = (omData?.series ?: emptyList()) + listOfNotNull(metSeries)
            val sunTimes = omData?.sunTimes?.takeIf { it.isNotEmpty() } ?: computeSunTimes(series, zone, location)
            val raw = RawForecast(zone = zone, series = series, sunTimes = sunTimes, usedFallback = omData == null)

            val nowSec = System.currentTimeMillis() / 1000
            val hours = ModelBlender.blend(raw, nowSec)
            if (hours.isEmpty()) return@coroutineScope RefreshResult.Failure(FailureReason.SERVER)
            val today = Instant.ofEpochSecond(nowSec).atZone(zone).toLocalDate()
            val days = DailyAggregator.aggregate(hours, zone, sunTimes, fromDate = today)

            val meta = ForecastMetaEntity(
                locationId = location.id,
                zone = zone.id,
                fetchedAt = System.currentTimeMillis(),
                sources = series.map { it.model.label }.distinct().joinToString(","),
                usedFallback = raw.usedFallback,
            )
            dao.replace(meta, hours.map { it.toEntity(location.id) }, days.map { it.toEntity(location.id) })
            RefreshResult.Success
        }
    }

    private suspend fun loadOpenMeteo(location: Location): OpenMeteoParser.Result {
        val models = WeatherModel.openMeteoModels
        val json = try {
            openMeteo.forecast(
                latitude = location.latitude,
                longitude = location.longitude,
                models = models.joinToString(",") { it.apiId!! },
            )
        } catch (e: HttpException) {
            throw IllegalStateException("Open-Meteo HTTP ${e.code()}", e)
        }
        val result = OpenMeteoParser.parse(json, models)
        if (result.series.isEmpty()) throw SerializationException("Open-Meteo не вернул ни одной модели")
        return result
    }

    private suspend fun loadMetNo(location: Location): ModelSeries {
        val json = metNo.forecast(
            latitude = String.format(Locale.US, "%.4f", location.latitude),
            longitude = String.format(Locale.US, "%.4f", location.longitude),
        )
        return MetNoParser.parse(json)
    }

    private fun computeSunTimes(series: List<ModelSeries>, zone: ZoneId, location: Location): Map<LocalDate, SunTimes> =
        series.flatMap { it.hours }
            .map { Instant.ofEpochSecond(it.time).atZone(zone).toLocalDate() }
            .distinct()
            .associateWith { SunCalculator.sunTimes(it, zone, location.latitude, location.longitude) }

    private fun toForecast(
        meta: ForecastMetaEntity,
        hours: List<ru.whattowear.domain.model.HourlyWeather>,
        days: List<ru.whattowear.domain.model.DailyWeather>,
    ) = Forecast(
        locationId = meta.locationId,
        zone = runCatching { ZoneId.of(meta.zone) }.getOrDefault(ZoneId.systemDefault()),
        fetchedAt = meta.fetchedAt,
        sources = meta.sources.split(',').filter { it.isNotBlank() },
        usedFallback = meta.usedFallback,
        hours = hours,
        days = days,
    )
}
