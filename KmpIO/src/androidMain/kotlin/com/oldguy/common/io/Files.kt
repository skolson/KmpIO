package com.oldguy.common.io

import android.net.Uri
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.util.*
import kotlin.io.use as kotlinIoUse

@Suppress("UNUSED_PARAMETER")
actual class Files actual constructor(charsetName: String) {
    val javaCharset: java.nio.charset.Charset = java.nio.charset.Charset.forName(charsetName)
    private val encoder = javaCharset.newEncoder()
    private val decoder = javaCharset.newDecoder()

    actual fun decode(bytes: ByteArray): String {
        return decoder.decode(ByteBuffer.wrap(bytes)).toString()
    }

    actual fun encode(inString: String): ByteArray {
        return if (inString.uppercase().equals(UTF_8))
            inString.encodeToByteArray()
        else {
            val buf = encoder.encode(CharBuffer.wrap(inString))
            val bytes = ByteArray(buf.limit())
            buf.get(bytes)
            bytes
        }
    }

    actual companion object {
        actual val UTF_8 = "UTF-8"
        actual val US_ASCII = "US-ASCII"
        actual val UTF_16LE = "UTF-16LE"
        actual val ISO8859_1 = "ISO8859-1"
        actual val UTF_16 = "UTF-16"
        private val singleByteList = listOf(UTF_8, US_ASCII, ISO8859_1)
    }
}

actual class TimeZones {
    actual companion object {
        actual fun getDefaultId(): String {
            return TimeZone.getDefault().id
        }
    }
}

actual class File actual constructor(val filePath: String, val platformFd: FileDescriptor?) {
    actual constructor(parentDirectory: String, name: String) :
            this(parentDirectory + name, null) {
        parentFile = File(parentDirectory, null)
    }

    actual constructor(parentDirectory: File, name: String) :
            this("${parentDirectory.fullPath}$pathSeparator$name", null) {
        parentFile = parentDirectory
    }

    actual constructor(fd: FileDescriptor) : this("", fd)

    private val javaFile: java.io.File = java.io.File(filePath)

    actual val name: String = javaFile.name
    actual val nameWithoutExtension: String = javaFile.nameWithoutExtension
    actual val extension: String = javaFile.extension
    actual val path: String = javaFile.path.trimEnd(pathSeparator[0])
    actual val fullPath: String = javaFile.absolutePath.trimEnd(pathSeparator[0])
    actual var parentFile: File? = null
    actual val isDirectory get() = javaFile.isDirectory
    actual val exists get() = javaFile.exists()
    actual val isUri = platformFd?.code == 1 && platformFd.descriptor is Uri
    actual val isUriString = platformFd?.code == 2 && platformFd.descriptor is String
    actual val listFiles: List<File>
        get() {
            return if (isDirectory)
                javaFile.listFiles()?.map { File(it.absolutePath) } ?: emptyList()
            else
                emptyList()
        }

    val fd: Uri? =
        if (platformFd != null && platformFd.code == 1)
            platformFd.descriptor as Uri
        else
            null

    actual fun delete(): Boolean {
        return javaFile.delete()
    }

    actual fun resolve(directoryName: String): File {
        val directory = File(this, directoryName)
        if (!directory.javaFile.exists())
            directory.javaFile.mkdir()
        return directory
    }

    actual fun copy(destinationPath: String): File {
        val dest = java.io.File(destinationPath)
        FileOutputStream(dest).use { outputStream ->
            FileInputStream(javaFile).use { inStream ->
                val buf = ByteArray(4096)
                var bytesRead: Int
                while (inStream.read(buf).also { bytesRead = it } > 0) {
                    outputStream.write(buf, 0, bytesRead)
                }
            }
        }
        return File(dest.absolutePath)
    }

    companion object {
        private const val pathSeparator = "/"
    }
}

actual typealias Closeable = java.io.Closeable

