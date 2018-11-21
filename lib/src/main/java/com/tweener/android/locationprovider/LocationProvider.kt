package com.tweener.android.locationprovider

import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.*
import com.tweener.android.locationprovider.LocationProvider.Companion.create
import io.reactivex.Maybe
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject

/**
 * This class helps retrieving the user's device location every time its location changes or every given time interval, subscribing to [location].
 *
 * It is also possible to retrieve the latest known location, if any.
 *
 * This class requires the [ACCESS_FINE_LOCATION] permission to be already granted to work properly. Subscribe to [requestPermission] and [openLocationSettings].
 *
 * Use [create] method to get a new instance of a [LocationProvider].
 */
class LocationProvider(
        private val context: Context,
        priority: Int,
        updateInterval: Long,
        fastestInterval: Long,
        smallestDisplacement: Float
) {

    companion object {
        private val TAG = LocationProvider::class.java.simpleName

        private const val REQUIRED_PERMISSION = ACCESS_FINE_LOCATION
        private const val UPDATE_INTERVAL_DEFAULT = 10 * 1000L; // 10 seconds
        private const val FASTEST_INTERVAL_DEFAULT = 2 * 1000L; // 2 seconds
        private const val SMALLEST_DISPLACEMENT_DEFAULT = 50F; // 50 meters

        /**
         * Creates a new instance of [LocationProvider] with the given [Context] and the optional parameters:
         *
         * ```
         * val provider: LocationProvider = LocationProvider.create(context) {
         *     priority = LocationRequest.PRIORITY_HIGH_ACCURACY
         *     updateInterval = 10 * 1000L
         *     fastestInterval = 2 * 1000L
         *     smallestDisplacement = 50f
         * }
         * ```
         */
        fun create(context: Context, block: LocationProviderBuilder.() -> Unit): LocationProvider = LocationProviderBuilder(context).apply(block).build()
    }

    // region Observable properties

    /**
     * Emits the user's device location every time it gets updated
     */
    val location: BehaviorSubject<Location> = BehaviorSubject.create()

    /**
     * Emits the name of the permission to request when not it's not granted
     */
    val requestPermission: PublishSubject<String> = PublishSubject.create()

    /**
     * Emits whether or not the Location Change Settings are available
     */
    val openLocationSettings: PublishSubject<Boolean> = PublishSubject.create()

    // endregion

    private val locationRequest = LocationRequest()
    private val locationSettingsRequest: LocationSettingsRequest
    private val locationClient = LocationServices.getFusedLocationProviderClient(context)
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult?) {
            locationResult?.lastLocation?.let { location.onNext(it) }
        }
    }

    init {
        locationRequest.priority = priority
        locationRequest.interval = updateInterval
        locationRequest.fastestInterval = fastestInterval
        locationRequest.smallestDisplacement = smallestDisplacement

        val builder = LocationSettingsRequest.Builder()
        builder.addLocationRequest(locationRequest)
        locationSettingsRequest = builder.build()
    }

    /**
     * Starts receiving location updates if permissions are already granted. Otherwise, will ask first to grant permissions.
     * [location] will start emitting.
     */
    fun startReceivingUpdates() {
        checkLocationSettings()
    }

    /**
     * Stops receiving location updates.
     * [location] will stop emitting.
     */
    fun stopReceivingUpdates() {
        locationClient.removeLocationUpdates(locationCallback)
    }

    /**
     * Tries to get the last known location, if any, and if permissions are already granted.
     */
    fun getLastKnownLocation(): Maybe<Location> {
        return Maybe.create { emitter ->
            try {
                if (ContextCompat.checkSelfPermission(context, REQUIRED_PERMISSION) == PackageManager.PERMISSION_GRANTED) {
                    locationClient.lastLocation
                            .addOnSuccessListener { location -> location?.let { emitter.onSuccess(location) } }
                            .addOnFailureListener { throwable -> emitter.onError(throwable) }
                } else {
                    emitter.onComplete()
                }
            } catch (throwable: Throwable) {
                emitter.onError(throwable)
            }
        }
    }

    private fun checkLocationSettings() {
        LocationServices.getSettingsClient(context)
                .checkLocationSettings(locationSettingsRequest)
                .addOnCompleteListener { task ->
                    try {
                        val response = task.getResult(ApiException::class.java)

                        // All location settings are satisfied. The client can initialize location requests here.
                        requestLocationUpdates()
                    } catch (exception: ApiException) {
                        when (exception.statusCode) {
                            // Location settings are not satisfied. But could be fixed by showing the user a dialog.
                            LocationSettingsStatusCodes.RESOLUTION_REQUIRED -> openLocationSettings.onNext(true)

                            // Location settings are not satisfied. However, we have no way to fix the settings so we won't show the dialog.
                            LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE -> openLocationSettings.onNext(false)
                        }
                    }
                }
    }

    private fun requestLocationUpdates() {
        if (ContextCompat.checkSelfPermission(context, REQUIRED_PERMISSION) == PackageManager.PERMISSION_GRANTED) {
            // All permissions granted, we can request location updates
            locationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.myLooper())
        } else {
            // At least one permission not granted
            requestPermission.onNext(REQUIRED_PERMISSION)
        }
    }

    // region Builder

    class LocationProviderBuilder(private val context: Context) {
        var priority: Int = LocationRequest.PRIORITY_HIGH_ACCURACY
        var updateInterval: Long = UPDATE_INTERVAL_DEFAULT
        var fastestInterval: Long = FASTEST_INTERVAL_DEFAULT
        var smallestDisplacement: Float = SMALLEST_DISPLACEMENT_DEFAULT

        fun build(): LocationProvider = LocationProvider(context, priority, updateInterval, fastestInterval, smallestDisplacement)
    }

    // endregion
}