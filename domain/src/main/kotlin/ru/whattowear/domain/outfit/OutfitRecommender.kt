package ru.whattowear.domain.outfit

import ru.whattowear.domain.model.Category
import ru.whattowear.domain.model.ClothingItem
import ru.whattowear.domain.model.Layer
import ru.whattowear.domain.model.Season
import ru.whattowear.domain.model.Style
import ru.whattowear.domain.model.Subtype
import ru.whattowear.domain.text.Fmt
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

data class RecommendationInput(
    val conditions: WindowConditions,
    /** Весь гардероб, включая вещи «в стирке» (они не предлагаются, но о них можно предупредить). */
    val items: List<ClothingItem>,
    val season: Season,
    val preferredStyle: Style?,
    /** Вещи, которые надевали в последние дни, — предлагаются чуть реже. */
    val recentlyWorn: Set<Long>,
    val variantIndex: Int,
    val zone: ZoneId,
)

/**
 * Подбор комплекта по правилам, локально и детерминированно.
 *
 * 1. По расчётной температуре окна ([WindowConditions.designTemp]) определяется нужная теплота корпуса, низа и обуви.
 * 2. Перебираются сочетания слоёв корпуса (база × средний × внешний), низа и обуви; каждое получает оценку:
 *    близость к нужной теплоте, жёсткие правила (дождь → непромокаемое, ветер → ветрозащита, снег/гололёд → обувь),
 *    возможность снять слой при большом перепаде, сезон, стиль, сочетаемость цветов, «давно не надевали».
 * 3. Лучшие различающиеся комплекты — варианты для кнопки «другой вариант».
 * 4. Добавляются аксессуары по правилам (шапка, перчатки, шарф, зонт, очки), советы и честные предупреждения.
 */
object OutfitRecommender {

    private const val MAX_VARIANTS = 5

    private class Torso(val base: ClothingItem?, val mid: ClothingItem?, val outer: ClothingItem?, val score: Double) {
        val items = listOfNotNull(base, mid, outer)
        val warmth = items.sumOf { it.warmth }
    }

    private class Candidate(val torso: Torso, val bottom: ClothingItem?, val shoes: ClothingItem?, val score: Double) {
        val main = torso.items + listOfNotNull(bottom, shoes)
        val slots = listOf(torso.base?.id, torso.mid?.id, torso.outer?.id, bottom?.id, shoes?.id)
    }

