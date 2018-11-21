package com.tweener.android.locationprovider

import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import io.reactivex.subjects.PublishSubject

/**
 * This class helps detecting when the user's device enters or exits a list of given geofences.
 *
 * You need to provide a [PendingIntent] using the [setPendingIntent] method to receive notifications when entering or exiting a geofence.
 *
 * This class requires the [ACCESS_FINE_LOCATION] permission to be already granted to work properly. Subscribe to [requestPermission].
 */
class GeofenceLocationProvider(private val context: Context) {

    companion object {
        private val TAG = GeofenceLocationProvider::class.java.simpleName

        private const val REQUIRED_PERMISSION = ACCESS_FINE_LOCATION
    }

    // Observable properties
    val requestPermission: PublishSubject<String> = PublishSubject.create() // String: Name of the permission to request

    private val geofencingClient = LocationServices.getGeofencingClient(context)
    private lateinit var geofencingRequest: GeofencingRequest
    private lateinit var geofencePendingIntent: PendingIntent

    /**
     * Adds a list of [Geofence] to monitor when the device enters or exists these geofences.
     */
    fun addGeofences(geofences: List<Geofence>) {
        geofencingRequest = GeofencingRequest.Builder().apply {
            setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            addGeofences(geofences)
        }.build()

        addGeofencesToClient()
    }

    /**
     * Sets the [PendingIntent] required to receive notifications when at least one [Geofence] is entered or exited.
     */
    fun setPendingIntent(pendingIntent: PendingIntent) {
        geofencePendingIntent = pendingIntent
    }

    private fun addGeofencesToClient() {
        if (ContextCompat.checkSelfPermission(context, REQUIRED_PERMISSION) == PackageManager.PERMISSION_GRANTED) {
            // All permissions granted, we can add the geofences
            geofencingClient.addGeofences(geofencingRequest, geofencePendingIntent)?.run {
                addOnCompleteListener {
                    Log.i(TAG, "addGeofencesToClient: GEOFENCES ADDED")
                }

                addOnFailureListener { throwable ->
                    Log.e(TAG, "addGeofencesToClient: GEOFENCES NOT ADDED: ", throwable)
                }
            }
        } else {
            // At least one permission not granted
            requestPermission.onNext(REQUIRED_PERMISSION)
        }
    }
}