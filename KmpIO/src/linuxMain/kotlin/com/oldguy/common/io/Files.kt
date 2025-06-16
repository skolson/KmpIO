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

/**
 * Represents a file or directory on the filesystem, providing various utility methods and properties
 * to interact with file paths, directories, and file metadata.
 *
 * This class is platform-specific and contains functionality for file manipulation,
 * directory creation, file copying, and retrieving file attributes such as permissions and timestamps.
 * It is a combination of platform-independent methods and Linux-specific features such
 * as POSIX file permissions.
 *
 * Note: Instances are immutable, with one exception - the newPermissions property,  So properties are
 * all set at constructor time and do not change as a result of using methods. See the newFile() function
 * as a convenience for getting a new current File instance using the same full path to get new state when desired.
 *
 * @constructor Creates a `File` instance using the given file path or `FileDescriptor`.
 * Platform-specific implementation details may vary for certain methods.
 *
 * @property name Name of the file or directory.
 * @property nameWithoutExtension Name of the file without its extension.
 * @property extension The file's extension (if any).
 * @property path The normalized file path.
 * @property fullPath The full absolute path of the file or directory.
 * @property directoryPath Path to the parent directory of the file or the path itself if it's a directory.
 * @property isUri Indicates whether the file is represented by a URI.
 * @property isUriString Checks if the file path is formatted as a URI string.
 * @property isDirectory Whether the path points to a directory.
 * @property exists Whether the file or directory exists on the filesystem.
 * @property size Size of the file in bytes.
 * @property lastModifiedEpoch The last modification timestamp of the file in seconds since the Unix epoch.
 * @property lastModified The last modification time as a `LocalDateTime` object.
 * @property createdTime The creation time of the file as a `LocalDateTime` object (if available).
 * @property lastAccessTime The last access time of the file as a `LocalDateTime` object (if available).
 * @property ownerPermissions File permission bits for the owner of the file (Linux-specific).
 * @property groupPermissions File permission bits for the group associated with the file (Linux-specific).
 * @property otherPermissions File permission bits for others (Linux-specific).
 * @property newPermissions Default permissions to use for newly created files and directories, represented as an octal string.
 */
