package com.oldguy.common.io.charsets

import com.oldguy.common.containsIgnoreCase

/**
 * Supported charsets, requires each target to supply an "actual" implementation, typically using
 * target's native charsets.
 */
enum class Charsets(
    val charsetName: String,
    val charsetAliases: List<String> = emptyList(),
    val charset: Charset) {
    Utf8(
        "UTF-8",
        listOf("utf8"),
        Utf8()),
    Utf16LE(
        "UTF-16LE",
        listOf("utf-16le", "utf16le", "X-UTF-16LE", "UnicodeLittleUnmarked"),
        Utf16LE()
    ),
    Utf16BE(
        "UTF-16BE",
        listOf("utf-16be", "utf16be", "ISO-10646-UCS-2", "X-UTF-16BE", "UnicodeBigUnmarked"),
        Utf16BE()
    ),
    Utf32LE(
        "UTF-32LE",
        listOf("UTF_32LE", "X-UTF-32LE"),
        Utf32LE()
    ),
    Utf32BE(
        "UTF-32BE",
        listOf("UTF_32BE", "X-UTF-32BE"),
        Utf32BE()
    ),
    Iso88591(
        "ISO-8859-1",
        listOf("iso-ir-100", "ISO_8859-1", "latin1", "l1", "IBM819", "cp819", "csISOLatin1", "819", "IBM-819", "ISO8859_1", "ISO_8859-1:1987", "ISO_8859_1", "8859_1", "ISO8859-1"),
        Iso88591()
    ),
    Window1252(
        "Windows-1252",
        listOf("cp1252", "ibm1252"),
        Windows1252()
    );


    companion object {
        /**
         * Converts a charset name to a Charset, throws an exception if no match
         */
        fun fromName(name: String): Charset {
            return entries.first() {
                it.charsetName == name ||
                it.charsetAliases.containsIgnoreCase(name)
            }.charset
        }
    }
}

/**
 * A Charset instance can encode a String to bytes or decode bytes to a String using the specified character set.
 * @param set from the enum class of supported character sets
 */
abstract class Charset(
    val name: String,
    val bytesPerChar: IntRange,
) {
    /**
     * Using the current character set, decode the entire ByteArray into a String
     * @param bytes For 8 bit character sets, has the same size as the number of characters. For 16-bit character sets,
     * bytes.size is double the number of String characters. Entire content is decoded.
     * @param count number of bytes to decode
     * @return decoded String
     */
    abstract fun decode(bytes: ByteArray, count: Int = bytes.size): String

    /**
     * Using the current character set, decode the ByteArray into a String
     * @param bytes For 8 bit character sets, has the same size as the number of characters. For 16-bit character sets,
     * bytes.size is double the number of String characters. Entire content is decoded.
     * @param count number of bytes to decode
     * @return decoded String
     */
    abstract fun decode(bytes: UByteArray, count: Int = bytes.size): String

    /**
     * Using the current character set, encode the entire String into a ByteArray.
     * @param inString
     * @return For 8 bit character sets, a ByteArray with the same size as the length of the String. For 16-bit character sets,
     * ByteArray Size is double the number of String characters.
     */
    abstract fun encode(inString: String): ByteArray

    /**
     * Using the current character set, encode the entire String into a ByteArray.
     * @param inString
     * @return For 8 bit character sets, a ByteArray with the same size as the length of the String. For 16-bit character sets,
     * ByteArray Size is double the number of String characters.
     */
    abstract fun UEencode(inString: String): UByteArray

}