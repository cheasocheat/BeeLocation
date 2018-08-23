package com.asvacoding.beelocation

import android.annotation.TargetApi
import android.app.Service
import android.content.*
import android.location.*
import android.os.*
import android.support.v4.content.LocalBroadcastManager
import android.util.Log
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*


/**
 * Created by cheasocheat On 8/17/18.
 */
class BeeLocationService : Service(), LocationListener {
    private val TAG = BeeLocationService::class.java.simpleName!!
    private val binder = LocationServiceBinder()
    private var kalmanFilter: KalmanLatLong? = null
    //Location
    private var locationManager: LocationManager? = null
    private var isLocationManagerUpdatingLocation: Boolean = false
    private var lstLocation: ArrayList<Location>? = null
    private var lstLastLocation: ArrayList<Location>? = null
    private var lstNoAccuracyLocation: ArrayList<Location>? = null
    private var lstLowAccuracyLocation: ArrayList<Location>? = null
    private var lstKalmanNGLocation: ArrayList<Location>? = null

    //Battery Info
    var batteryLevelArray: ArrayList<Int>? = null
    var batteryLevelScaledArray: ArrayList<Float>? = null

    //Logging
    private var enableLog = false
    private var writeLog = false

    //Save Log
    private var batteryScale: Int = 0
    private var gpsCount: Int = 0
    private var currentSpeed = 0.0f // meters/second

    //other
    private var runStartTimeInMillis: Long = 0


    override fun onCreate() {
        this.lstLocation = ArrayList()
        this.lstLastLocation = ArrayList()
        this.lstNoAccuracyLocation = ArrayList()
        this.lstLowAccuracyLocation = ArrayList()
        this.lstKalmanNGLocation = ArrayList()
        this.enableLog = false
        this.batteryLevelArray = ArrayList()
        this.batteryLevelScaledArray = ArrayList()
        this.kalmanFilter = KalmanLatLong(3F)
        this.registerReceiver(batteryInfoReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    /**
     * Public Access Method
     */

    /*
     * Tracking location
     * To get more accuracy data plz set it as True
     */
    fun startTrackingLocation(enableLog: Boolean) {
        Log.d(TAG, "Start tracking gps location service")
        this.enableLog = enableLog
        if (!isLocationManagerUpdatingLocation) {
            isLocationManagerUpdatingLocation = true

            runStartTimeInMillis = this.getLogTime()

            this.clearListLocation()

            locationManager = this.getSystemService(Context.LOCATION_SERVICE) as LocationManager

            //Exception thrown when GPS or Network provider were not available on the user's device.
            try {
                val criteria = Criteria()
                criteria.accuracy = Criteria.ACCURACY_FINE //setAccuracyは内部では、https://stackoverflow.com/a/17874592/1709287の用にHorizontalAccuracyの設定に変換されている。
                criteria.powerRequirement = Criteria.POWER_HIGH
                criteria.isAltitudeRequired = false
                criteria.isSpeedRequired = true
                criteria.isCostAllowed = true
                criteria.isBearingRequired = false

                //API level 9 and up
                criteria.horizontalAccuracy = Criteria.ACCURACY_HIGH
                criteria.verticalAccuracy = Criteria.ACCURACY_HIGH
                //criteria.setBearingAccuracy(Criteria.ACCURACY_HIGH);
                //criteria.setSpeedAccuracy(Criteria.ACCURACY_HIGH);

                val gpsFreqInMillis = 5000
                val gpsFreqInDistance = 5  // in meters

                //appLocationManager.addGpsStatusListener(this)

                locationManager?.requestLocationUpdates(gpsFreqInMillis.toLong(), gpsFreqInDistance.toFloat(), criteria, this, null)

                /* Battery Consumption Measurement */
                gpsCount = 0

            } catch (e: IllegalArgumentException) {
                Log.e(TAG, e.localizedMessage)
            } catch (e: SecurityException) {
                Log.e(TAG, e.localizedMessage)
            } catch (e: RuntimeException) {
                Log.e(TAG, e.localizedMessage)
            }

        }
    }


    /*
    *
    *  If user don't want to write log , just put it false
    * */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    fun stopTrackingLocation(writeLog: Boolean) {
        Log.d(TAG, "Stop tracking gps location service")
        this.writeLog = writeLog

        locationManager?.let {
            it.removeUpdates(this)
        }
        isLocationManagerUpdatingLocation = false

        if (enableLog) {
            if (lstLocation?.size!! > 1 && batteryLevelArray?.size!! > 1) {
                try {
                    val elapsedTimeInSeconds = (SystemClock.elapsedRealtimeNanos() / 1000000 - runStartTimeInMillis) / 1000
                    var totalDistanceInMeters = 0f
                    lstLocation?.forEachIndexed { index, location ->
                        totalDistanceInMeters += location.distanceTo(lstLocation?.get(index + 1))
                    }
                    val batteryLevelStart = batteryLevelArray?.get(0)
                    val batteryLevelEnd = batteryLevelArray?.get(batteryLevelArray!!.size - 1)

                    val batteryLevelScaledStart = batteryLevelScaledArray?.get(0)
                    val batteryLevelScaledEnd = batteryLevelScaledArray?.get(batteryLevelScaledArray!!.size - 1)

                    if (writeLog) {
                        saveLog(elapsedTimeInSeconds, totalDistanceInMeters.toDouble(), gpsCount, batteryLevelStart!!, batteryLevelEnd!!, batteryLevelScaledStart!!, batteryLevelScaledEnd!!)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, e.localizedMessage)
                }
                enableLog = false
            }
        }
    }


    /**
     * Returns the current time in either millis or nanos depending on the api level to be used with
     * [.getElapsedMillis].
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private fun getLogTime(): Long {
        return if (Build.VERSION_CODES.JELLY_BEAN_MR1 <= Build.VERSION.SDK_INT) {
            SystemClock.elapsedRealtimeNanos()
        } else {
            SystemClock.uptimeMillis()
        }
    }


    /**
     *  Implementation Location Listener Part
     *  @author Socheat
     */
    override fun onLocationChanged(location: Location?) {
        Log.d(TAG, "AppLocation : (" + location?.latitude + "," + location?.longitude + ")")

        //count gps changed
        gpsCount++
        if (enableLog) {
            location?.let {
                this.filterAndAddLocation(location)
            }
        }

        //Send Broadcast new location updated
        val intent = Intent(LocationConstant.Action.LOCATION_UPDATE)
        intent.putExtra(LocationConstant.Bundle.LOCATION, location)
        LocalBroadcastManager.getInstance(this.application).sendBroadcast(intent)
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        if (provider.equals(LocationManager.GPS_PROVIDER)) {
            if (status == LocationProvider.OUT_OF_SERVICE) {
                notifyLocationProviderStatusUpdated(false);
            } else {
                notifyLocationProviderStatusUpdated(true);
            }
        }
    }

    override fun onProviderEnabled(provider: String?) {
        if (provider.equals(LocationManager.GPS_PROVIDER)) {
            notifyLocationProviderStatusUpdated(true);
        }
    }

    override fun onProviderDisabled(provider: String?) {
        if (provider.equals(LocationManager.GPS_PROVIDER)) {
            this.notifyLocationProviderStatusUpdated(false)
        }
    }


    /**
     *  Implementation Service Part
     *  @author Socheat
     */
    override fun onBind(p0: Intent?): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return Service.START_STICKY
    }

    override fun onRebind(intent: Intent) {
        Log.d(TAG, "onRebind ")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "onUnbind")
        return true
    }

