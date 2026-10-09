package ru.whattowear.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import ru.whattowear.data.local.db.LocationDao
import ru.whattowear.data.local.db.LocationEntity
import ru.whattowear.data.local.db.toDomain
import ru.whattowear.data.remote.OpenMeteoApi
import ru.whattowear.domain.model.GeoPlace
import ru.whattowear.domain.model.LatLon
import ru.whattowear.domain.model.Location
import ru.whattowear.domain.repository.LocationRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationRepositoryImpl @Inject constructor(
    private val dao: LocationDao,
    private val api: OpenMeteoApi,
) : LocationRepository {

    override fun observeLocations(): Flow<List<Location>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getLocations(): List<Location> = dao.getAll().map { it.toDomain() }

    override suspend fun getLocation(id: Long): Location? = dao.get(id)?.toDomain()

    override suspend fun search(query: String): List<GeoPlace> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.length < 2) return@withContext emptyList()
        api.search(q).results.map {
            GeoPlace(
                name = it.name,
                region = it.region,
                country = it.country,
                latitude = it.latitude,
                longitude = it.longitude,
                timezone = it.timezone,
            )
        }
    }

    override suspend fun addPlace(place: GeoPlace): Long {
        // Тот же город уже сохранён — просто вернуть его
        dao.getAll().firstOrNull {
            !it.isDeviceLocation && it.name == place.name &&
                kotlin.math.abs(it.latitude - place.latitude) < 0.01 &&
                kotlin.math.abs(it.longitude - place.longitude) < 0.01
        }?.let { return it.id }
        return dao.insert(
            LocationEntity(
                name = place.name,
                region = place.region,
                country = place.country,
                latitude = place.latitude,
                longitude = place.longitude,
                timezone = place.timezone,
                isDeviceLocation = false,
                sortOrder = dao.maxSortOrder() + 1,
            ),
        )
    }

    override suspend fun delete(id: Long) = dao.delete(id)

    override suspend fun upsertDeviceLocation(coordinates: LatLon, name: String?): Long {
        val existing = dao.getDevice()
        val title = name ?: existing?.name ?: "Моё местоположение"
        return if (existing != null) {
            dao.update(existing.copy(name = title, latitude = coordinates.latitude, longitude = coordinates.longitude))
            existing.id
        } else {
            dao.insert(
                LocationEntity(
                    name = title,
                    region = null,
                    country = null,
                    latitude = coordinates.latitude,
                    longitude = coordinates.longitude,
                    timezone = null,
                    isDeviceLocation = true,
                    sortOrder = 0,
                ),
            )
        }
    }
}
