package com.oldguy.common.io

import kotlinx.cinterop.*
import platform.zlib.*

/**
 * Linux compression/deflate support uses the zlib functions.
 *
 * Note this class is essentially identical to the same class in appleMain, as both linux and Apple provide zlib.
 */
@OptIn(ExperimentalForeignApi::class)
actual class CompressionDeflate actual constructor(noWrap: Boolean): Compression {
    actual enum class Strategy {Default, Filtered, Huffman}
    actual override val algorithm: CompressionAlgorithms = CompressionAlgorithms.Deflate
    actual var strategy = Strategy.Default
    actual override var zlibHeader = false

    actual override val bufferSize = 4096
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
     * Processes data using the specified compression or decompression algorithm.
     *
     * During inflate, first two bytes of input are checked for zlib headers. If zlib headers are detected,
     * zlib inflate will process using them.
     * During deflate, if zlibHeader is true, zlib will create header bytes in first output buffer. Default is false,
     * as most standard zip files do not use this header.
     *
     * Note that usePinned is not used here as the input buffer changes every input call. usePinned could be used
     * for the outArray since the same one is reused over and over.  But pin/unpi for both seemed more readable.
     *
     * @param inflate If true, the operation will perform decompression; if false, it will perform compression.
     * @param input A suspendable function that provides the input data as a ByteArray. Pass an empty ByteArray to signal the end of data.
     * @param output A suspendable function that will be called with each chunk of processed data.
     * @return Total number of bytes processed as an unsigned long value.
     * @throws IOException If the underlying compression or decompression operation fails.
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