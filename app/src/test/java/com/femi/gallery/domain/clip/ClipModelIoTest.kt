package com.femi.gallery.domain.clip

import com.femi.gallery.domain.clip.ModelPort.ElementType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun port(name: String, type: ElementType, vararg shape: Long) = ModelPort(name, shape, type)

class ClipModelIoTest {

    @Test
    fun theImageInputIsTheFourDimensionalDecimalOne() {
        val inputs = listOf(port("pixel_values", ElementType.Float, -1, 3, 224, 224))
        assertEquals("pixel_values", ClipModelIo.imageInput(inputs)?.name)
    }

    @Test
    fun anImageInputWithAnOpenColourDimensionStillCounts() {
        assertEquals("image", ClipModelIo.imageInput(listOf(port("image", ElementType.Float, 1, -1, 256, 256)))?.name)
    }

    @Test
    fun aModelWithoutAPictureInputHasNone() {
        assertNull(ClipModelIo.imageInput(listOf(port("input_ids", ElementType.Int64, -1, 77))))
        assertNull(ClipModelIo.imageInput(listOf(port("x", ElementType.Float, -1, 3, 224)))) // three dimensions
    }

    @Test
    fun theImageSizeIsReadFromTheModelOrDefaultsTo224() {
        assertEquals(256, ClipModelIo.imageSize(port("p", ElementType.Float, -1, 3, 256, 256)))
        assertEquals(224, ClipModelIo.imageSize(port("p", ElementType.Float, -1, 3, -1, -1)))
        assertEquals(224, ClipModelIo.imageSize(port("p", ElementType.Float, -1, 3, 224, 160))) // not square: use the standard
    }

    @Test
    fun theTextInputsAreFoundByNameAndType() {
        val inputs = listOf(port("attention_mask", ElementType.Int64, -1, 77), port("input_ids", ElementType.Int64, -1, 77))
        assertEquals("input_ids", ClipModelIo.textIds(inputs)?.name)
        assertEquals("attention_mask", ClipModelIo.textMask(inputs)?.name)
    }

    @Test
    fun aTextModelWithOnlyOneWholeNumberInputUsesIt() {
        val inputs = listOf(port("text", ElementType.Int32, 1, 77))
        assertEquals("text", ClipModelIo.textIds(inputs)?.name)
        assertNull(ClipModelIo.textMask(inputs))
    }

    @Test
    fun theContextLengthIsReadOrDefaultsTo77() {
        assertEquals(64, ClipModelIo.contextLength(port("ids", ElementType.Int64, -1, 64)))
        assertEquals(77, ClipModelIo.contextLength(port("ids", ElementType.Int64, -1, -1)))
    }

    @Test
    fun theEmbeddingOutputIsTheOneCalledEmbeds() {
        val outputs = listOf(
            port("last_hidden_state", ElementType.Float, -1, 50, 768),
            port("pooler_output", ElementType.Float, -1, 768),
            port("image_embeds", ElementType.Float, -1, 512),
        )
        assertEquals("image_embeds", ClipModelIo.embeddingOutput(outputs)?.name)
    }

    @Test
    fun withoutANameTheFirstVectorOutputIsUsed() {
        val outputs = listOf(port("features", ElementType.Float, -1, 50, 768), port("output", ElementType.Float, 1, 512))
        assertEquals("output", ClipModelIo.embeddingOutput(outputs)?.name)
    }

    @Test
    fun aModelThatOnlyGivesFeaturesOfEveryPartHasNoEmbedding() {
        assertNull(ClipModelIo.embeddingOutput(listOf(port("last_hidden_state", ElementType.Float, -1, 77, 512))))
    }
}

class ClipModelStatusTest {

    @Test
    fun theStatusFollowsWhichFilesAreThere() {
        assertEquals(ClipModelStatus.NotInstalled, ClipModelStatus.of(false, false))
        assertEquals(ClipModelStatus.ImageOnly, ClipModelStatus.of(true, false))
        assertEquals(ClipModelStatus.TextOnly, ClipModelStatus.of(false, true))
        assertEquals(ClipModelStatus.Installed, ClipModelStatus.of(true, true))
    }

    @Test
    fun onlyBothFilesMakeItComplete() {
        assertTrue(ClipModelStatus.Installed.isComplete)
        assertFalse(ClipModelStatus.ImageOnly.isComplete)
    }

    @Test
    fun theSignatureChangesWithEitherFile() {
        val base = ClipModelSignature.of(100, 5, 200, 6)
        assertEquals(base, ClipModelSignature.of(100, 5, 200, 6))
        assertNotEquals(base, ClipModelSignature.of(101, 5, 200, 6))
        assertNotEquals(base, ClipModelSignature.of(100, 5, 200, 7))
        assertNotEquals(base, ClipModelSignature.of(100, 9, 200, 6))
    }

    @Test
    fun theSignatureIsNeverTheNoModelValue() {
        assertNotEquals(0L, ClipModelSignature.of(0, 0, 0, 0))
    }
}
