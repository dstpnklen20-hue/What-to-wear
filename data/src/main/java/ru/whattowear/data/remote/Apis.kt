package ru.whattowear.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import retrofit2.http.GET
import retrofit2.http.Query

/** Open-Meteo Forecast API: бесплатный, без ключа. Ответ разбирается вручную ([OpenMeteoParser]) — ключи зависят от моделей. */
interface OpenMeteoApi {
    @GET("v1/forecast")
    suspend fun forecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("hourly") hourly: String = OpenMeteoParser.HOURLY_VARIABLES.joinToString(","),
        @Query("daily") daily: String = "sunrise,sunset",
        @Query("models") models: String,
        @Query("wind_speed_unit") windSpeedUnit: String = "ms",
        @Query("timezone") timezone: String = "auto",
        @Query("timeformat") timeFormat: String = "unixtime",
        @Query("past_days") pastDays: Int = 1,
        @Query("forecast_days") forecastDays: Int = 8,
    ): JsonObject

    @GET("https://geocoding-api.open-meteo.com/v1/search")
    suspend fun search(
        @Query("name") name: String,
        @Query("count") count: Int = 15,
        @Query("language") language: String = "ru",
        @Query("format") format: String = "json",
    ): GeocodingResponse
}

/** MET Norway Locationforecast 2.0. Требует осмысленный User-Agent (добавляется интерцептором) и не больше 4 знаков в координатах. */
interface MetNoApi {
    @GET("https://api.met.no/weatherapi/locationforecast/2.0/complete")
    suspend fun forecast(
        @Query("lat") latitude: String,
        @Query("lon") longitude: String,
    ): JsonObject
}

@Serializable
data class GeocodingResponse(val results: List<GeocodingResult> = emptyList())

@Serializable
data class GeocodingResult(
    val id: Long = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String? = null,
    @SerialName("admin1") val region: String? = null,
    val timezone: String? = null,
)
