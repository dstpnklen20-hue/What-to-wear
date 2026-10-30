package ru.whattowear.app.ui.components

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import ru.whattowear.domain.usecase.DeviceLocationUpdate

/**
 * Запрос доступа к местоположению. Если пользователь отказал так, что система больше не показывает
 * диалог («больше не спрашивать» или два отказа), [blocked] = true, а [request] открывает настройки приложения.
 */
class LocationPermissionRequest(val blocked: Boolean, val request: () -> Unit)

@Composable
fun rememberLocationPermissionRequest(onResult: (granted: Boolean) -> Unit): LocationPermissionRequest {
    val context = LocalContext.current
    var blocked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val granted = result.values.any { it }
        val activity = context.findActivity()
        blocked = !granted && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_COARSE_LOCATION)
        onResult(granted)
    }
    return LocationPermissionRequest(blocked) {
        if (blocked) openAppSettings(context) else launcher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION))
    }
}

/** Что сказать пользователю, если «Моё местоположение» не определилось. */
fun locationProblemText(problem: DeviceLocationUpdate, permissionBlocked: Boolean): String = when (problem) {
    DeviceLocationUpdate.NO_PERMISSION -> if (permissionBlocked) {
        "Доступ к местоположению запрещён. Включите его в настройках приложения: «Разрешения» → «Местоположение»."
    } else {
        "Нет доступа к местоположению. Разрешите его — хватит приблизительного."
    }
    DeviceLocationUpdate.DISABLED -> "Геолокация на телефоне выключена. Включите её в настройках или выберите город вручную."
    else -> "Не удалось получить координаты: нет сигнала и последнего известного места. " +
        "Попробуйте ещё раз у окна или на улице либо выберите город вручную."
}

fun openAppSettings(context: Context) {
    context.startSafely(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
}

fun openLocationSettings(context: Context) {
    context.startSafely(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
}

private fun Context.startSafely(intent: Intent) {
    // На некоторых прошивках нужного экрана нет — тогда общие настройки
    runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { runCatching { startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