    fun recommend(input: RecommendationInput): OutfitRecommendation {
        val c = input.conditions
        val t = c.designTemp
        val available = input.items.filter { it.isAvailable }
        val targetTorso = WarmthModel.torso(t)
        val targetLegs = WarmthModel.legs(t)
        val targetShoes = WarmthModel.shoes(t)
        val torsoAtWarmest = WarmthModel.torso(c.effMax)

        fun pref(item: ClothingItem): Double =
            (if (!item.fitsSeason(input.season)) -1.2 else 0.0) +
                (if (item.id in input.recentlyWorn) -0.6 else 0.0) +
                (if (input.preferredStyle != null && item.style == input.preferredStyle) 0.6 else 0.0)

        // Кандидаты по слотам: не больше двух лучших вещей на каждый уровень теплоты
        fun prune(list: List<ClothingItem>) = list.groupBy { it.warmth }.values.flatMap { group ->
            group.sortedWith(compareByDescending<ClothingItem> { pref(it) }.thenBy { it.id }).take(2)
        }

        val torsoItems = available.filter { it.category.isTorso }
        val bases = torsoItems.filter { it.layer == Layer.BASE }
        val mids = torsoItems.filter { it.layer == Layer.MID }
        val outers = torsoItems.filter { it.layer == Layer.OUTER }
        val bottoms = available.filter { it.category == Category.BOTTOM }
        val shoes = available.filter { it.category == Category.SHOES }

        val warnings = mutableListOf<String>()
        val explanation = explain(c, input.zone)

        if (torsoItems.isEmpty() && bottoms.isEmpty() && shoes.isEmpty()) {
            if (input.items.any { it.category.isTorso || it.category == Category.BOTTOM || it.category == Category.SHOES }) {
                warnings += "Все подходящие вещи отмечены «в стирке»."
            }
            return OutfitRecommendation(
                date = c.window.date,
                conditions = c,
                outfit = null,
                variantIndex = 0,
                variantCount = 0,
                explanation = explanation,
                tips = generalTips(c),
                warnings = warnings,
                targetTorsoWarmth = targetTorso,
                genericAdvice = genericAdvice(c),
            )
        }

        // 1. Корпус
        val torsoCandidates = mutableListOf<Torso>()
        val baseOptions: List<ClothingItem?> = prune(bases).ifEmpty { listOf(null) }
        val midOptions: List<ClothingItem?> = listOf(null) + prune(mids)
        val outerOptions: List<ClothingItem?> = listOf(null) + prune(outers)
        for (b in baseOptions) for (m in midOptions) for (o in outerOptions) {
            val items = listOfNotNull(b, m, o)
            if (items.isEmpty()) continue
            val sum = items.sumOf { it.warmth }
            var score = -3.0 * abs(sum - targetTorso)
            val outermost = o ?: m ?: b
            if (c.rain) {
                if (t < 20) {
                    score -= if (o == null) 4.0 else if (!o.waterproof) 5.0 else 0.0
                } else if (o != null && !o.waterproof) {
                    score -= 1.0
                }
            }
            if (c.snow && o != null && !o.waterproof) score -= 2.0
            if (c.windy && outermost != null && !outermost.windproof) score -= if (t < 22) 4.0 else 1.5
            if (c.needsLayering) {
                val removable = listOfNotNull(o, m)
                val best = if (removable.isEmpty()) abs(sum - torsoAtWarmest)
                else removable.minOf { abs(sum - it.warmth - torsoAtWarmest) }
                score -= 2.0 * max(0.0, best - 1.0)
            }
            score += items.sumOf { pref(it) }
            torsoCandidates += Torso(b, m, o, score)
        }
        val torsoTop = torsoCandidates.sortedByDescending { it.score }.take(30)
            .ifEmpty { listOf(Torso(null, null, null, 0.0)) }

        // 2. Низ и обувь
        val bottomTop: List<Pair<ClothingItem?, Double>> = bottoms.map { b ->
            b to (-2.0 * abs(b.warmth - targetLegs) + pref(b) + if ((c.rain || c.snow) && b.waterproof) 0.3 else 0.0)
        }.sortedByDescending { it.second }.take(6).ifEmpty { listOf(null to 0.0) }

        val shoesTop: List<Pair<ClothingItem?, Double>> = shoes.map { s ->
            var score = -1.5 * abs(s.warmth - targetShoes) + pref(s)
            if ((c.rain || c.snow) && !s.waterproof) score -= 3.0
            if ((c.snow || c.iceRisk) && s.warmth < 3 && t < 5) score -= 2.0
            if (c.iceRisk && s.seasons.isNotEmpty() && Season.WINTER !in s.seasons) score -= 1.5
            s to score
        }.sortedByDescending { it.second }.take(6).ifEmpty { listOf(null to 0.0) }

        // 3. Полные комплекты и варианты
        val candidates = buildList {
            for (torso in torsoTop) for ((b, bs) in bottomTop) for ((s, ss) in shoesTop) {
                val main = torso.items + listOfNotNull(b, s)
                add(Candidate(torso, b, s, torso.score + bs + ss + harmony(main)))
            }
        }.sortedWith(compareByDescending<Candidate> { it.score }.thenBy { it.slots.joinToString() })

        val variants = pickVariants(candidates)
        val index = if (variants.isEmpty()) 0 else ((input.variantIndex % variants.size) + variants.size) % variants.size
        val chosen = variants[index]

        // 4. Сборка комплекта, слои, аксессуары
        val pieces = mutableListOf<OutfitPiece>()
        val tips = mutableListOf<String>()
        val removable = if (c.needsLayering) chooseRemovable(chosen.torso, torsoAtWarmest) else null
        chosen.torso.base?.let { pieces += OutfitPiece(it, Role.BASE) }
        chosen.torso.mid?.let { pieces += OutfitPiece(it, Role.MID, removable = it == removable) }
        chosen.torso.outer?.let { pieces += OutfitPiece(it, Role.OUTER, removable = it == removable) }
        chosen.bottom?.let { pieces += OutfitPiece(it, Role.BOTTOM) }
        chosen.shoes?.let { pieces += OutfitPiece(it, Role.SHOES) }

        if (removable != null) {
            val role = if (removable == chosen.torso.outer) Role.OUTER else Role.MID
            layeringTip(c, chosen.torso.warmth - removable.warmth, removable, role, input.zone)?.let { tips += it }
        }

        accessories(c, available, input.items, pieces, tips, warnings)
        tips += generalTips(c)
        warnings += wardrobeWarnings(c, input.items, available, chosen, targetTorso, bases)

        return OutfitRecommendation(
            date = c.window.date,
            conditions = c,
            outfit = Outfit(pieces),
            variantIndex = index,
            variantCount = variants.size,
            explanation = explanation,
            tips = tips.distinct(),
            warnings = warnings.distinct(),
            targetTorsoWarmth = targetTorso,
            genericAdvice = null,
        )
    }

