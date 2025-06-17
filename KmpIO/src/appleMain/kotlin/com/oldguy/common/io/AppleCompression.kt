package com.oldguy.common.io

import kotlinx.cinterop.*
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
import platform.zlib.deflateInit
import platform.zlib.deflateInit2
import platform.zlib.inflate
import platform.zlib.inflateEnd
import platform.zlib.inflateInit
import platform.zlib.inflateInit2
import platform.zlib.z_stream

/**
 * Apple zlib compression library used to implement compress/decompress operations.
 * This is a common implementation usable by all Apple target platforms.
 *
 * Note this class is essentially identical to the one in linuxMain, just built with Apple cinterop
 * bindings
 */
@OptIn(ExperimentalForeignApi::class)
class AppleCompression(override val algorithm: CompressionAlgorithms)
    :Compression
{
    override val bufferSize = 4096
    val chunk = 16384

    /*
    private val appleConst: compression_algorithm = when (algorithm) {
        CompressionAlgorithms.Deflate -> COMPRESSION_ZLIB
        CompressionAlgorithms.LZMA -> COMPRESSION_LZMA
        else -> throw IllegalArgumentException("Unsupported Apple compression $algorithm")
    }
     */

    override suspend fun compress(input: suspend () -> ByteBuffer,
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

    override suspend fun compressArray(input: suspend () -> ByteArray,
                              output: suspend (buffer: ByteArray) -> Unit
    ): ULong {
        return transform(false, input, output)
    }

    override suspend fun decompress(
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


    override suspend fun decompressArray(
        input: suspend () -> ByteArray,
        output: suspend (buffer: ByteArray) -> Unit
    ): ULong {
        return transform(true, input, output)
    }

    /**
     * Initializes an Apple compression stream, then consumes input buffers and produces output buffers. After the
     * transform (either encode/compress or decode/decompress) is complete, the stream is closed
     *
     * This code replaced by zlib support to allow code bases between linux and apple to match
    private suspend fun transformApple(
        encode: Boolean,
        input: suspend () -> ByteBuffer,
        output: suspend (buffer: ByteBuffer) -> Unit
    ): ULong {
        var outCount = 0UL
        memScoped {
            val cmp: CValuesRef<compression_stream> = alloc<compression_stream>().ptr
            val code = if (encode) COMPRESSION_STREAM_ENCODE else COMPRESSION_STREAM_DECODE
            val status = compression_stream_init(
                cmp,
                code,
                appleConst
            )
            try {
                var count = 0
                if (status == COMPRESSION_STATUS_OK) {
                    val inPage = UByteArray(bufferSize)
                    var inCount = 0UL
                    inPage.usePinned { pinIn ->
                        val outPage = UByteArray(bufferSize)
                        outPage.usePinned { pinOut ->
                            val pinOutSize = pinOut.get().size.toULong()
                            var inBuf = input()
                            if (inBuf.remaining == 0) return 0UL
                            var flags = 0
                            cmp.getPointer(this).pointed.apply {
                                var sourceLength = min(inBuf.remaining, bufferSize)
                                inCount += sourceLength.toULong()
                                inBuf.getBytes(sourceLength).toUByteArray().copyInto(pinIn.get())
                                dst_ptr = pinOut.addressOf(0)
                                dst_size = pinOut.get().size.toULong()
                                src_ptr = pinIn.addressOf(0)
                                src_size = sourceLength.toULong()

                                while (true) {
                                    val result = compression_stream_process(cmp, flags)
                                    count++
                                    //println("# $count - inBuf.remaining: ${inBuf.remaining}, in: $inCount, out: $outCount, src: $src_size, dst: $dst_size, result: $result, flag: $flags")
                                    when (result) {
                                        COMPRESSION_STATUS_OK -> {
                                            if (src_size == 0UL) {
                                                if (inBuf.remaining == 0) {
                                                    inBuf = input()
                                                }
                                                if (inBuf.remaining == 0) {
                                                    if (encode)
                                                        flags = COMPRESSION_STREAM_FINALIZE.toInt()
                                                } else {
                                                    sourceLength = min(inBuf.remaining, bufferSize)
                                                    inBuf.getBytes(sourceLength).toUByteArray().copyInto(pinIn.get())
                                                    src_ptr = pinIn.addressOf(0)
                                                    src_size = sourceLength.toULong()
                                                    inCount += sourceLength.toULong()
                                                }
                                            }
                                            if (dst_size == 0UL) {
                                                output(ByteBuffer(pinOut.get().toByteArray()))
                                                outCount += pinOutSize
                                                dst_ptr = pinOut.addressOf(0)
                                                dst_size = pinOutSize
                                            }
                                        }

                                        COMPRESSION_STATUS_END -> {
                                            outCount += sink(pinOut.get(), dst_size, output)
                                            //println("Compression end. dst_size: $dst_size, wrote: ${pinOutSize - dst_size}, total out: $outCount")
                                            break
                                        }

                                        COMPRESSION_STATUS_ERROR -> {
                                            throw IllegalStateException("Compression error. Result $result")
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else
                    throw IllegalArgumentException("Compression init failed")
            } catch (e: IllegalStateException) {
                throw e
            } finally {
                compression_stream_destroy(cmp)
            }
        }
        return outCount
    }
     */

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
        var inArray: UByteArray
        val outArray = UByteArray(chunk)
        var outCount = 0uL
        val str = if (inflate) "inflate" else "deflate"
        memScoped {
            alloc<z_stream>().apply {
                zalloc = null
                zfree = null
                opaque = null
                var rc = if (inflate)
                    inflateInit2(ptr, -MAX_WBITS)
                else
                    deflateInit2(
                        ptr,
                        Z_DEFAULT_COMPRESSION,
                        Z_DEFLATED,
                        -MAX_WBITS,
                        8,
                        Z_DEFAULT_STRATEGY
                    )
                if (rc != Z_OK)
                    throw IOException("zlib $str failed, rc = $rc")
                val outArrayPtr = outArray.pin()
                var inArrayPtr: Pinned<UByteArray>? = null
                try {
                    inArray = input().toUByteArray()
                    val bytes = inArray.size.toUInt()
                    var flush = if (bytes > 0u) Z_NO_FLUSH else Z_FINISH
                    inArrayPtr = inArray.pin()
                    next_in = inArrayPtr.addressOf(0)
                    avail_in = inArray.size.toUInt()
                    avail_out = outArray.size.toUInt()
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

    companion object {
        const val MAX_WBITS = 15
    }
}