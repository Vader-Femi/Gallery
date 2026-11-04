package com.femi.gallery.domain.locked

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.NonWritableChannelException

class OffsetChannelTest {
    private fun channelOver(bytes: ByteArray, start: Long): Pair<OffsetChannel, File> {
        val file = File.createTempFile("offset", ".bin").also { it.writeBytes(bytes); it.deleteOnExit() }
        return OffsetChannel(FileInputStream(file).channel, start) to file
    }

    @Test
    fun theViewStartsAfterTheHeaderAndHasTheRestOfTheSize() {
        val (channel, _) = channelOver(ByteArray(110) { it.toByte() }, 10)
        assertEquals(100L, channel.size())
        assertEquals(0L, channel.position())
        val buffer = ByteBuffer.allocate(5)
        assertEquals(5, channel.read(buffer))
        assertArrayEquals(byteArrayOf(10, 11, 12, 13, 14), buffer.array())
        assertEquals(5L, channel.position())
        channel.close()
    }

    @Test
    fun positionsCountFromTheStartOfTheView() {
        val (channel, _) = channelOver(ByteArray(110) { it.toByte() }, 10)
        channel.position(50)
        val buffer = ByteBuffer.allocate(2)
        channel.read(buffer)
        assertArrayEquals(byteArrayOf(60, 61), buffer.array())
        channel.position(98)
        val tail = ByteBuffer.allocate(10)
        assertEquals(2, channel.read(tail))
        assertEquals(-1, channel.read(ByteBuffer.allocate(1)))
        channel.close()
    }

    @Test
    fun theViewCannotBeWrittenTo() {
        val (channel, _) = channelOver(ByteArray(20), 4)
        try {
            channel.write(ByteBuffer.allocate(1))
            fail("Expected NonWritableChannelException")
        } catch (e: NonWritableChannelException) {
            assertTrue(true)
        }
        channel.close()
    }
}
