package com.oldguy.common.io

actual class CompressionDeflate: Compression {
    actual enum class Strategy {Default, Filtered, Huffman}
    actual override val algorithm: CompressionAlgorithms = CompressionAlgorithms.Deflate

    actual suspend fun compress(strategy: Strategy,
                                input: suspend () -> ByteBuffer,
                                output: suspend (buffer: ByteBuffer) -> Unit
    ): ULong {
        TODO("Not yet implemented")
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
        TODO("Not yet implemented")
    }

    actual suspend fun compressArray(strategy: Strategy,
                                     input: suspend () -> ByteArray,
                                     output: suspend (buffer: ByteArray) -> Unit
    ): ULong {
        TODO("Not yet implemented")
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
    actual override suspend fun decompress(
        totalCompressedBytes: ULong,
        bufferSize: UInt,
        input: suspend (bytesToRead: Int) -> ByteBuffer,
        output: suspend (buffer: ByteBuffer) -> Unit
    ): ULong {
        TODO("Not yet implemented")
    }
    /**
     * De-compress any amount of data.
     * @param totalCompressedBytes total number of bytes that will be provided by all calls to [input]
     * @param input Invoked for each block of Uncompressed data. Entire ByteArray will be processed.
     * Pass an empty ByteArray to signal premature end of data. Also processing will stop once
     * calls to [input] have returned exactly [totalCompressedBytes]. If more than [totalCompressedBytes]
     * is returned by all calls to [input], an exception is thrown.
     * @param output Invoked once for each chunk of compressed data produced,
     * @return Count of total compressed bytes.
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
        TODO("Not yet implemented")
    }
}