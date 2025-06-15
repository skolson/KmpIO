package com.oldguy.common.io

import com.oldguy.common.io.charsets.Charset
import com.oldguy.common.io.charsets.Charsets
import kotlinx.cinterop.*
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toLocalDateTime
import platform.Foundation.*
import platform.posix.memcpy
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.min

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
open class AppleCharset(val set: Charsets) {
    val nsEnc = when (set) {
        Charsets.Utf8 -> NSUTF8StringEncoding
        Charsets.Utf16LE -> NSUTF16LittleEndianStringEncoding
        Charsets.Utf16BE -> NSUTF16BigEndianStringEncoding
        Charsets.Utf32LE -> NSUTF32LittleEndianStringEncoding
        Charsets.Utf32BE -> NSUTF32BigEndianStringEncoding
        Charsets.Iso88591 -> NSISOLatin1StringEncoding
        Charsets.Window1252 -> NSWindowsCP1252StringEncoding
    }

    open fun decode(bytes: ByteArray): String {
        val nsData = bytes.usePinned {
            NSData.dataWithBytes(it.addressOf(0), it.get().size.toULong())
        }
        memScoped {
            return NSString.create(nsData, nsEnc).toString()
        }
    }

    open fun encode(inString: String): ByteArray {
        val err = IllegalArgumentException("String could not be encoded with $set")
        @Suppress("CAST_NEVER_SUCCEEDS")
        (inString as NSString).dataUsingEncoding(nsEnc)?.let {
            return it.bytes?.readBytes(it.length.toInt())
                ?: throw err
        }
        throw err
    }
}

open class AppleTimeZones {
    companion object {
        fun getDefaultId(): String {
            return NSTimeZone.defaultTimeZone.name
        }
    }
}

class NSErrorException(nsError: NSError): Exception(nsError.localizedDescription) {
    val code = nsError.code
    val domain = nsError.domain
    val description = nsError.description
    val reason = nsError.localizedFailureReason
    val suggestion = nsError.localizedRecoverySuggestion
    val causes = nsError.underlyingErrors.map { error ->
        val nsCause = error as NSError
        NSErrorException(nsCause)
    }

    override fun toString(): String {
        return buildString {
            append("Code: $code, domain: $domain, description: $description, reason: $reason\n")
            append("suggestion: $suggestion\n")
            causes.forEach {
                append("caused by\n")
                append(it.toString())
            }
        }
    }
}

