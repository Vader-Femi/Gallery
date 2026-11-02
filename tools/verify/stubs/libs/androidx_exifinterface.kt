@file:Suppress("unused")
package androidx.exifinterface.media

/** Stub for tools/verify: only what the app calls. */
class ExifInterface(fileDescriptor: java.io.FileDescriptor) {
    constructor(stream: java.io.InputStream) : this(java.io.FileDescriptor())
    fun getAttribute(tag: String): String? = null
    fun setAttribute(tag: String, value: String?) {}
    fun saveAttributes() {}
    companion object {
        const val ORIENTATION_NORMAL = 1
        const val TAG_DATETIME = "DATETIME"
        const val TAG_DATETIME_DIGITIZED = "DATETIME_DIGITIZED"
        const val TAG_DATETIME_ORIGINAL = "DATETIME_ORIGINAL"
        const val TAG_EXPOSURE_PROGRAM = "EXPOSURE_PROGRAM"
        const val TAG_EXPOSURE_TIME = "EXPOSURE_TIME"
        const val TAG_FLASH = "FLASH"
        const val TAG_FOCAL_LENGTH = "FOCAL_LENGTH"
        const val TAG_F_NUMBER = "F_NUMBER"
        const val TAG_GPS_ALTITUDE = "GPS_ALTITUDE"
        const val TAG_GPS_ALTITUDE_REF = "GPS_ALTITUDE_REF"
        const val TAG_GPS_LATITUDE = "GPS_LATITUDE"
        const val TAG_GPS_LATITUDE_REF = "GPS_LATITUDE_REF"
        const val TAG_GPS_LONGITUDE = "GPS_LONGITUDE"
        const val TAG_GPS_LONGITUDE_REF = "GPS_LONGITUDE_REF"
        const val TAG_MAKE = "MAKE"
        const val TAG_MODEL = "MODEL"
        const val TAG_ORIENTATION = "ORIENTATION"
        const val TAG_WHITE_BALANCE = "WHITE_BALANCE"
    }
}
