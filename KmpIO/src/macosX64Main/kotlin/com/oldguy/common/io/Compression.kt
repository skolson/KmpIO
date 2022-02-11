package com.oldguy.common.io

actual class Compression actual constructor(algorithm: CompressionAlgorithms) {
    private val lib = AppleCompression(algorithm)

    /**
     * Call this with one or more blocks of data to compress any amount of data using the algorithm specified at
     * constructor time.
     * @param input Uncompressed data. Will compress starting at the current position (typically 0) until limit.
     * @return buffer large enough to contain all Compressed data from input.  Buffer size and remaining value are
     * both equal to the uncompressed byte count. Position of the buffer on return is zero, no rewind required. Size
     * will always be <= input remaining
     */
    actual fun compress(input: ByteBuffer): ByteBuffer {
        return lib.compress(input)
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
    actual fun decompress(first: ByteBuffer): ByteBuffer {
        return lib.decompress(first)
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
        return lib.compress(input, startIndex, length)
    }

    /**
     * Call this with one or more blocks of data to de-compress any amount of data using the algorithm specified at
     * constructor time.
     * @param first Compressed data.
     * @param startIndex specify only for cases where only a portion of an array should be de-compressed. Default 0
     * @param length number of bytes. if ([startIndex] + [length] > [first.size] an exception is thrown.
     * @return array large enough to contain all de-compressed data from input.
     */
    actual fun decompress(first: ByteArray, startIndex: Int, length: Int): ByteArray {
        return lib.decompress(first, startIndex, length)
    }

    /**
     * Use this to reset state back to the same as initialization.  This allows reuse of this instance for additional
     * operations
     */
    actual fun reset() {
        lib.reset()
    }
}