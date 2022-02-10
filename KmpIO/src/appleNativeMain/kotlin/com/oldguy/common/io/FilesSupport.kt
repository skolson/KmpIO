package com.oldguy.common.io

import kotlinx.cinterop.*
import platform.Foundation.*
import platform.darwin.StringPtrVar
import platform.posix.memcpy
import kotlin.math.min

open class AppleCharset(val set: Charsets) {
    val nsEnc = when (set) {
        Charsets.Utf8 -> NSUTF8StringEncoding
        Charsets.Utf16le -> NSUTF16LittleEndianStringEncoding
        Charsets.Utf16be -> NSUTF16BigEndianStringEncoding
        Charsets.Iso8859_1 -> NSISOLatin1StringEncoding
        Charsets.UsAscii -> NSASCIIStringEncoding
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
        @Suppress("UNCHECKED_CAST")
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

class NSErrorException(val nsError: NSError): Exception(nsError.toString())

/**
 * Apple-specific native file code that is usable on macOS, iOS, and ios simulator targets.
 * This class owns an Objective C FileManager instance for use by any of the subclasses.
 *
 * Note: many of the FileManager and FileHandle functions return errors as an NSError object. This class
 * also provides help for allocating and translating an NSError required. If an error occurs,
 * a Kotlin NSErrorException is thrown.
 */
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
    open val fullPath: String = path

    open val size: ULong get() {
        var result: ULong = 0u
        throwError {
            val fm = NSFileManager.defaultManager
            val map = fm.attributesOfItemAtPath(path, it) as NSDictionary
            result = map.fileSize()
        }
        return result
    }

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

    open val exists: Boolean get() = pathExists(path)

    open val isUri: Boolean
        get() = TODO("Not yet implemented")
    open val isUriString: Boolean
        get() = TODO("Not yet implemented")


    private fun matches(path: String): List<String> {
        memScoped {
            val result = alloc<ObjCObjectVar<String?>>()
            val matches = alloc<ObjCObjectVar<List<*>?>>()
            @Suppress("UNCHECKED_CAST")
            val count = (path as NSString).completePathIntoString(
                result.ptr,
                true,
                matches.ptr,
                null
            )
            println("path count: $count")
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

    open fun copy(destinationPath: String): File {
        throwError {
            fm.copyItemAtPath(path, destinationPath, it)
        }
        return File(destinationPath, null)
    }

    open fun delete(): Boolean {
        return if (exists) {
            throwError {
                fm.removeItemAtPath(path, it)
            }
        } else
            false
    }

    /**
     * Determine if subdirectory exists. If not create it.
     * @param subdirectory of current filePath
     * @return File with path of new subdirectory
     */
    open fun resolve(directoryName: String): File {
        if (directoryName.isEmpty() || directoryName.startsWith(pathSeparator))
            throw IllegalArgumentException("resolve requires $directoryName to be not empty and cannot start with $pathSeparator")
        val newPath = "$path/$directoryName"
        if (!pathExists(newPath)) {
            throwError { errorPointer ->
                val withIntermediates = directoryName.contains(pathSeparator)
                fm.createDirectoryAtPath(
                    newPath,
                    withIntermediates,
                    null,
                    errorPointer
                )
            }
        }
        return File(newPath, null)
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
         * Creates an NSError pointer for use by File-based operations, and invokes [block] with it. If an error
         * is produced by the [block] invocation, it is converted to an NSErrorException and thrown.
         */
        fun <T> throwError(block: (errorPointer: CPointer<ObjCObjectVar<NSError?>>) -> T): T {
            memScoped {
                val errorPointer = alloc<ObjCObjectVar<NSError?>>().ptr
                val result: T = block(errorPointer)
                val error: NSError? = errorPointer.pointed.value
                if (error != null) {
                    throw NSErrorException(error)
                } else {
                    return result
                }
            }
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
        } ?: throw IllegalArgumentException("Path ${path} mode $mode could not be opened")

    fun close() {
        handle.closeFile()
    }
}

open class AppleRawFile(
    fileArg: File,
    val mode: FileMode,
    source: FileSource
) : Closeable {
    private val apple = AppleFileHandle(fileArg, mode)
    private val handle = apple.handle
    private val path = apple.path
    open val file = fileArg

    override fun close() {
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
                    handle.getOffset(result.ptr, it)
                    result.value
                }
            }
        }
        set(value) {
            AppleFile.throwError {
                val result = handle.seekToOffset(value, it)
                if (!result) {
                    it.pointed.value?.let {
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
     * Read bytes from a file, staring at the specified position.
     * @param buf read buf.remaining bytes into byte buffer.
     * @param position zero-relative position of file to start reading,
     * or if default of -1, the current file position
     * @return number of bytes actually read
     */
    open fun read(buf: ByteBuffer): UInt {
        var len = 0u
        AppleFile.throwError {
            handle.readDataUpToLength(buf.remaining.convert(), it)?.let { bytes ->
                len = min(buf.limit.toUInt(), bytes.length.convert())
                buf.buf.usePinned {
                    memcpy(it.addressOf(buf.position), bytes.bytes, len.convert())
                }
                buf.position += len.toInt()
            }
        }
        return len
    }
    /**
     * Read bytes from a file, staring at the specified position.
     * @param buf read buf.remaining bytes into byte buffer.
     * @param position zero-relative position of file to start reading,
     * or if default of -1, the current file position
     * @return number of bytes actually read
     */
    open fun read(buf: ByteBuffer, newPos: ULong): UInt {
        var len = 0u
        AppleFile.throwError {
            position = newPos
            handle.readDataUpToLength(buf.remaining.convert(), it)?.let { bytes ->
                len = min(buf.limit.toUInt(), bytes.length.convert())
                buf.buf.usePinned {
                    memcpy(it.addressOf(buf.position), bytes.bytes, len.convert())
                }
                buf.position += len.toInt()
            }
        }
        return len
    }

    /**
     * Read bytes from a file, staring at the current file position.
     * @param buf read buf.remaining bytes into byte buffer.
     * @return number of bytes actually read
     */
    open fun read(buf: UByteBuffer): UInt {
        var len = 0u
        AppleFile.throwError {
            handle.readDataUpToLength(buf.remaining.convert(), it)?.let { bytes ->
                len = min(buf.limit.toUInt(), bytes.length.convert())
                buf.buf.usePinned {
                    memcpy(it.addressOf(buf.position), bytes.bytes, len.convert())
                }
                buf.position += len.toInt()
            }
        }
        return len
    }

    /**
     * Read bytes from a file, staring at the specified position.
     * @param buf read buf.remaining bytes into byte buffer.
     * @param position zero-relative position of file to start reading,
     * or if default of -1, the current file position
     * @return number of bytes actually read
     */
    open fun read(buf: UByteBuffer, newPos: ULong): UInt {
        var len = 0u
        AppleFile.throwError {
            position = newPos
            handle.readDataUpToLength(buf.remaining.convert(), it)?.let { bytes ->
                len = min(buf.limit.toUInt(), bytes.length.convert())
                buf.buf.usePinned {
                    memcpy(it.addressOf(buf.position), bytes.bytes, len.convert())
                }
                buf.position += len.toInt()
            }
        }
        return len
    }

    /**
     * Write bytes to a file, staring at the current file position.
     * @param buf write buf.remaining bytes into byte buffer starting at the buffer's current position.
     * @return number of bytes actually read
     */
    open fun write(buf: ByteBuffer) {
        AppleFile.throwError { error ->
            memScoped {
                buf.buf.usePinned {
                    val nsData = NSData.create(bytesNoCopy = it.addressOf(buf.position), buf.remaining.convert())
                    handle.writeData(nsData, error)
                }
            }
        }
        buf.position += buf.remaining
    }

    /**
     * Write bytes to a file, staring at the specified position.
     * @param buf write buf.remaining bytes into byte buffer starting at the buffer's current position.
     * @param position zero-relative position of file to start writing,
     * or if default of -1, the current file position
     * @return number of bytes actually read
     */
    open fun write(buf: ByteBuffer, newPos: ULong) {
        AppleFile.throwError { error ->
            position = newPos
            memScoped {
                buf.buf.usePinned {
                    val nsData = NSData.create(bytesNoCopy = it.addressOf(buf.position), buf.remaining.convert())
                    handle.writeData(nsData, error)
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
    open fun write(buf: UByteBuffer) {
        AppleFile.throwError { error ->
            memScoped {
                buf.buf.usePinned {
                    val nsData = NSData.create(bytesNoCopy = it.addressOf(buf.position), buf.remaining.convert())
                    handle.writeData(nsData, error)
                }
            }
        }
        buf.position += buf.remaining
    }

    /**
     * Write bytes to a file, staring at the specified position.
     * @param buf write buf.remaining bytes into byte buffer starting at the buffer's current position.
     * @param position zero-relative position of file to start writing,
     * or if default of -1, the current file position
     * @return number of bytes actually read
     */
    open fun write(buf: UByteBuffer, newPos: ULong) {
        AppleFile.throwError { error ->
            position = newPos
            memScoped {
                buf.buf.usePinned {
                    val nsData = NSData.create(bytesNoCopy = it.addressOf(buf.position), buf.remaining.convert())
                    handle.writeData(nsData, error)
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
    open fun copyTo(
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
            var readCount = read(buffer, -1L)
            var bytesWritten = 0L
            val fileSize = size
            while (readCount > 0u) {
                bytesRead += readCount
                buffer.position = 0
                buffer.limit = readCount.toInt()
                val lastBlock = position >= fileSize
                val outBuffer = transform(buffer, lastBlock)
                val count = outBuffer.remaining
                destination.write(outBuffer, -1)
                bytesWritten += count
                readCount = if (lastBlock) 0u else read(buffer, -1L)
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
    open fun copyToU(
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
            var readCount = read(buffer, -1L)
            var bytesWritten = 0L
            val fileSize = size
            while (readCount > 0u) {
                bytesRead += readCount
                buffer.position = 0
                buffer.limit = readCount.toInt()
                val lastBlock = position >= fileSize
                val outBuffer = transform(buffer, lastBlock)
                val count = outBuffer.remaining
                destination.write(outBuffer, -1)
                bytesWritten += count
                readCount = if (lastBlock) 0u else read(buffer, -1L)
            }
        }
        close()
        destination.close()
        return bytesRead
    }

    /**
     * Copy a portion of the specified source file to the current file at the current position
     */
    open fun transferFrom(
        source: RawFile,
        startIndex: ULong,
        length: ULong
    ): ULong {
        var srcBuf = ByteBuffer(min(blockSize.toULong(), length).toInt())
        var bytesCount: ULong = 0u
        var rd = source.read(srcBuf, -1).toULong()
        while (rd > 0u) {
            srcBuf.rewind()
            bytesCount += rd
            write(srcBuf, startIndex.toLong())
            srcBuf.rewind()
            if (length - rd < blockSize)
                srcBuf = ByteBuffer((length - rd).toInt())
            rd = source.read(srcBuf, -1).toULong()
        }
        source.close()
        close()
        return bytesCount
    }

    /**
     * Truncate the current file to the specified size.  Not usable on Mode.Read files.
     */
    open fun truncate(size: ULong) {
        if (mode == FileMode.Read)
            throw IllegalStateException("No truncate on read-only RawFile")
        AppleFile.throwError {
            handle.truncateAtOffset(size.convert(), it)
        }
    }
}

open class AppleTextFile(
    file: File,
    open val charset: Charset,
    val mode: FileMode,
    source: FileSource
) : Closeable {
    private val apple = AppleFileHandle(file, mode)
    private var blockSize = 2048
    private var buf = ByteArray(blockSize)
    private var index = -1
    private var lineEndIndex = -1
    private var endOfFile = false
    private var str: String = ""
    private var readLock = false

    override fun close() {
        apple.close()
    }

    private fun nextBlock(): String {
        if (endOfFile) return ""
        AppleFile.throwError {
            apple.handle.readDataUpToLength(blockSize.convert(), it)?.let { bytes ->
                val len = min(blockSize.toUInt(), bytes.length.convert())
                if (len < buf.size.toUInt()) {
                    buf = ByteArray(len.toInt())
                    endOfFile = true
                }
                buf.usePinned {
                    memcpy(it.addressOf(0), bytes.bytes, len.convert())
                }
            }
            index = 0
        }
        return charset.decode(buf)
    }

    private fun nextBlockLineState(): Boolean {
        if (lineEndIndex < 0) {
            if (endOfFile) return false
            str += nextBlock()
            index = 0
            val x = str.indexOf(eol)
            lineEndIndex = if (x >= 0) x + 1 else -1
            return true
        }
        return false
    }

    /**
     * Reads one line of text, no matter how long, which has obvious implications for memory on large files with no
     * line breaks. Otherwise, reads blocks and maintains state of where next line is. So only use this on files with
     * line breaks.
     * @return a non empty line containing any text found and ended by a line separator. After all lines have been
     * returned subsequent calls will always be an empty string.
     */
    open fun readLine(): String {
        var lin: String
        while (!endOfFile && lineEndIndex == -1)
            nextBlockLineState()
        if (endOfFile && lineEndIndex == -1) {
            lin = str
            str = ""
        } else {
            lin = str.substring(index, lineEndIndex)
            index = lineEndIndex
            val x = str.indexOf(eol, index)
            lineEndIndex = if (x >= 0)
                x + 1
            else {
                str = str.substring(index)
                -1
            }
        }
        return lin
    }

    open fun forEachLine(action: (count: Int, line: String) -> Boolean) {
        try {
            readLock = true
            var count = 1
            var lin = readLine()
            while (lin.isNotEmpty()) {
                if (action(count, lin)) {
                    lin = readLine()
                    count++
                } else
                    break
            }
        } finally {
            close()
            readLock = false
        }
    }

    open fun forEachBlock(maxSizeBytes: Int, action: (text: String) -> Boolean) {
        blockSize = maxSizeBytes
        try {
            readLock = true
            val str = nextBlock()
            while (str.isNotEmpty()) {
                if (action(str))
                    nextBlock()
                else
                    break
            }
        } finally {
            close()
            readLock = false
        }
    }

    open fun read(maxSizeBytes: Int): String {
        if (readLock)
            throw IllegalStateException("Invoking read during existing forEach operation is not allowed ")
        if (endOfFile) return ""
        return nextBlock()
    }

    open fun write(text: String) {
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

    open fun writeLine(text: String) {
        write (text + eol)
    }

    companion object {
        const val eol = "\n"
    }
}
