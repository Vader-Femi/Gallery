@file:Suppress("unused", "UNUSED_PARAMETER")
package androidx.media3.common.audio

interface SpeedProvider {
    fun getSpeed(timeUs: Long): Float
    fun getNextSpeedChangeTimeUs(timeUs: Long): Long
}
