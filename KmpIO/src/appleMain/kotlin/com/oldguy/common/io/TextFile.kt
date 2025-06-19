package com.oldguy.common.io

import com.oldguy.common.io.charsets.Charset
import kotlinx.cinterop.*
import platform.posix.__sFILE
import platform.posix.fwrite
import platform.posix.memcpy


@OptIn(ExperimentalForeignApi::class)
actual class TextFile actual constructor(
    actual val file: File,
    charset: Charset,
    mode: FileMode,
    source: FileSource
) : Closeable
{
    actual constructor(
        filePath: String,
        charset: Charset,
        mode: FileMode,
        source: FileSource
    ) : this(File(filePath, null), charset, mode, source)

    actual val charset = charset
    private val apple = AppleFileHandle(file, mode)
    private var raw: CValuesRef<__sFILE>? = null

    val textBuffer = TextBuffer(charset) { buffer, count ->
        if (buffer.isEmpty())
            0u
        else {
            File.throwError { error ->
                apple.handle.readDataUpToLength(count.toULong(), error)?.let {
                    buffer.usePinned { buf ->
                        memcpy(buf.addressOf(0), it.bytes, it.length.convert())
                    }
                    it.length.toUInt()
                } ?: 0u
            }
        }
    }


    actual override suspend fun close() {
        apple.close()
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
        val buf = ByteBuffer(textBuffer.charset.encode(text))
        val arr = buf.getBytes()
        val bytesWritten = fwrite(arr.refTo(0), RawFile.byteSz, arr.size.toULong(), raw)
        if (bytesWritten != arr.size.toULong()) {
            throw IllegalStateException("Write error, bytes written: $bytesWritten, bytes to write: ${arr.size}")
        }    }

    actual suspend fun writeLine(text: String) {
        if (textBuffer.isReadLock)
            throw IllegalStateException("Invoking read during existing forEach operation is not allowed ")
        val buf = ByteBuffer(textBuffer.charset.encode(text + TextBuffer.EOL))
        val arr = buf.getBytes()
        val bytesWritten = fwrite(arr.refTo(0), RawFile.byteSz, arr.size.toULong(), raw)
        if (bytesWritten != arr.size.toULong()) {
            throw IllegalStateException("Write error, bytes written: $bytesWritten, bytes to write: ${arr.size}")
        }
    }

    companion object {
        const val EOL = "\n"
    }
}
