package ru.whattowear.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import ru.whattowear.domain.model.LatLon
import ru.whattowear.domain.repository.DeviceLocationSource
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import android.location.Location as AndroidLocation

/**
 * Местоположение через Google Play Services, а если их нет — через системный LocationManager
 * (телефоны без сервисов Google). Порядок: быстрая точка по сети → недавняя последняя известная (до 30 мин) →
 * GPS → давняя последняя известная.
 * GPS нужен, когда сетевой точки нет (за городом, в помещении без Wi-Fi), — с приблизительным разрешением
 * Android отдаёт и его, но огрубляет. Всё под тайм-аутами: задачи Play Services иногда не завершаются вовсе.
 * В сумме не дольше ~20 секунд.
 */
@Singleton
class FusedDeviceLocationSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : DeviceLocationSource {

    private val fused by lazy { LocationServices.getFusedLocationProviderClient(context) }
    private val manager by lazy { context.getSystemService(LocationManager::class.java) }

    private val hasPlayServices: Boolean by lazy {
        runCatching {
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
        }.getOrDefault(false)
    }

    override fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    override fun isLocationEnabled(): Boolean =
        manager?.let { runCatching { LocationManagerCompat.isLocationEnabled(it) }.getOrDefault(true) } ?: false

    override suspend fun currentLocation(): LatLon? {
        if (!hasPermission()) return null
        // Для погоды хватает точности квартала; сначала свежая точка, иначе последняя известная
        val location = if (hasPlayServices) fusedLocation() ?: systemLastKnown() else systemLocation()
        return location?.let { LatLon(it.latitude, it.longitude) }
    }

    private suspend fun fusedLocation(): AndroidLocation? {
        fusedCurrent(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 6_000)?.let { return it }
        // Недавняя последняя точка лучше долгого поиска GPS; давняя — только если GPS ничего не нашёл
        val last = runCatchingLocation { withTimeoutOrNull(2_000) { fused.lastLocation.await() } }
        if (last != null && last.ageMillis() < FRESH_LAST_LOCATION_MS) return last
        return fusedCurrent(Priority.PRIORITY_HIGH_ACCURACY, 12_000) ?: last
    }

    private fun AndroidLocation.ageMillis(): Long =
        (SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos) / 1_000_000

    @SuppressLint("MissingPermission")
    private suspend fun fusedCurrent(priority: Int, timeoutMs: Long): AndroidLocation? = runCatchingLocation {
        val cancel = CancellationTokenSource()
        withTimeoutOrNull(timeoutMs) { fused.getCurrentLocation(priority, cancel.token).await() }
            .also { if (it == null) cancel.cancel() }
    }

    /** Без Play Services — тот же порядок: сеть, недавняя последняя точка, GPS, давняя последняя точка. */
    private suspend fun systemLocation(): AndroidLocation? {
        val lm = manager ?: return null
        val providers = runCatchingLocation { lm.getProviders(true) }.orEmpty()
        providers.takeIf { LocationManager.NETWORK_PROVIDER in it }
            ?.let { systemCurrent(lm, LocationManager.NETWORK_PROVIDER, 6_000) }?.let { return it }
        val last = systemLastKnown()
        if (last != null && last.ageMillis() < FRESH_LAST_LOCATION_MS) return last
        return providers.takeIf { LocationManager.GPS_PROVIDER in it }
            ?.let { systemCurrent(lm, LocationManager.GPS_PROVIDER, 12_000) } ?: last
    }

    @SuppressLint("MissingPermission")
    private suspend fun systemCurrent(lm: LocationManager, provider: String, timeoutMs: Long): AndroidLocation? = runCatchingLocation {
        val signal = CancellationSignal()
        withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                cont.invokeOnCancellation { signal.cancel() }
                LocationManagerCompat.getCurrentLocation(lm, provider, signal, ContextCompat.getMainExecutor(context)) {
                    if (cont.isActive) cont.resume(it)
                }
            }
        }
    }

    /** Самая свежая из последних известных точек всех доступных провайдеров. */
    @SuppressLint("MissingPermission")
    private fun systemLastKnown(): AndroidLocation? {
        val lm = manager ?: return null
        val providers = runCatching { lm.getProviders(true) }.getOrDefault(emptyList())
        return providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.elapsedRealtimeNanos }
    }

    /** SecurityException (разрешение отозвали) и ошибки Play Services — не падать, а пробовать следующий способ. */
    private inline fun <T> runCatchingLocation(block: () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    override suspend fun placeName(coordinates: LatLon): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.forLanguageTag("ru"))
        val address: Address? = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                withTimeoutOrNull(5_000) {
                    suspendCancellableCoroutine { cont ->
                        geocoder.getFromLocation(coordinates.latitude, coordinates.longitude, 1, object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) = cont.resume(addresses.firstOrNull())
                            override fun onError(errorMessage: String?) = cont.resume(null)
                        })
                    }
                }
            } else {
                withTimeoutOrNull(5_000) {
                    withContext(Dispatchers.IO) {
                        @Suppress("DEPRECATION")
                        geocoder.getFromLocation(coordinates.latitude, coordinates.longitude, 1)?.firstOrNull()
                    }
                }
            }
        } catch (e: Exception) {
            null
        }
        return address?.locality ?: address?.subAdminArea ?: address?.adminArea
    }

    private companion object {
        /** Последняя известная точка моложе этого считается текущей. */
        const val FRESH_LAST_LOCATION_MS = 30 * 60 * 1000L
    }
}
