package ru.whattowear.domain.usecase

import kotlinx.coroutines.flow.first
import ru.whattowear.domain.model.ClothingItem
import ru.whattowear.domain.model.Forecast
import ru.whattowear.domain.model.Location
import ru.whattowear.domain.model.UserSettings
import ru.whattowear.domain.outfit.ComfortCalibrator
import ru.whattowear.domain.outfit.ComfortRating
import ru.whattowear.domain.outfit.OutfitRecommendation
import ru.whattowear.domain.outfit.OutfitRecommender
import ru.whattowear.domain.outfit.OutingWindow
import ru.whattowear.domain.outfit.RecommendationInput
import ru.whattowear.domain.outfit.SeasonResolver
import ru.whattowear.domain.outfit.WeatherWindowAnalyzer
import ru.whattowear.domain.repository.FeedbackEntry
import ru.whattowear.domain.repository.OutfitHistoryRepository
import ru.whattowear.domain.repository.OutfitLogEntry
import ru.whattowear.domain.repository.SettingsRepository
import ru.whattowear.domain.repository.WardrobeRepository
import ru.whattowear.domain.repository.WeatherRepository
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/** Собирает входные данные и вызывает алгоритм подбора. Чистая функция — без обращений к хранилищам. */
class BuildRecommendationUseCase @Inject constructor() {
    operator fun invoke(
        forecast: Forecast,
        location: Location?,
        items: List<ClothingItem>,
        settings: UserSettings,
        recentLogs: List<OutfitLogEntry>,
        variantIndex: Int,
        nowMillis: Long,
    ): OutfitRecommendation? {
        val window = OutingWindow.resolve(settings.leaveTime, settings.returnTime, forecast.zone, nowMillis)
        val conditions = WeatherWindowAnalyzer.analyze(forecast.hours, window, settings.personalOffset, forecast.zone) ?: return null
        // «Давно не надевали»: учитываем комплекты двух предыдущих дней
        val recentlyWorn = recentLogs
            .filter { it.date < window.date && it.date >= window.date.minusDays(2) }
            .flatMap { it.itemIds }
            .toSet()
        return OutfitRecommender.recommend(
            RecommendationInput(
                conditions = conditions,
                items = items,
                season = SeasonResolver.seasonOf(window.date, location?.latitude),
                preferredStyle = settings.preferredStyle,
                recentlyWorn = recentlyWorn,
                variantIndex = variantIndex,
                zone = forecast.zone,
            ),
        )
    }
}

data class CurrentRecommendation(
    val location: Location,
    val forecast: Forecast,
    val recommendation: OutfitRecommendation?,
)

/** Рекомендация для выбранного места «прямо сейчас» — для уведомления и виджета. Запоминает комплект дня. */
class GetCurrentRecommendationUseCase @Inject constructor(
    private val observeSelectedLocation: ObserveSelectedLocationUseCase,
    private val weather: WeatherRepository,
    private val wardrobe: WardrobeRepository,
    private val settings: SettingsRepository,
    private val history: OutfitHistoryRepository,
    private val build: BuildRecommendationUseCase,
    private val record: RecordOutfitUseCase,
) {
    suspend operator fun invoke(nowMillis: Long = System.currentTimeMillis()): CurrentRecommendation? {
        val location = observeSelectedLocation.current() ?: return null
        val forecast = weather.getForecast(location.id) ?: return null
        val s = settings.settings.first()
        val window = OutingWindow.resolve(s.leaveTime, s.returnTime, forecast.zone, nowMillis)
        val log = history.getLog(window.date)
        val recommendation = build(
            forecast = forecast,
            location = location,
            items = wardrobe.getItems(),
            settings = s,
            recentLogs = history.logsSince(window.date.minusDays(3)),
            variantIndex = log?.variantIndex ?: 0,
            nowMillis = nowMillis,
        )
        recommendation?.let { record(it, location.id) }
        return CurrentRecommendation(location, forecast, recommendation)
    }
}

/** Запомнить комплект дня (для «давно не надевали» и для отзыва вечером). Пишет, только если что-то изменилось. */
class RecordOutfitUseCase @Inject constructor(private val history: OutfitHistoryRepository) {
    suspend operator fun invoke(recommendation: OutfitRecommendation, locationId: Long?) {
        val outfit = recommendation.outfit ?: return
        val entry = OutfitLogEntry(
            date = recommendation.date,
            locationId = locationId,
            itemIds = outfit.itemIds,
            variantIndex = recommendation.variantIndex,
            effTempMin = recommendation.conditions.effMin,
            effTempMax = recommendation.conditions.effMax,
            targetWarmth = recommendation.targetTorsoWarmth,
            actualWarmth = outfit.torsoWarmth.toDouble(),
        )
        val existing = history.getLog(recommendation.date)
        if (existing == null || existing.itemIds != entry.itemIds || existing.variantIndex != entry.variantIndex) {
            history.saveLog(entry)
        }
    }
}

/** «Другой вариант»: сдвигает номер варианта для дня; рекомендация пересчитывается из потока данных. */
class NextVariantUseCase @Inject constructor(private val history: OutfitHistoryRepository) {
    suspend operator fun invoke(recommendation: OutfitRecommendation, locationId: Long?) {
        val outfit = recommendation.outfit ?: return
        if (recommendation.variantCount < 2) return
        val next = (recommendation.variantIndex + 1) % recommendation.variantCount
        history.saveLog(
            OutfitLogEntry(
                date = recommendation.date,
                locationId = locationId,
                itemIds = outfit.itemIds,
                variantIndex = next,
                effTempMin = recommendation.conditions.effMin,
                effTempMax = recommendation.conditions.effMax,
                targetWarmth = recommendation.targetTorsoWarmth,
                actualWarmth = outfit.torsoWarmth.toDouble(),
            ),
        )
    }
}

/** Отзыв «холодно / нормально / жарко» за день: сохраняет его и сдвигает личную поправку. */
class SubmitFeedbackUseCase @Inject constructor(
    private val history: OutfitHistoryRepository,
    private val settings: SettingsRepository,
) {
    suspend operator fun invoke(date: LocalDate, rating: ComfortRating) {
        val log = history.getLog(date)
        val delta = if (log != null) log.actualWarmth - log.targetWarmth else 0.0
        val s = settings.settings.first()
        val result = ComfortCalibrator.update(s.learnedOffset, s.feedbackCount, rating, delta)
        settings.setLearnedOffset(result.offset, result.feedbackCount)
        history.saveFeedback(
            FeedbackEntry(
                date = date,
                rating = rating,
                effTempAvg = log?.let { (it.effTempMin + it.effTempMax) / 2 } ?: 0.0,
                warmthDelta = delta,
            ),
        )
    }
}

/** Сбросить выученную поправку (ручная остаётся). */
class ResetLearningUseCase @Inject constructor(
    private val history: OutfitHistoryRepository,
    private val settings: SettingsRepository,
) {
    suspend operator fun invoke() {
        settings.setLearnedOffset(0.0, 0)
        history.clearFeedback()
    }
}

/** Дата «сегодня» в часовом поясе прогноза. */
fun Forecast.today(nowMillis: Long): LocalDate = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
