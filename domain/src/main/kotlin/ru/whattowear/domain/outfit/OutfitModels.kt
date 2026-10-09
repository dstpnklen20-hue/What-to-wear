package ru.whattowear.domain.outfit

import ru.whattowear.domain.model.ClothingItem
import java.time.LocalDate

/** Роль вещи в комплекте. [accusative] — для фраз вида «… можно снять». */
enum class Role(val title: String, val accusative: String) {
    BASE("Базовый слой", "базовый слой"),
    MID("Средний слой", "средний слой"),
    OUTER("Верхняя одежда", "верхнюю одежду"),
    BOTTOM("Низ", "низ"),
    SHOES("Обувь", "обувь"),
    HEADWEAR("Головной убор", "головной убор"),
    GLOVES("Перчатки", "перчатки"),
    SCARF("Шарф", "шарф"),
    UMBRELLA("Зонт", "зонт"),
    SUNGLASSES("Очки", "очки"),
}

data class OutfitPiece(
    val item: ClothingItem,
    val role: Role,
    /** Слой, который можно снять в тёплые часы. */
    val removable: Boolean = false,
)

data class Outfit(val pieces: List<OutfitPiece>) {
    val itemIds: List<Long> get() = pieces.map { it.item.id }
    val torsoWarmth: Int get() = pieces.filter { it.role in TORSO }.sumOf { it.item.warmth }

    /** «Главная вещь дня» для виджета и уведомления: верхняя одежда, иначе самый внешний слой. */
    val mainPiece: OutfitPiece?
        get() = pieces.firstOrNull { it.role == Role.OUTER }
            ?: pieces.firstOrNull { it.role == Role.MID }
            ?: pieces.firstOrNull { it.role == Role.BASE }
            ?: pieces.firstOrNull()

    companion object {
        val TORSO = setOf(Role.BASE, Role.MID, Role.OUTER)
    }
}

/** Погода в окне «вне дома», уже с личной поправкой. */
data class WindowConditions(
    val window: OutingWindow,
    /** Ощущаемая температура (как в прогнозе). */
    val feelsMin: Double,
    val feelsMax: Double,
    /** Эффективная температура = ощущаемая − личная поправка. По ней подбирается одежда. */
    val effMin: Double,
    val effMax: Double,
    val effMinTime: Long,
    val effMaxTime: Long,
    /** Эффективная температура по часам окна. */
    val effective: List<Pair<Long, Double>>,
    val rain: Boolean,
    val snow: Boolean,
    val precipPhrase: String?,
    val maxWind: Double,
    val maxGust: Double,
    val uvMax: Double?,
    val iceRisk: Boolean,
    val uncertain: Boolean,
    val personalOffset: Double,
) {
    val range: Double get() = effMax - effMin

    /** Расчётная температура: ближе к холодной части окна — слой можно снять, но нельзя добавить. */
    val designTemp: Double get() = effMin + 0.2 * range

    val windy: Boolean get() = maxWind >= WINDY_SPEED || maxGust >= WINDY_GUST
    val strongWind: Boolean get() = maxGust >= 15.0
    val needsLayering: Boolean get() = range >= LAYERING_RANGE

    companion object {
        const val WINDY_SPEED = 8.0
        const val WINDY_GUST = 12.0
        const val LAYERING_RANGE = 6.0
        const val UV_GLASSES = 3.0
        const val UV_HIGH = 6.0
    }
}

data class OutfitRecommendation(
    val date: LocalDate,
    val conditions: WindowConditions,
    /** null — гардероб пуст или в нём нет ни одной подходящей вещи; тогда есть [genericAdvice]. */
    val outfit: Outfit?,
    val variantIndex: Int,
    val variantCount: Int,
    /** Короткое «почему именно так». */
    val explanation: List<String>,
    /** Советы: что снять днём, зонт, крем от солнца, гололёд. */
    val tips: List<String>,
    /** Честные сообщения о том, чего не хватает в гардеробе. */
    val warnings: List<String>,
    val targetTorsoWarmth: Double,
    val genericAdvice: String?,
)
