package ru.whattowear.domain.model

/** Сохранённое место. Запись с [isDeviceLocation] = true — «Моё местоположение» (координаты от GPS). */
data class Location(
    val id: Long,
    val name: String,
    val region: String?,
    val country: String?,
    val latitude: Double,
    val longitude: Double,
    val timezone: String?,
    val isDeviceLocation: Boolean,
    val sortOrder: Int,
) {
    val subtitle: String
        get() = listOfNotNull(region, country).filter { it.isNotBlank() && it != name }.joinToString(", ")
}

/** Результат поиска города через Geocoding API. */
data class GeoPlace(
    val name: String,
    val region: String?,
    val country: String?,
    val latitude: Double,
    val longitude: Double,
    val timezone: String?,
) {
    val subtitle: String
        get() = listOfNotNull(region, country).filter { it.isNotBlank() && it != name }.joinToString(", ")
}

data class LatLon(val latitude: Double, val longitude: Double)