    /** Стиль и цвета: один стиль на весь комплект и не больше двух ярких цветов. */
    private fun harmony(main: List<ClothingItem>): Double {
        if (main.isEmpty()) return 0.0
        val dominant = main.groupingBy { it.style }.eachCount().maxBy { it.value }.key
        val styleScore = -0.8 * main.count { it.style != dominant }
        val accents = main.filter { !it.color.neutral }.map { it.color }.distinct().size
        val colorScore = when {
            accents <= 1 -> 0.5
            accents == 2 -> 0.0
            else -> -1.0 * (accents - 2)
        }
        return styleScore + colorScore
    }

    /** Лучшие комплекты, заметно отличающиеся друг от друга (хотя бы двумя основными вещами). */
    private fun pickVariants(sorted: List<Candidate>): List<Candidate> {
        val picked = mutableListOf<Candidate>()
        for (minDiff in listOf(2, 1)) {
            for (cand in sorted) {
                if (picked.size >= MAX_VARIANTS) break
                if (picked.any { it === cand }) continue
                if (picked.all { p -> p.slots.zip(cand.slots).count { (a, b) -> a != b } >= minDiff }) picked += cand
            }
            if (picked.size >= 2) break
        }
        return picked.ifEmpty { listOfNotNull(sorted.firstOrNull()) }
    }

    /** Какой слой снимать в тёплые часы: тот, без которого теплота ближе всего к нужной в самое тёплое время. */
    private fun chooseRemovable(torso: Torso, targetAtWarmest: Double): ClothingItem? {
        val options = listOfNotNull(torso.outer, torso.mid)
        if (options.isEmpty() || torso.items.size < 2) return null
        val best = options.minBy { abs(torso.warmth - it.warmth - targetAtWarmest) }
        return best.takeIf { abs(torso.warmth - it.warmth - targetAtWarmest) <= 2.0 }
    }

    private fun layeringTip(c: WindowConditions, remainingWarmth: Int, piece: ClothingItem, role: Role, zone: ZoneId): String? {
        // Тот же допуск в 1 балл, что и при выборе съёмного слоя: чуть прохладнее нужного — ещё комфортно
        val threshold = WarmthModel.torsoComfortTemp(remainingWarmth + 1.0)
        val warm = c.effective.filter { it.second >= threshold }
        if (warm.isEmpty()) return null
        val first = warm.first().first
        val last = warm.last().first
        val name = "«${piece.name}»"
        return if (first > c.window.start) {
            val back = if (last + 3600 < c.window.end) ", после ${Fmt.hour(last + 3600, zone)} наденьте снова" else ""
            "К ${Fmt.hour(first, zone)} потеплеет до ${Fmt.temp(c.feelsMax)} — ${role.accusative} ($name) можно снять$back."
        } else {
            val coldFrom = last + 3600
            if (coldFrom >= c.window.end) return null
            "Утром можно без $name, но возьмите с собой: после ${Fmt.hour(coldFrom, zone)} похолодает до ${Fmt.temp(c.feelsMin)}."
        }
    }