actual inline fun <T : Closeable?, R> T.use(body: (T) -> R): R =
    kotlinIoUse(body)

/**
 * Use this to access a file at the bytes level. Random access by byte position is required, with
 * first byte of file as position 0. No translation of data is performed
 */
actual class RawFile actual constructor(
    fileArg: File,
    mode: FileMode,
    source: FileSource
) : Closeable {
    actual val file = fileArg

    val javaMode = when (mode) {
        FileMode.Read -> "r"
        FileMode.Write -> "rw"
    }
    val javaFile = when (source) {
        FileSource.Asset -> TODO()
        FileSource.Classpath -> throw IllegalArgumentException("cannot access raw file on classpath")
        FileSource.File -> java.io.RandomAccessFile(file.fullPath, javaMode)
    }

    actual var position: Long
        get() = javaFile.channel.position()
        set(value) {
            javaFile.channel.position(value)
        }

    actual val size: Long get() = javaFile.length()

    actual var copyBlockSize = 4096

    actual override fun close() {
        javaFile.channel.close()
    }

    /**
     * Read bytes from a file, staring at the specified position.
     * @param buf read buf.remaining bytes into byte buffer.
     * @param position zero-relative position of file to start reading,
     * or if default of -1, the current file position
     * @return number of bytes actually read
     */
    actual fun read(buf: com.oldguy.common.io.ByteBuffer, position: Long): Int {
        val javaBuf = makeJavaBuffer(buf)
        val bytesRead = if (position < 0)
            javaFile.channel.read(javaBuf)
        else
            javaFile.channel.read(javaBuf, position)
        buf.put(javaBuf.array())
        return bytesRead
    }

    /**
     * Read bytes from a file, staring at the specified position.
     * @param buf read buf.remaining bytes into byte buffer.
     * @param position zero-relative position of file to start reading,
     * or if default of -1, the current file position
     * @return number of bytes actually read
     */
    actual fun read(buf: UByteBuffer, position: Long): Int {
        val javaBuf = makeJavaBuffer(buf)
        val bytesRead = if (position < 0)
            javaFile.channel.read(javaBuf)
        else
            javaFile.channel.read(javaBuf, position)
        buf.put(javaBuf.array().toUByteArray())
        return bytesRead
    }

    actual fun write(buf: com.oldguy.common.io.ByteBuffer, position: Long) {
        val javaBuf = makeJavaBuffer(buf)
        val bytesWritten = if (position < 0)
            javaFile.channel.write(javaBuf)
        else
            javaFile.channel.write(javaBuf, position)
        buf.position += bytesWritten
    }

    actual fun write(buf: UByteBuffer, position: Long) {
        val javaBuf = makeJavaBuffer(buf)
        val bytesWritten = if (position < 0)
            javaFile.channel.write(javaBuf)
        else
            javaFile.channel.write(javaBuf, position)
        buf.position += bytesWritten
    }

    actual fun copyTo(
        destination: RawFile, blockSize: Int,
        transform: ((buffer: com.oldguy.common.io.ByteBuffer, lastBlock: Boolean) -> com.oldguy.common.io.ByteBuffer)?
    ): Long {
        var bytesRead: Long = 0
        if (transform == null) {
            val channel = javaFile.channel
            val sourceSize = this.size
            while (bytesRead < sourceSize) {
                bytesRead += channel.transferTo(
                    bytesRead,
                    channel.size(),
                    destination.javaFile.channel
                )
            }
            destination.close()
        } else {
            val blkSize = if (blockSize <= 0) copyBlockSize else blockSize
            val buffer = ByteBuffer(blkSize, isReadOnly = true)
            var readCount = read(buffer)
            while (readCount > 0) {
                bytesRead += readCount.toLong()
                buffer.position = 0
                buffer.limit = readCount
                val outBuffer = transform(buffer, position >= size)
                write(outBuffer)
                readCount = read(buffer)
            }
        }
        close()
        return bytesRead
    }

    actual fun copyToU(
        destination: RawFile, blockSize: Int,
        transform: ((buffer: UByteBuffer, lastBlock: Boolean) -> UByteBuffer)?
    ): Long {
        var bytesRead: Long = 0
        if (transform == null) {
            val channel = javaFile.channel
            while (bytesRead < destination.size) {
                bytesRead += channel.transferTo(
                    bytesRead,
                    channel.size(),
                    destination.javaFile.channel
                )
            }
        } else {
            val blkSize = if (blockSize <= 0) copyBlockSize else blockSize
            val buffer = UByteBuffer(blkSize, isReadOnly = true)
            var readCount = read(buffer)
            while (readCount > 0) {
                bytesRead += readCount.toLong()
                buffer.position = 0
                buffer.limit = readCount
                val outBuffer = transform(buffer, position >= size)
                destination.write(outBuffer)
                readCount = read(buffer)
            }
        }
        close()
        destination.close()
        return bytesRead
    }

    actual fun transferFrom(
        source: RawFile,
        startIndex: Long,
        length: Long
    ): Long {
        return javaFile.channel.transferFrom(source.javaFile.channel, startIndex, length)
    }

    actual fun truncate(size: Long) {
        javaFile.channel.truncate(size)
    }

    private fun makeJavaBuffer(buf: com.oldguy.common.io.ByteBuffer): ByteBuffer {
        return ByteBuffer.wrap(buf.slice().contentBytes)
    }

    private fun makeJavaBuffer(buf: UByteBuffer): ByteBuffer {
        return ByteBuffer.wrap(buf.slice().toByteBuffer().contentBytes)
    }
}

