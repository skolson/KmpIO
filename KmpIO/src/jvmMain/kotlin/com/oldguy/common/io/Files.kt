package com.oldguy.common.io

import com.oldguy.common.io.charsets.Charset
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toLocalDateTime
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.FileTime
import java.util.*

actual class TimeZones {
    actual companion object {
        actual fun getDefaultId(): String {
            return TimeZone.getDefault().id
        }
    }
}

actual fun tempDirectory(): String = System.getProperty("java.io.tmpdir")

actual class File actual constructor(filePath: String, val platformFd: FileDescriptor?) {
    actual constructor(parentDirectory: String, name: String) :
            this(parentDirectory + name, null)

    actual constructor(parentDirectory: File, name: String) :
            this("${parentDirectory.fullPath}$pathSeparator$name", null)

    actual constructor(fd: FileDescriptor) : this("", fd)

    private val javaFile: java.io.File = java.io.File(filePath)

    actual val name: String = javaFile.name
    actual val nameWithoutExtension: String = javaFile.nameWithoutExtension
    actual val extension: String = if (javaFile.extension.isNotEmpty()) ".${javaFile.extension}" else ""
    actual val path: String = javaFile.path.trimEnd(pathSeparator[0])
    actual val fullPath: String = javaFile.absolutePath.trimEnd(pathSeparator[0])
    actual val directoryPath: String = path.replace(name, "").trimEnd(pathSeparator[0])
    actual val isDirectory get() = javaFile.isDirectory
    actual val exists get() = javaFile.exists()
    actual val isUri = platformFd?.code == 1 && platformFd.descriptor is URI
    actual val isUriString = platformFd?.code == 2 && platformFd.descriptor is String
    actual val size: ULong get() = javaFile.length().toULong()
    actual val lastModifiedEpoch: Long = javaFile.lastModified()
    actual val lastModified = Instant
        .fromEpochMilliseconds(lastModifiedEpoch)
        .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
    actual val createdTime: LocalDateTime get() {
        if (!exists)
            throw IllegalStateException("File $path does not exist")
        val fileTime = Files.getAttribute(Paths.get(path), "creationTime") as FileTime
        return Instant
            .fromEpochMilliseconds(fileTime.toMillis())
            .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
    }
    actual val lastAccessTime: LocalDateTime get() {
        if (!exists)
            throw IllegalStateException("File $path does not exist")
        val fileTime = Files.getAttribute(Paths.get(path), "lastAccessTime") as FileTime
        return Instant
            .fromEpochMilliseconds(fileTime.toMillis())
            .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
    }

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

    actual val tempDirectory: String
        get() = System.getProperty("java.io.tmpdir")

    private fun directoryWalk(dir: File, list: MutableList<File>) {
        if (dir.isDirectory) {
            list.add(dir)
            dir.listFiles.forEach { directoryWalk(it, list) }
        } else
            list.add(dir)
    }

    val fd: URI? =
        if (platformFd != null && platformFd.code == 1)
            platformFd.descriptor as URI
        else
            null

    actual suspend fun delete(): Boolean {
        return javaFile.delete()
    }

    actual suspend fun makeDirectory(): Boolean {
        return javaFile.mkdir()
    }

    actual suspend fun resolve(directoryName: String): File {
        if (!this.isDirectory)
            throw IllegalArgumentException("Only invoke resolve on a directory")
        if (directoryName.isBlank()) return this
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

actual interface Closeable {
    actual suspend fun close()
}

actual suspend fun <T : Closeable?, R> T.use(body: suspend (T) -> R): R {
    return try {
        body(this)
    } finally {
        this?.close()
    }
}

/**
 * Use this to access a file at the bytes level. Random access by byte position is required, with
 * first byte of file as position 0. No translation of data is performed
 */
@Suppress("BlockingMethodInNonBlockingContext")
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

    actual override suspend fun close() {
        javaFile.channel.close()
    }

    private fun acceptRead(buf: com.oldguy.common.io.ByteBuffer,
                           bytesRead: Int,
                           javaBuf: ByteBuffer,
                           reuseBuffer: Boolean
    ): UInt {
        return if (bytesRead <= 0) {
            buf.positionLimit(0, 0)
            0u
        } else {
            buf.putBytes(javaBuf.array(), 0, bytesRead)
            if (reuseBuffer) buf.flip()
            bytesRead.toUInt()
        }
    }

    /**
     * Read bytes from a file, from the current file position.
     * @param buf read buf.remaining bytes into byte buffer.
     * @param reuseBuffer if false (default), position is advanced by number of bytes read and function
     * returns. If true, buffer is cleared before read so capacity bytes can be read. Position
     * advances by number of bytes read, then buffer flip() is called so position is zero, limit and
     * remaining are both number of bytes read, and capacity remains unchanged.
     * @return number of bytes actually read
     */
    actual suspend fun read(buf: com.oldguy.common.io.ByteBuffer, reuseBuffer: Boolean): UInt {
        if (reuseBuffer) buf.clear()
        val javaBuf = makeJavaBuffer(buf)
        val bytesRead = javaFile.channel.read(javaBuf)
        return acceptRead(buf, bytesRead, javaBuf, reuseBuffer)
    }

    /**
     * Read bytes from a file, staring at the specified position.
     * @param buf read buf.remaining bytes into byte buffer.
     * @param newPos zero-relative position of file to start reading,
     * or if default of -1, the current file position
     * @param reuseBuffer if false (default), position is advanced by number of bytes read and function
     * returns. If true, buffer is cleared before read so capacity bytes can be read. Position
     * advances by number of bytes read, then buffer flip() is called so position is zero, limit and
     * remaining are both number of bytes read, and capacity remains unchanged.
     * @return number of bytes actually read
     */
    actual suspend fun read(buf: com.oldguy.common.io.ByteBuffer, newPos: ULong, reuseBuffer: Boolean): UInt {
        if (reuseBuffer) buf.clear()
        val javaBuf = makeJavaBuffer(buf)
        val bytesRead = javaFile.channel.read(javaBuf, newPos.toLong())
        javaFile.channel.position((newPos + bytesRead.toULong()).toLong())
        return acceptRead(buf, bytesRead, javaBuf, reuseBuffer)
    }

    private fun acceptRead(buf: UByteBuffer,
                           bytesRead: Int,
                           javaBuf: ByteBuffer,
                           reuseBuffer: Boolean
    ): UInt {
        return if (bytesRead <= 0) {
            buf.positionLimit(0, 0)
            0u
        } else {
            buf.putBytes(javaBuf.array().toUByteArray(), 0, bytesRead)
            if (reuseBuffer) buf.flip()
            bytesRead.toUInt()
        }
    }

    /**
     * Read bytes from a file, staring at the specified position.
     * @param buf read buf.remaining bytes into byte buffer.
     * @param reuseBuffer if false (default), position is advanced by number of bytes read and function
     * returns. If true, buffer is cleared before read so capacity bytes can be read. Position
     * advances by number of bytes read, then buffer flip() is called so position is zero, limit and
     * remaining are both number of bytes read, and capacity remains unchanged.
     * @return number of bytes actually read
     */
    actual suspend fun read(buf: UByteBuffer, reuseBuffer: Boolean): UInt {
        if (reuseBuffer) buf.clear()
        val javaBuf = makeJavaBuffer(buf)
        val bytesRead = javaFile.channel.read(javaBuf)
        return acceptRead(buf, bytesRead, javaBuf, reuseBuffer)
    }

    /**
     * Read bytes from a file, starting at the specified position.
     * @param buf read buf.remaining bytes into byte buffer.
     * @param newPos zero-relative position of file to start reading,
     * or if default of -1, the current file position
     * @param reuseBuffer if false (default), position is advanced by number of bytes read and function
     * returns. If true, buffer is cleared before read so capacity bytes can be read. Position
     * advances by number of bytes read, then buffer flip() is called so position is zero, limit and
     * remaining are both number of bytes read, and capacity remains unchanged.
     * @return number of bytes actually read
     */
    actual suspend fun read(buf: UByteBuffer, newPos: ULong, reuseBuffer: Boolean): UInt {
        if (reuseBuffer) buf.clear()
        val javaBuf = makeJavaBuffer(buf)
        val bytesRead = javaFile.channel.read(javaBuf, newPos.toLong())
        javaFile.channel.position((newPos + bytesRead.toULong()).toLong())
        return acceptRead(buf, bytesRead, javaBuf, reuseBuffer)
    }

    /**
     * Allocates a new buffer of length specified. Reads bytes at current position.
     * @param length maximum number of bytes to read
     * @return buffer: capacity == length, position = 0, limit = number of bytes read, remaining = limit.
     */
    actual suspend fun readBuffer(length: UInt): com.oldguy.common.io.ByteBuffer {
        return ByteBuffer(length.toInt()).apply {
            read(this, true)
        }
    }

    /**
     * Allocates a new buffer of length specified. Reads bytes at specified position.
     * @param length maximum number of bytes to read
     * @return buffer: capacity == length, position = 0, limit = number of bytes read, remaining = limit.
     */
    actual suspend fun readBuffer(
        length: UInt,
        newPos: ULong
    ): com.oldguy.common.io.ByteBuffer {
        return ByteBuffer(length.toInt()).apply {
            read(this, newPos,true)
        }
    }

    /**
     * Allocates a new buffer of length specified. Reads bytes at current position.
     * @param length maximum number of bytes to read
     * @return buffer: capacity == length, position = 0, limit = number of bytes read, remaining = limit.
     */
    actual suspend fun readUBuffer(length: UInt): UByteBuffer {
        return UByteBuffer(length.toInt()).apply {
            read(this, true)
        }
    }

    /**
     * Allocates a new buffer of length specified. Reads bytes at specified position.
     * @param length maximum number of bytes to read
     * @return buffer: capacity == length, position = 0, limit = number of bytes read, remaining = limit.
     */
    actual suspend fun readUBuffer(
        length: UInt,
        newPos: ULong
    ): UByteBuffer {
        return UByteBuffer(length.toInt()).apply {
            read(this, newPos,true)
        }
    }

    actual suspend fun setLength(length: ULong) {
        if (mode != FileMode.Write)
            throw IllegalStateException("setLength only usable with FileMode.Write")
        javaFile.setLength(length.toLong())
    }

    actual suspend fun write(buf: com.oldguy.common.io.ByteBuffer) {
        val javaBuf = makeJavaBuffer(buf)
        val bytesWritten = javaFile.channel.write(javaBuf)
        buf.position += bytesWritten
    }

    actual suspend fun write(buf: com.oldguy.common.io.ByteBuffer, newPos: ULong) {
        val javaBuf = makeJavaBuffer(buf)
        val bytesWritten = javaFile.channel.write(javaBuf, newPos.toLong())
        javaFile.channel.position((newPos + bytesWritten.toULong()).toLong())
        buf.position += bytesWritten
    }

    actual suspend fun write(buf: UByteBuffer) {
        val javaBuf = makeJavaBuffer(buf)
        val bytesWritten = javaFile.channel.write(javaBuf)
        buf.position += bytesWritten
    }

    actual suspend fun write(buf: UByteBuffer, newPos: ULong) {
        val javaBuf = makeJavaBuffer(buf)
        val bytesWritten = javaFile.channel.write(javaBuf, newPos.toLong())
        javaFile.channel.position((newPos + bytesWritten.toULong()).toLong())
        buf.position += bytesWritten
    }

    actual suspend fun copyTo(
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

    actual suspend fun copyToU(
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

    actual suspend fun transferFrom(
        source: RawFile,
        startIndex: ULong,
        length: ULong
    ): ULong {
        return javaFile.channel.transferFrom(
                source.javaFile.channel,
                startIndex.toLong(),
                length.toLong()
            ).toULong()
    }

    actual suspend fun truncate(size: ULong) {
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
@Suppress("BlockingMethodInNonBlockingContext")
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
        javaReader = stream.bufferedReader(java.nio.charset.Charset.forName(charset.name))
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
        if (javaReader == null && mode == FileMode.Read) {
            val stream = when (source) {
                FileSource.Asset -> null
                FileSource.Classpath -> TextFile::class.java.getResourceAsStream(file.fullPath)
                FileSource.File -> FileInputStream(file.fullPath)
            }
            if (stream != null)
                javaReader =
                    java.io.BufferedReader(java.io.InputStreamReader(stream, java.nio.charset.Charset.forName(charset.name)))
        }

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
}
