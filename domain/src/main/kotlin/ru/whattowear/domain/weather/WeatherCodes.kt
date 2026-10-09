package ru.whattowear.domain.weather

/** Группы кодов погоды WMO (их возвращает Open-Meteo; символы MET Norway переводятся в них же). */
enum class CodeGroup { CLEAR, PARTLY, CLOUDY, FOG, DRIZZLE, RAIN, SLEET, FREEZING, SNOW, THUNDER }

object WeatherCodes {
    /** WMO 68/69 — «дождь со снегом»; Open-Meteo их не отдаёт, используем для мокрого снега MET Norway. */
    const val SLEET_CODE = 68

    fun group(code: Int): CodeGroup = when (code) {
        0, 1 -> CodeGroup.CLEAR
        2 -> CodeGroup.PARTLY
        3 -> CodeGroup.CLOUDY
        45, 48 -> CodeGroup.FOG
        51, 53, 55 -> CodeGroup.DRIZZLE
        56, 57, 66, 67 -> CodeGroup.FREEZING
        61, 63, 65, 80, 81, 82 -> CodeGroup.RAIN
        68, 69 -> CodeGroup.SLEET
        71, 73, 75, 77, 85, 86 -> CodeGroup.SNOW
        95, 96, 99 -> CodeGroup.THUNDER
        else -> CodeGroup.CLOUDY
    }

    fun isFreezing(code: Int?): Boolean = code != null && group(code) == CodeGroup.FREEZING

    /** symbol_code MET Norway (например "lightrainshowers_day") → ближайший код WMO. */
    fun fromMetSymbol(symbol: String?): Int? {
        val s = symbol?.substringBefore('_') ?: return null
        return when {
            "thunder" in s -> 95
            "sleet" in s -> SLEET_CODE
            "snow" in s -> if ("heavy" in s) 75 else if ("light" in s) 71 else 73
            "rain" in s -> if ("heavy" in s) 65 else if ("light" in s) 61 else 63
            s == "fog" -> 45
            s == "clearsky" -> 0
            s == "fair" -> 1
            s == "partlycloudy" -> 2
            s == "cloudy" -> 3
            else -> null
        }
    }
}
