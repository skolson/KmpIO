package com.oldguy.common.io

/**
 * Supported charsets, requires each target to supply an "actual" implementation, typically using target's natuve charsets.
 */
enum class Charsets(val charsetName: String, val bytesPerChar: Int = 1) {
    Utf8("UTF-8"),
    Utf16le("UTF-16LE", 2),
    Utf16be("UTF-16BE", 2),
    Iso8859_1("ISO8859-1"),
    UsAscii("US-ASCII");

    companion object {
        /**
         * Converts a charset name to a Charsets enum value, throws an exception if no match
         */
        fun fromName(name: String): Charsets {
            return values().first { it.charsetName == name }
        }
    }
}

/**
 * A Charset instance can encode a String to bytes or decode bytes to a String using the specified character set.
 * @param set from the enum class of supported character sets
 */
expect class Charset(set: Charsets) {
    val charset: Charsets

    /**
     * Using the current character set, decode the entire ByteArray into a String
     * @param bytes For 8 bit character sets, has the same size as the number of characters. For 16-bit character sets,
     * bytes.size is double the number of String characters. Entire content is decoded.
     * @return decoded String
     */
    fun decode(bytes: ByteArray): String

    /**
     * Using the current character set, encode the entire String into a ByteArray.
     * @param inString
     * @return For 8 bit character sets, a VyteArray with the same size as the length of the String. For 16-bit character sets,
     * ByteArray Size is double the number of String characters.
     */
    fun encode(inString: String): ByteArray

}