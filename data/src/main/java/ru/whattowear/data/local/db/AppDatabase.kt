package ru.whattowear.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Локальная база. При изменении схемы: поднять version, собрать (появится schemas/…/N.json)
 * и добавить явную миграцию — никаких destructive-миграций, гардероб пользователя терять нельзя.
 */
@Database(
    entities = [
        LocationEntity::class,
        ForecastHourEntity::class,
        ForecastDayEntity::class,
        ForecastMetaEntity::class,
        ClothingItemEntity::class,
        OutfitLogEntity::class,
        FeedbackEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun locationDao(): LocationDao
    abstract fun forecastDao(): ForecastDao
    abstract fun clothingDao(): ClothingDao
    abstract fun outfitLogDao(): OutfitLogDao
    abstract fun feedbackDao(): FeedbackDao

    companion object {
        const val NAME = "what_to_wear.db"
    }
}
