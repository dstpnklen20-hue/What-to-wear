package ru.whattowear.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.whattowear.data.local.db.FeedbackDao
import ru.whattowear.data.local.db.FeedbackEntity
import ru.whattowear.data.local.db.OutfitLogDao
import ru.whattowear.data.local.db.OutfitLogEntity
import ru.whattowear.domain.outfit.ComfortRating
import ru.whattowear.domain.repository.FeedbackEntry
import ru.whattowear.domain.repository.OutfitHistoryRepository
import ru.whattowear.domain.repository.OutfitLogEntry
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OutfitHistoryRepositoryImpl @Inject constructor(
    private val logDao: OutfitLogDao,
    private val feedbackDao: FeedbackDao,
) : OutfitHistoryRepository {

    override fun observeLog(date: LocalDate): Flow<OutfitLogEntry?> = logDao.observe(date.toString()).map { it?.toDomain() }

    override suspend fun getLog(date: LocalDate): OutfitLogEntry? = logDao.get(date.toString())?.toDomain()

    override suspend fun logsSince(date: LocalDate): List<OutfitLogEntry> = logDao.getSince(date.toString()).map { it.toDomain() }

    override suspend fun saveLog(entry: OutfitLogEntry) {
        logDao.upsert(
            OutfitLogEntity(
                date = entry.date.toString(),
                locationId = entry.locationId,
                itemIds = entry.itemIds.joinToString(","),
                variantIndex = entry.variantIndex,
                effTempMin = entry.effTempMin,
                effTempMax = entry.effTempMax,
                targetWarmth = entry.targetWarmth,
                actualWarmth = entry.actualWarmth,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    override fun observeFeedback(date: LocalDate): Flow<FeedbackEntry?> = feedbackDao.observe(date.toString()).map { it?.toDomain() }

    override suspend fun saveFeedback(entry: FeedbackEntry) {
        feedbackDao.upsert(
            FeedbackEntity(
                date = entry.date.toString(),
                rating = entry.rating.value,
                effTempAvg = entry.effTempAvg,
                warmthDelta = entry.warmthDelta,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun clearFeedback() = feedbackDao.deleteAll()

    private fun OutfitLogEntity.toDomain() = OutfitLogEntry(
        date = LocalDate.parse(date),
        locationId = locationId,
        itemIds = itemIds.split(',').mapNotNull { it.trim().toLongOrNull() },
        variantIndex = variantIndex,
        effTempMin = effTempMin,
        effTempMax = effTempMax,
        targetWarmth = targetWarmth,
        actualWarmth = actualWarmth,
    )

    private fun FeedbackEntity.toDomain() = FeedbackEntry(
        date = LocalDate.parse(date),
        rating = ComfortRating.entries.firstOrNull { it.value == rating } ?: ComfortRating.OK,
        effTempAvg = effTempAvg,
        warmthDelta = warmthDelta,
    )
}
