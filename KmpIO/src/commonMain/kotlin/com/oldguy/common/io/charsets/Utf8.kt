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
    override fun decode(bytes: ByteArray): String {
        return bytes.decodeToString()
    }

    override fun decode(bytes: UByteArray): String {
        return bytes.toByteArray().decodeToString()
    }

    override fun encode(inString: String): ByteArray {
        return inString.encodeToByteArray()
    }

    override fun UEencode(inString: String): UByteArray {
        return inString.encodeToByteArray().toUByteArray()
    }
}