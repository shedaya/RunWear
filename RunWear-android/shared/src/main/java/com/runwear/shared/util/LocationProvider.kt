package com.runwear.shared.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val LOCATION_CACHE_PREFS = "location_cache"
private const val KEY_LAT = "cached_lat"
private const val KEY_LNG = "cached_lng"
private const val KEY_TIME = "cached_time"
private const val CACHE_MAX_AGE_MS = 3600_000L // 1 hour

data class UserLocation(
    val latitude: Double,
    val longitude: Double
)

enum class LocationErrorReason {
    PERMISSION_DENIED,
    TIMEOUT,
    UNAVAILABLE
}

sealed class LocationFetchResult {
    data class Success(val location: UserLocation) : LocationFetchResult()
    data class Error(val reason: LocationErrorReason) : LocationFetchResult()
}

@Singleton
class LocationProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    
    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun cacheLocation(location: UserLocation) {
        context.getSharedPreferences(LOCATION_CACHE_PREFS, Context.MODE_PRIVATE).edit()
            .putFloat(KEY_LAT, location.latitude.toFloat())
            .putFloat(KEY_LNG, location.longitude.toFloat())
            .putLong(KEY_TIME, System.currentTimeMillis())
            .apply()
    }

    fun getCachedLocation(): UserLocation? {
        val prefs = context.getSharedPreferences(LOCATION_CACHE_PREFS, Context.MODE_PRIVATE)
        val time = prefs.getLong(KEY_TIME, 0L)
        if (time == 0L || System.currentTimeMillis() - time > CACHE_MAX_AGE_MS) return null
        val lat = prefs.getFloat(KEY_LAT, 0f).toDouble()
        val lng = prefs.getFloat(KEY_LNG, 0f).toDouble()
        if (lat == 0.0 && lng == 0.0) return null
        return UserLocation(lat, lng)
    }

    /**
     * Get last known location instantly from cache. Returns null if no cached location.
     * Ideal for background services (tiles, complications) where GPS may not be active.
     */
    suspend fun getLastKnownLocation(): UserLocation? {
        if (!hasLocationPermission()) return null
        return try {
            suspendCancellableCoroutine { continuation ->
                fusedLocationClient.lastLocation
                    .addOnSuccessListener { location: Location? ->
                        if (location != null) {
                            continuation.resume(UserLocation(location.latitude, location.longitude))
                        } else {
                            continuation.resume(null)
                        }
                    }
                    .addOnFailureListener {
                        continuation.resume(null)
                    }
            }
        } catch (e: SecurityException) {
            null
        }
    }

    suspend fun getCurrentLocation(): Result<UserLocation> = runCatching {
        if (!hasLocationPermission()) {
            throw SecurityException("Location permission not granted")
        }
        
        suspendCancellableCoroutine { continuation ->
            val cancellationTokenSource = CancellationTokenSource()
            
            continuation.invokeOnCancellation {
                cancellationTokenSource.cancel()
            }
            
            try {
                fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    cancellationTokenSource.token
                ).addOnSuccessListener { location: Location? ->
                    if (location != null) {
                        val userLoc = UserLocation(location.latitude, location.longitude)
                        cacheLocation(userLoc)
                        continuation.resume(userLoc)
                    } else {
                        // Try to get last known location as fallback
                        fusedLocationClient.lastLocation.addOnSuccessListener { lastLocation ->
                            if (lastLocation != null) {
                                val userLoc = UserLocation(lastLocation.latitude, lastLocation.longitude)
                                cacheLocation(userLoc)
                                continuation.resume(userLoc)
                            } else {
                                continuation.resumeWithException(Exception("Unable to get location"))
                            }
                        }.addOnFailureListener { e ->
                            continuation.resumeWithException(e)
                        }
                    }
                }.addOnFailureListener { e ->
                    continuation.resumeWithException(e)
                }
            } catch (e: SecurityException) {
                continuation.resumeWithException(e)
            }
        }
    }

    /**
     * Get current location with a 15-second timeout (matches PWA).
     * Returns a sealed result indicating success or the reason for failure.
     */
    suspend fun getCurrentLocationWithTimeout(): LocationFetchResult {
        if (!hasLocationPermission()) {
            return LocationFetchResult.Error(LocationErrorReason.PERMISSION_DENIED)
        }

        val result = withTimeoutOrNull(15000L) {
            suspendCancellableCoroutine<UserLocation?> { continuation ->
                val cancellationTokenSource = CancellationTokenSource()

                continuation.invokeOnCancellation {
                    cancellationTokenSource.cancel()
                }

                try {
                    fusedLocationClient.getCurrentLocation(
                        Priority.PRIORITY_BALANCED_POWER_ACCURACY, // Faster than HIGH_ACCURACY
                        cancellationTokenSource.token
                    ).addOnSuccessListener { location: Location? ->
                        if (location != null) {
                            val userLoc = UserLocation(location.latitude, location.longitude)
                            cacheLocation(userLoc)
                            continuation.resume(userLoc)
                        } else {
                            // Try last known location as fallback
                            fusedLocationClient.lastLocation.addOnSuccessListener { lastLocation ->
                                if (lastLocation != null) {
                                    val userLoc = UserLocation(lastLocation.latitude, lastLocation.longitude)
                                    cacheLocation(userLoc)
                                    continuation.resume(userLoc)
                                } else {
                                    continuation.resume(null)
                                }
                            }.addOnFailureListener {
                                continuation.resume(null)
                            }
                        }
                    }.addOnFailureListener {
                        continuation.resume(null)
                    }
                } catch (e: SecurityException) {
                    continuation.resume(null)
                }
            }
        }

        return if (result != null) {
            LocationFetchResult.Success(result)
        } else {
            LocationFetchResult.Error(LocationErrorReason.TIMEOUT)
        }
    }
}
