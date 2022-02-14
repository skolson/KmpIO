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
     * @param totalCompressedBytes Compressed data byte count. This is the number of input bytes to
     * process.  Function will continue until this number of bytes are provided via the [input] function.
     * @param input will be invoked once for each input required.  Total size (sum of remainings) of
     * all ByteBuffers provided must equal [totalCompressedBytes]. Note if input is remaining == 0
     * indicating an empty buffer, decompress operation will cease.
     * @param output will be called repeatedly as decompressed bytes are produced. Buffer argument will
     * have position zero and limit set to however many bytes were uncompressed. This buffer has a
     * capacity equal to the input ByteBuffer, but the number of bytes it contains will be 0 < limit
     * <= capacity, as any one compress can produce any non-zero number of bytes.
     * Implementation should consume Buffer content (between position 0 and remaining) as for large
     * payloads with high compression ratios, this function may be called MANY times.
     * @return sum of all uncompressed bytes count passed via [output] function calls.
     */
    suspend fun decompress(
        totalCompressedBytes: ULong,
        bufferSize: UInt,
        input: suspend (bytesToRead: Int) -> ByteBuffer,
        output: suspend (buffer: ByteBuffer) -> Unit
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