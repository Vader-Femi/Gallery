package com.femi.gallery.data.db

import android.content.Context
import com.femi.gallery.di.IoDispatcher
import com.femi.gallery.domain.clip.DetectedEmbedding
import com.femi.gallery.domain.color.ColorCodec
import com.femi.gallery.domain.color.DetectedColor
import com.femi.gallery.domain.geo.GeoPoint
import com.femi.gallery.domain.geo.LocationCodec
import com.femi.gallery.domain.geo.LocationEntry
import com.femi.gallery.domain.label.DetectedLabel
import com.femi.gallery.domain.label.LabelCodec
import com.femi.gallery.domain.repository.ClipStore
import com.femi.gallery.domain.repository.ColorStore
import com.femi.gallery.domain.repository.LabelStore
import com.femi.gallery.domain.repository.LocationStore
import com.femi.gallery.domain.repository.TextStore
import com.femi.gallery.domain.text.DetectedText
import com.femi.gallery.domain.text.TextCodec
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomTextStore @Inject constructor(
    @param:ApplicationContext context: Context,
    @param:IoDispatcher io: CoroutineDispatcher,
    private val dao: TextDao,
) : DiffingStore<DetectedText>(context, io, "texts.bin", TextCodec::decode), TextStore {

    override fun idOf(entry: DetectedText) = entry.id
    override suspend fun readAll() = dao.all().map { DetectedText(it.id, it.modified, it.text) }
    override suspend fun write(changed: List<DetectedText>, removedIds: List<Long>) =
        dao.apply(changed.map { TextRow(it.id, it.modified, it.text) }, removedIds)

    override suspend fun load(): List<DetectedText> = loadAll()
    override suspend fun save(entries: List<DetectedText>) = saveAll(entries)
}

@Singleton
class RoomLabelStore @Inject constructor(
    @param:ApplicationContext context: Context,
    @param:IoDispatcher io: CoroutineDispatcher,
    private val dao: LabelDao,
) : DiffingStore<DetectedLabel>(context, io, "labels.bin", LabelCodec::decode), LabelStore {

    override fun idOf(entry: DetectedLabel) = entry.id
    override suspend fun readAll() = dao.all().map { DetectedLabel(it.id, it.modified, it.labels) }
    override suspend fun write(changed: List<DetectedLabel>, removedIds: List<Long>) =
        dao.apply(changed.map { LabelRow(it.id, it.modified, it.labels) }, removedIds)

    override suspend fun load(): List<DetectedLabel> = loadAll()
    override suspend fun save(entries: List<DetectedLabel>) = saveAll(entries)
}

@Singleton
class RoomColorStore @Inject constructor(
    @param:ApplicationContext context: Context,
    @param:IoDispatcher io: CoroutineDispatcher,
    private val dao: ColorDao,
) : DiffingStore<DetectedColor>(context, io, "colors.bin", ColorCodec::decode), ColorStore {

    override fun idOf(entry: DetectedColor) = entry.id
    override suspend fun readAll() = dao.all().map { DetectedColor(it.id, it.modified, it.colors) }
    override suspend fun write(changed: List<DetectedColor>, removedIds: List<Long>) =
        dao.apply(changed.map { ColorRow(it.id, it.modified, it.colors) }, removedIds)

    override suspend fun load(): List<DetectedColor> = loadAll()
    override suspend fun save(entries: List<DetectedColor>) = saveAll(entries)
}

@Singleton
class RoomLocationStore @Inject constructor(
    @param:ApplicationContext context: Context,
    @param:IoDispatcher io: CoroutineDispatcher,
    private val dao: LocationDao,
) : DiffingStore<LocationEntry>(context, io, "locations.bin", LocationCodec::decode), LocationStore {

    override fun idOf(entry: LocationEntry) = entry.id
    override suspend fun readAll() = dao.all().map {
        val latitude = it.latitude
        val longitude = it.longitude
        LocationEntry(it.id, it.modified, if (latitude != null && longitude != null) GeoPoint(latitude, longitude) else null)
    }

    override suspend fun write(changed: List<LocationEntry>, removedIds: List<Long>) =
        dao.apply(changed.map { LocationRow(it.id, it.modified, it.place?.latitude, it.place?.longitude) }, removedIds)

    override suspend fun load(): List<LocationEntry> = loadAll()
    override suspend fun save(entries: List<LocationEntry>) = saveAll(entries)
}

@Singleton
class RoomClipStore @Inject constructor(
    @param:ApplicationContext context: Context,
    @param:IoDispatcher io: CoroutineDispatcher,
    private val dao: ClipDao,
) : DiffingStore<DetectedEmbedding>(
    context,
    io,
    // The file the placeholder model used to write held made-up numbers, not worth importing: it is just deleted.
    "clip.bin",
    { null },
),
    ClipStore {

    override fun idOf(entry: DetectedEmbedding) = entry.id
    override suspend fun readAll() = dao.all().map { DetectedEmbedding(it.id, it.modified, it.embedding) }
    override suspend fun write(changed: List<DetectedEmbedding>, removedIds: List<Long>) =
        dao.apply(changed.map { ClipRow(it.id, it.modified, it.embedding) }, removedIds)

    override suspend fun load(): List<DetectedEmbedding> = loadAll()
    override suspend fun save(entries: List<DetectedEmbedding>) = saveAll(entries)
}
