package com.oldguy.common.io

import platform.Foundation.*


/**
 * Apple support compression library used to implement compress/decompress operations. This is a common implementation
 * usable by all Apple target platforms
 */
class AppleCompression(override val algorithm: CompressionAlgorithms)
    :Compression
{
    var pageSize = 4096

    private val appleConst: Int = when (algorithm) {
        CompressionAlgorithms.None -> 0
        CompressionAlgorithms.Deflate -> 0x205
        CompressionAlgorithms.BZip2 -> 0x100
        CompressionAlgorithms.LZMA -> 0x306
    }

    override suspend fun compress(input: suspend () -> ByteBuffer,
                         output: suspend (buffer: ByteBuffer) -> Unit
    ): ULong {
        var count = 0UL

        return count
    }

    override suspend fun compressArray(input: suspend () -> ByteArray,
                              output: suspend (buffer: ByteArray) -> Unit
    ): ULong {
    }

    override suspend fun decompress(
        totalCompressedBytes: ULong,
        bufferSize: UInt,
        input: suspend (bytesToRead: Int) -> ByteBuffer,
        output: suspend (buffer: ByteBuffer) -> Unit
    ): ULong {

    }


    override suspend fun decompressArray(
        totalCompressedBytes: ULong,
        bufferSize: UInt,
        input: suspend (bytesToRead: Int) -> ByteArray,
        output: suspend (buffer: ByteArray) -> Unit
    ): ULong {

    }

    override fun reset() {

    }
}