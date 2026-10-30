package ru.whattowear.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.whattowear.domain.model.GeoPlace
import ru.whattowear.domain.model.LatLon
import ru.whattowear.domain.model.Location
import ru.whattowear.domain.model.Style
import ru.whattowear.domain.model.ThemeMode
import ru.whattowear.domain.model.UserSettings
import ru.whattowear.domain.repository.DeviceLocationSource
import ru.whattowear.domain.repository.LocationRepository
import ru.whattowear.domain.repository.SettingsRepository
import java.time.LocalTime

class UpdateDeviceLocationUseCaseTest {

    private class FakeSource(
        var permission: Boolean = true,
        var enabled: Boolean = true,
        var coordinates: LatLon? = LatLon(55.75, 37.62),
    ) : DeviceLocationSource {
        var requests = 0
        override fun hasPermission() = permission
        override fun isLocationEnabled() = enabled
        override suspend fun currentLocation(): LatLon? = coordinates.also { requests++ }
        override suspend fun placeName(coordinates: LatLon) = "Москва"
    }

    private class FakeLocations : LocationRepository {
        val list = mutableListOf<Location>()
        override fun observeLocations(): Flow<List<Location>> = MutableStateFlow(list.toList())
        override suspend fun getLocations() = list.toList()
        override suspend fun getLocation(id: Long) = list.firstOrNull { it.id == id }
        override suspend fun search(query: String) = emptyList<GeoPlace>()
        override suspend fun addPlace(place: GeoPlace) = error("не нужно")
        override suspend fun delete(id: Long) { list.removeAll { it.id == id } }
        override suspend fun upsertDeviceLocation(coordinates: LatLon, name: String?): Long {
            list.removeAll { it.isDeviceLocation }
            list += Location(1, name ?: "Моё местоположение", null, null, coordinates.latitude, coordinates.longitude, null, true, 0)
            return 1
        }
    }

    private class FakeSettings : SettingsRepository {
        private val state = MutableStateFlow(UserSettings())
        override val settings: Flow<UserSettings> = state
        override suspend fun setSelectedLocation(id: Long?) = state.update { it.copy(selectedLocationId = id) }
        override suspend fun setOutingWindow(leave: LocalTime, back: LocalTime) = Unit
        override suspend fun setNotifications(enabled: Boolean, time: LocalTime) = Unit
        override suspend fun setManualOffset(offset: Double) = Unit
        override suspend fun setLearnedOffset(offset: Double, feedbackCount: Int) = Unit
        override suspend fun setPreferredStyle(style: Style?) = Unit
        override suspend fun setTheme(mode: ThemeMode) = Unit
        override suspend fun setDynamicColor(enabled: Boolean) = Unit
        override suspend fun setOnboardingDone() = Unit
    }

    private val source = FakeSource()
    private val locations = FakeLocations()
    private val settings = FakeSettings()
    private val useCase = UpdateDeviceLocationUseCase(source, locations, settings)

    @Test
    fun `без разрешения координаты не запрашиваются`() = runTest {
        source.permission = false
        assertEquals(DeviceLocationUpdate.NO_PERMISSION, useCase())
        assertEquals(0, source.requests)
    }

    @Test
    fun `выключенная геолокация отличается от отсутствия сигнала`() = runTest {
        source.enabled = false
        assertEquals(DeviceLocationUpdate.DISABLED, useCase())
        source.enabled = true
        source.coordinates = null
        assertEquals(DeviceLocationUpdate.NOT_FOUND, useCase())
        assertNull(locations.getLocations().firstOrNull())
    }

    @Test
    fun `первая точка создаёт место и делает его выбранным, повтор рядом — без изменений`() = runTest {
        assertEquals(DeviceLocationUpdate.MOVED, useCase())
        val device = locations.getLocations().single()
        assertEquals("Москва", device.name)
        assertEquals(device.id, settings.settings.first().selectedLocationId)

        source.coordinates = LatLon(55.751, 37.621)
        assertEquals(DeviceLocationUpdate.UNCHANGED, useCase())
        source.coordinates = LatLon(59.94, 30.31)
        assertEquals(DeviceLocationUpdate.MOVED, useCase())
    }
}
