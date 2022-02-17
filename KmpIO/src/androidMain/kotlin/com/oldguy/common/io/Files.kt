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
actual class Charset actual constructor(val set: Charsets) {
    actual val charset:Charsets = set
    val javaCharset: java.nio.charset.Charset = java.nio.charset.Charset.forName(set.charsetName)

    private val encoder = javaCharset.newEncoder()
    private val decoder = javaCharset.newDecoder()

    actual fun decode(bytes: ByteArray): String {
        return decoder.decode(ByteBuffer.wrap(bytes)).toString()
    }

    actual fun encode(inString: String): ByteArray {
        return if (set == Charsets.Utf8)
            inString.encodeToByteArray()
        else {
            val buf = encoder.encode(CharBuffer.wrap(inString))
            val bytes = ByteArray(buf.limit())
            buf.get(bytes)
            bytes
        }
    }
}

actual class TimeZones {
    actual companion object {
        actual fun getDefaultId(): String {
            return TimeZone.getDefault().id
        }
    }
}

actual class File actual constructor(filePath: String, platformFd: FileDescriptor?) {
    actual constructor(parentDirectory: String, name: String) :
            this(parentDirectory + name, null)

    actual constructor(parentDirectory: File, name: String) :
            this("${parentDirectory.fullPath}$pathSeparator$name", null)

    actual constructor(fd: FileDescriptor) : this("", fd)

    private val javaFile: java.io.File = java.io.File(filePath)

    actual val name: String = javaFile.name
    actual val nameWithoutExtension: String = javaFile.nameWithoutExtension
    actual val extension: String = javaFile.extension
    actual val path: String = javaFile.path.trimEnd(pathSeparator[0])
    actual val fullPath: String = javaFile.absolutePath.trimEnd(pathSeparator[0])
    actual val directoryPath: String = path.replace(name, "").trimEnd(pathSeparator[0])
    actual val isDirectory get() = javaFile.isDirectory
    actual val exists get() = javaFile.exists()
    actual val isUri = platformFd?.code == 1 && platformFd.descriptor is Uri
    actual val isUriString = platformFd?.code == 2 && platformFd.descriptor is String
    actual val size: ULong get() = javaFile.length().toULong()
    actual val listFiles: List<File>
        get() {
            val list = mutableListOf<File>()
            return if (isDirectory)
                javaFile.listFiles()?.map { File(it.absolutePath, null) } ?: emptyList()
            else
                list
        }
    actual val listFilesTree: List<File> get() {
            return mutableListOf<File>().apply {
                listFiles.forEach { directoryWalk(it, this) }
            }
        }
    actual val listNames: List<String> get() = listFiles.map { it.name }

    private fun directoryWalk(dir: File, list: MutableList<File>) {
        if (dir.isDirectory) {
            list.add(dir)
            dir.listFiles.forEach { directoryWalk(it, list) }
        } else
            list.add(dir)
    }

    val fd: Uri? =
        if (platformFd != null && platformFd.code == 1)
            platformFd.descriptor as Uri
        else
            null

    actual fun delete(): Boolean {
        return javaFile.delete()
    }

    actual fun makeDirectory(): Boolean {
        return javaFile.mkdir()
    }

    actual fun resolve(directoryName: String): File {
        val directory = File(this, directoryName)
        if (!directory.javaFile.exists())
            directory.makeDirectory()
        return directory
    }

    actual suspend fun copy(destinationPath: String): File {
        val dest = java.io.File(destinationPath)
        kotlin.runCatching {
            FileOutputStream(dest).use { outputStream ->
                FileInputStream(javaFile).use { inStream ->
                    val buf = ByteArray(4096)
                    var bytesRead: Int
                    while (inStream.read(buf).also { bytesRead = it } > 0) {
                        outputStream.write(buf, 0, bytesRead)
                    }
                }
            }
        }.onFailure {
            throw IOException("Copy failed: ${it.message}", it)
        }
        return File(dest.absolutePath, null)
    }

    actual companion object {
        actual val pathSeparator = "/"
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
    val mode: FileMode,
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

    actual var position: ULong
        get() = javaFile.channel.position().toULong()
        set(value) {
            javaFile.channel.position(value.toLong())
        }

    actual val size: ULong get() = file.size

    actual var blockSize = 4096u

    actual override fun close() {
        javaFile.channel.close()
    }

    actual fun read(buf: com.oldguy.common.io.ByteBuffer): UInt {
        val javaBuf = makeJavaBuffer(buf)
        val bytesRead = javaFile.channel.read(javaBuf)
        buf.put(javaBuf.array())
        return bytesRead.toUInt()
    }

    actual fun read(buf: com.oldguy.common.io.ByteBuffer, newPos: ULong): UInt {
        val javaBuf = makeJavaBuffer(buf)
        val bytesRead = javaFile.channel.read(javaBuf, newPos.toLong())
        javaFile.channel.position((newPos + bytesRead.toULong()).toLong())
        buf.put(javaBuf.array())
        return bytesRead.toUInt()
    }

    actual fun read(buf: UByteBuffer): UInt {
        val javaBuf = makeJavaBuffer(buf)
        val bytesRead = javaFile.channel.read(javaBuf)
        buf.put(javaBuf.array().toUByteArray())
        return bytesRead.toUInt()
    }

    actual fun read(buf: UByteBuffer, newPos: ULong): UInt {
        val javaBuf = makeJavaBuffer(buf)
        val bytesRead = javaFile.channel.read(javaBuf, newPos.toLong())
        javaFile.channel.position((newPos + bytesRead.toULong()).toLong())
        buf.put(javaBuf.array().toUByteArray())
        return bytesRead.toUInt()
    }

    actual fun setLength(length: ULong) {
        if (mode != FileMode.Write)
            throw IllegalStateException("setLength only usable with FileMode.Write")
        javaFile.setLength(length.toLong())
    }

    actual fun write(buf: com.oldguy.common.io.ByteBuffer) {
        val javaBuf = makeJavaBuffer(buf)
        val bytesWritten = javaFile.channel.write(javaBuf)
        buf.position += bytesWritten
    }

    actual fun write(buf: com.oldguy.common.io.ByteBuffer, newPos: ULong) {
        val javaBuf = makeJavaBuffer(buf)
        val bytesWritten = javaFile.channel.write(javaBuf, newPos.toLong())
        javaFile.channel.position((newPos + bytesWritten.toULong()).toLong())
        buf.position += bytesWritten
    }

    actual fun write(buf: UByteBuffer) {
        val javaBuf = makeJavaBuffer(buf)
        val bytesWritten = javaFile.channel.write(javaBuf)
        buf.position += bytesWritten
    }

    actual fun write(buf: UByteBuffer, newPos: ULong) {
        val javaBuf = makeJavaBuffer(buf)
        val bytesWritten = javaFile.channel.write(javaBuf, newPos.toLong())
        javaFile.channel.position((newPos + bytesWritten.toULong()).toLong())
        buf.position += bytesWritten
    }

    actual fun copyTo(
        destination: RawFile, blockSize: Int,
        transform: ((buffer: com.oldguy.common.io.ByteBuffer, lastBlock: Boolean) -> com.oldguy.common.io.ByteBuffer)?
    ): ULong {
        var bytesRead: ULong = 0u
        if (transform == null) {
            val channel = javaFile.channel
            val sourceSize = this.size
            while (bytesRead < sourceSize) {
                bytesRead += channel.transferTo(
                    bytesRead.toLong(),
                    channel.size(),
                    destination.javaFile.channel
                ).toULong()
            }
            destination.close()
        } else {
            val blkSize = if (blockSize <= 0) this.blockSize else blockSize.toUInt()
            val buffer = ByteBuffer(blkSize.toInt(), isReadOnly = true)
            var readCount = read(buffer)
            val fileSize = size
            var bytesWritten = 0L
            while (readCount > 0u) {
                bytesRead += readCount
                buffer.position = 0
                buffer.limit = readCount.toInt()
                val lastBlock = position >= fileSize
                val outBuffer = transform(buffer, lastBlock)
                val count = outBuffer.remaining
                destination.write(outBuffer)
                bytesWritten += count
                readCount = if (lastBlock) 0u else read(buffer)
            }
        }
        close()
        destination.close()
        return bytesRead
    }

    actual fun copyToU(
        destination: RawFile,
        blockSize: Int,
        transform: ((buffer: UByteBuffer, lastBlock: Boolean) -> UByteBuffer)?
    ): ULong {
        var bytesRead: ULong = 0u
        if (transform == null) {
            val channel = javaFile.channel
            val sourceSize = this.size
            while (bytesRead < sourceSize) {
                bytesRead += channel.transferTo(
                    bytesRead.toLong(),
                    channel.size(),
                    destination.javaFile.channel
                ).toULong()
            }
            destination.close()
        } else {
            val blkSize = if (blockSize <= 0) this.blockSize else blockSize.toUInt()
            val buffer = UByteBuffer(blkSize.toInt())
            var readCount = read(buffer)
            val fileSize = size
            var bytesWritten = 0L
            while (readCount > 0u) {
                bytesRead += readCount
                buffer.position = 0
                buffer.limit = readCount.toInt()
                val lastBlock = position >= fileSize
                val outBuffer = transform(buffer, lastBlock)
                val count = outBuffer.remaining
                destination.write(outBuffer)
                bytesWritten += count
                readCount = if (lastBlock) 0u else read(buffer)
            }
        }
        close()
        destination.close()
        return bytesRead
    }

    actual fun transferFrom(
        source: RawFile,
        startIndex: ULong,
        length: ULong
    ): ULong {
        return javaFile.channel.transferFrom(
            source.javaFile.channel,
            startIndex.toLong(),
            length.toLong()).toULong()
    }

    actual fun truncate(size: ULong) {
        javaFile.channel.truncate(size.toLong())
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
    actual val file: File,
    actual val charset: Charset,
    val mode: FileMode,
    source: FileSource
) : Closeable {

    var javaReader: java.io.BufferedReader? = null
    var javaWriter: java.io.BufferedWriter? = null

    constructor(
        file: File,
        charset: Charset,
        mode: FileMode,
        stream: InputStream
    ) : this(file, charset, mode, FileSource.Asset) {
        javaReader = stream.bufferedReader(charset.javaCharset)
    }

    constructor(
        file: File,
        charset: Charset,
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
        charset: Charset,
        mode: FileMode,
        source: FileSource
    ) : this(File(filePath, null), charset, mode, source)

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

    actual fun forEachLine(action: (count: Int, line: String) -> Boolean) {
        if (mode == FileMode.Write)
            throw IllegalStateException("Mode is write, cannot read")
        val rdr = javaReader ?: throw IllegalStateException("Reader is invalid")
        var count = 1
        try {
            rdr.forEachLine { if (!action(count++, it)) throw IllegalStateException("ignore") }
        } catch (_: IllegalStateException) {
        }
    }

    actual fun forEachBlock(maxSizeBytes: Int, action: (text: String) -> Boolean) {
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

    actual fun read(maxSizeBytes: Int): String {
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
}
