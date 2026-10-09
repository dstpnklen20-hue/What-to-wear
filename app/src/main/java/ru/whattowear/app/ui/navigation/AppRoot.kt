package ru.whattowear.app.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import ru.whattowear.app.ui.forecast.ForecastScreen
import ru.whattowear.app.ui.itemeditor.ItemEditorScreen
import ru.whattowear.app.ui.locations.LocationsScreen
import ru.whattowear.app.ui.settings.SettingsScreen
import ru.whattowear.app.ui.today.TodayScreen
import ru.whattowear.app.ui.wardrobe.WardrobeScreen

@Composable
fun AppRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val isTopLevel = destination == null || TopLevel.entries.any { top ->
        destination.hierarchy.any { it.hasRoute(top.route::class) }
    }

    fun openTopLevel(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // Нижняя панель на телефоне, боковая колонка на планшете; на вложенных экранах панели нет
    val adaptiveType = NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo())
    val layoutType = if (isTopLevel) adaptiveType else NavigationSuiteType.None
    val colors = MaterialTheme.colorScheme
    NavigationSuiteScaffold(
        layoutType = layoutType,
        navigationSuiteColors = NavigationSuiteDefaults.colors(
            navigationBarContainerColor = colors.surface,
            navigationRailContainerColor = colors.surface,
        ),
        containerColor = colors.surface,
        navigationSuiteItems = {
            TopLevel.entries.forEach { top ->
                item(
                    selected = destination?.hierarchy?.any { it.hasRoute(top.route::class) } == true,
                    onClick = { openTopLevel(top.route) },
                    icon = { Icon(top.icon, contentDescription = null) },
                    label = { Text(top.label) },
                )
            }
        },
    ) {
        NavHost(
            navController = navController,
            // С нижней панелью отступ от системной панели даёт она сама; с боковой колонкой — добавляем
            modifier = if (layoutType == NavigationSuiteType.NavigationBar) Modifier else Modifier.navigationBarsPadding(),
            startDestination = TodayRoute,
            enterTransition = { fadeIn(MotionSpec.enter()) },
            exitTransition = { fadeOut(MotionSpec.exit()) },
        ) {
            composable<TodayRoute> {
                TodayScreen(
                    onOpenWardrobe = { openTopLevel(WardrobeRoute) },
                    onAddItem = { navController.navigate(ItemEditorRoute()) },
                    onOpenLocations = { navController.navigate(LocationsRoute) },
                )
            }
            composable<ForecastRoute> {
                ForecastScreen(onOpenLocations = { navController.navigate(LocationsRoute) })
            }
            composable<WardrobeRoute> {
                WardrobeScreen(
                    onAddItem = { navController.navigate(ItemEditorRoute()) },
                    onEditItem = { navController.navigate(ItemEditorRoute(it)) },
                )
            }
            composable<SettingsRoute> {
                SettingsScreen(onOpenLocations = { navController.navigate(LocationsRoute) })
            }
            composable<LocationsRoute> {
                LocationsScreen(onBack = { navController.popBackStack() })
            }
            composable<ItemEditorRoute> { entry ->
                val route = entry.toRoute<ItemEditorRoute>()
                ItemEditorScreen(itemId = route.itemId, onDone = { navController.popBackStack() })
            }
        }
    }
}
