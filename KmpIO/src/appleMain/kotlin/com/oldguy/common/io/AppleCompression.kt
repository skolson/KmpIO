package com.oldguy.common.io

import kotlinx.cinterop.*
import platform.darwin.*
import kotlin.math.min

/**
 * Apple support compression library used to implement compress/decompress operations. This is a common implementation
 * usable by all Apple target platforms
 */
class AppleCompression(override val algorithm: CompressionAlgorithms)
    :Compression
{
    override val bufferSize = 4096

    private val appleConst: compression_algorithm = when (algorithm) {
        CompressionAlgorithms.Deflate -> COMPRESSION_ZLIB
        CompressionAlgorithms.LZMA -> COMPRESSION_LZMA
        else -> throw IllegalArgumentException("Unsupported Apple compression $algorithm")
    }

    override suspend fun compress(input: suspend () -> ByteBuffer,
                         output: suspend (buffer: ByteBuffer) -> Unit
    ): ULong {
        return transform(true, input, output)
    }

    override suspend fun compressArray(input: suspend () -> ByteArray,
                              output: suspend (buffer: ByteArray) -> Unit
    ): ULong {
        return transform(true,
            input = { ByteBuffer(input()) },
            output = { output( it.getBytes()) }
        )
    }

    override suspend fun decompress(
        input: suspend () -> ByteBuffer,
        output: suspend (buffer: ByteBuffer) -> Unit
    ): ULong {
        return transform(false, input, output)
    }


    override suspend fun decompressArray(
        input: suspend () -> ByteArray,
        output: suspend (buffer: ByteArray) -> Unit
    ): ULong {
        return transform(false,
            input = { ByteBuffer(input()) },
            output = { output( it.getBytes()) }
        )
    }

    /**
     * Initializes an Apple compression stream, then consumes input buffers and produces output buffers. After the
     * transform (either encode/compress or decode/decompress) is complete, the stream is closed
     */
    @OptIn(ExperimentalForeignApi::class)
    private suspend fun transform(
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
                    throw IllegalStateException("Compression init failed")
            } catch (e: IllegalStateException) {
                throw e
            } finally {
                compression_stream_destroy(cmp)
            }
        }
        return outCount
    }
}

private suspend fun sink(outPage: UByteArray, dstSize: ULong, output: suspend (buffer: ByteBuffer) -> Unit): UInt {
    val length = outPage.size.toUInt() - dstSize
    if (length > 0u) {
        ByteBuffer(length.toInt()).apply {
            putBytes(outPage.toByteArray(), length = length.toInt())
            flip()
            output(this)
        }
    }
    return length.toUInt()
}