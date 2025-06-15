package com.oldguy.common.io.charsets

/**
 * Simple wrapper for UTF-8 support available with all Kotlin flavors
 */
class Utf8:
    Charset(
        "UTF-8",
        1..3
    )
{
    override fun decode(bytes: ByteArray, count: Int): String {
        return bytes.sliceArray(0 until count).decodeToString()
    }

    override fun decode(bytes: UByteArray, count: Int): String {
        return bytes.toByteArray().sliceArray(0 until count).decodeToString()
    }

    override fun encode(inString: String): ByteArray {
        return inString.encodeToByteArray()
    }

    override fun UEencode(inString: String): UByteArray {
        return inString.encodeToByteArray().toUByteArray()
    }
}