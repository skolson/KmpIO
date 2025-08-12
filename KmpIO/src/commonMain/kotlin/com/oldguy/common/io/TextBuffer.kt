package com.oldguy.common.io

import com.oldguy.common.io.charsets.Charset
import com.oldguy.common.io.charsets.MultiByteDecodeException

/**
 * Platform-neutral text buffering for simple text file read (or other source) operations, using blocks of
 * bytes as input. Various access methods are provided for processing decoded text.
 *
 * Source lambda supplies all bytes, in order. TextBuffer handles decoding using the specified charset,
 * including handling multi-byte character sets in the edge case where a partial character is found
 * at the end of a ByteArray. If the source lambda indicates no more data by returning 0 bytes, and TextBuffer
 * determines there is an incomplete character at the end of the file, it will throw MultiByteDecodeException.
 *
 * @param charset specifies how to decode the incoming bytes from the underlying file.
 * @param blockSizeArg specifies number of bytes to request from source lambda on each call. Value is
 * rounded up to a multiple of the maximum number of bytes per character for the specified Charset.
 * @param source function should perform a read operation up to count bytes,
 * into the specified buffer which is a ByteArray. It should return the number of bytes read, or 0
 * to indicate end of file. ByteArray can be any size and does not have to end on a line break. Can
 * also end in the middle of a multi-byte character, see above comments.
 */