    override fun onDestroy() {
        Log.d(TAG, "OnDestroy")
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "onTaskRemoved ")
        this.stopTrackingLocation(false)
        this.stopSelf()
    }

    /**
     * Inner Class Part
     */
    inner class LocationServiceBinder : Binder() {
        val service: BeeLocationService
            get() = this@BeeLocationService
    }


    /**
     * Broadcast Receiver Part
     */
    //Battery Consumption
    private val batteryInfoReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctxt: Context, intent: Intent) {
            val batteryLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)

            val batteryLevelScaled = batteryLevel / scale.toFloat()



            batteryLevelArray?.add(Integer.valueOf(batteryLevel))
            batteryLevelScaledArray?.add(java.lang.Float.valueOf(batteryLevelScaled))
            batteryScale = scale
        }
    }

    private fun notifyLocationProviderStatusUpdated(isLocationProviderAvailable: Boolean) {
        //Broadcast location provider status change here
    }

    /**
     * Util Func
     */

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private fun filterAndAddLocation(location: Location) {
        val age = getLocationAge(location)
        try {
            if (age > 5 * 1000) { //more than 5 seconds
                Log.d(TAG, "AppLocation : Location is old")
                lstLastLocation?.add(location)
                return
            }


            if (location.accuracy <= 0) {
                Log.d(TAG, "AppLocation : Latitidue and longitude values are invalid.")
                lstNoAccuracyLocation?.add(location)
                return
            }

            //setAccuracy(newLocation.getAccuracy());
            val horizontalAccuracy = location.accuracy
            if (horizontalAccuracy > 10) { //10meter filter
                Log.d(TAG, "AppLocation : Accuracy is too low.")
                lstLowAccuracyLocation?.add(location)
                return
            }

            /* Kalman Filter */
            val q_value: Float = if (currentSpeed == 0.0f) {
                3.0f //3 meters per second
            } else {
                currentSpeed // meters per second
            }

            val locationTimeInMillis = location.elapsedRealtimeNanos / 1000000
            val elapsedTimeInMillis = locationTimeInMillis - runStartTimeInMillis

            kalmanFilter?.Process(location.latitude, location.longitude, location.accuracy, elapsedTimeInMillis, q_value)
            val predictedLat = kalmanFilter?._lat
            val predictedLng = kalmanFilter?._lng

            val predictedLocation = Location("")//provider name is unecessary
            predictedLocation.latitude = predictedLat!!//your coords of course
            predictedLocation.longitude = predictedLng!!
            val predictedDeltaInMeters = predictedLocation.distanceTo(location)

            if (predictedDeltaInMeters > 60) {
                Log.d(TAG, "AppLocation : Kalman Filter detects mal GPS, we should probably remove this from track")
                kalmanFilter?.consecutiveRejectCount = kalmanFilter?.consecutiveRejectCount?.plus(1)

                if (kalmanFilter?.consecutiveRejectCount!! > 3) {
                    kalmanFilter = KalmanLatLong(3F) //reset Kalman Filter if it rejects more than 3 times in raw.
                }

                lstKalmanNGLocation?.add(location)
                return
            } else {
                kalmanFilter?.consecutiveRejectCount = 0
            }

            /* Notifiy predicted location to UI */
            val intent = Intent(LocationConstant.Action.PREDICT_LOCATION)
            intent.putExtra(LocationConstant.Bundle.LOCATION, predictedLocation)
            LocalBroadcastManager.getInstance(this.application).sendBroadcast(intent)

            Log.d(TAG, "AppLocation : Location quality is good enough.")
            currentSpeed = location.speed
            lstLocation?.add(location)
        } catch (e: Exception) {
            Log.e(TAG, e.localizedMessage)
        }
    }

    /**
     * Get Location Age
     */
    private fun getLocationAge(location: Location): Long {
        val locationAge: Long
        locationAge = if (android.os.Build.VERSION.SDK_INT >= 17) {
            val currentTimeInMilli = SystemClock.elapsedRealtimeNanos() / 1000000
            val locationTimeInMilli = location.elapsedRealtimeNanos / 1000000
            currentTimeInMilli - locationTimeInMilli
        } else {
            System.currentTimeMillis() - location.time
        }
        return locationAge
    }

    private fun clearListLocation() {
        lstLocation?.clear()
        lstLastLocation?.clear()
        lstNoAccuracyLocation?.clear()
        lstLowAccuracyLocation?.clear()
        lstKalmanNGLocation?.clear()
        batteryLevelArray?.clear()
        batteryLevelScaledArray?.clear()
    }

    /* Data Logging */
    @Synchronized
    private fun saveLog(timeInSeconds: Long, distanceInMeters: Double, gpsCount: Int, batteryLevelStart: Int, batteryLevelEnd: Int, batteryLevelScaledStart: Float, batteryLevelScaledEnd: Float) {
        val fileNameDateTimeFormat = SimpleDateFormat("yyyy_MMdd_HHmm")
        val filePath = (this.getExternalFilesDir(null)!!.absolutePath + "/"
                + fileNameDateTimeFormat.format(Date()) + "_battery" + ".csv")

        Log.d(TAG, "saving to $filePath")

        var fileWriter: FileWriter? = null
        try {
            fileWriter = FileWriter(filePath, false)
            fileWriter.append("Time,Distance,GPSCount,BatteryLevelStart,BatteryLevelEnd,BatteryLevelStart(/$batteryScale),BatteryLevelEnd(/$batteryScale)\n")
            val record = "" + timeInSeconds + ','.toString() + distanceInMeters + ','.toString() + gpsCount + ','.toString() + batteryLevelStart + ','.toString() + batteryLevelEnd + ','.toString() + batteryLevelScaledStart + ','.toString() + batteryLevelScaledEnd + '\n'.toString()
            fileWriter.append(record)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            if (fileWriter != null) {
                try {
                    fileWriter!!.close()
                } catch (ioe: IOException) {
                    ioe.printStackTrace()
                }

            }
        }
    }
}