package com.oldguy.common.io

import java.util.zip.Deflater
import java.util.zip.Inflater

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
     * Call this with one or more blocks of data to compress any amount of data using the algorithm specified at
     * constructor time.
     * If the selected algorithm fails during the operation, and IllegalArgumentException is thrown. There is no
     * attempt at dynamically determining the algorithm used to originally do the compression.
     * @param first Compressed data. Will de-compress starting at the current position (typically 0) until limit.
     * @return buffer large enough to contain all De-compressed data from input.  Buffer size and remaining value are
     * both equal to the uncompressed byte count. Position of the buffer on return is zero, no rewind required. Size
     * will always be >= input remaining.
     */
    actual suspend fun decompress(first: ByteBuffer,
                                  next: suspend (output: ByteBuffer) -> ByteBuffer
    ): ULong {
        return when (algorithm) {
            CompressionAlgorithms.Deflate -> {
                var total = 0UL
                inflater.apply {
                    var count = first.remaining
                    setInput(first.getBytes(count))
                    val out = ByteBuffer(first.capacity * 4)
                    while (count != 0) {
                        count = inflate(out.contentBytes)
                        out.positionLimit(0, count)
                        total += count.toUInt()
                        /*
                        val f = finished()
                        val d = needsDictionary()
                        val i = needsInput()
                         */
                        next(out).apply {
                            if (remaining > 0) {
                                setInput(contentBytes)
                            }
                            out.clear()
                        }
                    }
                    end()
                }
                total
            }
            else -> throw IllegalArgumentException("Unsupported compression $algorithm")
        }
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