/**
 * Read a text file, and provide both character set translation as well as line-based processing
 */
actual class TextFile actual constructor(
    val file: File,
    charset: Files,
    val mode: FileMode,
    source: FileSource
) : Closeable {

    var javaReader: java.io.BufferedReader? = null
    var javaWriter: java.io.BufferedWriter? = null

    constructor(
        file: File,
        charset: Files,
        mode: FileMode,
        stream: InputStream
    ) : this(file, charset, mode, FileSource.Asset) {
        javaReader = stream.bufferedReader(charset.javaCharset)
    }

    constructor(
        file: File,
        charset: Files,
        mode: FileMode,
        stream: OutputStream
    ) : this(file, charset, mode, FileSource.Asset) {
        javaWriter = stream.bufferedWriter(charset.javaCharset)
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
                    java.io.BufferedReader(java.io.InputStreamReader(stream, charset.javaCharset))
        }

        if (javaWriter == null && mode == FileMode.Write) {
            val stream = when (source) {
                FileSource.Asset -> null
                FileSource.Classpath -> throw IllegalArgumentException("cannot write to a file on classpath")
                FileSource.File -> FileOutputStream(file.fullPath)
            }
            if (stream != null)
                javaWriter = stream.bufferedWriter(charset.javaCharset)
        }
    }

    actual constructor(
        filePath: String,
        charset: Files,
        mode: FileMode,
        source: FileSource
    ) : this(File(filePath), charset, mode, source)

    actual override fun close() {
        javaReader?.close()
        javaWriter?.close()
    }

    actual fun readLine(): String {
        if (mode == FileMode.Write)
            throw IllegalStateException("Mode is write, cannot read")
        return javaReader?.readLine() ?: ""
    }

    actual fun write(text: String) {
        if (mode == FileMode.Read)
            throw IllegalStateException("Mode is read, cannot write")
        javaWriter!!.write(text)
    }

    actual fun writeLine(text: String) {
        write(text)
        javaWriter!!.newLine()
    }

    actual fun forEachLine(action: (line: String) -> Unit) {
        if (mode == FileMode.Write)
            throw IllegalStateException("Mode is write, cannot read")
        val rdr = javaReader ?: throw IllegalStateException("Reader is invalid")
        rdr.forEachLine { action(it) }
    }
}
