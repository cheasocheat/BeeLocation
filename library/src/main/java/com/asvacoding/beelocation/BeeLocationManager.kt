package com.asvacoding.beelocation

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.IntentSender
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.location.*

/**
 * Created by cheasocheat On 8/17/18.
 */
class BeeLocationManager constructor(private val activity: Activity) : GoogleApiClient.OnConnectionFailedListener, GoogleApiClient.ConnectionCallbacks {

    companion object {
        const val REQUEST_CHECK_SETTINGS = 0x1
    }

    //Private Field
    private val TAG = BeeLocationManager::class.java.simpleName!!
    private var mGoogleApiClient: GoogleApiClient? = null
    private val locationManager = activity.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    //Last Location
    private var mFusedLocationProviderClient: FusedLocationProviderClient? = null

    /* This is public method to initialize value of google api*/
    fun initGoogleApiClient() {
        if (mGoogleApiClient == null) {
            mGoogleApiClient = GoogleApiClient.Builder(activity.applicationContext)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build()

            mGoogleApiClient?.connect()
        }
    }

    /**
     * This method is used to check location provider was enabled or not on device
     * - if device is not support GPS, it will not able to use this library
     * @author Socheat
     */
    fun isLocationProviderEnabled() : Boolean {
        var supportGPS = true
        if (!hasGPSDevice()) {
            supportGPS = false
            return supportGPS
        }
        return !(!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) && supportGPS)
    }


    /**
     * This method is used to get last location via Google Api Client
     * @author Socheat
     */
    @SuppressLint("MissingPermission")
    fun getLastLocationByGoogleApi(completion :(location : Location?)-> Unit){
        val location: Location? = null
        if (mFusedLocationProviderClient == null) {
            mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(activity)
        }

        mFusedLocationProviderClient?.lastLocation?.addOnSuccessListener {
            if (it != null) {
                completion(it)
            } else {
                val isGPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                if (!(isGPSEnabled || isNetworkEnabled)) {
                    this.initGoogleApiClient()
                    completion(location)
                }
            }
        }
    }


    private fun hasGPSDevice(): Boolean {
        val providers = locationManager.allProviders ?: return false
        return providers.contains(LocationManager.GPS_PROVIDER)
    }

    override fun onConnectionFailed(connectionResult: ConnectionResult) {
        Log.e(TAG, connectionResult.errorMessage)
    }

    override fun onConnected(p0: Bundle?) {
        val locationRequest = LocationRequest.create()
        locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY//Setting priotity of Location request to high
        locationRequest.interval = (30 * 1000).toLong()
        locationRequest.fastestInterval = (15 * 1000).toLong()//15 sec Time interval for location update
        val builder = LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest)
        builder.setAlwaysShow(true) //this is the key ingredient to show dialog always when GPS is off

        val result = LocationServices.SettingsApi.checkLocationSettings(mGoogleApiClient, builder.build())
        result.setResultCallback { result ->
            val status = result.status
            val state = result.locationSettingsStates
            when (status.statusCode) {
                LocationSettingsStatusCodes.SUCCESS -> {
                    //Do Sth when GPS Enabled
                }
                LocationSettingsStatusCodes.RESOLUTION_REQUIRED -> {
                    // Location settings are not satisfied. But could be fixed by showing the user
                    // a dialog.
                    try {
                        // Show the dialog by calling startResolutionForResult(),
                        // and check the result in onActivityResult().
                        status.startResolutionForResult(activity, REQUEST_CHECK_SETTINGS)
                    } catch (e: IntentSender.SendIntentException) {
                        //e.printStackTrace()
                        // Ignore the error.
                        Log.e(TAG, e.localizedMessage)
                    }

                }
                LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE -> {

                }
            }// Location settings are not satisfied. However, we have no way to fix the
            // settings so we won't show the dialog.
        }
    }

    override fun onConnectionSuspended(p0: Int) {
        mGoogleApiClient?.connect()
    }
}