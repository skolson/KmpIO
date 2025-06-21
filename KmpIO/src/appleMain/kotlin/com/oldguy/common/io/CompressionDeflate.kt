package com.oldguy.common.io

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.Pinned
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pin
import kotlinx.cinterop.ptr
import platform.zlib.Z_DEFAULT_COMPRESSION
import platform.zlib.Z_DEFAULT_STRATEGY
import platform.zlib.Z_DEFLATED
import platform.zlib.Z_FINISH
import platform.zlib.Z_NO_FLUSH
import platform.zlib.Z_OK
import platform.zlib.Z_STREAM_END
import platform.zlib.deflate
import platform.zlib.deflateEnd
import platform.zlib.deflateInit2
import platform.zlib.inflate
import platform.zlib.inflateEnd
import platform.zlib.inflateInit2
import platform.zlib.z_stream

@OptIn(ExperimentalForeignApi::class)
actual class CompressionDeflate actual constructor(noWrap: Boolean): Compression {
    actual enum class Strategy {Default, Filtered, Huffman}
    actual override val algorithm: CompressionAlgorithms = CompressionAlgorithms.Deflate
    actual override val bufferSize = 4096
    private val chunk = 16384
    actual var strategy = Strategy.Default

    actual override var zlibHeader: Boolean = false

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
        return transform(
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
        return transform(false, input, output)
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
        return transform(
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
        return transform(true, input, output)
    }

    /**
     * Performs a transformation on the input data using zlib compression or decompression.
     *
     * Note this function is identical to the one in linuxMain and should stay identical as both Apple and Linux
     * support zlib.
     *
     * @param inflate A boolean flag indicating whether to inflate (decompress) the input data or deflate (compress) it.
     * @param input A suspending function that provides input data in the form of a ByteArray when invoked.
     * @param output A suspending function that processes the transformed output data in the form of a ByteArray.
     * @return The total number of bytes output as an unsigned long integer.
     * @throws IOException If the zlib transformation fails.
     */
    private suspend fun transform(
        inflate: Boolean,
        input: suspend () -> ByteArray,
        output: suspend (buffer: ByteArray) -> Unit
    ): ULong {
        var inArray = input().toUByteArray()
        if (inArray.isEmpty()) return 0uL
        val windowBits = if ((inflate && detectZlibHeaders(inArray)) || (!inflate && zlibHeader))
            Compression.MAX_WBITS
        else
            - Compression.MAX_WBITS
        val outArray = UByteArray(chunk)
        val outArraySize = outArray.size.toUInt()
        var outCount = 0uL
        val str = if (inflate) "inflate" else "deflate"
        memScoped {
            alloc<z_stream>().apply {
                zalloc = null
                zfree = null
                opaque = null
                var rc = if (inflate)
                    inflateInit2(ptr, windowBits)
                else
                    deflateInit2(
                        ptr,
                        Z_DEFAULT_COMPRESSION,
                        Z_DEFLATED,
                        windowBits,
                        8,
                        Z_DEFAULT_STRATEGY
                    )
                if (rc != Z_OK)
                    throw IOException("zlib $str failed, rc = $rc")
                val outArrayPtr = outArray.pin()
                var inArrayPtr: Pinned<UByteArray>? = null
                try {
                    val bytes = inArray.size.toUInt()
                    var flush = if (bytes > 0u) Z_NO_FLUSH else Z_FINISH
                    inArrayPtr = inArray.pin()
                    next_in = inArrayPtr.addressOf(0)
                    avail_in = inArray.size.toUInt()
                    avail_out = outArraySize
                    next_out = outArrayPtr.addressOf(0)
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
                            inArrayPtr!!.unpin()
                            inArray = input().toUByteArray()
                            inArrayPtr = inArray.pin()
                            avail_in = inArray.size.toUInt()
                            if (avail_in == 0u)
                                flush = Z_FINISH
                            else
                                next_in = inArrayPtr.addressOf(0)
                        }
                        if (avail_out == 0u) {
                            output(outArray.toByteArray())
                            next_out = outArrayPtr.addressOf(0)
                            avail_out = outArraySize
                        }
                    } while (rc == Z_OK)
                    if (avail_out > 0u) {
                        output(outArray.copyOfRange(0, outArray.size - avail_out.toInt()).toByteArray())
                    }
                } finally {
                    inArrayPtr?.unpin()
                    outArrayPtr.unpin()
                    outCount = total_out
                    if (inflate)
                        inflateEnd(ptr)
                    else
                        deflateEnd(ptr)
                }
            }
        }
        return outCount
    }
}