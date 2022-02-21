package com.oldguy.common.io

/**
 * Use platform-specific compression implementations to provide basic compress/decompress functions.
 * Actual implementations will typically use a built-in library like ZLIB (for Apple and Android),
 * or can cinterop to ZLIB or any other compression lib if desired.
 */

enum class CompressionAlgorithms {
    None, Deflate, BZip2, LZMA
}

/**
 * Implementations should handle one Algorithm and provide the various coroutine-friendly operations.
 * Strategy is for implementations to manage the logic for accessing the compression algorithms,
 * and have callers just implement input and output functions for producing and consuming data.
 */
interface Compression {
    val algorithm: CompressionAlgorithms

    /**
     * Compress one or more blocks of data in ByteBuffer using an implementation-specific algorithm.
     * @param input Uncompressed data starting at the current position (typically 0) until limit.
     * ByteBuffer returned typically is reusing the same buffer with different data, but that is
     * not required.
     * @param output argument contains compressed data starting at position 0 until limit. This buffer
     * is reused for every call with different data each time. Any changes to the state of the
     * ByteBuffer argument will be ignored. Intended for read-only use.
     * @return count of total compressed bytes by this operation
     */
    suspend fun compress(input: suspend () -> ByteBuffer,
                         output: suspend (buffer: ByteBuffer) -> Unit
    ): ULong

    /**
     * Call this with one or more blocks of data to compress any amount of data using the algorithm specified at
     * constructor time.
     * @param input Uncompressed data.
     * @param output argument contains compressed data. Any changes to the state of the
     * ByteArray argument will be ignored. Intended for read-only use.
     * @return count of total compressed bytes by this operation. This will be the sum of the sizes
     * of every ByteArray passed to the [output] function.
     */
    suspend fun compressArray(input: suspend () -> ByteArray,
                              output: suspend (buffer: ByteArray) -> Unit
    ): ULong

    /**
     * Call this with one or more blocks of data to de-compress any amount of data using the algorithm specified at
     * constructor time.
     * If the selected algorithm fails during the operation, and IllegalArgumentException is thrown. There is no
     * attempt at dynamically determining the algorithm used to originally do the compression.
     * @param totalCompressedBytes Compressed data byte count. This is the number of input bytes to
     * process.  Function will continue until this number of bytes are provided via the [input] function.
     * @param bufferSize specifies max amount of bytes that will be passed in the bytesToRead argument
     * @param input will be invoked once for each input required.  Total size (sum of remainings) of
     * all ByteBuffers provided must equal [totalCompressedBytes]. Note if input is remaining == 0
     * indicating an empty buffer, decompress operation will cease. If total bytes provided
     * exceeds [totalCompressedBytes], an exception is thrown.
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
     * constructor time. This is a convenience wrapper for the above [decompress] using ByteBuffers.
     * There may be a little extra performance hit with this version as implementation uses
     * ByteBuffers
     * @param totalCompressedBytes Compressed data byte count. This is the number of input bytes to
     * process.  Function will continue until this number of bytes are provided via the [input] function.
     * @param bufferSize specifies max amount of bytes that will be passed in the bytesToRead argument
     * @param input will be invoked once for each input required.  Total size of
     * all ByteArrays provided must equal [totalCompressedBytes]. Note if input is an empty
     * ByteArray, decompress operation will cease/abort and crc check will likely throw an exception.
     * @param output will be called repeatedly as decompressed bytes are produced. ByteArray argument will
     * contain however many bytes were uncompressed. The size is dictated by the Deflate algorithm,
     * as any one decompress can produce any non-zero number of bytes.
     * Implementation should consume the ByteArray data in some non-memory-intensive way as for large
     * payloads with high compression ratios, this function may be called many more times then the
     * [input] function.
     * @return count of total uncompressed bytes.
     */
    suspend fun decompressArray(
        totalCompressedBytes: ULong,
        bufferSize: UInt,
        input: suspend (bytesToRead: Int) -> ByteArray,
        output: suspend (buffer: ByteArray) -> Unit
    ): ULong

    /**
     * Use this to reset state back to the same as initialization.  This allows reuse of this instance for additional
     * operations
     */
    fun reset()
}