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
 * @param source function should perform a read operation up to count bytes,
 * into the specified buffer which is a ByteArray. It should return the number of bytes read, or 0
 * to indicate end of file. ByteArray can be any size and does not have to end on a line break. Can
 * also end in the middle of a multi-byte character, see above comments.
 */
open class TextBuffer(
    val charset: Charset,
    blockSize: Int = DEFAULT_BLOCK_SIZE,
    val source: (suspend (
        buffer: ByteArray,
        count: Int
    ) -> UInt )
) {
    private val bytes = ByteArray(blockSize)
    private var buf = ByteBuffer(blockSize + charset.bytesPerChar.last)
    private var endOfFile = false
    val isEndOfFile get() = endOfFile
    private var readLock = false
    val isReadLock get() = readLock
    private var partialLine: String = ""
    private var lines = emptyList<String>()
    private var lineIndex = 0
    private var remainder = ByteArray(charset.bytesPerChar.last)
    private var partial = ByteArray(0)
    var bytesRead: Long = 0
        private set

    private suspend fun useSource(): UInt {
        val count = source(bytes, bytes.size)
        bytesRead += count.toLong()
        if (count == 0u)
            endOfFile = true
        else {
            buf.clear()
            val partialBytes = charset.checkMultiByte(bytes, bytes.size, 0, false)
            if (partial.isNotEmpty()) buf.putBytes(partial)
            buf.limit = (count.toInt() - partialBytes) + partial.size
            buf.putBytes(bytes, length = buf.limit)
            partial = ByteArray(partialBytes)
            if (partialBytes > 0) {
                bytes.copyInto(
                    partial,
                    0,
                    count.toInt() - partialBytes,
                    count.toInt()
                )
            }
            buf.flip()
        }
        return count
    }

    /**
     * Use this to read decoded character by decoded character, until isEndOfFile is true.
     * @param peek true if decoded character should be returned without advancing to the next character.
     * @return decoded character. if isEndOfFile is true, returns code 0x00 character.
     */
    suspend fun next(peek: Boolean = false): Char {
        if (bytesRead == 0L && !isEndOfFile)
            useSource()    // initial read
        else if (!isEndOfFile && buf.remaining == 0)
            useSource()
        if (buf.remaining == 0) return Char(0)
        val pos = buf.position
        val byteCount = charset.byteCount(buf.get(pos))
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
     * Gets and decodes one block of text. If at the end of the block, a partial character is found,
     * the partial bytes are saved and applied to the bytes from the next call to the source lambda.
     */
    suspend fun nextBlock(): String {
        if (endOfFile) return ""
        useSource()
        return charset.decode(buf.buf, buf.limit)
    }

    private suspend fun readAndSplit() {
        lines = (partialLine + nextBlock()).lines()
        lineIndex = 0
        if (!endOfFile) {
            partialLine = lines.last()
            lines = lines.subList(0, lines.size - 1)
        }
    }

    /**
     * Reads next line of text, no matter how long, which has obvious implications for memory on large files with no
     * line breaks. It uses the source function to read blocks when needed and maintains state of where next line is.
     * So only use this on files with line breaks.
     * @return a line containing any text found without a line separator. Line may be empty. After all lines have been
     * returned, subsequent calls will always be an empty string.
     */
    open suspend fun readLine(): String {
        return if (lineIndex < lines.size) {
            lines[lineIndex++]
        } else {
            if (endOfFile) ""
            else {
                readAndSplit()
                if (lines.isNotEmpty())
                    lines[lineIndex++]
                else
                    ""
            }
        }
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
        var lineCount = 0
        try {
            readLock = true
            while (true) {
                val line = readLine()
                if (endOfFile && (lineIndex >= lines.size)) break
                lineCount++
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

    companion object {
        const val EOL = "\n"
        const val DEFAULT_BLOCK_SIZE = 4096
    }
}