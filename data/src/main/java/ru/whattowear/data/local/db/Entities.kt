package ru.whattowear.data.local.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// Перечисления хранятся строками (имя константы), даты — строкой ISO (yyyy-MM-dd), время — секундами эпохи.

@Entity(tableName = "locations")
data class LocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val region: String?,
    val country: String?,
    val latitude: Double,
    val longitude: Double,
    val timezone: String?,
    val isDeviceLocation: Boolean,
    val sortOrder: Int,
)

@Entity(
    tableName = "forecast_hours",
    primaryKeys = ["locationId", "time"],
    foreignKeys = [
        ForeignKey(
            entity = LocationEntity::class,
            parentColumns = ["id"],
            childColumns = ["locationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ForecastHourEntity(
    val locationId: Long,
    val time: Long,
    val temperature: Double,
    val apparentTemperature: Double,
    val humidity: Int?,
    val cloudCover: Int?,
    val visibilityM: Double?,
    val precipProbability: Int,
    val precipitationMm: Double,
    val rainMm: Double,
    val snowfallCm: Double,
    val precipType: String,
    val windSpeed: Double,
    val windGust: Double,
    val windDirection: Int?,
    val uvIndex: Double?,
    val kind: String,
    val isDay: Boolean,
    val tempSpread: Double,
    val precipSpread: Double,
    val wetShare: Double,
    val modelCount: Int,
)

@Entity(
    tableName = "forecast_days",
    primaryKeys = ["locationId", "date"],
    foreignKeys = [
        ForeignKey(
            entity = LocationEntity::class,
            parentColumns = ["id"],
            childColumns = ["locationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ForecastDayEntity(
    val locationId: Long,
    val date: String,
    val tempMin: Double,
    val tempMax: Double,
    val feelsMin: Double,
    val feelsMax: Double,
    val precipSumMm: Double,
    val precipProbMax: Int,
    val precipType: String,
    val windMax: Double,
    val gustMax: Double,
    val uvMax: Double?,
    val sunrise: Long?,
    val sunset: Long?,
    val kind: String,
    val isUncertain: Boolean,
)

@Entity(
    tableName = "forecast_meta",
    foreignKeys = [
        ForeignKey(
            entity = LocationEntity::class,
            parentColumns = ["id"],
            childColumns = ["locationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ForecastMetaEntity(
    @PrimaryKey val locationId: Long,
    val zone: String,
    val fetchedAt: Long,
    /** Источники через запятую. */
    val sources: String,
    val usedFallback: Boolean,
)

@Entity(
    tableName = "clothing_items",
    indices = [Index("category"), Index("isAvailable")],
)
data class ClothingItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String,
    val subtype: String,
    val photoPath: String?,
    val warmth: Int,
    val waterproof: Boolean,
    val windproof: Boolean,
    val layer: String,
    val style: String,
    val color: String,
    /** Битовая маска сезонов (бит = порядковый номер Season); 0 — любой сезон. */
    val seasons: Int,
    val isAvailable: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Какой комплект был рекомендован (и выбран) в конкретный день. */
@Entity(tableName = "outfit_log")
data class OutfitLogEntity(
    @PrimaryKey val date: String,
    val locationId: Long?,
    /** id вещей через запятую. */
    val itemIds: String,
    val variantIndex: Int,
    val effTempMin: Double,
    val effTempMax: Double,
    val targetWarmth: Double,
    val actualWarmth: Double,
    val updatedAt: Long,
)

/** Отзыв «холодно / нормально / жарко» за день. */
@Entity(tableName = "feedback")
data class FeedbackEntity(
    @PrimaryKey val date: String,
    /** −1 холодно, 0 нормально, +1 жарко. */
    val rating: Int,
    val effTempAvg: Double,
    /** Насколько комплект дня был теплее (+) или холоднее (−) рекомендованного, в баллах теплоты. */
    val warmthDelta: Double,
    val createdAt: Long,
)
