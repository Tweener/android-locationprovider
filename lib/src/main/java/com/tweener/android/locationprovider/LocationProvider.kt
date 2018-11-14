package com.tweener.android.locationprovider

import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.*
import io.reactivex.Maybe
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject

class LocationProvider(
        private val context: Context
) {

    companion object {
        private val TAG = LocationProvider::class.java.simpleName

        private const val REQUIRED_PERMISSION = ACCESS_FINE_LOCATION
        private const val UPDATE_INTERVAL = 10 * 1000L; // 10 seconds
        private const val FASTEST_INTERVAL = 2 * 1000L; // 2 seconds
        private const val SMALLEST_DISPLACEMENT = 50F; // 50 meters
    }

    // Observable properties
    val location: BehaviorSubject<Location> = BehaviorSubject.create() // Location: Last updated location
    val requestPermission: PublishSubject<String> = PublishSubject.create() // String: Name of the permission to request
    val openLocationSettings: PublishSubject<Boolean> =
            PublishSubject.create() // Boolean: Whether or not the Location Change Settings are available

    private val locationRequest = LocationRequest()
    private val locationSettingsRequest: LocationSettingsRequest
    private val locationClient = LocationServices.getFusedLocationProviderClient(context)
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult?) {
            locationResult?.lastLocation?.let {
                Log.i(TAG, "onLocationChanged: $it")
                location.onNext(it)
            }
        }
    }

    init {
        locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        locationRequest.interval = UPDATE_INTERVAL
        locationRequest.fastestInterval = FASTEST_INTERVAL
        locationRequest.smallestDisplacement = SMALLEST_DISPLACEMENT

        val builder = LocationSettingsRequest.Builder()
        builder.addLocationRequest(locationRequest)
        locationSettingsRequest = builder.build()
    }

    /**
     * Starts receiving location updates if permissions are already granted. Otherwise, will ask first to grant permissions.
     */
    fun startReceivingUpdates() {
        checkLocationSettings()
    }

    /**
     * Stops receiving location updates.
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
}