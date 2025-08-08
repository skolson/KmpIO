package com.oldguy.common.io

import com.oldguy.common.io.charsets.Charset
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream

/**
 * Read a text file, and provide both character set translation as well as line-based processing
 */
@Suppress("BlockingMethodInNonBlockingContext")
actual class TextFile actual constructor(
    actual val file: File,
    actual val charset: Charset,
    val mode: FileMode,
    source: FileSource
) : Closeable {

    var javaReader: BufferedReader? = null
    var javaWriter: BufferedWriter? = null
    val javaCharset: java.nio.charset.Charset =
        java.nio.charset.Charset.forName(charset.name)

    constructor(
        file: File,
        charset: Charset,
        mode: FileMode,
        stream: InputStream
    ) : this(file, charset, mode, FileSource.Asset) {
        javaReader = stream.bufferedReader(javaCharset)
    }

    constructor(
        file: File,
        charset: Charset,
        mode: FileMode,
        stream: OutputStream
    ) : this(file, charset, mode, FileSource.Asset) {
        javaWriter = stream.bufferedWriter(javaCharset)
    }

    init {
        if (javaReader == null && mode == FileMode.Read) {
            val stream = when (source) {
                FileSource.Asset -> null
                FileSource.Classpath -> TextFile::class.java.getResourceAsStream(file.fullPath)
                FileSource.File -> FileInputStream(file.fullPath)
            }
            if (stream != null)
                javaReader =
                    BufferedReader(InputStreamReader(stream, javaCharset))
        }

        if (javaWriter == null && mode == FileMode.Write) {
            val stream = when (source) {
                FileSource.Asset -> null
                FileSource.Classpath -> throw IllegalArgumentException("cannot write to a file on classpath")
                FileSource.File -> FileOutputStream(file.fullPath)
            }
            if (stream != null)
                javaWriter = stream.bufferedWriter(javaCharset)
        }
    }

    actual constructor(
        filePath: String,
        charset: Charset,
        mode: FileMode,
        source: FileSource
    ) : this(File(filePath, null), charset, mode, source)

    actual override suspend fun close() {
        javaReader?.close()
        javaWriter?.close()
    }

    actual suspend fun readLine(): String {
        if (mode == FileMode.Write)
            throw IllegalStateException("Mode is write, cannot read")
        return javaReader?.readLine() ?: ""
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
        val rdr = javaReader ?: throw IllegalStateException("Reader is invalid")
        var count = 1
        try {
            rdr.forEachLine { if (!action(count++, it)) throw IllegalStateException("ignore") }
        } catch (_: IllegalStateException) {
        }
    }

    actual suspend fun forEachBlock(maxSizeBytes: Int, action: (text: String) -> Boolean) {
        if (mode == FileMode.Write)
            throw IllegalStateException("Mode is write, cannot read")
        val rdr = javaReader ?: throw IllegalStateException("Reader is invalid")
        val chars = CharArray(maxSizeBytes)
        var count = rdr.read(chars)
        while (count > 0) {
            if (!action(String(chars, 0, count)))
                break
            count = rdr.read(chars)
        }
    }

    actual suspend fun read(maxSizeBytes: Int): String {
        if (mode == FileMode.Write)
            throw IllegalStateException("Mode is write, cannot read")
        val rdr = javaReader ?: throw IllegalStateException("Reader is invalid")
        val chars = CharArray(maxSizeBytes)
        val count = rdr.read(chars)
        return if (count > 0)
            String(chars, 0, count)
        else
            ""
    }

    actual suspend fun skip(bytesCount: ULong) {
        if (mode != FileMode.Read)
            throw IllegalStateException("Mode must be Read to use skip()")
        val count = javaReader?.skip(bytesCount.toLong())
        if (count != bytesCount.toLong())
            throw IllegalStateException("skip($bytesCount) attempted, $count bytes skipped")
    }
}
