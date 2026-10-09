package ru.whattowear.app.widget

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import ru.whattowear.app.R
import ru.whattowear.app.ui.components.categoryIconRes
import ru.whattowear.app.ui.components.weatherIconRes
import ru.whattowear.domain.model.ClothingItem
import ru.whattowear.domain.model.Forecast
import ru.whattowear.domain.model.Location
import ru.whattowear.domain.model.UserSettings
import ru.whattowear.domain.outfit.OutingWindow
import ru.whattowear.domain.repository.OutfitHistoryRepository
import ru.whattowear.domain.repository.SettingsRepository
import ru.whattowear.domain.repository.WardrobeRepository
import ru.whattowear.domain.repository.WeatherRepository
import ru.whattowear.domain.text.Fmt
import ru.whattowear.domain.usecase.BuildRecommendationUseCase
import ru.whattowear.domain.usecase.ObserveSelectedLocationUseCase
import javax.inject.Inject
import javax.inject.Singleton

/** Что показывает виджет. [message] — вместо погоды, если данных нет. */
data class WidgetData(
    val locationName: String = "",
    val temperature: String = "",
    val feels: String = "",
    @param:DrawableRes val weatherIcon: Int = R.drawable.ic_wx_cloudy,
    val mainItemName: String? = null,
    val mainItemCaption: String? = null,
    @param:DrawableRes val mainItemIcon: Int? = null,
    val mainItemPhoto: Bitmap? = null,
    val message: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class WidgetDataSource @Inject constructor(
    private val observeSelected: ObserveSelectedLocationUseCase,
    private val weather: WeatherRepository,
    private val wardrobe: WardrobeRepository,
    private val settings: SettingsRepository,
    private val history: OutfitHistoryRepository,
    private val buildRecommendation: BuildRecommendationUseCase,
) {
    fun observe(): Flow<WidgetData> = observeSelected().flatMapLatest { location ->
        if (location == null) {
            flowOf(WidgetData(message = "Откройте приложение и выберите место"))
        } else {
            combine(weather.observeForecast(location.id), wardrobe.observeItems(), settings.settings) { fc, items, s ->
                Triple(fc, items, s)
            }.map { (fc, items, s) -> build(location, fc, items, s) }
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun build(location: Location, forecast: Forecast?, items: List<ClothingItem>, s: UserSettings): WidgetData {
        if (forecast == null || forecast.hours.isEmpty()) {
            return WidgetData(locationName = location.name, message = "Прогноз ещё не загружен")
        }
        val now = System.currentTimeMillis()
        val current = forecast.hours.lastOrNull { it.time <= now / 1000 } ?: forecast.hours.first()
        val window = OutingWindow.resolve(s.leaveTime, s.returnTime, forecast.zone, now)
        val log = history.getLog(window.date)
        val recommendation = buildRecommendation(
            forecast, location, items, s, history.logsSince(window.date.minusDays(3)), log?.variantIndex ?: 0, now,
        )
        val main = recommendation?.outfit?.mainPiece
        return WidgetData(
            locationName = location.name,
            temperature = Fmt.tempPlain(current.temperature),
            feels = Fmt.tempPlain(current.apparentTemperature),
            weatherIcon = weatherIconRes(current.kind, current.isDay),
            mainItemName = main?.item?.name ?: recommendation?.genericAdvice?.substringBefore(',')?.trimEnd('.'),
            mainItemCaption = when {
                main == null -> "По погоде"
                window.isTomorrow -> "Главное завтра"
                else -> "Главное сегодня"
            },
            mainItemIcon = main?.item?.category?.let { categoryIconRes(it) },
            mainItemPhoto = main?.item?.photoPath?.let { thumbnail(it) },
        )
    }

    /** Маленькая копия фото: виджет передаёт картинку через Binder, большой размер не пройдёт. */
    private fun thumbnail(path: String, size: Int = 160): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= size) sample *= 2
        val decoded = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val side = minOf(decoded.width, decoded.height)
        val cropped = Bitmap.createBitmap(decoded, (decoded.width - side) / 2, (decoded.height - side) / 2, side, side)
        Bitmap.createScaledBitmap(cropped, size, size, true)
    }.getOrNull()
}
