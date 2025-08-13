package com.oldguy.common.io

import com.oldguy.common.io.charsets.Charset
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import kotlin.IllegalStateException

/**
 * Read a text file, and provide both character set translation as well as line-based processing
 */
@Suppress("BlockingMethodInNonBlockingContext")
actual class TextFile actual constructor(
    actual val file: File,
    actual val charset: Charset,
    val mode: FileMode,
    source: FileSource,
    bufferSize: Int
) : Closeable {

    var javaWriter: java.io.BufferedWriter? = null

    private var stream = when (source) {
        FileSource.Asset -> null
        FileSource.Classpath -> TextFile::class.java.getResourceAsStream(file.fullPath)
        FileSource.File -> FileInputStream(file.fullPath)
    }

    actual val textBuffer = TextBuffer(charset, bufferSize) { buffer, count ->
        stream?.read(buffer)?.toUInt()
            ?: throw IllegalStateException("InputStream is null")
    }

    constructor(
        file: File,
        charset: Charset,
        mode: FileMode,
        stream: InputStream
    ) : this(file, charset, mode, FileSource.Asset) {
        this.stream = stream
    }

    constructor(
        file: File,
        charset: Charset,
        mode: FileMode,
        stream: OutputStream
    ) : this(file, charset, mode, FileSource.Asset) {
        javaWriter = stream.bufferedWriter(java.nio.charset.Charset.forName(charset.name))
    }

    init {
        if (javaWriter == null && mode == FileMode.Write) {
            val stream = when (source) {
                FileSource.Asset -> null
                FileSource.Classpath -> throw IllegalArgumentException("cannot write to a file on classpath")
                FileSource.File -> FileOutputStream(file.fullPath)
            }
            if (stream != null)
                javaWriter = stream.bufferedWriter(java.nio.charset.Charset.forName(charset.name))
        }
    }

    actual constructor(
        filePath: String,
        charset: Charset,
        mode: FileMode,
        source: FileSource,
        bufferSize: Int
    ) : this(File(filePath, null), charset, mode, source)

    actual override suspend fun close() {
        javaWriter?.close()
        stream?.close()
    }

    actual suspend fun readLine(): String {
        if (mode == FileMode.Write)
            throw IllegalStateException("Mode is write, cannot read")
        return textBuffer.readLine()
    }

    actual suspend fun write(text: String) {
        if (mode == FileMode.Read)
            throw IllegalStateException("Mode is read, cannot write")
        javaWriter!!.write(text)
    }

    actual suspend fun writeLine(text: String) {
        write(text)
        javaWriter!!.newLine()
    }

    actual suspend fun forEachLine(action: (count: Int, line: String) -> Boolean) {
        if (mode == FileMode.Write)
            throw IllegalStateException("Mode is write, cannot read")
        try {
            textBuffer.forEachLine() { count, line ->
                action(count, line)
            }
        } finally {
            close()
        }
    }

    actual suspend fun forEachBlock(maxSizeBytes: Int, action: (text: String) -> Boolean) {
        if (mode == FileMode.Write)
            throw IllegalStateException("Mode is write, cannot read")
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

    actual suspend fun skip(bytesCount: ULong) {
        if (mode != FileMode.Read)
            throw IllegalStateException("Mode must be Read to use skip()")
        val count = stream?.skip(bytesCount.toLong()) ?: 0L
        if (count != bytesCount.toLong())
            throw IllegalStateException("skip($bytesCount) attempted, $count bytes skipped")
    }
}