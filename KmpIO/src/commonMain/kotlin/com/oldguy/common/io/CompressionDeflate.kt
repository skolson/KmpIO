package com.oldguy.common.io

/**
 * Implementations provide the Deflate algorithm from the ZipSpec. Compress operations can specify
 * a [Strategy] or use the Default.
 * Note - Zip needs both the deflater and the inflater to use the nowrap=true option
 * This can be overridden for both compression and decompression, but this class does not offer
 * the ability to use a compressor and de-compressor that do not use the same nowrap option.
 * @param noWrap = true adds a header and CRC to the payload on deflate, and expects these on inflate
 */
expect class CompressionDeflate constructor(noWrap: Boolean): Compression {
    enum class Strategy {Default, Filtered, Huffman}
    override val algorithm: CompressionAlgorithms
    override val bufferSize: Int
    var strategy: Strategy

    override suspend fun compress(input: suspend () -> ByteBuffer,
                                  output: suspend (buffer: ByteBuffer) -> Unit
    ): ULong
    override suspend fun compressArray(input: suspend () -> ByteArray,
                                       output: suspend (buffer: ByteArray) -> Unit
    ): ULong
    override suspend fun decompress(
        input: suspend () -> ByteBuffer,
        output: suspend (buffer: ByteBuffer) -> Unit
    ): ULong

    override suspend fun decompressArray(
        input: suspend () -> ByteArray,
        output: suspend (buffer: ByteArray) -> Unit
    ): ULong
}