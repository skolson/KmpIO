package com.oldguy.common.io

import kotlinx.cinterop.*
import platform.linux.ether_hostton
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
        return process(
            false,
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
        return process(false, input, output)
    }

    private suspend fun process(
        inflate: Boolean,
        input: suspend () -> ByteArray,
        output: suspend (buffer: ByteArray) -> Unit
    ): ULong {
        var inArray: ByteArray
        val outArray = UByteArray(chunk)
        var outCount = 0uL
        val str = if (inflate) "inflate" else "deflate"
        memScoped {
            alloc<z_stream>().apply {
                zalloc = null
                zfree = null
                opaque = null
                var rc = if (inflate)
                    inflateInit(ptr)
                else
                    deflateInit(ptr, Z_DEFAULT_COMPRESSION)
                if (rc != Z_OK)
                    throw IOException("zlib $str failed, rc = $rc")
                try {
                    inArray = input()
                    val bytes = inArray.size.toUInt()
                    var flush = if (bytes > 0u) Z_NO_FLUSH else Z_FINISH
                    inArray.toUByteArray().usePinned { inPtr ->
                        outArray.usePinned { outPtr ->
                            next_in = inPtr.addressOf(0)
                            avail_in = inArray.size.toUInt()
                            avail_out = outArray.size.toUInt()
                            next_out = outPtr.addressOf(0)
                            var loopCount = 0
                            do {
                                loopCount++
                                rc = if (inflate)
                                    inflate(ptr, flush)
                                else
                                    deflate(ptr, flush)
                                if (rc != Z_OK && rc != Z_STREAM_END)
                                    throw IOException("zlib $str failed, rc = $rc")

                                if (avail_in == 0u) {
                                    inArray = input()
                                    avail_in = inArray.size.toUInt()
                                    next_in = inPtr.addressOf(0)
                                    if (avail_in == 0u) flush = Z_FINISH
                                }
                                if (avail_out == 0u) {
                                    output(outArray.toByteArray())
                                    next_out = outPtr.addressOf(0)
                                }
                            } while (rc == Z_OK)
                            if (avail_out > 0u) {
                                output(copy(outArray, (outArray.size.toUInt() - avail_out)))
                            }
                        }
                    }
                } finally {
                    outCount = total_out
                    deflateEnd(ptr)
                }
            }
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
        return process(
            true,
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
        return process(true, input, output)
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