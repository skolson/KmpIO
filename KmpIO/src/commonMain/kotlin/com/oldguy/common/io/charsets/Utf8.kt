package com.oldguy.common.io.charsets

/**
 * Simple wrapper for UTF-8 support available with all Kotlin flavors
 */
class Utf8:
    Charset(
        "UTF-8",
        1..4
    )
{
    /**
     * throws a Utf8DecodeException if a partial multi-byte character is found at the end of a ByteArray.
     * @param bytes
     * @param count bytes to decode
     * @param offset offset into bytes to start decoding from
     */
    override fun decode(bytes: ByteArray, count: Int, offset: Int): String {
        checkMultiByte(bytes, count, offset)
        return bytes.sliceArray(offset until offset + count).decodeToString()
    }

    /**
     * throws a Utf8DecodeException if a partial multi-byte character is found at the end of a ByteArray.
     * @param bytes
     * @param count bytes to decode
     * @param offset offset into bytes to start decoding from
     */
    override fun decode(bytes: UByteArray, count: Int, offset: Int): String {
        bytes.toByteArray().apply {
            checkMultiByte(this, count, offset)
            return sliceArray(offset until offset + count).decodeToString()
        }
    }

    override fun encode(inString: String): ByteArray {
        return inString.encodeToByteArray()
    }

    override fun UEencode(inString: String): UByteArray {
        return inString.encodeToByteArray().toUByteArray()
    }

    override fun checkMultiByte(bytes: ByteArray, count: Int, offset: Int, throws: Boolean): Int {
        var bytesMissing = 0
        bytes[count+offset-1].toUByte().toInt().also {
            if (it in twoBytes ||
                it in threeBytes ||
                it in fourBytes
            ) {
                bytesMissing = when (it) {
                    in twoBytes -> 1
                    in threeBytes -> 2
                    else -> 3
                }
                if (throws) {
                    throw MultiByteDecodeException(
                        errorMessage,
                        count + offset,
                        when (it) {
                            in twoBytes -> 2
                            in threeBytes -> 3
                            else -> 4
                        },
                        bytesMissing,
                        it.toByte()
                    )
                }
            }
        }
        if (count+offset-2 >= 0) {
            bytes[count + offset - 2].toUByte().toInt().also {
                if (it in threeBytes ||
                    it in fourBytes
                ) {
                    bytesMissing = if (it in threeBytes) 1 else 2
                    if (throws) {
                        throw MultiByteDecodeException(
                            errorMessage,
                            count + offset - 1,
                            if (it in threeBytes) 3
                            else 4,
                            bytesMissing,
                            it.toByte()
                        )
                    }
                }
            }
        }
        if (count+offset-3 >= 0) {
            bytes[count + offset - 3].toUByte().toInt().also {
                if (it in fourBytes) {
                    bytesMissing = 1
                    if (throws) {
                        throw MultiByteDecodeException(
                            errorMessage,
                            count + offset - 2,
                            4,
                            bytesMissing,
                            it.toByte()
                        )
                    }
                }
            }
        }
        return bytesMissing
    }

    override fun byteCount(bytes: ByteArray): Int {
        return when (bytes[0].toUByte().toInt()) {
            in twoBytes -> 2
            in threeBytes -> 3
            in fourBytes -> 4
            else -> 1
        }
    }

    override fun byteCount(bytes: UByteArray): Int {
        return when (bytes[0].toInt()) {
            in twoBytes -> 2
            in threeBytes -> 3
            in fourBytes -> 4
            else -> 1
        }
    }

    companion object {
        // from https://encoding.spec.whatwg.org/#utf-8 for detecting partial character at end of ByteArray
        private val twoBytes = 0xC2..0xDF
        private val threeBytes = 0xE0..0xED
        private val fourBytes = 0xF0..0xF4
        private const val errorMessage = "Last character to decode indicates missing byte(s)"
    }

}