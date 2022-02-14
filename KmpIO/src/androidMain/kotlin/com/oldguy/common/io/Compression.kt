package com.oldguy.common.io

import java.util.zip.Deflater
import java.util.zip.Inflater
import kotlin.math.min

/**
 * Uses platform-specific compression implementations to provide basic compress/decompress functions. Actual
 * implementations will typically use a built-in library like ZLIB (for Apple and Android), or can cinterop to
 * ZLIB if desired (for mimgw64 as an example).
 * This class maintains state on the current compression op. NEVER use the same instance for multiple different
 * simultaneous operations.
 */
actual class Compression actual constructor(private val algorithm: CompressionAlgorithms) {

    private val inflater = Inflater(true)
    private val deflater = Deflater()

    /**
     * Call this with one or more blocks of data to compress any amount of data using the algorithm specified at
     * constructor time.
     * @param input Uncompressed data. Will compress starting at the current position (typically 0) until limit.
     * @return buffer large enough to contain all Compressed data from input.  Buffer size and remaining value are
     * both equal to the uncompressed byte count. Position of the buffer on return is zero, no rewind required. Size
     * will always be <= input remaining
     */
    actual fun compress(input: ByteBuffer): ByteBuffer {
        TODO("Not yet implemented")
    }

    /**
     * Perform a decompress operation of any amount of data using the algorithm specified at
     * constructor time.
     * If the selected algorithm fails during the operation, an Exception is thrown. There is no
     * attempt at dynamically determining the algorithm used to originally do the compression.
     * @param totalCompressedBytes Compressed data byte count. This is the number of input bytes to
     * process.  Function will continue until this number of bytes are provided via the [input] function.
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
    actual suspend fun decompress(
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
     * Use this to reset state back to the same as initialization.  This allows reuse of this instance for additional
     * operations
     */
    actual fun reset() {
    }

    /**
     * Call this with one or more blocks of data to compress any amount of data using the algorithm specified at
     * constructor time.
     * @param input Uncompressed data.
     * @param startIndex specify only for cases where only a portion of an array should be compressed. Default 0
     * @param length number of bytes. if ([startIndex] + [length] > [input.size] an exception is thrown.
     * @return array large enough to contain all Compressed data from input.
     */
    actual fun compress(input: ByteArray, startIndex: Int, length: Int): ByteArray {
        TODO("Not yet implemented")
    }

    /**
     * Call this with one or more blocks of data to de-compress any amount of data using the algorithm specified at
     * constructor time.
     * @param first Compressed data.
     * @param next this function will be invoked if additional data is required.  Args; output is prior output buffer
     * and number of uncompressed bytes. Must return a ByteArray with the next block of data in the compression stream.
     * @return array large enough to contain all de-compressed data from input.
     */
    actual fun decompress(first: ByteArray,
                          next: (output: ByteArray, count: Int) -> ByteArray
    ): Long {
        TODO("Not yet implemented")

    }

}