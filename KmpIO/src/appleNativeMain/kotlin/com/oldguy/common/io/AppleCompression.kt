package com.oldguy.common.io

/**
 * Apple support compression library used to implement compress/decompress operations. This is a common implementation
 * usable by all Apple target platforms
 */
class AppleCompression(algorithm: CompressionAlgorithms) {
    val lib = Compression(algorithm)
    private val appleConst: Int = when (algorithm) {
        CompressionAlgorithms.None -> 0
        CompressionAlgorithms.Deflate -> 0x205
        CompressionAlgorithms.Deflate64 -> 0x205
        CompressionAlgorithms.LZ4 -> 0x100
        CompressionAlgorithms.LZMA -> 0x306
        CompressionAlgorithms.LZFSE -> 0x801
    }

    fun compress(input: ByteBuffer): ByteBuffer {
        return input
    }

    fun compress(input: ByteArray, startIndex: Int, length: Int): ByteArray {
        return input
    }

    fun decompress(input: ByteBuffer): ByteBuffer {
        return input
    }

    fun decompress(input: ByteArray, startIndex: Int, length: Int): ByteArray {
        return input
    }

    fun reset() {

    }
}