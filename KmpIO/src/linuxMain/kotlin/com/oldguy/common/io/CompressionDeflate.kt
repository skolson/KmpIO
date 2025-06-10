package com.oldguy.common.io

import kotlinx.cinterop.*
import platform.zlib.*

/**
 * Linux compression/deflate support uses the zlib functions. Logic is modeled after @see https://zlib.net/zpipe.c
 */
@OptIn(ExperimentalForeignApi::class)
actual class CompressionDeflate actual constructor(noWrap: Boolean): Compression {
    actual enum class Strategy {Default, Filtered, Huffman}
    actual override val algorithm: CompressionAlgorithms = CompressionAlgorithms.Deflate
    actual override val bufferSize = 4096
    actual var strategy = Strategy.Default

    private val chunk = 16384

    /**
     * Call this with one or more blocks of data to compress any amount of data using the algorithm specified at
     * constructor time.
     * @param input Uncompressed data. Will compress starting at the current position (typically 0) until limit.
     * [compress] function will continue operation until an empty ByteBuffer is returned (positionLimit(0,0))
     * @return buffer large enough to contain all Compressed data from input.  Buffer size and remaining value are
     * both equal to the uncompressed byte count. Position of the buffer on return is zero, no rewind required. Size
     * will always be <= input remaining
     */
    actual override suspend fun compress(input: suspend () -> ByteBuffer,
                                         output: suspend (buffer: ByteBuffer) -> Unit
    ): ULong {
        return compressCore(
            {
                input().getBytes()
            },
            { compressedBytes ->
                output(ByteBuffer(compressedBytes))
            }
        )
    }

    /**
     * Call this with one or more blocks of data to compress any amount of data using the algorithm specified at
     * constructor time.
     * @param input Invoked for each block of Uncompressed data. Entire ByteArray will be processed.
     * Pass an empty ByteArray to signal end of data.
     * @param output Invoked once for each chunk of compressed data produced,
     * @return Count of total compressed bytes.
     */
    actual override suspend fun compressArray(input: suspend () -> ByteArray,
                                              output: suspend (buffer: ByteArray) -> Unit
    ): ULong {
        return compressCore(input, output)
    }

    private suspend fun compressCore(
        input: suspend () -> ByteArray,
        output: suspend (buffer: ByteArray) -> Unit
    ): ULong {
        var inArray: ByteArray
        val outArray = UByteArray(chunk)
        var outCount = 0uL
        memScoped {
            val zlibStream = alloc<z_stream>().apply {
                zalloc = null
                zfree = null
                opaque = null
            }
            var rc = deflateInit(zlibStream.ptr, Z_DEFAULT_COMPRESSION)
            if (rc != Z_OK)
                throw IOException("zlib deflateInit failed, rc = $rc")
            var flush: Int
            var have: UInt
            do {
                inArray = input()
                val bytes = inArray.size.toUInt()
                flush = if (bytes > 0u) Z_NO_FLUSH else Z_FINISH
                inArray.toUByteArray().usePinned { inPtr ->
                    outArray.usePinned { outPtr ->
                        zlibStream.next_in = inPtr.addressOf(0)
                        do {
                            zlibStream.apply {
                                avail_out = outArray.size.toUInt()
                                next_out = outPtr.addressOf(0)
                            }
                            rc = deflate(zlibStream.ptr, flush)
                            if (rc == Z_STREAM_ERROR)
                                throw IOException("zlib deflate failed, rc = $rc")
                            have = outArray.size.toUInt() - zlibStream.avail_out
                            output(copy(outArray, have))
                            outCount += have
                        } while (zlibStream.avail_in == 0u)
                    }
                }
            } while (flush != Z_FINISH)
            deflateEnd(zlibStream.ptr)
        }
        return outCount
    }

    /**
     * Perform a decompress operation of any amount of data using the algorithm specified at
     * constructor time.
     * If the selected algorithm fails during the operation, an Exception is thrown. There is no
     * attempt at dynamically determining the algorithm used to originally do the compression.
     * @param input will be invoked once for each time the process needs more compressed data.
     * Total size (sum of remainings) of all ByteBuffers provided must contain entire compressed payload.
     * @param output will be called repeatedly as decompressed bytes are produced. Buffer argument will
     * have position zero and limit set to however many bytes were uncompressed. This buffer has a
     * capacity equal to the first input ByteBuffer, but the number of bytes it contains will be 0 < limit
     * <= capacity, as any one compress can produce any non-zero number of bytes.
     * Implementation should consume Buffer content (between position 0 and remaining) as for large
     * payloads with high compression ratios, this function may be called MANY times.
     * @return sum of all uncompressed bytes count passed via [output] function calls.
     */
    actual override suspend fun decompress(
        input: suspend () -> ByteBuffer,
        output: suspend (buffer: ByteBuffer) -> Unit
    ): ULong {
        return decompressCore(
            {
                input().getBytes()
            },
            { compressedBytes ->
                output(ByteBuffer(compressedBytes))
            }
        )
    }

    /**
     * Call this with one or more blocks of data to de-compress any amount of data using the algorithm specified at
     * constructor time.
     * @param input will be invoked once for each time the process needs more compressed data.
     * Total size (sum of remainings) of all ByteBuffers provided must contain entire compressed payload. Indicate
     * end of compressed data with an empty buffer (hasRemaining == false)
     * @param output will be called repeatedly as decompressed bytes are produced. Buffer argument will
     * have position zero and limit set to however many bytes were uncompressed. This buffer has a
     * capacity equal to the first input ByteBuffer, but the number of bytes it contains will be 0 < limit
     * <= capacity, as any one compress can produce any non-zero number of bytes.
     * Implementation should consume Buffer content (between position 0 and remaining) as for large
     * payloads with high compression ratios, this function may be called MANY times.
     * @return sum of all uncompressed bytes count passed via [output] function calls.
     */
    actual override suspend fun decompressArray(
        input: suspend () -> ByteArray,
        output: suspend (buffer: ByteArray) -> Unit
    ): ULong {
        return decompressCore(input, output)
    }

    private suspend fun decompressCore(
        input: suspend () -> ByteArray,
        output: suspend (buffer: ByteArray) -> Unit
    ): ULong {
        var inArray: UByteArray
        val outArray = UByteArray(chunk)
        var outCount = 0uL
        memScoped {
            val zlibStream = alloc<z_stream>().apply {
                zalloc = null
                zfree = null
                opaque = null
                avail_in = 0u
                next_in = null
            }
            try {
                var rc = inflateInit(zlibStream.ptr)
                if (rc != Z_OK)
                    throw IOException("zlib inflateInit failed, rc = $rc")
                var have: UInt
                do {
                    inArray = input().toUByteArray()
                    val bytes = inArray.size
                    inArray.usePinned { inPtr ->
                        outArray.usePinned { outPtr ->
                            zlibStream.avail_in = bytes.toUInt()
                            zlibStream.next_in = inPtr.addressOf(0)
                            do {
                                zlibStream.apply {
                                    avail_out = outArray.size.toUInt()
                                    next_out = outPtr.addressOf(0)
                                }
                                rc = inflate(zlibStream.ptr, Z_NO_FLUSH)
                                if (rc == Z_STREAM_ERROR)
                                    throw IOException("zlib deflate failed, rc = $rc")
                                when (rc) {
                                    Z_NEED_DICT, Z_DATA_ERROR, Z_MEM_ERROR -> rc = Z_DATA_ERROR

                                }
                                have = outArray.size.toUInt() - zlibStream.avail_out
                                output(copy(outArray, have))
                                outCount += have
                            } while (zlibStream.avail_out == 0u)
                        }
                    }
                } while (rc != Z_STREAM_END)
            } finally {
                deflateEnd(zlibStream.ptr)
            }
        }
        return outCount
    }

    /**
     * performs the same functionality as a copyOfRange(0, length), except also changes type from UByteArray to ByteArray
     * @param input array of unsigned bytes to be copied
     * @param length number of byes to be copied, 0 < length < input.size
     * @return
     */
    private fun copy(input: UByteArray, length: UInt): ByteArray
    {
        val out = ByteArray(length.toInt())
        for (i in 0..<length.toInt()) out[i] = input[i].toByte()
        return out
    }
}