package com.oldguy.common.io

import kotlin.math.min

/**
 * Stream-like read operations across any number of buffers, for any source of individual buffers of
 * any size.
 *
 * Sequentially reads any number of incoming Buffers as if they were a conventional stream. Provides full control
 * over bytes read count. Can have any number of incoming buffers of any size.
 * @param nextBuffer called each time a new chunk of content is required, at least once. End of content indicated by
 * returning a ByteBuffer with hasRemaining = false (remaining == 0).
 * any Buffer passed in is read starting at current [position] for remaining bytes.
 */
class BufferReader(
    val nextBuffer: suspend () -> ByteBuffer
) {
    /**
     * Count of total bytes read at any given time
     */
    var position = 0UL
        private set
    var isDrained = false

    private lateinit var current: ByteBuffer

    suspend fun readArray(count: Int): ByteArray {
        return read(count)
    }

    suspend fun read(destination: ByteBuffer, count: Int = destination.remaining): Int {
        val buf = read(count)
        destination.putBytes(buf)
        return buf.size
    }

    suspend fun read(destination: ByteArray, start: Int, length: Int): Int {
        val buf = read(length)
        buf.copyInto(destination, start)
        return buf.size
    }

    private suspend fun read(count: Int): ByteArray {
        if (!this::current.isInitialized)
            current = nextBuffer()
        val buf = ByteArray(count)
        var remaining = count
        var offset = 0
        while (remaining > 0) {
            val readCount = min(current.remaining, remaining)
            if (readCount == 0) {
                isDrained = true
                return if (offset == 0) ByteArray(0) else buf.sliceArray(0 until offset)
            }
            current.getBytes(readCount).copyInto(buf, offset)
            position += readCount.toUInt()
            offset += readCount
            remaining -= readCount
            if (!current.hasRemaining) {
                current = nextBuffer()
            }
        }
        return buf
    }
}