package ru.whattowear.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao {
    @Query("SELECT * FROM locations ORDER BY isDeviceLocation DESC, sortOrder, id")
    fun observeAll(): Flow<List<LocationEntity>>

    @Query("SELECT * FROM locations ORDER BY isDeviceLocation DESC, sortOrder, id")
    suspend fun getAll(): List<LocationEntity>

    @Query("SELECT * FROM locations WHERE id = :id")
    suspend fun get(id: Long): LocationEntity?

    @Query("SELECT * FROM locations WHERE isDeviceLocation = 1 LIMIT 1")
    suspend fun getDevice(): LocationEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM locations")
    suspend fun maxSortOrder(): Int

    @Insert
    suspend fun insert(entity: LocationEntity): Long

    @Update
    suspend fun update(entity: LocationEntity)

    @Query("DELETE FROM locations WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ForecastDao {
    @Query("SELECT * FROM forecast_meta WHERE locationId = :locationId")
    fun observeMeta(locationId: Long): Flow<ForecastMetaEntity?>

    @Query("SELECT * FROM forecast_meta WHERE locationId = :locationId")
    suspend fun getMeta(locationId: Long): ForecastMetaEntity?

    @Query("SELECT * FROM forecast_hours WHERE locationId = :locationId ORDER BY time")
    fun observeHours(locationId: Long): Flow<List<ForecastHourEntity>>

    @Query("SELECT * FROM forecast_hours WHERE locationId = :locationId ORDER BY time")
    suspend fun getHours(locationId: Long): List<ForecastHourEntity>

    @Query("SELECT * FROM forecast_days WHERE locationId = :locationId ORDER BY date")
    fun observeDays(locationId: Long): Flow<List<ForecastDayEntity>>

    @Query("SELECT * FROM forecast_days WHERE locationId = :locationId ORDER BY date")
    suspend fun getDays(locationId: Long): List<ForecastDayEntity>

    @Query("DELETE FROM forecast_hours WHERE locationId = :locationId")
    suspend fun deleteHours(locationId: Long)

    @Query("DELETE FROM forecast_days WHERE locationId = :locationId")
    suspend fun deleteDays(locationId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHours(hours: List<ForecastHourEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDays(days: List<ForecastDayEntity>)

    @Upsert
    suspend fun upsertMeta(meta: ForecastMetaEntity)

    /** Заменить прогноз места целиком одной транзакцией (наблюдатели увидят только итог). */
    @Transaction
    suspend fun replace(meta: ForecastMetaEntity, hours: List<ForecastHourEntity>, days: List<ForecastDayEntity>) {
        deleteHours(meta.locationId)
        deleteDays(meta.locationId)
        insertHours(hours)
        insertDays(days)
        upsertMeta(meta)
    }
}

@Dao
interface ClothingDao {
    @Query("SELECT * FROM clothing_items ORDER BY category, name COLLATE NOCASE, id")
    fun observeAll(): Flow<List<ClothingItemEntity>>

    @Query("SELECT * FROM clothing_items ORDER BY category, name COLLATE NOCASE, id")
    suspend fun getAll(): List<ClothingItemEntity>

    @Query("SELECT * FROM clothing_items WHERE id = :id")
    suspend fun get(id: Long): ClothingItemEntity?

    @Insert
    suspend fun insert(entity: ClothingItemEntity): Long

    @Insert
    suspend fun insertAll(entities: List<ClothingItemEntity>)

    @Update
    suspend fun update(entity: ClothingItemEntity)

    @Query("UPDATE clothing_items SET isAvailable = :available, updatedAt = :now WHERE id = :id")
    suspend fun setAvailable(id: Long, available: Boolean, now: Long)

    @Query("DELETE FROM clothing_items WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM clothing_items")
    suspend fun deleteAll()
}

@Dao
interface OutfitLogDao {
    @Query("SELECT * FROM outfit_log WHERE date = :date")
    fun observe(date: String): Flow<OutfitLogEntity?>

    @Query("SELECT * FROM outfit_log WHERE date = :date")
    suspend fun get(date: String): OutfitLogEntity?

    @Query("SELECT * FROM outfit_log WHERE date >= :fromDate ORDER BY date")
    suspend fun getSince(fromDate: String): List<OutfitLogEntity>

    @Upsert
    suspend fun upsert(entity: OutfitLogEntity)
}

@Dao
interface FeedbackDao {
    @Query("SELECT * FROM feedback WHERE date = :date")
    fun observe(date: String): Flow<FeedbackEntity?>

    @Query("SELECT * FROM feedback WHERE date = :date")
    suspend fun get(date: String): FeedbackEntity?

    @Upsert
    suspend fun upsert(entity: FeedbackEntity)

    @Query("DELETE FROM feedback")
    suspend fun deleteAll()
}
