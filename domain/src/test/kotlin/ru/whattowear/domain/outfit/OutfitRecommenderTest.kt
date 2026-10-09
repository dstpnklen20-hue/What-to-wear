package ru.whattowear.domain.outfit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.whattowear.domain.model.Category
import ru.whattowear.domain.model.ClothingColor
import ru.whattowear.domain.model.ClothingItem
import ru.whattowear.domain.model.Layer
import ru.whattowear.domain.model.Season
import ru.whattowear.domain.model.Style
import ru.whattowear.domain.model.Subtype
import ru.whattowear.domain.weather.hour
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class OutfitRecommenderTest {

    private val zone = ZoneId.of("UTC")
    private val date = LocalDate.of(2026, 10, 12)
    private val start = date.atTime(8, 0).atZone(zone).toEpochSecond()
    private val window = OutingWindow(date, start, start + 10 * 3600, isTomorrow = false)

    private var nextId = 1L
    private fun item(
        name: String,
        category: Category,
        warmth: Int,
        layer: Layer = category.defaultLayer,
        waterproof: Boolean = false,
        windproof: Boolean = false,
        subtype: Subtype = Subtype.NONE,
        seasons: Set<Season> = emptySet(),
        color: ClothingColor = ClothingColor.BLACK,
        available: Boolean = true,
    ) = ClothingItem(
        id = nextId++, name = name, category = category, subtype = subtype, warmth = warmth, layer = layer,
        waterproof = waterproof, windproof = windproof, style = Style.CASUAL, color = color, seasons = seasons,
        isAvailable = available,
    )

    private val tee = item("Футболка", Category.TOP, 1)
    private val longsleeve = item("Лонгслив", Category.TOP, 2)
    private val lightSweater = item("Кофта", Category.SWEATER, 2)
    private val warmSweater = item("Тёплый свитер", Category.SWEATER, 4)
    private val rainJacket = item("Дождевик", Category.OUTERWEAR, 2, waterproof = true, windproof = true)
    private val demiJacket = item("Демисезонная куртка", Category.OUTERWEAR, 3)
    private val downJacket = item("Пуховик", Category.OUTERWEAR, 5, waterproof = true, windproof = true, seasons = setOf(Season.WINTER))
    private val shorts = item("Шорты", Category.BOTTOM, 1)
    private val jeans = item("Джинсы", Category.BOTTOM, 2)
    private val warmPants = item("Утеплённые брюки", Category.BOTTOM, 4)
    private val sandals = item("Сандалии", Category.SHOES, 1)
    private val sneakers = item("Кеды", Category.SHOES, 2)
    private val boots = item("Зимние ботинки", Category.SHOES, 4, waterproof = true, seasons = setOf(Season.WINTER))
    private val beanie = item("Шапка", Category.HEADWEAR, 4)
    private val cap = item("Кепка", Category.HEADWEAR, 1)
    private val gloves = item("Перчатки", Category.GLOVES_SCARVES, 3, subtype = Subtype.GLOVES)
    private val scarf = item("Шарф", Category.GLOVES_SCARVES, 3, subtype = Subtype.SCARF)
    private val umbrella = item("Зонт", Category.ACCESSORIES, 1, subtype = Subtype.UMBRELLA)
    private val glasses = item("Очки", Category.ACCESSORIES, 1, subtype = Subtype.SUNGLASSES)

    private val wardrobe = listOf(
        tee, longsleeve, lightSweater, warmSweater, rainJacket, demiJacket, downJacket, shorts, jeans, warmPants,
        sandals, sneakers, boots, beanie, cap, gloves, scarf, umbrella, glasses,
    )

    private fun conditions(
        effMin: Double,
        effMax: Double = effMin,
        rain: Boolean = false,
        snow: Boolean = false,
        gust: Double = 5.0,
        uv: Double = 1.0,
        ice: Boolean = false,
        offset: Double = 0.0,
        warmFirst: Boolean = false,
    ): WindowConditions {
        // Температура линейно растёт (или падает) в течение окна
        val effective = (0 until 10).map { i ->
            val t = if (warmFirst) effMax - (effMax - effMin) * i / 9 else effMin + (effMax - effMin) * i / 9
            (start + i * 3600L) to t
        }
        return WindowConditions(
            window = window,
            feelsMin = effMin + offset, feelsMax = effMax + offset,
            effMin = effMin, effMax = effMax,
            effMinTime = effective.minBy { it.second }.first, effMaxTime = effective.maxBy { it.second }.first,
            effective = effective,
            rain = rain, snow = snow,
            precipPhrase = if (rain) "дождь с 14:00, вероятность до 80 %" else if (snow) "снег весь день, вероятность до 90 %" else null,
            maxWind = gust / 1.5, maxGust = gust,
            uvMax = uv, iceRisk = ice, uncertain = false, personalOffset = offset,
        )
    }

    private fun recommend(c: WindowConditions, items: List<ClothingItem> = wardrobe, variant: Int = 0, season: Season = Season.AUTUMN) =
        OutfitRecommender.recommend(RecommendationInput(c, items, season, null, emptySet(), variant, zone))

    private fun OutfitRecommendation.has(item: ClothingItem) = outfit!!.pieces.any { it.item.id == item.id }
    private fun OutfitRecommendation.role(role: Role) = outfit!!.pieces.firstOrNull { it.role == role }?.item

    @Test
    fun `hot sunny day - light clothes, sunglasses, cap and sunscreen`() {
        val r = recommend(conditions(26.0, 28.0, uv = 7.0), season = Season.SUMMER)
        assertTrue(r.has(tee))
        assertNull(r.role(Role.OUTER))
        assertEquals(shorts, r.role(Role.BOTTOM))
        assertEquals(sandals, r.role(Role.SHOES))
        assertTrue(r.has(glasses))
        assertEquals(cap, r.role(Role.HEADWEAR))
        assertTrue(r.tips.any { "крем" in it })
    }

    @Test
    fun `rain - waterproof outerwear and umbrella`() {
        val r = recommend(conditions(11.0, 13.0, rain = true))
        assertTrue(r.role(Role.OUTER)!!.waterproof)
        assertTrue(r.has(umbrella))
        assertTrue(r.tips.any { "зонт" in it })
        assertTrue(r.explanation.any { "Дождь" in it })
    }

    @Test
    fun `rain without waterproof outerwear - honest warning`() {
        val items = wardrobe.filter { it != rainJacket && it != downJacket && it != umbrella }
        val r = recommend(conditions(11.0, 13.0, rain = true), items)
        assertTrue(r.warnings.any { "непромокаем" in it })
    }

    @Test
    fun `waterproof jacket in laundry is not suggested and is mentioned`() {
        val items = wardrobe.map { if (it == rainJacket || it == downJacket) it.copy(isAvailable = false) else it }
        val r = recommend(conditions(11.0, 13.0, rain = true), items)
        assertFalse(r.has(rainJacket))
        assertFalse(r.has(downJacket))
        assertTrue(r.warnings.any { "в стирке" in it })
    }

    @Test
    fun `strong wind - windproof outer layer`() {
        val r = recommend(conditions(12.0, 13.0, gust = 15.0))
        val outermost = r.role(Role.OUTER) ?: r.role(Role.MID) ?: r.role(Role.BASE)
        assertTrue(outermost!!.windproof)
        assertTrue(r.explanation.any { "Ветер" in it })
    }

    @Test
    fun `frost - warm layers, hat, gloves, scarf and winter boots`() {
        val r = recommend(conditions(-12.0, -9.0, snow = true), season = Season.WINTER)
        assertTrue(r.outfit!!.torsoWarmth >= 10)
        assertEquals(downJacket, r.role(Role.OUTER))
        assertEquals(beanie, r.role(Role.HEADWEAR))
        assertTrue(r.has(gloves))
        assertTrue(r.has(scarf))
        assertEquals(boots, r.role(Role.SHOES))
        assertEquals(warmPants, r.role(Role.BOTTOM))
    }

    @Test
    fun `ice risk - waterproof warm shoes and a tip`() {
        val r = recommend(conditions(-1.0, 1.0, ice = true), season = Season.WINTER)
        assertEquals(boots, r.role(Role.SHOES))
        assertTrue(r.tips.any { "гололёд" in it })
    }

    @Test
    fun `cold morning and warm afternoon - layered outfit with what to take off`() {
        val r = recommend(conditions(2.0, 12.0))
        val removable = r.outfit!!.pieces.filter { it.removable }
        assertEquals(1, removable.size)
        assertTrue(r.outfit!!.pieces.count { it.role in Outfit.TORSO } >= 2)
        assertTrue(r.tips.any { "можно снять" in it })
        assertTrue(r.explanation.any { "многослойный" in it })
    }

    @Test
    fun `warm morning and cold evening - take the layer with you`() {
        val r = recommend(conditions(4.0, 14.0, warmFirst = true))
        assertTrue(r.tips.any { "возьмите с собой" in it })
    }

    @Test
    fun `mild weather does not need a hat or gloves`() {
        val r = recommend(conditions(15.0, 17.0))
        assertNull(r.role(Role.HEADWEAR))
        assertNull(r.role(Role.GLOVES))
        assertNull(r.role(Role.UMBRELLA))
    }

    @Test
    fun `missing warm clothes - honest warning about warmth gap`() {
        val r = recommend(conditions(-10.0, -8.0), listOf(tee, jeans, sneakers))
        assertTrue(r.warnings.any { "холоднее нужного" in it })
        assertTrue(r.warnings.any { "шапка" in it })
    }

    @Test
    fun `empty wardrobe gives generic advice`() {
        val r = recommend(conditions(3.0, 6.0, rain = true), emptyList())
        assertNull(r.outfit)
        assertNotNull(r.genericAdvice)
        assertTrue(r.genericAdvice!!.contains("зонт"))
    }

    @Test
    fun `all items in laundry - warning and no outfit`() {
        val r = recommend(conditions(10.0), wardrobe.map { it.copy(isAvailable = false) })
        assertNull(r.outfit)
        assertTrue(r.warnings.any { "в стирке" in it })
    }

    @Test
    fun `another variant differs from the first`() {
        val c = conditions(10.0, 12.0)
        val first = recommend(c, variant = 0)
        val second = recommend(c, variant = 1)
        assertTrue(first.variantCount >= 2)
        assertNotEquals(first.outfit!!.itemIds, second.outfit!!.itemIds)
        // Варианты идут по кругу
        assertEquals(first.outfit!!.itemIds, recommend(c, variant = first.variantCount).outfit!!.itemIds)
    }

    @Test
    fun `out of season items are less likely`() {
        // Пуховик помечен как зимний — осенью при −1 он уступает куртке со свитером, если они дают нужную теплоту
        val r = recommend(conditions(4.0, 5.0), season = Season.SUMMER)
        assertFalse(r.has(downJacket))
    }

    @Test
    fun `recommendation is deterministic`() {
        val c = conditions(7.0, 9.0, rain = true)
        assertEquals(recommend(c).outfit!!.itemIds, recommend(c).outfit!!.itemIds)
    }

    @Test
    fun `explanation mentions personal offset`() {
        val r = recommend(conditions(5.0, 7.0, offset = 3.0))
        assertTrue(r.explanation.any { "холоднее" in it })
    }
}

