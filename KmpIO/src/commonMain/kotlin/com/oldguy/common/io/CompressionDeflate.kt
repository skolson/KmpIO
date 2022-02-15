package com.oldguy.common.io

/**
 * Implementations provide the Deflate algorithm from the ZipSpec. Compress operations can specify
 * a [Strategy] or use the Default.
 */
expect class CompressionDeflate constructor(): Compression {
    enum class Strategy {Default, Filtered, Huffman}
    override val algorithm: CompressionAlgorithms

    /**
     * Use this to invoke the [compress] operation using a specified Strategy.
     */
    suspend fun compress(strategy: Strategy,
                         input: suspend () -> ByteBuffer,
                         output: suspend (buffer: ByteBuffer) -> Unit
    ): ULong
    override suspend fun compress(input: suspend () -> ByteBuffer,
                                  output: suspend (buffer: ByteBuffer) -> Unit
    ): ULong
    override suspend fun compressArray(input: suspend () -> ByteArray,
                                       output: suspend (buffer: ByteArray) -> Unit
    ): ULong
    suspend fun compressArray(strategy: Strategy,
                              input: suspend () -> ByteArray,
                              output: suspend (buffer: ByteArray) -> Unit
    ): ULong
    override suspend fun decompress(
        totalCompressedBytes: ULong,
        bufferSize: UInt,
        input: suspend (bytesToRead: Int) -> ByteBuffer,
        output: suspend (buffer: ByteBuffer) -> Unit
    ): ULong

    override suspend fun decompressArray(
        totalCompressedBytes: ULong,
        bufferSize: UInt,
        input: suspend (bytesToRead: Int) -> ByteArray,
        output: suspend (buffer: ByteArray) -> Unit
    ): ULong

    override fun reset()
}