package com.oldguy.common.io

import com.oldguy.common.io.charsets.Charset
import kotlinx.cinterop.*
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import platform.posix.*
import kotlin.*

actual class TimeZones {
    actual companion object {
        actual fun getDefaultId(): String {
            return TimeZone.currentSystemDefault().id
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
actual fun tempDirectory(): String {
    return getenv("TMPDIR")?.toKString() ?: "/tmp"
}

@OptIn(ExperimentalForeignApi::class)
actual class File actual constructor(filePath: String, val platformFd: FileDescriptor?) {
    actual constructor(parentDirectory: String, name: String) :
            this(parentDirectory + name, null)

    actual constructor(parentDirectory: File, name: String) :
            this("${parentDirectory.fullPath}$pathSeparator$name", null)

    actual constructor(fd: FileDescriptor) : this("", fd)

    val isAbsolute = filePath.startsWith(pathSeparator)
    actual val name: String
    val isHidden: Boolean
    var mode = "777".toUInt(radix = 8)
    actual val nameWithoutExtension: String
    actual val extension: String
    actual val path: String
    actual val fullPath: String
    actual val directoryPath: String
    actual val isDirectory: Boolean
    actual val exists: Boolean
    actual val isUri = false
    actual val isUriString = false
    actual val size: ULong
    actual val lastModifiedEpoch: Long
    actual val lastModified: LocalDateTime
    actual val createdTime: LocalDateTime
    actual val lastAccessTime: LocalDateTime

    actual val listFiles: List<File>
    actual val listFilesTree: List<File>
    actual val listNames: List<String>
    actual val tempDirectory: String = tempDirectory()

    init {
        val index = filePath.indexOfLast { it == pathSeparator[0] }
        name = if (index < 0)
            filePath
        else
            filePath.substring(index + 1)
        isHidden = name.startsWith('.')
        val extIndex = name.indexOfLast { it == '.' }
        extension = if (index < 0) name else name.substring(0, extIndex)
        nameWithoutExtension = if (index < 0) name else name.substring(extIndex + 1)
        path = if (index < 0)
            ""
        else
            filePath.substring(0, index).trimEnd(pathSeparator[0])
        fullPath = path
        directoryPath = if (name.isNotEmpty())
            path.replace(name, "").trimEnd(pathSeparator[0])
        else
            fullPath
        memScoped {
            val statBuf = alloc<stat>()
            val result = stat(filePath, statBuf.ptr)
            exists = result == 0
            if (result != 0)
                throw IOException("stat function error: $errno on $filePath", null)
            size = statBuf.st_size.toULong()
            isDirectory = (statBuf.st_mode and 0x170000u) == 0x040000u  // stat.h source
            lastModifiedEpoch = statBuf.st_mtim.tv_sec
            val tz = TimeZone.currentSystemDefault()
            lastModified = Instant
                .fromEpochMilliseconds(lastModifiedEpoch)
                .toLocalDateTime(tz)
            createdTime = Instant
                .fromEpochMilliseconds(statBuf.st_ctim.tv_sec)
                .toLocalDateTime(tz)
            lastAccessTime = Instant
                .fromEpochMilliseconds(statBuf.st_atim.tv_sec)
                .toLocalDateTime(tz)
        }
        val list = mutableListOf<File>()
        listFiles = if (isDirectory) {
            val dir = opendir(filePath)
            if (dir != null) {
                try {
                    var ep = readdir(dir)
                    while (ep != null) {
                        list.add(File(ep.pointed.d_name.toKString(), null))
                        ep = readdir(dir)
                    }
                } finally {
                    closedir(dir)
                }
            }
            list
        } else
            list
        listFilesTree =
            mutableListOf<File>().apply {
                listFiles.forEach { directoryWalk(it, this) }
            }
        listNames = listFiles.map { it.name }
    }


    private fun directoryWalk(dir: File, list: MutableList<File>) {
        if (dir.isDirectory) {
            list.add(dir)
            dir.listFiles.forEach { directoryWalk(it, list) }
        } else
            list.add(dir)
    }

    actual suspend fun delete(): Boolean {
        return remove(fullPath) == 0
    }

    actual suspend fun makeDirectory(): Boolean {
        return if (isDirectory)
            mkdir(fullPath, mode) == 0
        else
            false
    }

    actual suspend fun resolve(directoryName: String): File {
        if (!this.isDirectory)
            throw IllegalArgumentException("Only invoke resolve on a directory")
        if (directoryName.isBlank()) return this
        val directory = File(this, directoryName)
        if (!directory.exists)
            directory.makeDirectory()
        return directory
    }

    /**
     * POSIX has no low-level file copy, so this does explicit copy
     */
    actual suspend fun copy(destinationPath: String): File {
        val dest = File(destinationPath)
        copyFile(this, dest)
        return dest
    }

    actual companion object {
        actual val pathSeparator = "/"


    }
}

internal val defaultBufferSize = 4096uL
internal val byteSz = 1.toULong()

/**
 * utility function for copying one file to another.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun copyFile(source: File,
                      dest: File,
                      transform: ((buffer: ByteBuffer, lastBlock: Boolean) -> ByteBuffer)? = null)
    :ULong
{
    var bytesRead = 0uL
    var destF: CPointer<_IO_FILE>? = null
    var sourceF: CPointer<_IO_FILE>? = null
    try {
        destF = fopen(dest.fullPath, "w")
        if (destF == null)
            throw IOException("destination file: ${dest.fullPath} could not be opened. errno: $errno")
        sourceF = fopen(source.fullPath, "r")
        if (sourceF == null)
            throw IOException("source file: ${source.fullPath} could not be opened. errno: $errno")
        val bufArray = ByteArray(defaultBufferSize.toInt())
        val buf = bufArray.refTo(0)
        var n = 0uL
        var outCount = 0uL
        do {
            n = fread(buf, byteSz, defaultBufferSize, sourceF)
            if (n > 0uL) {
                bytesRead += n
                transform?.let {
                    val inBuf = ByteBuffer(bufArray)
                    val newBuf = it(inBuf, n < defaultBufferSize)
                    n = newBuf.remaining.toULong()
                    outCount = fwrite(newBuf.getBytes().refTo(0), byteSz, n, destF)
                } ?: run {
                    outCount = fwrite(buf, byteSz, n, destF)
                }
                if (outCount != n)
                    throw IOException("Copy failed: tried to write $n bytes, wrote $outCount")
            }
        } while (n > 0uL)
    } finally {
        destF?.let { fclose(it) }
        sourceF?.let { fclose(it) }
    }
    return bytesRead
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

@OptIn(ExperimentalForeignApi::class)
abstract class LinuxFile(
    val file: File,
    val mode: FileMode,
    source: FileSource
) : Closeable {
    private val byteSz = 1.toULong()

    val linuxMode = when (mode) {
        FileMode.Read -> "r"
        FileMode.Write -> "rw"
    }

    val linuxFile = when (source) {
        FileSource.Asset -> TODO()
        FileSource.Classpath -> throw IllegalArgumentException("cannot access raw file on classpath")
        FileSource.File -> fopen(file.fullPath, linuxMode)
    }

    var raw: CPointer<_IO_FILE>? = null

    open var position: ULong = 0uL
        get() = raw?.let { ftell(it).toULong() } ?: 0uL
        set(value) {
            raw?.let {
                val result = fseek(it, value.toLong(), SEEK_SET)
                if (result != 0)
                    throw IOException("Seek to $value on file ${file.fullPath} failed. fseek result: $result")
                field = value
            } ?: 0uL
        }

    val size: ULong get() = file.size
    var blockSize = 4096u

    init {
        raw = fopen(file.fullPath, linuxMode)
        if (raw == null)
            throw IOException("File: ${file.fullPath} open failed with error $errno")
    }

    override suspend fun close() {
        raw?.let {
            fclose(raw)
            raw = null
        }
    }

    private fun acceptRead(buf: ByteBuffer,
                           bytesRead: Int,
                           readBuf: ByteArray,
                           reuseBuffer: Boolean
    ): UInt {
        return if (bytesRead <= 0) {
            buf.positionLimit(0, 0)
            0u
        } else {
            buf.putBytes(readBuf, 0, bytesRead)
            if (reuseBuffer) buf.flip()
            bytesRead.toUInt()
        }
    }

    private fun acceptRead(buf: UByteBuffer,
                           bytesRead: Int,
                           readBuf: ByteArray,
                           reuseBuffer: Boolean
    ): UInt {
        return if (bytesRead <= 0) {
            buf.positionLimit(0, 0)
            0u
        } else {
            buf.putBytes(readBuf.toUByteArray(), 0, bytesRead)
            if (reuseBuffer) buf.flip()
            bytesRead.toUInt()
        }
    }

    open suspend fun read(buf: ByteArray): UInt {
        val bytesRead = fread(
            buf.refTo(0),
            byteSz,
            buf.size.toULong(), raw
        )
        return bytesRead.toUInt()
    }

    open suspend fun read(buf: ByteBuffer, reuseBuffer: Boolean): UInt {
        if (reuseBuffer) buf.clear()
        val bytes = ByteArray(buf.remaining)
        val bytesRead = fread(bytes.refTo(0),
            com.oldguy.common.io.byteSz, buf.remaining.toULong(), raw)
        return acceptRead(buf, bytesRead.toInt(), bytes, reuseBuffer)
    }

    open suspend fun read(buf: UByteBuffer, reuseBuffer: Boolean): UInt {
        if (reuseBuffer) buf.clear()
        val bytes = ByteArray(buf.remaining)
        val bytesRead = fread(bytes.refTo(0),
            com.oldguy.common.io.byteSz, buf.remaining.toULong(), raw)
        return acceptRead(buf, bytesRead.toInt(), bytes, reuseBuffer)
    }

    open suspend fun setLength(length: ULong) {
        if (mode != FileMode.Write)
            throw IllegalStateException("setLength only usable with FileMode.Write")
        fflush(raw)
        ftruncate(fileno(raw), length.toLong())
    }

    open suspend fun write(buf: ByteBuffer) {
        val arr = buf.getBytes()
        val bytesWritten = fwrite(arr.refTo(0), com.oldguy.common.io.byteSz, arr.size.toULong(), raw)
        buf.position += bytesWritten.toInt()
    }

    open suspend fun write(buf: UByteBuffer) {
        val arr = buf.getBytes()
        val bytesWritten = fwrite(arr.refTo(0), com.oldguy.common.io.byteSz, arr.size.toULong(), raw)
        buf.position += bytesWritten.toInt()
    }

    open suspend fun copyTo(
        destination: LinuxFile,
        blockSize: Int,
        transform: ((buffer: ByteBuffer, lastBlock: Boolean) -> ByteBuffer)?
    ): ULong {
        var bytesRead: ULong = 0u
        if (transform == null) {
            close()
            bytesRead = copyFile(this.file, destination.file)
        } else {
            val blkSize = if (blockSize <= 0) this.blockSize else blockSize.toUInt()
            val buffer = ByteBuffer(blkSize.toInt(), isReadOnly = true)
            var readCount = read(buffer, true)
            val fileSize = file.size
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
                readCount = if (lastBlock) 0u else read(buffer, true)
            }
            destination.close()
            close()
        }
        return bytesRead
    }

    open suspend fun copyTo(
        destination: LinuxFile,
        blockSize: Int,
        transform: ((buffer: UByteBuffer, lastBlock: Boolean) -> UByteBuffer)?
    ): ULong {
        var bytesRead: ULong = 0u
        if (transform == null) {
            close()
            bytesRead = copyFile(this.file, destination.file)
        } else {
            val blkSize = if (blockSize <= 0) this.blockSize else blockSize.toUInt()
            val buffer = UByteBuffer(blkSize.toInt(), isReadOnly = true)
            var readCount = read(buffer, true)
            val fileSize = file.size
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
                readCount = if (lastBlock) 0u else read(buffer, true)
            }
            destination.close()
            close()
        }
        return bytesRead
    }
}

/**
 * Use this to access a file at the bytes level. Random access by byte position is required, with
 * first byte of file as position 0. No translation of data is performed
 */
@OptIn(ExperimentalForeignApi::class)
actual class RawFile actual constructor(
    fileArg: File,
    mode: FileMode,
    source: FileSource
) : LinuxFile(fileArg, mode, source) {
    actual override var position: ULong
        get() = super.position
        set(value) {
            super.position = value
        }

    actual override suspend fun close() {
        super.close()
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
    actual override suspend fun read(buf: ByteBuffer, reuseBuffer: Boolean): UInt {
        return super.read(buf, reuseBuffer)
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
    actual suspend fun read(buf: ByteBuffer, newPos: ULong, reuseBuffer: Boolean): UInt {
        position = newPos
        return super.read(buf, reuseBuffer)
    }

    /**
     * Read bytes from a file, staring at the current position.
     * @param buf read buf.remaining bytes into byte buffer.
     * @param reuseBuffer if false (default), position is advanced by number of bytes read and function
     * returns. If true, buffer is cleared before read so capacity bytes can be read. Position
     * advances by number of bytes read, then buffer flip() is called so position is zero, limit and
     * remaining are both number of bytes read, and capacity remains unchanged.
     * @return number of bytes actually read
     */
    actual override suspend fun read(buf: UByteBuffer, reuseBuffer: Boolean): UInt {
        return super.read(buf, reuseBuffer)
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
        position = newPos
        return super.read(buf, reuseBuffer)
    }

    /**
     * Allocates a new buffer of length specified. Reads bytes at current position.
     * @param length maximum number of bytes to read
     * @return buffer: capacity == length, position = 0, limit = number of bytes read, remaining = limit.
     */
    actual suspend fun readBuffer(length: UInt): ByteBuffer {
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
    ): ByteBuffer {
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

    actual override suspend fun setLength(length: ULong) {
        super.setLength(length)
    }

    actual override suspend fun write(buf: ByteBuffer) {
        super.write(buf)
    }

    actual suspend fun write(buf: ByteBuffer, newPos: ULong) {
        val arr = buf.getBytes()
        val bytesWritten = fwrite(arr.refTo(0), byteSz, arr.size.toULong(), raw)
        position = newPos + bytesWritten
        buf.position += bytesWritten.toInt()
    }

    actual override suspend fun write(buf: UByteBuffer) {
        super.write(buf)
    }

    actual suspend fun write(buf: UByteBuffer, newPos: ULong) {
        val arr = buf.getBytes()
        val bytesWritten = fwrite(arr.refTo(0), byteSz, arr.size.toULong(), raw)
        position = newPos + bytesWritten
        buf.position += bytesWritten.toInt()
    }

    actual suspend fun copyTo(
        destination: RawFile, blockSize: Int,
        transform: ((buffer: ByteBuffer, lastBlock: Boolean) -> ByteBuffer)?
    ): ULong {
        return super.copyTo(destination, blockSize, transform)
    }

    actual suspend fun copyToU(
        destination: RawFile,
        blockSize: Int,
        transform: ((buffer: UByteBuffer, lastBlock: Boolean) -> UByteBuffer)?
    ): ULong {
        return super.copyTo(destination, blockSize, transform)
    }

    actual suspend fun transferFrom(
        source: RawFile,
        startIndex: ULong,
        length: ULong
    ): ULong {
        position = startIndex
        val blkSize = 4096
        val buffer = ByteBuffer(blkSize.toInt(), isReadOnly = true)
        var readCount = source.read(buffer)
        val fileSize = size
        var bytesWritten = 0L
        var bytesRead = 0uL
        while (readCount > 0u) {
            bytesRead += readCount
            buffer.position = 0
            buffer.limit = readCount.toInt()
            val lastBlock = position >= fileSize
            val count = buffer.remaining
            write(buffer)
            bytesWritten += count
            readCount = if (lastBlock) 0u else source.read(buffer)
        }
        source.close()
        close()
        return bytesRead
    }

    actual suspend fun truncate(size: ULong) {
        fflush(raw)
        ftruncate(fileno(raw), size.toLong())
    }
}

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
    val textBuffer = TextBuffer(charset)

    actual suspend fun readLine(): String {
        return textBuffer.readLine()
    }

    actual suspend fun forEachLine(action: (count: Int, line: String) -> Boolean) {
        try {
            textBuffer.forEachLine(
                {buffer ->
                    if (textBuffer.isEndOfFile)
                        0u
                    else
                        read(buffer)
                },
                action)
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
}