class WeatherWindowAnalyzerTest {
    private val zone = ZoneId.of("UTC")
    private val date = LocalDate.of(2026, 10, 12)

    private fun hours(feels: (Int) -> Double) = (0 until 24).map { h ->
        hour(date.atStartOfDay(zone).toEpochSecond() + h * 3600L, temp = feels(h), feels = feels(h))
    }

    @Test
    fun `uses only hours outside home and applies personal offset`() {
        val all = hours { h -> h.toDouble() } // ощущается = час суток
        val window = OutingWindow.resolve(LocalTime.of(8, 0), LocalTime.of(18, 0), zone, date.atTime(6, 0).atZone(zone).toInstant().toEpochMilli())
        val c = WeatherWindowAnalyzer.analyze(all, window, personalOffset = 2.0, zone = zone)!!
        assertEquals(8.0, c.feelsMin, 0.01)
        assertEquals(17.0, c.feelsMax, 0.01)
        // Мерзлявому (+2) погода «ощущается» на 2° холоднее
        assertEquals(6.0, c.effMin, 0.01)
        assertEquals(15.0, c.effMax, 0.01)
        assertTrue(c.needsLayering)
    }

    @Test
    fun `personal offset makes recommended outfit warmer`() {
        val all = hours { 10.0 }
        val window = OutingWindow(date, date.atTime(8, 0).atZone(zone).toEpochSecond(), date.atTime(18, 0).atZone(zone).toEpochSecond(), false)
        val normal = WeatherWindowAnalyzer.analyze(all, window, 0.0, zone)!!
        val chilly = WeatherWindowAnalyzer.analyze(all, window, 4.0, zone)!!
        assertTrue(WarmthModel.torso(chilly.designTemp) > WarmthModel.torso(normal.designTemp))
    }
}

