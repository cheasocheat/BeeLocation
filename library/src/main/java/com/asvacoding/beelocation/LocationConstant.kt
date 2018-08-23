package com.asvacoding.beelocation

/**
 * Created by cheasocheat On 8/17/18.
 */
class LocationConstant {
    interface Action {
        companion object {
            const val LOCATION_UPDATE = "location_update"
            const val PREDICT_LOCATION = "predict_location"
        }
    }

    interface Bundle {
        companion object {
            const val LOCATION = "location"
        }
    }
}