@OptIn(ExperimentalForeignApi::class)
actual class File actual constructor(filePath: String, val platformFd: FileDescriptor?)
{
    actual constructor(parentDirectory: String, name: String) :
            this(parentDirectory + name, null)

    actual constructor(parentDirectory: File, name: String) :
            this("${parentDirectory.fullPath}$pathSeparator$name", null)

    actual constructor(fd: FileDescriptor) : this("", fd)

    private val p = Path(filePath, pathSeparator[0])
    actual val name = p.name
    actual val nameWithoutExtension = p.nameWithoutExtension
    actual val extension = p.extension
    actual val path = p.path
    actual val fullPath = p.fullPath
    actual val directoryPath = p.directoryPath
    actual val isUri = p.isUri
    actual val isUriString = p.isUriString
    actual val isDirectory: Boolean
    actual val exists: Boolean
    actual val size: ULong
    actual val lastModifiedEpoch: Long
    actual val lastModified: LocalDateTime?
    actual val createdTime: LocalDateTime?
    actual val lastAccessTime: LocalDateTime?

    // Linux-specific properties
    val ownerPermissions: UInt
    val groupPermissions: UInt
    val otherPermissions: UInt
    /**
     * Set this to an octal three digit string specifying the permissions to be used for new files and directories
     */
    var newPermissions: String = "666"
        set(value) {
            val mode = value.toUInt(8)
            if (mode in 0u.."777".toUInt(8)) {
                field = value
                newMode = newPermissions.toUInt(8)
            } else
                throw IllegalArgumentException("File permission must be between 000 and 777 octal")
        }
    private var newMode = newPermissions.toUInt(8)

    init {
        memScoped {
            val statBuf = alloc<stat>()
            val result = stat(filePath, statBuf.ptr)
            exists = if (result == 0)
                true
            else if (errno == 2) false
            else
                throw IOException("stat function result: $result, errorno: $errno on $filePath", null)

            val tz = TimeZone.currentSystemDefault()
            if (exists) {
                size = statBuf.st_size.toULong()
                statBuf.st_mode.also {
                    isDirectory = (it and isDirectoryMask) == isDirectoryValue
                    otherPermissions = (it and permissionMask)
                    groupPermissions = (it and permissionMask.shl(3)).shr(3)
                    ownerPermissions = (it and permissionMask.shl(6)).shr(6)
                }
                lastModifiedEpoch = statBuf.st_mtim.tv_sec
                lastModified = Instant
                    .fromEpochMilliseconds(lastModifiedEpoch * 1000)
                    .toLocalDateTime(tz)
                createdTime = Instant
                    .fromEpochMilliseconds(statBuf.st_ctim.tv_sec * 1000)
                    .toLocalDateTime(tz)
                lastAccessTime = Instant
                    .fromEpochMilliseconds(statBuf.st_atim.tv_sec * 1000)
                    .toLocalDateTime(tz)
            } else {
                size = 0u
                isDirectory = false
                lastModifiedEpoch = 0
                lastModified = null
                createdTime = null
                lastAccessTime = null
                ownerPermissions = 0u
                groupPermissions = 0u
                otherPermissions = 0u
            }
        }
    }

    actual fun newFile() = File(fullPath)

    actual suspend fun directoryList(): List<String> {
        val list = mutableListOf<String>()
        if (isDirectory && exists) {
            val dir = opendir(fullPath)
            if (dir != null) {
                try {
                    var ep = readdir(dir)
                    while (ep != null) {
                        val path = ep.pointed.d_name.toKString()
                        if (path != "." && path != "..")
                            list.add(ep.pointed.d_name.toKString())
                        ep = readdir(dir)
                    }
                } finally {
                    closedir(dir)
                }
            }
        }
        return list
    }

    actual suspend fun delete(): Boolean {
        return remove(fullPath) == 0
    }

    actual suspend fun makeDirectory(): File {
        if (isDirectory) {
            if (exists) return this
        } else if (exists)
            throw IllegalArgumentException("Path exists and is not a directory")
        val rc = mkdir(fullPath, newMode)
        if (rc == 0)
            return File(fullPath)
        throw IllegalStateException("Failed to create directory: $fullPath, errno = $errno")
    }

    actual suspend fun resolve(directoryName: String, make: Boolean): File {
        if (!this.isDirectory)
            throw IllegalArgumentException("Only invoke resolve on a directory")
        if (directoryName.isBlank()) return this
        val d = File(this, directoryName)
        return if (make) d.makeDirectory() else d
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

        actual fun tempDirectoryPath(): String {
            return getenv("TMPDIR")?.toKString() ?: "/tmp"
        }
        actual fun tempDirectoryFile(): File = File(tempDirectoryPath())

        actual fun workingDirectory(): File {
            memScoped {
                val bufferLength = 4096uL
                val buffer = allocArray<ByteVar>(bufferLength.toInt())

                if (getcwd(buffer, bufferLength) == null)
                    throw IllegalStateException("getcwd() returned error: $errno")
                return File(buffer.toKString())
            }
        }

        private val noTime = Instant.DISTANT_PAST
        // these values used for decoding the st_mode value from stat()
        private val isDirectoryMask = "170000".toUInt(8)
        private val isDirectoryValue = "40000".toUInt(8)
        private val permissionMask = "7".toUInt(8)
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
        FileMode.Write -> "w+"
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

    open suspend fun read(buf: ByteArray, count: Int = buf.size): UInt {
        val bytesRead = fread(
            buf.refTo(0),
            byteSz,
            count.toULong(), raw
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
        if (bytesWritten != arr.size.toULong()) {
            throw IllegalStateException("Write error, bytes written: $bytesWritten, bytes to write: ${arr.size}")
        }
    }

    open suspend fun write(buf: UByteBuffer) {
        val arr = buf.getBytes()
        val bytesWritten = fwrite(arr.refTo(0), com.oldguy.common.io.byteSz, arr.size.toULong(), raw)
        if (bytesWritten != arr.size.toULong()) {
            throw IllegalStateException("Write error, bytes written: $bytesWritten, bytes to write: ${arr.size}")
        }
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
}