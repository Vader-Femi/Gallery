package com.femi.gallery.di

import com.femi.gallery.data.clip.FileClipStore
import com.femi.gallery.data.db.RoomLabelStore
import com.femi.gallery.data.db.RoomColorStore
import com.femi.gallery.data.db.RoomTextStore
import com.femi.gallery.data.db.RoomLocationStore
import com.femi.gallery.data.clip.UnavailableImageEmbedder
import com.femi.gallery.data.duplicates.ContentFileHasher
import com.femi.gallery.data.repository.DuplicateRepositoryImpl
import com.femi.gallery.data.story.FileStoryEditStore
import com.femi.gallery.data.edit.BitmapEditor
import com.femi.gallery.data.label.MlKitImageLabeller
import com.femi.gallery.data.map.AssetMapDataRepository
import com.femi.gallery.data.repository.ClipIndexRepositoryImpl
import com.femi.gallery.data.repository.ColorIndexRepositoryImpl
import com.femi.gallery.data.repository.LabelIndexRepositoryImpl
import com.femi.gallery.data.repository.LocationRepositoryImpl
import com.femi.gallery.data.repository.MediaRepositoryImpl
import com.femi.gallery.data.repository.TextIndexRepositoryImpl
import com.femi.gallery.data.text.MlKitTextRecognizer
import com.femi.gallery.domain.repository.ClipIndexRepository
import com.femi.gallery.domain.repository.ClipStore
import com.femi.gallery.domain.repository.ColorIndexRepository
import com.femi.gallery.domain.duplicates.FileHasher
import com.femi.gallery.domain.repository.ColorStore
import com.femi.gallery.domain.repository.DuplicateRepository
import com.femi.gallery.domain.repository.StoryEditStore
import com.femi.gallery.domain.repository.EditRepository
import com.femi.gallery.domain.repository.ImageEmbedder
import com.femi.gallery.domain.repository.ImageLabeller
import com.femi.gallery.domain.repository.LabelIndexRepository
import com.femi.gallery.domain.repository.LabelStore
import com.femi.gallery.domain.repository.LocationRepository
import com.femi.gallery.domain.repository.LocationStore
import com.femi.gallery.domain.repository.MapDataRepository
import com.femi.gallery.domain.repository.MediaRepository
import com.femi.gallery.domain.repository.TextIndexRepository
import com.femi.gallery.domain.repository.TextRecognizer
import com.femi.gallery.domain.repository.TextStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindMediaRepository(impl: MediaRepositoryImpl): MediaRepository

    @Binds
    abstract fun bindEditRepository(impl: BitmapEditor): EditRepository

    @Binds
    abstract fun bindLocationRepository(impl: LocationRepositoryImpl): LocationRepository

    @Binds
    abstract fun bindLocationStore(impl: RoomLocationStore): LocationStore

    @Binds
    abstract fun bindMapDataRepository(impl: AssetMapDataRepository): MapDataRepository

    @Binds
    abstract fun bindTextStore(impl: RoomTextStore): TextStore

    @Binds
    abstract fun bindTextRecognizer(impl: MlKitTextRecognizer): TextRecognizer

    @Binds
    abstract fun bindTextIndexRepository(impl: TextIndexRepositoryImpl): TextIndexRepository

    @Binds
    abstract fun bindColorStore(impl: RoomColorStore): ColorStore

    @Binds
    abstract fun bindStoryEditStore(impl: FileStoryEditStore): StoryEditStore

    @Binds
    abstract fun bindFileHasher(impl: ContentFileHasher): FileHasher

    @Binds
    abstract fun bindDuplicateRepository(impl: DuplicateRepositoryImpl): DuplicateRepository

    @Binds
    abstract fun bindColorIndexRepository(impl: ColorIndexRepositoryImpl): ColorIndexRepository

    @Binds
    abstract fun bindLabelStore(impl: RoomLabelStore): LabelStore

    @Binds
    abstract fun bindImageLabeller(impl: MlKitImageLabeller): ImageLabeller

    @Binds
    abstract fun bindLabelIndexRepository(impl: LabelIndexRepositoryImpl): LabelIndexRepository

    @Binds
    abstract fun bindClipStore(impl: FileClipStore): ClipStore

    @Binds
    abstract fun bindImageEmbedder(impl: UnavailableImageEmbedder): ImageEmbedder

    @Binds
    abstract fun bindClipIndexRepository(impl: ClipIndexRepositoryImpl): ClipIndexRepository
}
