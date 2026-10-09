package ru.whattowear.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.whattowear.domain.outfit.ComfortRating
import java.time.LocalDate

/** Комплект, рекомендованный (и выбранный) в конкретный день. */
data class OutfitLogEntry(
    val date: LocalDate,
    val locationId: Long?,
    val itemIds: List<Long>,
    val variantIndex: Int,
    val effTempMin: Double,
    val effTempMax: Double,
    val targetWarmth: Double,
    val actualWarmth: Double,
)

data class FeedbackEntry(
    val date: LocalDate,
    val rating: ComfortRating,
    val effTempAvg: Double,
    val warmthDelta: Double,
)

interface OutfitHistoryRepository {
    fun observeLog(date: LocalDate): Flow<OutfitLogEntry?>
    suspend fun getLog(date: LocalDate): OutfitLogEntry?
    suspend fun logsSince(date: LocalDate): List<OutfitLogEntry>
    suspend fun saveLog(entry: OutfitLogEntry)

    fun observeFeedback(date: LocalDate): Flow<FeedbackEntry?>
    suspend fun saveFeedback(entry: FeedbackEntry)
    suspend fun clearFeedback()
}