class OutingWindowTest {
    private val zone = ZoneId.of("Europe/Moscow")
    private val date = LocalDate.of(2026, 10, 12)

    @Test
    fun `before return time the window is today`() {
        val now = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val w = OutingWindow.resolve(LocalTime.of(8, 0), LocalTime.of(18, 0), zone, now)
        assertEquals(date, w.date)
        assertFalse(w.isTomorrow)
    }

    @Test
    fun `after return time the window moves to tomorrow`() {
        val now = date.atTime(19, 0).atZone(zone).toInstant().toEpochMilli()
        val w = OutingWindow.resolve(LocalTime.of(8, 0), LocalTime.of(18, 0), zone, now)
        assertEquals(date.plusDays(1), w.date)
        assertTrue(w.isTomorrow)
    }

    @Test
    fun `night shift ends next day`() {
        val now = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val w = OutingWindow.resolve(LocalTime.of(20, 0), LocalTime.of(6, 0), zone, now)
        assertEquals(10 * 3600L, w.end - w.start)
    }
}

class WarmthModelTest {
    @Test
    fun `needed warmth grows as it gets colder`() {
        var previous = 0.0
        for (t in 30 downTo -30 step 2) {
            val w = WarmthModel.torso(t.toDouble())
            assertTrue("t=$t", w >= previous)
            previous = w
        }
    }

