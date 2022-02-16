package com.oldguy.common.io

import kotlin.math.min

/**
 * Does straight copy and count, input is copied to output essentially a NOOP implementation of
 * the compression interface.
 */
class CompressionNone: Compression {
    override val algorithm = CompressionAlgorithms.None

    override suspend fun compress(
        input: suspend () -> ByteBuffer,
        output: suspend (buffer: ByteBuffer) -> Unit
    ): ULong {
        var count = 0UL
        while (true) {
            val buf = input()
            if (!buf.hasRemaining) break
            count += buf.remaining.toUInt()
            output(buf)
        }
        return count
    }

    override suspend fun compressArray(
        input: suspend () -> ByteArray,
        output: suspend (buffer: ByteArray) -> Unit
    ): ULong {
        var count = 0UL
        while (true) {
            val buf = input()
            if (buf.isEmpty()) break
            count += buf.size.toUInt()
            output(buf)
        }
        return count
    }

    override suspend fun decompress(
        totalCompressedBytes: ULong,
        bufferSize: UInt,
        input: suspend (bytesToRead: Int) -> ByteBuffer,
        output: suspend (buffer: ByteBuffer) -> Unit
    ): ULong {
        var count = 0UL
        while (true) {
            val buf = input(min(totalCompressedBytes - count, bufferSize.toULong()).toInt())
            if (!buf.hasRemaining) break
            count += buf.remaining.toUInt()
            output(buf)
        }
        return count
    }

    override suspend fun decompressArray(
        totalCompressedBytes: ULong,
        bufferSize: UInt,
        input: suspend (bytesToRead: Int) -> ByteArray,
        output: suspend (buffer: ByteArray) -> Unit
    ): ULong {
        var count = 0UL
        while (true) {
            val buf = input(min(totalCompressedBytes - count, bufferSize.toULong()).toInt())
            if (buf.isEmpty()) break
            count += buf.size.toUInt()
            output(buf)
        }
        return count
    }

    override fun reset() {
    }
}