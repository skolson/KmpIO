package com.oldguy.common.io

import com.oldguy.common.io.charsets.Charset
import kotlin.math.min

/**
 * Platform-neutral text buffering for simple text file read operations. Use function forEachLine
 * to read all lines of text.
 * @param charset specifies how to decode the incoming bytes from the underlying file.
 * @param source function should perform a read operation up to count bytes,
 * into the specified buffer which is a ByteArray. It should return the number of bytes read, or 0
 * to indicate end of file. ByteArray can be any size and does not have to end on a line break.
 */
open class TextBuffer(
    val charset: Charset,
    blockSize: Int = DEFAULT_BLOCK_SIZE,
    val source: (suspend (
        buffer: ByteArray,
        count: Int
    ) -> UInt )
) {
    private var buf = ByteArray(blockSize)
    private var endOfFile = false
    val isEndOfFile get() = endOfFile
    private var readLock = false
    val isReadLock get() = readLock
    private var partialLine: String = ""
    private var lines = emptyList<String>()
    private var lineIndex = 0

    suspend fun nextBlock(): String {
        if (endOfFile) return ""
        val count = source(buf, buf.size)
        endOfFile = count < buf.size.toUInt()
        return charset.decode(buf, count.toInt())
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
    suspend fun forEachLine(
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

    companion object {
        const val EOL = "\n"
        const val DEFAULT_BLOCK_SIZE = 4096
    }
}