package com.oldguy.common.io

import com.oldguy.common.io.charsets.Charset
import kotlin.math.min

/**
 * Platform-neutral text buffering for simple text file read operations. Use function forEachLine
 * to read all lines of text.
 * @param charset specifies how to decode the incoming bytes from the underlying file.
 * @param readBlock function should perform a read operation up to the length of the specified buffer,
 * into the specified buffer which is a ByteArray. The length of the supplied buffer is specified
 * as a companion constant
 */
open class TextBuffer(
    open val charset: Charset,
    val blockSize: Int = defaultBlockSize
) {
    private var buf = ByteArray(blockSize)
    private var index = -1
    private var lineEndIndex = -1
    private var endOfFile = false
    val isEndOfFile get() = endOfFile
    private var str: String = ""
    private var readLock = false
    val isReadLock get() = readLock
    private lateinit var readBlock: (suspend (buffer: ByteArray) -> UInt )

    suspend fun nextBlock(): String {
        if (endOfFile) return ""
        val bytesRead = readBlock(buf)
        val len = min(blockSize.toUInt(), bytesRead)
        if (len < buf.size.toUInt()) {
            buf = ByteArray(len.toInt())
            endOfFile = true
        }
        index = 0
        return charset.decode(buf)
    }

    private suspend fun nextBlockLineState(): Boolean {
        if (lineEndIndex < 0) {
            if (endOfFile) return false
            str += nextBlock()
            index = 0
            val x = str.indexOf(EOL)
            lineEndIndex = if (x >= 0) x + 1 else -1
            return true
        }
        return false
    }

    /**
     * Reads one line of text, no matter how long, which has obvious implications for memory on large files with no
     * line breaks. Otherwise, reads blocks and maintains state of where next line is. So only use this on files with
     * line breaks.
     * @return a non empty line containing any text found and ended by a line separator. After all lines have been
     * returned subsequent calls will always be an empty string.
     */
    open suspend fun readLine(): String {
        val lin: String
        while (!endOfFile && lineEndIndex == -1)
            nextBlockLineState()
        if (endOfFile && lineEndIndex == -1) {
            lin = str
            str = ""
        } else {
            lin = str.substring(index, lineEndIndex)
            index = lineEndIndex
            val x = str.indexOf(EOL, index)
            lineEndIndex = if (x >= 0)
                x + 1
            else {
                str = str.substring(index)
                -1
            }
        }
        return lin
    }

    /**
     * Runs the read process.
     * @param readBlock function is passed a ByteArray of size blockSize. It should read bytes up to
     * the size of the ByteArray, and return the number of bytes actually read. Returning any number
     * from zero up to but not including the buffer size, is considered end-of-file.
     * @param action function is called for each line. Processing continues until end of file is
     * reached and all text lines have been passed to this function. Function is called with two
     * arguments; the one-relative line number of the text, and the text without any line separator
     */
    suspend fun forEachLine(
        readBlock: suspend (buffer: ByteArray) -> UInt,
        action: (count: Int, line: String) -> Boolean)
    {
        this.readBlock = readBlock
        try {
            readLock = true
            var count = 1
            var lin = readLine()
            while (lin.isNotEmpty()) {
                if (action(count, lin)) {
                    lin = readLine()
                    count++
                } else
                    break
            }
        } finally {
            readLock = false
        }
    }

    companion object {
        const val EOL = "\n"
        const val defaultBlockSize = 4096
    }
}