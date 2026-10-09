package ru.whattowear.domain.model

enum class Category(val title: String) {
    OUTERWEAR("Верхняя одежда"),
    SWEATER("Кофты и свитеры"),
    TOP("Футболки и рубашки"),
    BOTTOM("Низ"),
    SHOES("Обувь"),
    HEADWEAR("Головные уборы"),
    GLOVES_SCARVES("Перчатки и шарфы"),
    ACCESSORIES("Аксессуары");

    /** Вещь надевается на корпус и участвует в расчёте слоёв. */
    val isTorso: Boolean get() = this == OUTERWEAR || this == SWEATER || this == TOP

    /** Для категории нужно уточнить вид вещи (перчатки или шарф, зонт или очки). */
    val subtypes: List<Subtype>
        get() = when (this) {
            GLOVES_SCARVES -> listOf(Subtype.GLOVES, Subtype.SCARF)
            ACCESSORIES -> listOf(Subtype.UMBRELLA, Subtype.SUNGLASSES, Subtype.OTHER)
            else -> emptyList()
        }

    val defaultLayer: Layer
        get() = when (this) {
            TOP -> Layer.BASE
            SWEATER -> Layer.MID
            else -> Layer.OUTER
        }

    val defaultWarmth: Int
        get() = when (this) {
            OUTERWEAR -> 3
            SWEATER -> 2
            TOP, BOTTOM, SHOES -> 2
            HEADWEAR, GLOVES_SCARVES -> 3
            ACCESSORIES -> 1
        }
}

/** Вид вещи внутри категорий «Перчатки и шарфы» и «Аксессуары». */
enum class Subtype(val title: String) {
    NONE(""),
    GLOVES("Перчатки"),
    SCARF("Шарф"),
    UMBRELLA("Зонт"),
    SUNGLASSES("Солнцезащитные очки"),
    OTHER("Другое"),
}

enum class Layer(val title: String) { BASE("Базовый"), MID("Средний"), OUTER("Внешний") }

enum class Style(val title: String) { CASUAL("Повседневный"), SPORT("Спортивный"), BUSINESS("Деловой") }

enum class Season(val title: String) { WINTER("Зима"), SPRING("Весна"), SUMMER("Лето"), AUTUMN("Осень") }

/** Цвет вещи. [neutral] — сочетается с чем угодно (учитывается при оценке гармонии комплекта). */
enum class ClothingColor(val title: String, val argb: Long, val neutral: Boolean) {
    BLACK("Чёрный", 0xFF1C1C1E, true),
    WHITE("Белый", 0xFFF7F7F5, true),
    GREY("Серый", 0xFF9A9A9E, true),
    BEIGE("Бежевый", 0xFFD9C9AE, true),
    BROWN("Коричневый", 0xFF7A5537, true),
    NAVY("Тёмно-синий", 0xFF243B63, true),
    DENIM("Джинсовый", 0xFF4F6D8F, true),
    BLUE("Синий", 0xFF3A6FD8, false),
    LIGHT_BLUE("Голубой", 0xFF9CC3E8, false),
    GREEN("Зелёный", 0xFF3E8A55, false),
    OLIVE("Оливковый", 0xFF707A3E, false),
    YELLOW("Жёлтый", 0xFFE8C547, false),
    ORANGE("Оранжевый", 0xFFE47B39, false),
    RED("Красный", 0xFFC8423B, false),
    BURGUNDY("Бордовый", 0xFF7C2433, false),
    PINK("Розовый", 0xFFE8A0B4, false),
    PURPLE("Фиолетовый", 0xFF7A55B0, false),
    MULTI("Разноцветный", 0xFF888888, false),
}

data class ClothingItem(
    val id: Long = 0,
    val name: String,
    val category: Category,
    val subtype: Subtype = Subtype.NONE,
    /** Абсолютный путь к фото во внутреннем хранилище приложения. */
    val photoPath: String? = null,
    /** Уровень теплоты 1..5. */
    val warmth: Int,
    val waterproof: Boolean = false,
    val windproof: Boolean = false,
    val layer: Layer = category.defaultLayer,
    val style: Style = Style.CASUAL,
    val color: ClothingColor = ClothingColor.BLACK,
    /** Пустое множество = подходит для любого сезона. */
    val seasons: Set<Season> = emptySet(),
    /** false — «в стирке / недоступно», в подборе не участвует. */
    val isAvailable: Boolean = true,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
) {
    fun fitsSeason(season: Season): Boolean = seasons.isEmpty() || season in seasons
}
