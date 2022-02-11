package com.oldguy.common.io

/**
 * Uses platform-specific compression implementations to provide basic compress/decompress functions. Actual
 * implementations will typically use a built-in library like ZLIB (for Apple and Android), or can cinterop to
 * ZLIB if desired (for mimgw64 as an example).
 * This class maintains state on the current compression op. NEVER use the same instance for multiple different
 * simultaneous operations.
 */

enum class CompressionAlgorithms {
    None, Deflate, Deflate64, LZ4, LZMA, LZFSE
}

expect class Compression(algorithm: CompressionAlgorithms) {

    /**
     * Call this with one or more blocks of data to compress any amount of data using the algorithm specified at
     * constructor time.
     * @param input Uncompressed data. Will compress starting at the current position (typically 0) until limit.
     * @return buffer large enough to contain all Compressed data from input.  Buffer size and remaining value are
     * both equal to the uncompressed byte count. Position of the buffer on return is zero, no rewind required. Size
     * will always be <= input remaining
     */
    fun compress(input: ByteBuffer): ByteBuffer

    /**
     * Call this with one or more blocks of data to compress any amount of data using the algorithm specified at
     * constructor time.
     * @param input Uncompressed data.
     * @param startIndex specify only for cases where only a portion of an array should be compressed. Default 0
     * @param length number of bytes. if ([startIndex] + [length] > [input.size] an exception is thrown.
     * @return array large enough to contain all Compressed data from input.
     */
    fun compress(input: ByteArray, startIndex: Int = 0, length: Int = input.size): ByteArray

    /**
     * Call this with one or more blocks of data to de-compress any amount of data using the algorithm specified at
     * constructor time.
     * If the selected algorithm fails during the operation, and IllegalArgumentException is thrown. There is no
     * attempt at dynamically determining the algorithm used to originally do the compression.
     * @param first Compressed data. Will de-compress starting at the current position (typically 0) until limit.
     * @return buffer large enough to contain all De-compressed data from input.  Buffer size and remaining value are
     * both equal to the uncompressed byte count. Position of the buffer on return is zero, no rewind required. Size
     * will always be >= input remaining.
     */
    suspend fun decompress(first: ByteBuffer,
                   next: suspend (output: ByteBuffer) -> ByteBuffer
    ): ULong

    /**
     * Call this with one or more blocks of data to de-compress any amount of data using the algorithm specified at
     * constructor time.
     * @param first beginning of compressed data.
     * @param nextBuffer if more input is required to complete the decompress, this lambda is invoked and must provide
     * the next block of bytes.
     * @return count of total uncompressed bytes.
     */
    fun decompress(first: ByteArray,
                   next: (output: ByteArray, count: Int) -> ByteArray
    ): Long

    /**
     * Use this to reset state back to the same as initialization.  This allows reuse of this instance for additional
     * operations
     */
    fun reset()
}