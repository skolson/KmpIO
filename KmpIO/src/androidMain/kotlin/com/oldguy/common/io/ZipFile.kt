package com.oldguy.common.io

import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.util.zip.ZipOutputStream

actual class ZipFile actual constructor(
    val file: File,
    private val mode: Mode,
    val charset: Charset
) {
    actual constructor(file: File): this(file, Mode.Read, Charset(Charsets.Utf8))

    actual enum class Mode {
        Read, Write
    }

    private lateinit var zipOutputStream: ZipOutputStream
    private lateinit var javaZipFile: java.util.zip.ZipFile
    actual var lineSeparator: String = System.lineSeparator()

    /**
     * Implementations should return the current list of entries for the zip file. On Mode Read
     * files, if file is not a Zip os some other error occurs, an IOException is thrown.
     */
    private val entriesAdded = mutableListOf<ZipEntry>()
    actual val entries: List<ZipEntry>
        get() = when (mode) {
            Mode.Read -> {
                val list = mutableListOf<ZipEntry>()
                javaZipFile.entries().asSequence().forEach {
                    list.add(
                        ZipEntry(
                            it.name,
                            it.comment ?: "",
                            it.extra ?: ByteArray(0)
                        )
                    )
                }
                list
            }
            Mode.Write -> {
                entriesAdded
            }
        }

    private var _isOpen = false
    actual val isOpen get() = _isOpen

    /**
     * Opens the ZipFile specified.
     */
    actual fun open() {
        when (mode) {
            Mode.Read -> {
                javaZipFile = java.util.zip.ZipFile(file.fullPath)
            }
            Mode.Write -> {
                zipOutputStream =
                    ZipOutputStream(BufferedOutputStream(FileOutputStream(file.fullPath)))
            }
        }
        _isOpen = true
    }

    /**
     * Closes the ZipFile specified, and frees any associated buffers or resources
     */
    actual fun close() {
        if (isOpen) {
            when (mode) {
                Mode.Read -> {
                    javaZipFile.close()
                }
                Mode.Write -> {
                    zipOutputStream.close()
                }
            }
        }
        _isOpen = false
        entriesAdded.clear()
    }

    private fun checkOpen(mode: Mode) {
        if (!isOpen)
            throw IllegalStateException("Zip file is not open")
        if (this.mode != mode)
            throw IllegalStateException("Zip file is ${this.mode}, requested $mode")
    }

    private fun javaEntry(entry: ZipEntry): java.util.zip.ZipEntry {
        return java.util.zip.ZipEntry(entry.name).apply {
            comment = entry.comment
            extra = entry.extra
        }
    }

    /**
     * Convenience method, opens current zip file, invokes lambda, closes file.
     * @param block for a Mode Write file, perform all AddEntry or AddTextEntry calls desired.
     * For a Mode Read file, perform all ReadEntry or ReadTextEntry calls needed.
     */
    actual fun use(block: () -> Unit) {
        try {
            if (!isOpen) open()
            block()
        } finally {
            close()
        }
    }

    actual fun addEntry(entry: ZipEntry) {
        zipOutputStream.putNextEntry(javaEntry(entry))
        zipOutputStream.closeEntry()
    }

    /**
     * Use this to add a new Entry, and use the lambda to provide content to the entry.
     * @param entry describes the entry to be added
     * @param bufferSize size of the content ByteArray that will be passed to the block
     * lambda, controls how much data will be written in one write
     * @param block lambda will be invoked repeatedly until it returns 0.  Each time,
     * content ByteArray that is supplied will be of size [bufferSize] and will be initialized
     * with all binary zeros.  It should be populated with data to be written. Lambda
     * should return a count of actual number of bytes in the content to be written, between
     * zero and [bufferSize]. Return zero to indicate entry is complete and should be closed,
     * resulting in [addEntry] being complete.
     */
    @Suppress("BlockingMethodInNonBlockingContext")
    actual suspend fun addEntry(
        entry: ZipEntry,
        bufferSize: Int,
        block: suspend (content: ByteArray) -> Int
    ) {
        checkOpen(Mode.Write)
        val buf = ByteArray(bufferSize)
        zipOutputStream.putNextEntry(javaEntry(entry))
        while (true) {
            buf.forEachIndexed { i: Int, _: Byte -> buf[i] = 0 }
            val bytes = block(buf)
            if (bytes <= 0) break
            zipOutputStream.write(buf, 0, bytes)
        }
        zipOutputStream.closeEntry()
        entriesAdded.add(entry)
    }

    /**
     * Use this to add a new Entry, and use the lambda to provide content to the entry. Content
     * is treated as text. If it does not end with a line separator sequence, one is appended before
     * writing to the Entry.
     * @param entry describes the entry to be added
     * @param charset controls how String will be encoded when written to entry
     * @param block lambda will be invoked repeatedly until it returns empty String.  Each time,
     * String returned will be encoded with the specified Charset and written to entry. If
     * String returned is empty, write process stops, entry is closed and [addTextEntry] completes.
     */
    @Suppress("BlockingMethodInNonBlockingContext")
    actual suspend fun addTextEntry(
        entry: ZipEntry,
        charset: Charset,
        appendEol: Boolean,
        block: suspend () -> String
    ) {
        checkOpen(Mode.Write)
        zipOutputStream.putNextEntry(javaEntry(entry))
        while (true) {
            var s = block()
            if (s.isEmpty()) break
            s = if (appendEol)
                if (s.endsWith(lineSeparator)) s else "$s$lineSeparator"
            else
                s
            zipOutputStream.write(charset.encode(s))
        }
        zipOutputStream.closeEntry()
        entriesAdded.add(entry)
    }

    /**
     * For a mode read file, reads the specified entry if it exists, throws an exception if it does
     * not. Reads continue as long as lambda returns true, until all data is consumed.
     * @param entryName specifies the entry to be read, typically a name from the [entries] list
     * @param bufferSize controls the maximum number of bytes to be read for one lambda call.
     * @param block invoked once for each block read, until either the entire entry is read, or
     * until the lambda returns false, indicating complete entry read is not desired.  Arguments
     * to lambda contain the butes read, and a count of the number of bytes read.
     * @return the ZipEntry read, including its metadata
     */
    @Suppress("BlockingMethodInNonBlockingContext")
    actual suspend fun readEntry(
        entryName: String,
        bufferSize: Int,
        block: suspend (content: ByteArray, bytes: Int) -> Boolean
    ): ZipEntry {
        checkOpen(Mode.Read)
        val javaEntry = javaZipFile.getEntry(entryName)
        val buf = ByteArray(bufferSize)
        javaZipFile.getInputStream(javaEntry).use {
            var bytes = it.read(buf)
            while (bytes > 0) {
                if (!block(buf, bytes)) break
                bytes = it.read(buf)
            }
        }
        return ZipEntry(javaEntry.name, javaEntry.comment, javaEntry.extra)
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    actual suspend fun readTextEntry(
        entryName: String,
        bufferSize: Int,
        block: suspend (text: String) -> Boolean
    ): ZipEntry {
        checkOpen(Mode.Read)
        val javaEntry = javaZipFile.getEntry(entryName)
        BufferedReader(
            InputStreamReader(
                javaZipFile.getInputStream(javaEntry),
                charset.javaCharset
            )
        )
            .use { reader ->
                if (bufferSize <= 0) {
                    reader.useLines {
                        it.forEach {
                            if (!block(it))
                                return@useLines
                        }
                    }
                } else {
                    val buf = CharArray(bufferSize)
                    var count = reader.read(buf)
                    while (count > 0) {
                        if (block(String(buf, 0, count)))
                            count = reader.read(buf)
                        else
                            break
                    }
                }
            }
        return ZipEntry(javaEntry.name, javaEntry.comment, javaEntry.extra)
    }

    /**
     * merges a set of input zip files into this zip file.  If duplicate entries are detected,
     * if an input file is missing or is not a zip file, or if any other errors occur, an exception
     * is thrown. If this zipfile is not write mode, an exception is thrown.
     *
     * This ZipFile must already be open with Write mode.
     *
     * @param zipFiles one or more Zip files to be merged.
     */
    actual fun merge(vararg zipFiles: ZipFile) {
        checkOpen(Mode.Write)
        zipFiles.forEach { inputZip ->
            if (inputZip.file.exists) {
                inputZip.open()
                inputZip.entries.forEach { zipEntry ->
                    val javaEntry = javaEntry(zipEntry)
                    val inStream = inputZip.javaZipFile.getInputStream(javaEntry)
                    val buffer = ByteArray(4096)
                    zipOutputStream.putNextEntry(javaEntry)
                    var len: Int
                    while (inStream.read(buffer).also { len = it } > 0) {
                        zipOutputStream.write(buffer, 0, len)
                    }
                    zipOutputStream.closeEntry()
                    inStream.close()
                }
                inputZip.close()
            }
        }
    }
}