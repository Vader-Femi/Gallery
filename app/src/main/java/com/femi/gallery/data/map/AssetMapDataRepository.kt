package com.femi.gallery.data.map

import android.content.Context
import android.util.Log
import com.femi.gallery.di.IoDispatcher
import com.femi.gallery.domain.geo.MapData
import com.femi.gallery.domain.geo.MapDataCodec
import com.femi.gallery.domain.repository.MapDataRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Reads the map data that ships inside the app (assets/map, made by tools/build_map_data.py). */
@Singleton
class AssetMapDataRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : MapDataRepository {

    private val mutex = Mutex()

    @Volatile
    private var loaded: MapData? = null

    override suspend fun load(): MapData =
        loaded ?: mutex.withLock {
            loaded ?: withContext(io) {
                MapData(
                    land = read("land.bin")?.let(MapDataCodec::decodePolylines).orEmpty(),
                    borders = read("borders.bin")?.let(MapDataCodec::decodePolylines).orEmpty(),
                    places = read("places.bin")?.let(MapDataCodec::decodePlaces).orEmpty(),
                )
            }.also {
                Log.i(TAG, "Map data: ${it.land.size} land outlines, ${it.borders.size} borders, ${it.places.size} places")
                loaded = it
            }
        }

    private fun read(name: String): ByteArray? = try {
        context.assets.open("map/$name").use { it.readBytes() }
    } catch (e: Exception) {
        Log.w(TAG, "Could not read map/$name", e)
        null
    }

    private companion object {
        const val TAG = "MapData"
    }
}
