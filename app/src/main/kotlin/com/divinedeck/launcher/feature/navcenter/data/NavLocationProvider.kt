package com.divinedeck.launcher.feature.navcenter.data

import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Ubicación actual con FusedLocationProviderClient (el mismo motor que usa
 * el velocímetro GPS de DivineDeck). Sin GPS no hay origen para la ruta:
 * [awaitLocation] devuelve null en ese caso y el ViewModel muestra el error.
 */
class NavLocationProvider(context: Context) {
    private val fused = LocationServices.getFusedLocationProviderClient(context.applicationContext)

    /** Última ubicación conocida del sistema, o null si nunca hubo fix. */
    suspend fun lastLocation(): Location? = try {
        withContext(Dispatchers.IO) { Tasks.await(fused.lastLocation) }
    } catch (_: Exception) {
        null
    }

    /**
     * Devuelve la última ubicación si existe; si no, espera un fix nuevo
     * hasta [timeoutMs]. Null si no hay permiso de ubicación o no hay fix.
     */
    suspend fun awaitLocation(timeoutMs: Long = 10_000): Location? {
        lastLocation()?.let { return it }
        return try {
            withTimeout(timeoutMs) {
                callbackFlow<Location> {
                    val callback = object : LocationCallback() {
                        override fun onLocationResult(result: LocationResult) {
                            result.lastLocation?.let { trySend(it) }
                        }
                    }
                    val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2_000).build()
                    try {
                        fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
                    } catch (_: SecurityException) {
                        close()
                    }
                    awaitClose { fused.removeLocationUpdates(callback) }
                }.first()
            }
        } catch (_: TimeoutCancellationException) {
            null
        } catch (_: Exception) {
            null
        }
    }
}