/**
 * Apple-specific native file code that is usable on macOS, iOS, and ios simulator targets.
 * This class owns an Objective C FileManager instance for use by any of the subclasses.
 *
 * Note: many of the FileManager and FileHandle functions return errors as an NSError object. This class
 * also provides help for allocating and translating an NSError required. If an error occurs,
 * a Kotlin NSErrorException is thrown.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
open class AppleFile(pathArg: String, val fd: FileDescriptor?) {
    val fm = NSFileManager.defaultManager
    open val path = pathArg.trimEnd(pathSeparator[0])

    open val name: String
        get() {
            val index = path.lastIndexOf(pathSeparator)
            return if (index < 0 || index == path.length - 1)
                ""
            else
                path.substring(index + 1)
        }
    open val nameWithoutExtension: String
        get() {
            val index = name.lastIndexOf(".")
            return if (index <= 0)
                name
            else
                name.substring(0, index)
        }
    open val extension: String get() {
            val index = name.lastIndexOf(".")
            return if (index < 0)
                ""
            else
                name.substring(index)
        }
    open val fullPath: String = pathArg.trimEnd(pathSeparator[0])
    open val directoryPath: String get() = if (isDirectory) path else path.replace(name, "").trimEnd(pathSeparator[0])

    open val size: ULong get() {
        var result: ULong = 0u
        throwError {
            val map = fm.attributesOfItemAtPath(path, it) as NSDictionary
            result = map.fileSize()
        }
        return result
    }

    open val lastModifiedEpoch: Long get() {
        var epoch = 0L
        throwError { error ->
            (fm.attributesOfItemAtPath(path, error) as NSDictionary?)
                ?.let { dict ->
                    dict.fileModificationDate()?.let {
                        epoch = (it.timeIntervalSince1970 * 1000.0).toLong()
                    }
                }
        }
        return epoch
    }
    open val lastModified get() = Instant
        .fromEpochMilliseconds(lastModifiedEpoch)
        .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())

    open val createdTime: LocalDateTime get() {
        var epoch = 0L
        throwError { cPointer ->
            (fm.attributesOfItemAtPath(path, cPointer) as NSDictionary?)
                ?.let { dict ->
                    dict.fileCreationDate()?.let {
                        epoch = (it.timeIntervalSince1970 * 1000.0).toLong()
                    }
                }
        }
        return Instant
            .fromEpochMilliseconds(epoch)
            .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
    }

    open val lastAccessTime: LocalDateTime get() = lastModified

    open val isDirectory: Boolean get() =
        memScoped {
            val isDirPointer = alloc<BooleanVar>()
            fm.fileExistsAtPath(path, isDirPointer.ptr)
            return isDirPointer.value
        }

    open val listNames: List<String> get() {
        val list = mutableListOf<String>()
        throwError {
            fm.contentsOfDirectoryAtPath(path, it)?.let { names ->
                names.forEach { name ->
                    name?.let { ptr ->
                        val n = ptr as String
                        if (n.isNotEmpty())
                            list.add(n)
                    }
                }
            }
        }
        return list
    }

    open val listFiles: List<File> get() {
        return listNames.map { File(path, it) }
    }

    open val listFilesTree: List<File> get() {
        return mutableListOf<File>().apply {
            listFiles.forEach { directoryWalk(it, this) }
        }
    }

    open val exists: Boolean get() = pathExists(path)
    open val tempDirectory: String = NSTemporaryDirectory()

    open val isUri: Boolean
        get() = TODO("Not yet implemented")
    open val isUriString: Boolean
        get() = TODO("Not yet implemented")

    private fun directoryWalk(dir: File, list: MutableList<File>) {
        if (dir.isDirectory) {
            list.add(dir)
            dir.listFiles.forEach { directoryWalk(it, list) }
        } else
            list.add(dir)
    }

    private fun matches(path: String): List<String> {
        memScoped {
            val result = alloc<ObjCObjectVar<String?>>()
            val matches = alloc<ObjCObjectVar<List<*>?>>()
            @Suppress("CAST_NEVER_SUCCEEDS")
            val count = (path as NSString).completePathIntoString(
                result.ptr,
                true,
                matches.ptr,
                null
            )
            return when (count.toInt()) {
                0 -> emptyList()
                1 -> listOf(result.value ?: "")
                else -> matches.value?.let {
                    @Suppress("UNCHECKED_CAST")
                    val list = it as List<String>
                    if (list.size != count.toInt())
                        throw IllegalArgumentException("completePathIntoString count: ${count.toInt()}, matches size: ${list.size}")
                    list
                } ?: emptyList()
            }
        }
    }

    private fun pathExists(path: String): Boolean {
        if (path.isEmpty() || path.isBlank())
            return false
        memScoped {
            val isDirPointer = alloc<BooleanVar>()
            return fm.fileExistsAtPath(path, isDirPointer.ptr)
        }
    }

    open suspend fun copy(destinationPath: String): File {
        throwError {
            fm.copyItemAtPath(path, destinationPath, it)
        }
        return File(destinationPath, null)
    }

    @Throws(NSErrorException::class, CancellationException::class)
    open suspend fun delete(): Boolean {
        return if (exists) {
            throwError {
                val rc = fm.removeItemAtPath(path, it)
                println("delete NSError: ${it.pointed.value?.localizedDescription ?: "null"}")
                rc
            }
        } else
            false
    }

    /**
     * Determine if subdirectory exists. If not create it.
     * @param directoryName subdirectory of current filePath
     * @return File with path of new subdirectory
     */
    open suspend fun resolve(directoryName: String): File {
        if (!isDirectory)
            throw IllegalArgumentException("Only invoke resolve on a directory")
        if (directoryName.isBlank()) return File(path)
        if (directoryName.startsWith(pathSeparator))
            throw IllegalArgumentException("resolve requires $directoryName to be not empty and cannot start with $pathSeparator")
        return File("$path/$directoryName", null).apply {
            makeDirectory()
        }
    }

    open suspend fun makeDirectory(): Boolean {
        var rc = false
        throwError { errorPointer ->
            val withIntermediates = path.contains(pathSeparator)
            rc = fm.createDirectoryAtPath(
                path,
                withIntermediates,
                null,
                errorPointer
            )
        }
        return rc
    }

    companion object {
        const val pathSeparator = "/"

        fun makePath(base: String, vararg names: String): String {
            return buildString {
                append(base)
                names.filter {it.isNotEmpty()}.forEach {
                    append("$pathSeparator$it")
                }
            }
        }

        /**
         * Creates a pointer to an NSError pointer for use by File-based operations, and invokes [block] with it. If an error
         * is produced by the [block] invocation, it is converted to an NSErrorException and thrown.
         * @param block lambda that typically uses the errorPointer argument in an Apple API that requires an NSError**
         */
        fun <T> throwError(block: (errorPointer: CPointer<ObjCObjectVar<NSError?>>) -> T): T {
            val errorPointer: ObjCObjectVar<NSError?> = nativeHeap.alloc()

            println("throwError. raw: ${errorPointer.rawPtr}, ptr: ${errorPointer.ptr.rawValue}, objc: ${errorPointer.objcPtr()}")
                val result: T = block(errorPointer.ptr)
                errorPointer.value?.let {
                    val appleError = NSErrorException(it)
                    println("Attempting throw NSErrorException:")
                    println(appleError.toString())
                    throw appleError
                }
                return result
            }

    }
}

