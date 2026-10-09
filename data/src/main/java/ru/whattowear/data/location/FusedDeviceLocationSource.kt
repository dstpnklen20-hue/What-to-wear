package ru.whattowear.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
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

@Singleton
class FusedDeviceLocationSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : DeviceLocationSource {

    private val client by lazy { LocationServices.getFusedLocationProviderClient(context) }

    override fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    override suspend fun currentLocation(): LatLon? {
        if (!hasPermission()) return null
        return try {
            // Для погоды хватает точности квартала; сначала свежая точка, иначе последняя известная.
            // Каждый шаг ограничен по времени: задачи Play Services иногда не завершаются вовсе.
            val cancel = CancellationTokenSource()
            val fresh = withTimeoutOrNull(10_000) {
                client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancel.token).await()
            }
            if (fresh == null) cancel.cancel()
            val location = fresh ?: withTimeoutOrNull(3_000) { client.lastLocation.await() }
            location?.let { LatLon(it.latitude, it.longitude) }
        } catch (e: SecurityException) {
            null
        } catch (e: Exception) {
            null
        }
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
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(coordinates.latitude, coordinates.longitude, 1)?.firstOrNull()
                }
            }
        } catch (e: Exception) {
            null
        }
        return address?.locality ?: address?.subAdminArea ?: address?.adminArea
    }
}
