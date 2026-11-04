package com.femi.gallery.domain.locked

import java.nio.ByteBuffer
import java.nio.channels.NonWritableChannelException
import java.nio.channels.SeekableByteChannel

/** A read-only view of [delegate] that starts [start] bytes in: the file minus its header. */
class OffsetChannel(private val delegate: SeekableByteChannel, private val start: Long) : SeekableByteChannel {
    init {
        delegate.position(start)
    }

    override fun read(dst: ByteBuffer): Int = delegate.read(dst)

    override fun write(src: ByteBuffer): Int = throw NonWritableChannelException()

    override fun position(): Long = delegate.position() - start

    override fun position(newPosition: Long): SeekableByteChannel {
        require(newPosition >= 0) { "Negative position" }
        delegate.position(start + newPosition)
        return this
    }

    override fun size(): Long = (delegate.size() - start).coerceAtLeast(0L)

    override fun truncate(size: Long): SeekableByteChannel = throw NonWritableChannelException()

    override fun isOpen(): Boolean = delegate.isOpen

    override fun close() = delegate.close()
}