    private fun accessories(
        c: WindowConditions,
        available: List<ClothingItem>,
        all: List<ClothingItem>,
        pieces: MutableList<OutfitPiece>,
        tips: MutableList<String>,
        warnings: MutableList<String>,
    ) {
        val t = c.designTemp
        val headwear = available.filter { it.category == Category.HEADWEAR }
        val gloves = available.filter { it.category == Category.GLOVES_SCARVES && it.subtype == Subtype.GLOVES }
        val scarves = available.filter { it.category == Category.GLOVES_SCARVES && it.subtype == Subtype.SCARF }
        val umbrellas = available.filter { it.category == Category.ACCESSORIES && it.subtype == Subtype.UMBRELLA }
        val glasses = available.filter { it.category == Category.ACCESSORIES && it.subtype == Subtype.SUNGLASSES }
        val uv = c.uvMax ?: 0.0

        // Головной убор: от холода — тёплый, от солнца — лёгкий
        val headTarget = WarmthModel.head(t)
        if (headTarget > 0) {
            val hat = headwear.filter { it.warmth >= 2 }.minByOrNull { abs(it.warmth - headTarget) * 10 + it.id % 10 }
            if (hat != null) pieces += OutfitPiece(hat, Role.HEADWEAR)
            else warnings += "Нужна тёплая шапка, но в гардеробе её нет."
        } else if (uv >= WindowConditions.UV_HIGH) {
            val cap = headwear.filter { it.warmth <= 2 }.minByOrNull { it.warmth * 10 + it.id % 10 }
            if (cap != null) pieces += OutfitPiece(cap, Role.HEADWEAR)
            else warnings += "Высокий УФ-индекс, а лёгкого головного убора от солнца в гардеробе нет."
        }

        if (t <= 2 || (t <= 6 && c.windy)) {
            val pair = gloves.maxByOrNull { if (t <= -10) it.warmth else -abs(it.warmth - 3) }
            if (pair != null) pieces += OutfitPiece(pair, Role.GLOVES)
            else if (t <= 0) warnings += "Нужны перчатки, но в гардеробе их нет."
        }
        if (t <= -2 || (t <= 6 && c.windy)) {
            val scarf = scarves.maxByOrNull { if (t <= -10) it.warmth else -abs(it.warmth - 3) }
            if (scarf != null) pieces += OutfitPiece(scarf, Role.SCARF)
            else if (t <= -5) warnings += "Пригодился бы шарф — в гардеробе его нет."
        }

        if (c.rain) {
            val umbrella = umbrellas.firstOrNull()
            if (umbrella != null) pieces += OutfitPiece(umbrella, Role.UMBRELLA)
            tips += if (c.strongWind) {
                "Возьмите зонт, но при порывах до ${Fmt.number(c.maxGust)} м/с надёжнее капюшон."
            } else {
                "Не забудьте зонт."
            }
            val hasWaterproofOuter = all.any { it.category.isTorso && it.layer == Layer.OUTER && it.waterproof }
            if (umbrella == null && !hasWaterproofOuter) warnings += "Нет ни зонта, ни непромокаемой верхней одежды — можно промокнуть."
        }

        if (uv >= WindowConditions.UV_GLASSES) {
            val pair = glasses.firstOrNull()
            if (pair != null) pieces += OutfitPiece(pair, Role.SUNGLASSES)
            else if (uv >= WindowConditions.UV_HIGH) warnings += "Солнцезащитных очков в гардеробе нет."
        }
    }

    private fun generalTips(c: WindowConditions): List<String> = buildList {
        val uv = c.uvMax ?: 0.0
        if (uv >= WindowConditions.UV_HIGH) add("УФ-индекс до ${Fmt.number(uv)}: нанесите солнцезащитный крем.")
        if (c.iceRisk) add("Возможен гололёд: обувь с рельефной подошвой и осторожнее на ступенях.")
        if (c.uncertain) add("Модели расходятся — лучше взять вещь «на всякий случай».")
    }

