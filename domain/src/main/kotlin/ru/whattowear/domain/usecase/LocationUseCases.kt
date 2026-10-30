package ru.whattowear.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import ru.whattowear.domain.model.Location
import ru.whattowear.domain.repository.DeviceLocationSource
import ru.whattowear.domain.repository.LocationRepository
import ru.whattowear.domain.repository.SettingsRepository
import javax.inject.Inject
import kotlin.math.abs

/** Выбранное место: из настроек, а если оно удалено или не выбрано — первое в списке. */
class ObserveSelectedLocationUseCase @Inject constructor(
    private val locations: LocationRepository,
    private val settings: SettingsRepository,
) {
    operator fun invoke(): Flow<Location?> =
        combine(locations.observeLocations(), settings.settings) { list, s ->
            list.firstOrNull { it.id == s.selectedLocationId } ?: list.firstOrNull()
        }.distinctUntilChanged()

    suspend fun current(): Location? {
        val s = settings.settings.first()
        val list = locations.getLocations()
        return list.firstOrNull { it.id == s.selectedLocationId } ?: list.firstOrNull()
    }
}

enum class DeviceLocationUpdate {
    /** Пользователь не дал доступ к местоположению. */
    NO_PERMISSION,
    /** Геолокация выключена в настройках телефона. */
    DISABLED,
    /** Координаты не получены: нет сигнала и последней известной позиции (или истекло время). */
    NOT_FOUND,
    UNCHANGED,
    /** Место создано или заметно сдвинулось — прогноз нужно перезагрузить. */
    MOVED;

    val failed: Boolean get() = this == NO_PERMISSION || this == DISABLED || this == NOT_FOUND
}

/**
 * Обновляет запись «Моё местоположение» по GPS. Если пользователь ещё ничего не выбирал —
 * делает её выбранной.
 */
class UpdateDeviceLocationUseCase @Inject constructor(
    private val source: DeviceLocationSource,
    private val locations: LocationRepository,
    private val settings: SettingsRepository,
) {
    suspend operator fun invoke(): DeviceLocationUpdate {
        if (!source.hasPermission()) return DeviceLocationUpdate.NO_PERMISSION
        if (!source.isLocationEnabled()) return DeviceLocationUpdate.DISABLED
        val coordinates = source.currentLocation() ?: return DeviceLocationUpdate.NOT_FOUND
        val existing = locations.getLocations().firstOrNull { it.isDeviceLocation }
        // Название ищем, только если место заметно изменилось (~1 км) — Geocoder небыстрый
        val moved = existing == null ||
            abs(existing.latitude - coordinates.latitude) > 0.01 ||
            abs(existing.longitude - coordinates.longitude) > 0.01
        if (!moved) return DeviceLocationUpdate.UNCHANGED
        val name = source.placeName(coordinates)
        val id = locations.upsertDeviceLocation(coordinates, name)
        if (settings.settings.first().selectedLocationId == null) settings.setSelectedLocation(id)
        return DeviceLocationUpdate.MOVED
    }
}

/** Сколько ждать определения местоположения вместе с названием места (GPS без сигнала ищет долго). */
const val LOCATE_TIMEOUT_MS = 30_000L
