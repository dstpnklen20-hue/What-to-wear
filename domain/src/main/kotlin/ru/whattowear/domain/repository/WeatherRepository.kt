package ru.whattowear.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.whattowear.domain.model.Forecast
import ru.whattowear.domain.model.GeoPlace
import ru.whattowear.domain.model.LatLon
import ru.whattowear.domain.model.Location

enum class FailureReason { NO_NETWORK, SERVER }

sealed interface RefreshResult {
    data object Success : RefreshResult
    /** Свежий прогноз ещё в кеше — загрузка не понадобилась. */
    data object UpToDate : RefreshResult
    data class Failure(val reason: FailureReason) : RefreshResult
}

interface WeatherRepository {
    /** Последний сохранённый (усреднённый) прогноз места; null — ещё ни разу не загружался. */
    fun observeForecast(locationId: Long): Flow<Forecast?>

    suspend fun getForecast(locationId: Long): Forecast?

    /** Загрузить прогноз по всем моделям, усреднить и сохранить в кеш. */
    suspend fun refresh(location: Location): RefreshResult
}

interface LocationRepository {
    fun observeLocations(): Flow<List<Location>>
    suspend fun getLocations(): List<Location>
    suspend fun getLocation(id: Long): Location?

    /** Поиск городов (Open-Meteo Geocoding). Бросает исключение при ошибке сети. */
    suspend fun search(query: String): List<GeoPlace>
    suspend fun addPlace(place: GeoPlace): Long
    suspend fun delete(id: Long)

    /** Создать или обновить запись «Моё местоположение»; возвращает её id. */
    suspend fun upsertDeviceLocation(coordinates: LatLon, name: String?): Long
}

/** Текущее местоположение устройства (FusedLocationProvider). */
interface DeviceLocationSource {
    fun hasPermission(): Boolean
    suspend fun currentLocation(): LatLon?
    /** Название места по координатам (системный Geocoder); null — если недоступно. */
    suspend fun placeName(coordinates: LatLon): String?
}