open class TextBuffer(
    val charset: Charset,
    blockSizeArg: Int = DEFAULT_BLOCK_SIZE,
    val source: (suspend (
        buffer: ByteArray,
        count: Int
    ) -> UInt )
) {
    private val blockSize = blockSizeArg + (blockSizeArg % charset.bytesPerChar.last)
    private val bytes = ByteArray(blockSize)
    private var buf = ByteBuffer(blockSize + (charset.bytesPerChar.last * 2))
        .apply { limit = 0 }
    private var endOfFile = false
    private var noMoreSource = false
    private var readLock = false
    private var remainder = ByteArray(charset.bytesPerChar.last)
    private var partial = ByteArray(0)

    /**
     * While processing text by line, this attribute is the current line count processed
     */
    var lineCount = 0
        private set

    /**
     * Count of the number of bytes read from source, before decoding.
     */
    var bytesRead: Long = 0
        private set

    /**
     * true if source has returned zero indicating no more data, and all characters are processed
     */
    val isEndOfFile get() = endOfFile

    /**
     * True if a read operation is in progress. Do not alter or close the underlying source while this is true.
     */
    val isReadLock get() = readLock


    private suspend fun useSource(): UInt {
        if (buf.remaining > 0) {
            if (buf.remaining >= charset.bytesPerChar.last)
                throw IllegalStateException("useSource called when more than ${charset.bytesPerChar.last} bytes available: ${buf.remaining}")
            val remainder = buf.getBytes()
            buf.clear()
            buf.putBytes(remainder)
        } else {
            if (noMoreSource) {
                endOfFile = true
                return 0u
            }
            buf.clear()
        }
        val count = source(bytes, bytes.size).toInt()
        bytesRead += count.toLong()
        if (count == 0)
            noMoreSource = true
        else {
            val partialBytes = charset.checkMultiByte(bytes, count, 0, false)
            if (partial.isNotEmpty()) buf.putBytes(partial)
            val count = (count - partialBytes) + partial.size
            buf.putBytes(bytes, length = count)
            partial = ByteArray(partialBytes)
            if (partialBytes > 0) {
                bytes.copyInto(
                    partial,
                    0,
                    count - partialBytes,
                    count
                )
            }
        }
        buf.flip()
        return count.toUInt()
    }

    private fun checkBytes(position: Int): ByteArray {
        var pos = position
        return ByteArray(charset.bytesPerChar.first).apply {
            repeat(charset.bytesPerChar.first) {
                this[it] = buf.get(pos++)
            }
        }
    }

    /**
     * Use this to read decoded character by decoded character, until isEndOfFile is true.
     * @param peek true if decoded character should be returned without advancing to the next character.
     * @return decoded character. if isEndOfFile is true, returns code 0x00 character.
     */
    suspend fun next(peek: Boolean = false): Char {
        if (!isEndOfFile && buf.remaining < charset.bytesPerChar.last)
            useSource()
        if (buf.remaining == 0) return Char(0)
        val pos = buf.position
        val byteCount = charset.byteCount(checkBytes(pos))
        if (byteCount > buf.remaining)
            throw MultiByteDecodeException(
                "Missing bytes to complete indicated character at position in last block $pos, ",
                pos,
                byteCount,
                byteCount - buf.remaining,
                buf.get(pos)
            )
        for (i in pos until pos + byteCount)
            remainder[i - pos] = buf.get(i)
        val s = charset.decode(remainder, byteCount)
        if (s.length != 1)
            throw MultiByteDecodeException(
                "decode of $byteCount bytes returned $s, length = ${s.length}, should have been 1",
                pos,
                byteCount,
                -1,
                remainder[0]
            )
        if (!peek) buf.position = buf.position + byteCount
        return s[0]
    }

    /**
     * Reads next line of text, no matter how long, which has obvious implications for memory on large files with no
     * line breaks. It uses the source function to read blocks when needed and maintains state of where next line is.
     * So only use this on files with line breaks.
     * @return a line containing any text found without a line separator. Line may be empty. After all lines have been
     * returned, subsequent calls will always be an empty string.
     */
    open suspend fun readLine(): String {
        return StringBuilder(blockSize).apply {
            while (!isEndOfFile) {
                val c = next()
                if (c == '\n') break
                append(c)
            }
            lineCount++
        }.toString()
    }

    /**
     * Runs the read process.
     * @param action function is called for each line. Processing continues until end of file is
     * reached and all text lines have been passed to this function. Function is called with two
     * arguments; the one-relative line number of the text, and the text without any line separator.
     * action should return false if reading should stop
     */
    open suspend fun forEachLine(
        action: (count: Int, line: String) -> Boolean
    ) {
        try {
            readLock = true
            while (true) {
                val line = readLine()
                if (isEndOfFile) break
                if (!action(lineCount, line))
                    break
            }
        } finally {
            readLock = false
        }
    }

    suspend fun next(characterCount: Int, peek: Boolean = false): String {
        return StringBuilder(characterCount).apply {
            repeat(characterCount) {
                if (!isEndOfFile)
                    append(next(peek))
            }
        }.toString()
    }

    /**
     * Verifies that the current position is a quote character. If it is, retrieves characters and
     * builds a String until the next quote character is seen. If an escape is specified for an
     * enclose quote, handle the escape as well. If end of input is reached before the closing quote,
     * all characters since the last quote are returned.
     * @param quote character to look for
     * @param escape String to match as an escape for quote. If empty, no escape processing happens
     * @param maxSize number of characters to read before returning.
     * @return String containing characters between quote characters. If current position is not a quote,
     * empty string is returned
     */
    suspend fun quotedString(
        quote: Char = '"',
        escape: String = "\\\"",
        maxSize: Int = 1024
    ): String {
        if (next(true) != quote) return ""
        var c = next()
        if (c != quote)
            throw IllegalStateException("Quoted string must start with quote")
        return StringBuilder(maxSize).apply {
            while (true) {
                c = next()
                if (escape.isEmpty() && c == quote) break
                if (escape.isNotEmpty()) {
                    var match = 0
                    var temp = ""
                    for (m in escape) {
                        if (c == m) {
                            match++
                            temp += c
                            c = next()
                        } else
                            break
                    }
                    if (match == escape.length)
                        append(quote)
                    else {
                        append(temp)
                        if (c == quote) break
                    }
                }
                append(c)
                if (isEndOfFile || this.length >= maxSize) break
            }
        }.toString()

    }

    companion object {
        const val EOL = "\n"
        const val DEFAULT_BLOCK_SIZE = 4096
    }
}