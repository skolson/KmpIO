package com.oldguy.common.io.charsets

import com.oldguy.common.containsIgnoreCase

/**
 * Thrown if a partial multi-byte character is found at the end of a ByteArray.
 * @param message error text
 * @param offset offset of the partial character
 * @param bytesMissing number of bytes missing to complete the character
 * @param byte the 'header' byte of the character indicating number of bytes required to complete it
 */
class MultiByteDecodeException(
    message: String,
    val offset: Int,
    val bytesRequired: Int,
    val bytesMissing: Int,
    val byte: Byte
): Exception(message)

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
        listOf("iso-ir-100", "ISO_8859-1", "latin1", "l1", "IBM819", "cp819", "csISOLatin1", "819", "IBM-819", "ISO8859_1", "ISO_8859-1:1987", "ISO_8859_1", "8859_1", "ISO8859-1", "ASCII", "USASCII"),
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
                it.charsetName.lowercase() == name.lowercase() ||
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
     * To verify a complete multi-byte character at the end of the selected bytes, use checkMultiByte()
     * @param count number of bytes to decode
     * @param offset offset into bytes to start decoding from
     * @return decoded String
     */
    abstract fun decode(bytes: ByteArray, count: Int = bytes.size, offset: Int = 0): String

    /**
     * Using the current character set, decode the ByteArray into a String
     * @param bytes For 8 bit character sets, has the same size as the number of characters. For 16-bit character sets,
     * bytes.size is double the number of String characters. Entire content is decoded.
     * To verify a complete multi-byte character at the end of the selected bytes, use checkMultiByte()
     * @param count number of bytes to decode
     * @return decoded String
     */
    abstract fun decode(bytes: UByteArray, count: Int = bytes.size, offset: Int = 0): String

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

    /**
     * Use to verify that a decode operation on selected bytes will not have a partial character at the end.
     * If the selected bytes end with a partial character, either return false or throw an exception.
     * @param bytes to be decoded
     * @param count number of bytes to decode
     * @param offset offset into bytes to start decoding from
     * @param throws true if partial character detection at end of bytes should throw an exception.
     * False if it should return false. Exception thrown
     * @return 0 if no incomplete characters at end of selected bytes, otherwise the number of bytes
     * required to complete the character.
     */
    abstract fun checkMultiByte(bytes: ByteArray, count: Int, offset: Int, throws: Boolean = true): Int

    /**
     * For the specified byte(s), determine the number of bytes required to complete the character.
     * @param bytes must be a ByteArray(charset.bytesPerChar.first) containing byte(s) to be checked.
     * @return number of bytes required to complete a character. Between charset.bytesPerChar.first
     * and charset.bytesPerChar.last.
     */
    abstract fun byteCount(bytes: ByteArray): Int

    /**
     * For the specified byte, determine the number of bytes required to complete the character.
     * @param bytes must be a ByteArray(charset.bytesPerChar.first) containing byte(s) to be checked.
     * @return number of bytes required to complete a character. Between charset.bytesPerChar.first
     * and charset.bytesPerChar.last.
     */
    abstract fun byteCount(bytes: UByteArray): Int
}