    @Test
    fun `inverse function matches`() {
        for (w in listOf(2.0, 4.0, 7.0, 10.0)) {
            assertEquals(w, WarmthModel.torso(WarmthModel.torsoComfortTemp(w)), 0.01)
        }
    }
}

class ComfortCalibratorTest {
    @Test
    fun `cold feedback raises offset, hot lowers it, ok keeps it`() {
        assertTrue(ComfortCalibrator.update(0.0, 0, ComfortRating.COLD).offset > 0)
        assertTrue(ComfortCalibrator.update(0.0, 0, ComfortRating.HOT).offset < 0)
        val ok = ComfortCalibrator.update(1.0, 3, ComfortRating.OK)
        assertEquals(1.0, ok.offset, 0.001)
        assertEquals(4, ok.feedbackCount)
    }

    @Test
    fun `steps shrink with more feedback`() {
        val early = ComfortCalibrator.update(0.0, 0, ComfortRating.COLD).offset
        val late = ComfortCalibrator.update(0.0, 40, ComfortRating.COLD).offset
        assertTrue(late < early)
    }

    @Test
    fun `offset is clamped`() {
        var offset = 0.0
        var n = 0
        repeat(100) {
            val r = ComfortCalibrator.update(offset, n, ComfortRating.COLD)
            offset = r.offset
            n = r.feedbackCount
        }
        assertEquals(ComfortCalibrator.MAX_OFFSET, offset, 0.001)
    }

    @Test
    fun `feedback after dressing lighter than recommended barely counts`() {
        val trusted = ComfortCalibrator.update(0.0, 0, ComfortRating.COLD, warmthDelta = 0.0).offset
        val underdressed = ComfortCalibrator.update(0.0, 0, ComfortRating.COLD, warmthDelta = -3.0).offset
        assertTrue(underdressed < trusted)
    }
}
