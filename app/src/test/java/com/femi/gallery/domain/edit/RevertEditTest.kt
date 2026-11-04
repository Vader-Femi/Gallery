package com.femi.gallery.domain.edit

import android.graphics.Bitmap
import com.femi.gallery.domain.repository.EditRepository
import com.femi.gallery.domain.usecase.CanRevertOriginalUseCase
import com.femi.gallery.domain.usecase.RevertOriginalUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RevertEditTest {

    private class FakeEditRepository : EditRepository {
        var canRevertVal = false
        var revertedVal = false

        override suspend fun loadPreview(uri: String, maxSize: Int): Bitmap? = null
        override suspend fun applyGeometry(bitmap: Bitmap, state: EditState, includeCrop: Boolean): Bitmap = bitmap
        override suspend fun applyEffects(bitmap: Bitmap, state: EditState): Bitmap = bitmap
        override suspend fun saveCopy(uri: String, state: EditState): Boolean = true
        override suspend fun replaceOriginal(uri: String, state: EditState): Boolean = true

        override suspend fun canRevert(uri: String): Boolean = canRevertVal

        override suspend fun revertOriginal(uri: String): Boolean {
            if (canRevertVal) {
                revertedVal = true
                canRevertVal = false
                return true
            }
            return false
        }
    }

    @Test
    fun canRevertChecksRepository() {
        val repo = FakeEditRepository()
        val useCase = CanRevertOriginalUseCase(repo)

        repo.canRevertVal = false
        runBlocking {
            assertFalse(useCase("content://media/external/images/media/42"))
        }

        repo.canRevertVal = true
        runBlocking {
            assertTrue(useCase("content://media/external/images/media/42"))
        }
    }

    @Test
    fun revertOriginalCallsRepositoryAndUpdatesState() {
        val repo = FakeEditRepository()
        val canRevert = CanRevertOriginalUseCase(repo)
        val revert = RevertOriginalUseCase(repo)

        repo.canRevertVal = true
        runBlocking {
            assertTrue(canRevert("content://media/external/images/media/42"))
            val result = revert("content://media/external/images/media/42")
            assertTrue(result)
            assertTrue(repo.revertedVal)
            assertFalse(canRevert("content://media/external/images/media/42"))
        }
    }
}