    private fun wardrobeWarnings(
        c: WindowConditions,
        all: List<ClothingItem>,
        available: List<ClothingItem>,
        chosen: Candidate,
        targetTorso: Double,
        bases: List<ClothingItem>,
    ): List<String> = buildList {
        val t = c.designTemp
        if (bases.isEmpty()) add("Нет вещей базового слоя (футболки, рубашки) — добавьте их в гардероб.")
        if (chosen.bottom == null) add("В гардеробе нет низа (брюки, джинсы, юбки).")
        if (chosen.shoes == null) add("В гардеробе нет обуви.")

        val gap = targetTorso - chosen.torso.warmth
        if (gap >= 1.5) {
            val n = gap.roundToInt()
            add("Комплект холоднее нужного примерно на $n ${Fmt.plural(n, "балл", "балла", "баллов")} теплоты — в гардеробе не хватает тёплых вещей.")
        } else if (gap <= -2.0 && chosen.torso.items.size == 1) {
            add("Даже самая лёгкая вещь теплее, чем нужно, — добавьте в гардероб лёгкие вещи.")
        }

        fun laundryNote(match: (ClothingItem) -> Boolean): String =
            if (all.any { !it.isAvailable && match(it) }) " (подходящая вещь в стирке)" else ""

        if (c.rain && t < 20 && chosen.torso.outer?.waterproof != true) {
            val isWaterproofOuter = { i: ClothingItem -> i.category.isTorso && i.layer == Layer.OUTER && i.waterproof }
            if (available.none(isWaterproofOuter)) add("В гардеробе нет доступной непромокаемой верхней одежды${laundryNote(isWaterproofOuter)}.")
        }
        if (c.windy && t < 22) {
            val outermost = chosen.torso.outer ?: chosen.torso.mid ?: chosen.torso.base
            val isWindproof = { i: ClothingItem -> i.category.isTorso && i.windproof }
            if (outermost?.windproof != true && available.none(isWindproof)) {
                add("Сильный ветер, а ветрозащитной верхней одежды нет${laundryNote(isWindproof)}.")
            }
        }
        if ((c.rain || c.snow) && chosen.shoes != null && !chosen.shoes.waterproof) {
            val isWaterproofShoes = { i: ClothingItem -> i.category == Category.SHOES && i.waterproof }
            if (available.none(isWaterproofShoes)) add("Обувь может промокнуть: непромокаемой обуви нет${laundryNote(isWaterproofShoes)}.")
        }
        if ((c.snow || c.iceRisk) && t < 3) {
            val isWinterShoes = { i: ClothingItem -> i.category == Category.SHOES && i.warmth >= 3 && i.waterproof }
            if (available.none(isWinterShoes)) add("Для снега и гололёда нужна тёплая непромокаемая обувь — в гардеробе её нет${laundryNote(isWinterShoes)}.")
        }
    }

    /** Почему комплект именно такой — 2–5 коротких строк. */
    private fun explain(c: WindowConditions, zone: ZoneId): List<String> = buildList {
        add(
            "Ощущается от ${Fmt.temp(c.feelsMin)} до ${Fmt.temp(c.feelsMax)} " +
                "с ${Fmt.hour(c.window.start, zone)} до ${Fmt.hour(c.window.end, zone)}",
        )
        if (abs(c.personalOffset) >= 0.5) {
            val n = Fmt.number(abs(c.personalOffset))
            add(if (c.personalOffset > 0) "Учтено, что вам обычно холоднее (на $n°)" else "Учтено, что вам обычно теплее (на $n°)")
        }
        if (c.needsLayering) add("Перепад ${c.range.roundToInt()}° за день → многослойный комплект")
        c.precipPhrase?.let { phrase ->
            val need = when {
                c.snow && !c.rain -> "непромокаемая обувь"
                c.designTemp < 20 -> "непромокаемая верхняя одежда и зонт"
                else -> "зонт"
            }
            add("${phrase.replaceFirstChar(Char::uppercase)} → $need")
        }
        if (c.windy) add("Ветер с порывами до ${Fmt.number(c.maxGust)} м/с → ветрозащита")
        val uv = c.uvMax ?: 0.0
        if (uv >= WindowConditions.UV_GLASSES) {
            add("УФ-индекс до ${Fmt.number(uv)} → очки" + if (uv >= WindowConditions.UV_HIGH) " и головной убор" else "")
        }
        if (c.iceRisk) add("Возможен гололёд → нескользкая обувь")
        if (c.precipPhrase == null && !c.windy && !c.needsLayering) add("Без осадков и сильного ветра")
    }

    /** Общий совет, когда гардероб пуст: что надеть «в целом». */
    fun genericAdvice(c: WindowConditions): String {
        val t = c.designTemp
        val base = when {
            t >= 24 -> "футболка, лёгкие брюки или шорты, лёгкая обувь"
            t >= 18 -> "футболка или рубашка, лёгкая кофта на прохладные часы"
            t >= 12 -> "кофта или лёгкая куртка, брюки, закрытая обувь"
            t >= 5 -> "свитер и демисезонная куртка, закрытая обувь"
            t >= -3 -> "тёплая куртка, свитер, шапка и перчатки"
            t >= -12 -> "зимняя куртка, тёплый свитер, шапка, шарф, перчатки и зимняя обувь"
            else -> "пуховик, термобельё, тёплая шапка, шарф, варежки и зимняя обувь"
        }
        val extras = buildList {
            if (c.rain) add("непромокаемая куртка или зонт")
            if (c.windy) add("ветрозащита")
            if ((c.uvMax ?: 0.0) >= WindowConditions.UV_HIGH) add("кепка и солнцезащитные очки")
        }
        return (listOf(base) + extras).joinToString(", ").replaceFirstChar(Char::uppercase) + "."
    }
}