private class AppleFileHandle(val file: File, mode: FileMode)
{
    constructor(path: String, mode: FileMode): this(File(path), mode)

    var updating = false
        private set
    val path = file.path
    val handle = when (mode) {
            FileMode.Read ->
                NSFileHandle.fileHandleForReadingAtPath(path)
            FileMode.Write -> {
                updating = file.exists
                if (updating) {
                    NSFileHandle.fileHandleForUpdatingAtPath(path)
                } else {
                    val fm = NSFileManager.defaultManager
                    if (fm.createFileAtPath(path, null, null))
                        NSFileHandle.fileHandleForWritingAtPath(path)
                    else
                        throw IllegalArgumentException("Create file failed: $path ")
                }
            }
        } ?: throw IllegalArgumentException("Path $path mode $mode could not be opened")

    fun close() {
        handle.closeFile()
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
open class AppleRawFile(
    fileArg: File,
    val mode: FileMode
) : Closeable {
    private val apple = AppleFileHandle(fileArg, mode)
    private val path = apple.path
    open val file = fileArg

    override suspend fun close() {
        apple.close()
    }

    /**
     * Current position of the file, in bytes. Can be changed, if attempt to set outside the limits
     * of the current file, an exception is thrown.
     */
    open var position: ULong
        get() {
            return AppleFile.throwError {
                memScoped {
                    val result = alloc<ULongVar>()
                    apple.handle.getOffset(result.ptr, it)
                    result.value
                }
            }
        }
        set(value) {
            AppleFile.throwError { cPointer ->
                val result = apple.handle.seekToOffset(value, cPointer)
                if (!result) {
                    cPointer.pointed.value?.let {
                        println("NSerror content: ${it.localizedDescription}")
                    }
                    throw IllegalArgumentException("Could not position file to $value")
                }
            }
        }

    /**
     * Current size of the file in bytes
     */
    open val size: ULong get() = apple.file.size

    open var blockSize: UInt = 4096u

    /**
     * Read bytes from a file, from the current file position.
     * @param buf read buf.remaining bytes into byte buffer.
     * @param reuseBuffer if false (default), position is advanced by number of bytes read and function
     * returns. If true, buffer is cleared before read so capacity bytes can be read. Position
     * advances by number of bytes read, then buffer flip() is called so position is zero, limit and
     * remaining are both number of bytes read, and capacity remains unchanged.
     * @return number of bytes actually read
     */
    open suspend fun read(buf: ByteBuffer, reuseBuffer: Boolean): UInt {
        var len = 0u
        AppleFile.throwError { cPointer ->
            if (reuseBuffer) buf.clear()
            apple.handle.readDataUpToLength(buf.remaining.convert(), cPointer)?.let { bytes ->
                len = min(buf.limit.toUInt(), bytes.length.convert())
                buf.buf.usePinned {
                    memcpy(it.addressOf(buf.position), bytes.bytes, len.convert())
                }
                buf.position += len.toInt()
                if (reuseBuffer) buf.flip()
            }
        }
        return len
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
    open suspend fun read(buf: ByteBuffer, newPos: ULong, reuseBuffer: Boolean): UInt {
        var len = 0u
        AppleFile.throwError { cPointer ->
            if (reuseBuffer) buf.clear()
            position = newPos
            apple.handle.readDataUpToLength(buf.remaining.convert(), cPointer)?.let { bytes ->
                len = min(buf.limit.toUInt(), bytes.length.convert())
                buf.buf.usePinned {
                    memcpy(it.addressOf(buf.position), bytes.bytes, len.convert())
                }
                buf.position += len.toInt()
                if (reuseBuffer) buf.flip()
            }
        }
        return len
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
    open suspend fun read(buf: UByteBuffer, reuseBuffer: Boolean): UInt {
        var len = 0u
        AppleFile.throwError { cPointer ->
            if (reuseBuffer) buf.clear()
            apple.handle.readDataUpToLength(buf.remaining.convert(), cPointer)?.let { bytes ->
                len = min(buf.limit.toUInt(), bytes.length.convert())
                buf.buf.usePinned {
                    memcpy(it.addressOf(buf.position), bytes.bytes, len.convert())
                }
                buf.position += len.toInt()
                if (reuseBuffer) buf.flip()
            }
        }
        return len
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
    open suspend fun read(buf: UByteBuffer, newPos: ULong, reuseBuffer: Boolean): UInt {
        var len = 0u
        AppleFile.throwError { cPointer ->
            if (reuseBuffer) buf.clear()
            position = newPos
            apple.handle.readDataUpToLength(buf.remaining.convert(), cPointer)?.let { bytes ->
                len = min(buf.limit.toUInt(), bytes.length.convert())
                buf.buf.usePinned {
                    memcpy(it.addressOf(buf.position), bytes.bytes, len.convert())
                }
                buf.position += len.toInt()
                if (reuseBuffer) buf.flip()
            }
        }
        return len
    }

    /**
     * Allocates a new buffer of length specified. Reads bytes at current position.
     * @param length maximum number of bytes to read
     * @return buffer: capacity == length, position = 0, limit = number of bytes read, remaining = limit.
     */
    open suspend fun readBuffer(length: UInt): ByteBuffer {
        return ByteBuffer(length.toInt()).apply {
            read(this, true)
        }
    }

    /**
     * Allocates a new buffer of length specified. Reads bytes at specified position.
     * @param length maximum number of bytes to read
     * @return buffer: capacity == length, position = 0, limit = number of bytes read, remaining = limit.
     */
    open suspend fun readBuffer(
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
    open suspend fun readUBuffer(length: UInt): UByteBuffer {
        return UByteBuffer(length.toInt()).apply {
            read(this, true)
        }
    }

    /**
     * Allocates a new buffer of length specified. Reads bytes at specified position.
     * @param length maximum number of bytes to read
     * @return buffer: capacity == length, position = 0, limit = number of bytes read, remaining = limit.
     */
    open suspend fun readUBuffer(
        length: UInt,
        newPos: ULong
    ): UByteBuffer {
        return UByteBuffer(length.toInt()).apply {
            read(this, newPos,true)
        }
    }

    /**
     * Write bytes to a file, staring at the current file position.
     * @param buf write buf.remaining bytes into byte buffer starting at the buffer's current position.
     * @return number of bytes actually read
     */
    open suspend fun write(buf: ByteBuffer) {
        AppleFile.throwError { error ->
            memScoped {
                buf.buf.usePinned {
                    val nsData = NSData.create(bytesNoCopy = it.addressOf(buf.position), buf.remaining.convert())
                    apple.handle.writeData(nsData, error)
                }
            }
        }
        buf.position += buf.remaining
    }

    /**
     * Write bytes to a file, staring at the specified position.
     * @param buf write buf.remaining bytes into byte buffer starting at the buffer's current position.
     * @param newPos zero-relative position of file to start writing,
     * or if default of -1, the current file position
     * @return number of bytes actually read
     */
    open suspend fun write(buf: ByteBuffer, newPos: ULong) {
        AppleFile.throwError { error ->
            position = newPos
            memScoped {
                buf.buf.usePinned {
                    val nsData = NSData.create(bytesNoCopy = it.addressOf(buf.position), buf.remaining.convert())
                    apple.handle.writeData(nsData, error)
                }
            }
        }
        buf.position += buf.remaining
    }

    /**
     * Write bytes to a file, staring at the current file position.
     * @param buf write buf.remaining bytes into byte buffer starting at the buffer's current position.
     * @return number of bytes actually read
     */
    open suspend fun write(buf: UByteBuffer) {
        AppleFile.throwError { error ->
            memScoped {
                buf.buf.usePinned {
                    val nsData = NSData.create(bytesNoCopy = it.addressOf(buf.position), buf.remaining.convert())
                    apple.handle.writeData(nsData, error)
                }
            }
        }
        buf.position += buf.remaining
    }

    /**
     * Write bytes to a file, staring at the specified position.
     * @param buf write buf.remaining bytes into byte buffer starting at the buffer's current position.
     * @param newPos zero-relative position of file to start writing,
     * or if default of -1, the current file position
     * @return number of bytes actually read
     */
    open suspend fun write(buf: UByteBuffer, newPos: ULong) {
        AppleFile.throwError { error ->
            position = newPos
            memScoped {
                buf.buf.usePinned {
                    val nsData = NSData.create(bytesNoCopy = it.addressOf(buf.position), buf.remaining.convert())
                    apple.handle.writeData(nsData, error)
                }
            }
        }
        buf.position += buf.remaining
    }

    /**
     * Copy a file. the optional lambda supports altering the output data on a block-by-block basis.
     * Useful for various transforms; i.e. encryption/decryption
     * @param destination output file
     * @param blockSize if left default, straight file copy is performed with no opportunity for a
     * transform. If left default and the lambda is specified, a blockSize of 1024 is used.  blockSize
     * sets the initial capacity of both buffers used in the lambda. The source file will be read
     * [blockSize] bytes at a time and lamdba invoked.
     * @param transform is an optional lambda. If specified, will be invoked once for each read
     * operation until no bytes remain in the source. Argument buffer will have position 0, limit = number
     * of bytes read, and capacity set to [blockSize]. Argument outBuffer will have position 0,
     * capacity set to [blockSize].  State of outBuffer should be set by lambda. On return,
     * write will be invoked to [destination] starting at outBuffer.position for outBuffer.remaining
     * bytes.
     * @return number of bytes read from source (this).
     */
    open suspend fun copyTo(
        destination: RawFile,
        blockSize: Int,
        transform: ((buffer: ByteBuffer, lastBlock: Boolean) -> ByteBuffer)?
    ): ULong {
        var bytesRead:ULong = 0u
        if (transform == null) {
            AppleFile.throwError {
                val fm = NSFileManager.defaultManager
                fm.copyItemAtPath(path, destination.file.fullPath, it)
            }
        } else {
            val blkSize = if (blockSize <= 0) this.blockSize else blockSize.toUInt()
            val buffer = ByteBuffer(blkSize.toInt(), isReadOnly = true)
            var readCount = read(buffer, true)
            var bytesWritten = 0L
            val fileSize = size
            while (readCount > 0u) {
                bytesRead += readCount
                val lastBlock = position >= fileSize
                val outBuffer = transform(buffer, lastBlock)
                val count = outBuffer.remaining
                destination.write(outBuffer)
                bytesWritten += count
                readCount = if (lastBlock) 0u else read(buffer, true)
            }
        }
        close()
        destination.close()
        return bytesRead
    }

    /**
     * Copy a file. the optional lambda supports altering the output data on a block-by-block basis.
     * Useful for various transforms; i.e. encryption/decryption
     * NOTE: Both this and the destination RawFile objects will be closed at the end of this operation.
     * @param destination output file
     * @param blockSize if left default, straight file copy is performed with no opportunity for a
     * transform. If left default and the lambda is specified, a blockSize of 1024 is used.  blockSize
     * sets the initial capacity of both buffers used in the lambda. The source file will be read
     * [blockSize] bytes at a time and lamdba invoked.
     * @param transform is an optional lambda. If specified, will be invoked once for each read
     * operation until no bytes remain in the source. Argument buffer will have position 0, limit = number
     * of bytes read, and capacity set to [blockSize]. Argument outBuffer will have position 0,
     * capacity set to [blockSize].  State of outBuffer should be set by lambda. On return,
     * write will be invoked to [destination] starting at outBuffer.position for outBuffer.remaining
     * bytes.
     */
    open suspend fun copyToU(
        destination: RawFile,
        blockSize: Int,
        transform: ((buffer: UByteBuffer, lastBlock: Boolean) -> UByteBuffer)?
    ): ULong {
        var bytesRead: ULong = 0u
        if (transform == null) {
            AppleFile.throwError {
                val fm = NSFileManager.defaultManager
                fm.copyItemAtPath(path, destination.file.fullPath, it)
            }
        } else {
            val blkSize = if (blockSize <= 0) this.blockSize else blockSize.toUInt()
            val buffer = UByteBuffer(blkSize.toInt(), isReadOnly = true)
            var readCount = read(buffer, true)
            var bytesWritten = 0L
            val fileSize = size
            while (readCount > 0u) {
                bytesRead += readCount
                val lastBlock = position >= fileSize
                val outBuffer = transform(buffer, lastBlock)
                val count = outBuffer.remaining
                destination.write(outBuffer)
                bytesWritten += count
                readCount = if (lastBlock) 0u else read(buffer, true)
            }
        }
        close()
        destination.close()
        return bytesRead
    }

    /**
     * Copy a portion of the specified source file to the current file at the current position
     */
    open suspend fun transferFrom(
        source: RawFile,
        startIndex: ULong,
        length: ULong
    ): ULong {
        var srcBuf = ByteBuffer(min(blockSize.toULong(), length).toInt())
        var bytesCount: ULong = 0u
        var rd = source.read(srcBuf).toULong()
        while (rd > 0u) {
            srcBuf.rewind()
            bytesCount += rd
            write(srcBuf, startIndex)
            srcBuf.rewind()
            if (length - rd < blockSize)
                srcBuf = ByteBuffer((length - rd).toInt())
            rd = source.read(srcBuf).toULong()
        }
        source.close()
        close()
        return bytesCount
    }

    /**
     * Truncate the current file to the specified size.  Not usable on Mode.Read files.
     */
    open suspend fun truncate(size: ULong) {
        if (mode == FileMode.Read)
            throw IllegalStateException("No truncate on read-only RawFile")
        AppleFile.throwError {
            apple.handle.truncateAtOffset(size.convert(), it)
        }
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
open class AppleTextFile(
    file: File,
    charset: Charset,
    mode: FileMode
) : TextBuffer(
    charset
), Closeable {
    private val apple = AppleFileHandle(file, mode)

    override suspend fun close() {
        apple.close()
    }

    open suspend fun forEachLine(action: (count: Int, line: String) -> Boolean) {
        try {
            super.forEachLine( { buffer ->
                    var len = 0u
                    if (!isEndOfFile) {
                        AppleFile.throwError { cPointer ->
                            apple.handle.readDataUpToLength(blockSize.convert(), cPointer)
                                ?.let { bytes ->
                                    len = min(blockSize.toUInt(), bytes.length.convert())
                                    if (len > 0u) {
                                        buffer.usePinned {
                                            memcpy(it.addressOf(0), bytes.bytes, len.convert())
                                        }
                                    }
                                }
                        }
                    }
                    len
                },
                action)
        } finally {
            close()
        }
    }

    open suspend fun forEachBlock(action: (text: String) -> Boolean) {
        try {
            val str = nextBlock()
            while (str.isNotEmpty()) {
                if (action(str))
                    nextBlock()
                else
                    break
            }
        } finally {
            close()
        }
    }

    open suspend fun read(maxSizeBytes: Int): String {
        if (isReadLock)
            throw IllegalStateException("Invoking read during existing forEach operation is not allowed ")
        if (isEndOfFile) return ""
        return nextBlock()
    }

    open suspend fun write(text: String) {
        if (isReadLock)
            throw IllegalStateException("Invoking read during existing forEach operation is not allowed ")
        AppleFile.throwError { error ->
            memScoped {
                val buf = charset.encode(text)
                buf.usePinned {
                    val nsData = NSData.create(bytesNoCopy = it.addressOf(0), buf.size.convert())
                    apple.handle.writeData(nsData, error)
                }
            }
        }
    }

    open suspend fun writeLine(text: String) {
        if (isReadLock)
            throw IllegalStateException("Invoking read during existing forEach operation is not allowed ")
        write (text + EOL)
    }
}

fun appleTempDirectory(): String {
    return NSTemporaryDirectory()
}
