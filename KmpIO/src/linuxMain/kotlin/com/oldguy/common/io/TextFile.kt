package com.oldguy.common.io

import com.oldguy.common.io.charsets.Charset

actual class TextFile actual constructor(
    file: File,
    charset: Charset,
    mode: FileMode,
    source: FileSource
) : LinuxFile(file, mode, source)
{
    actual constructor(
        filePath: String,
        charset: Charset,
        mode: FileMode,
        source: FileSource
    ) : this(
        File(filePath),
        charset,
        mode,
        source
    )

    actual val charset = charset
    val textBuffer = TextBuffer(charset) { buffer, count ->
        read(buffer, count)
    }

    actual suspend fun readLine(): String {
        return textBuffer.readLine()
    }

    actual suspend fun forEachLine(action: (count: Int, line: String) -> Boolean) {
        try {
            textBuffer.forEachLine() { count, line ->
                action(count, line)
            }
        } finally {
            close()
        }
    }


    actual suspend fun forEachBlock(maxSizeBytes: Int, action: (text: String) -> Boolean) {
        try {
            val str = textBuffer.nextBlock()
            while (str.isNotEmpty()) {
                if (action(str))
                    textBuffer.nextBlock()
                else
                    break
            }
        } finally {
            close()
        }
    }

    actual suspend fun read(maxSizeBytes: Int): String {
        if (textBuffer.isReadLock)
            throw IllegalStateException("Invoking read during existing forEach operation is not allowed ")
        if (textBuffer.isEndOfFile) return ""
        return textBuffer.nextBlock()
    }

    actual suspend fun write(text: String) {
        if (textBuffer.isReadLock)
            throw IllegalStateException("Invoking read during existing forEach operation is not allowed ")
        write(ByteBuffer(textBuffer.charset.encode(text)))
    }

    actual suspend fun writeLine(text: String) {
        if (textBuffer.isReadLock)
            throw IllegalStateException("Invoking read during existing forEach operation is not allowed ")
        write(ByteBuffer(textBuffer.charset.encode(text + TextBuffer.EOL)))
    }

    actual suspend fun skip(bytesCount: ULong) {
        position = position + bytesCount
    }
}