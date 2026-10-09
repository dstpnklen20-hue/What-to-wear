package ru.whattowear.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

@Serializable data object TodayRoute
@Serializable data object ForecastRoute
@Serializable data object WardrobeRoute
@Serializable data object SettingsRoute
@Serializable data object LocationsRoute

/** Редактор вещи; itemId = 0 — новая вещь. */
@Serializable data class ItemEditorRoute(val itemId: Long = 0L)

enum class TopLevel(val route: Any, val label: String, val icon: ImageVector) {
    TODAY(TodayRoute, "Сегодня", Icons.Outlined.WbSunny),
    FORECAST(ForecastRoute, "7 дней", Icons.Outlined.CalendarMonth),
    WARDROBE(WardrobeRoute, "Гардероб", Icons.Outlined.Checkroom),
    SETTINGS(SettingsRoute, "Настройки", Icons.Outlined.Settings),
}
