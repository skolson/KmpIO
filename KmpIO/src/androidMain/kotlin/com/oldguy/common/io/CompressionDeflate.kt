package com.oldguy.common.io

import java.util.zip.Deflater
import java.util.zip.Inflater
import kotlin.math.min

/**
 * Uses Android Inflater/Deflater classes for implementation of the Deflate algorithm.
 * This class maintains state on the current compression op. NEVER use the same instance for multiple
 * different simultaneous operations. Instance state is not thread-safe.
 */
actual class CompressionDeflate: Compression {
    actual enum class Strategy {Default, Filtered, Huffman}
    actual override val algorithm: CompressionAlgorithms = CompressionAlgorithms.Deflate

    private val inflater = Inflater(true)
    private var deflater = Deflater()

    private fun setDeflater(strategy: Strategy) {
        deflater = Deflater(when (strategy) {
            Strategy.Default -> Deflater.DEFAULT_STRATEGY
            Strategy.Filtered -> Deflater.FILTERED
            Strategy.Huffman -> Deflater.HUFFMAN_ONLY
        })
    }

    actual suspend fun compress(strategy: Strategy,
                        input: suspend () -> ByteBuffer,
                        output: suspend (buffer: ByteBuffer) -> Unit
    ): ULong {
        setDeflater(strategy)
        return compress(input, output)
    }


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
        var count = 0UL
        val bufferSize = 4096
        deflater.apply {
            reset()
            var inProgress = true
            var out = ByteArray(bufferSize)
            while (inProgress) {
                input().also {
                    if (it.capacity > out.size) out = ByteArray(it.capacity + 100)
                    val opCount = if (!it.hasRemaining) {
                        inProgress = false
                        deflate(out).also { finish() }
                    } else {
                        deflate(out)
                    }
                    output(ByteBuffer(out))
                    count += opCount.toUInt()
                }
            }
            end()
        }
        return count
    }

    actual suspend fun compressArray(strategy: Strategy,
                                     input: suspend () -> ByteArray,
                                     output: suspend (buffer: ByteArray) -> Unit
    ): ULong {
        setDeflater(strategy)
        return compressArray(input, output)
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
        var count = 0UL
        val bufferSize = 4096
        deflater.apply {
            reset()
            var inProgress = true
            var out = ByteArray(bufferSize)
            while (inProgress) {
                input().also {
                    if (it.size > out.size) out = ByteArray(it.size + 100)
                    val opCount = if (it.isEmpty()) {
                        inProgress = false
                        deflate(out).also { finish() }
                    } else {
                        deflate(out)
                    }
                    output(out.sliceArray(0 until opCount))
                    count += opCount.toUInt()
                }
            }
            end()
        }
        return count
    }

    /**
     * Perform a decompress operation of any amount of data using the algorithm specified at
     * constructor time.
     * If the selected algorithm fails during the operation, an Exception is thrown. There is no
     * attempt at dynamically determining the algorithm used to originally do the compression.
     * @param totalCompressedBytes Compressed data byte count. This is the number of input bytes to
     * process.  Function will continue until this number of bytes are provided via the [input] function.
     * @param bufferSize specifies max amount of bytes that will be passed in the bytesToRead argument
     * @param input will be invoked once for each time the process needs more compressed data.
     * Total size (sum of remainings) of all ByteBuffers provided must equal [totalCompressedBytes].
     * If total number of bytes passed to all input calls exceeds [totalCompressedBytes] an
     * exception is thrown.
     * @param output will be called repeatedly as decompressed bytes are produced. Buffer argument will
     * have position zero and limit set to however many bytes were uncompressed. This buffer has a
     * capacity equal to the first input ByteBuffer, but the number of bytes it contains will be 0 < limit
     * <= capacity, as any one compress can produce any non-zero number of bytes.
     * Implementation should consume Buffer content (between position 0 and remaining) as for large
     * payloads with high compression ratios, this function may be called MANY times.
     * @return sum of all uncompressed bytes count passed via [output] function calls.
     */
    actual override suspend fun decompress(
        totalCompressedBytes: ULong,
        bufferSize: UInt,
        input: suspend (bytesToRead: Int) -> ByteBuffer,
        output: suspend (buffer: ByteBuffer) -> Unit
    ): ULong {
        var remaining = totalCompressedBytes
        var outCount = 0UL
        var inBuf = input(min(bufferSize.toULong(), remaining).toInt())
        val outBuf = ByteBuffer(inBuf.capacity)
        while (inBuf.remaining > 0 && remaining > 0U) {
            when (algorithm) {
                CompressionAlgorithms.Deflate -> {
                    remaining -= inBuf.remaining.toULong()
                    if (remaining < 0UL)
                        throw IllegalStateException("totalCompressedBytes expected: $totalCompressedBytes. Excess bytes provided: ${remaining.toLong() *-1L}")
                    inflater.apply {
                        setInput(inBuf.getBytes(inBuf.remaining))
                        var count: Int
                        do {
                            count = inflate(outBuf.contentBytes)
                            if (count > 0) {
                                outBuf.positionLimit(0, count)
                                output(outBuf)
                                outCount += count.toULong()
                            }
                            val need = needsInput()
                        } while (count > 0 || !need)
                        if (remaining > 0u)
                            inBuf = input(min(bufferSize.toULong(), remaining).toInt())
                        else
                            end()
                    }
                }
                else -> throw IllegalArgumentException("Unsupported compression $algorithm")
            }
        }
        return outCount
    }
    /**
     * Call this with one or more blocks of data to de-compress any amount of data using the algorithm specified at
     * constructor time.
     * @param totalCompressedBytes Compressed data byte count. This is the number of input bytes to
     * process.  Function will continue until this number of bytes are provided via the [input] function.
     * @param bufferSize specifies max amount of bytes that will be passed in the bytesToRead argument
     * @param input will be invoked once for each time the process needs more compressed data.
     * Total size (sum of remainings) of all ByteBuffers provided must equal [totalCompressedBytes].
     * If total number of bytes passed to all input calls exceeds [totalCompressedBytes] an
     * exception is thrown.
     * @param output will be called repeatedly as decompressed bytes are produced. Buffer argument will
     * have position zero and limit set to however many bytes were uncompressed. This buffer has a
     * capacity equal to the first input ByteBuffer, but the number of bytes it contains will be 0 < limit
     * <= capacity, as any one compress can produce any non-zero number of bytes.
     * Implementation should consume Buffer content (between position 0 and remaining) as for large
     * payloads with high compression ratios, this function may be called MANY times.
     * @return sum of all uncompressed bytes count passed via [output] function calls.
     */
    actual override suspend fun decompressArray(
        totalCompressedBytes: ULong,
        bufferSize: UInt,
        input: suspend (bytesToRead: Int) -> ByteArray,
        output: suspend (buffer: ByteArray) -> Unit
    ): ULong {
        TODO("Not yet implemented")

    }

    /**
     * Use this to reset state back to the same as initialization.  This allows reuse of this instance for additional
     * operations
     */
    actual override fun reset() {
        inflater.reset()
        deflater.reset()
    }
}