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
 * Note: many of the FileManager functions return errors as an NSError object. This class
 * also provides help for allocationg and translating an NSError required. If an error occurs,
 * a Kotlin NSErrorException is thrown.
 */
open class AppleFile(val filePath: String, val fd: FileDescriptor?) {
    val fm = NSFileManager.defaultManager
    open val name: String
        get() {
            val index = filePath.lastIndexOf(pathSeparator)
            return if (index < 0 || index == filePath.length - 1)
                ""
            else
                filePath.substring(index + 1)
        }
    open val nameWithoutExtension: String
        get() {
            val index = name.lastIndexOf(".")
            return if (index <= 0)
                ""
            else
                name.substring(0, index)
        }
    open val extension: String get() {
            val index = name.lastIndexOf(".")
            return if (index < 0 || index == name.length - 1)
                ""
            else
                name.substring(index + 1)
        }
    open val path: String get() {
            val index = filePath.lastIndexOf(pathSeparator)
            return if (index < 0)
                ""
            else
                filePath.substring(0, index + 1)
        }
    open val fullPath: String get() {
        memScoped {
            val result = alloc<ObjCObjectVar<String?>>()
            val matches = alloc<ObjCObjectVar<List<*>?>>()
            (filePath as NSString).completePathIntoString(
                result.ptr,
                true,
                matches.ptr,
                null
            )
            return result.value ?: ""
        }
    }

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
            fm.fileExistsAtPath(filePath, isDirPointer.ptr)
            return isDirPointer.value
        }

    open val listFiles: List<File> get() {
        val list = mutableListOf<File>()
        throwError {
            fm.contentsOfDirectoryAtPath(filePath, it)?.let { names ->
                names.forEach { ptr ->
                    memScoped {
                        if (ptr is ObjCObjectVar<*>) {
                            val c = ptr as ObjCObjectVar<NSString>
                            val n = c as String
                            if (n.isNotEmpty())
                                list.add(File(n, null))
                        } else
                            throw IllegalStateException("Type returned in List from contentsOfDirectoryAtPath is ${ptr.toString()}")
                    }
                }
            }
        }
        return list
    }

    open val exists: Boolean get() {
        memScoped {
            val isDirPointer = alloc<BooleanVar>()
            return fm.fileExistsAtPath(filePath, isDirPointer.ptr)
        }
    }
    open val isUri: Boolean
        get() = TODO("Not yet implemented")
    open val isUriString: Boolean
        get() = TODO("Not yet implemented")


    open fun copy(destinationPath: String): File {
        throwError {
            fm.copyItemAtPath(filePath, destinationPath, it)
        }
        return File(destinationPath, null)
    }

    open fun delete(): Boolean {
        var result = false
        throwError {
            result = fm.removeItemAtPath(filePath, it)
        }
        return result
    }

    /**
     * Determine if subdirectory exists. If not create it.
     * @param subdirectory of current filePath
     * @return File with path of new subdirectory
     */
    open fun resolve(directoryName: String): File {
        val newPath = "$filePath/$directoryName"
        throwError { errorPointer ->
            fm.createDirectoryAtPath(
                newPath,
                false,
                null,
                errorPointer)
        }
        return File(newPath, null)
    }

    companion object {
        const val pathSeparator = "/"

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
    val path = file.fullPath
    val handle = when (mode) {
        FileMode.Read ->
            NSFileHandle.fileHandleForReadingAtPath(path)
        FileMode.Write -> {
            updating = file.exists
            if (updating)
                NSFileHandle.fileHandleForUpdatingAtPath(path)
            else
                NSFileHandle.fileHandleForWritingAtPath(path)
        }
    } ?: throw IllegalArgumentException("Path ${path} could not be opened")

    fun close() {
        handle.closeFile()
    }
}

open class AppleRawFile(
    open val file: File,
    val mode: FileMode,
    source: FileSource
) : Closeable {
    private val apple = AppleFileHandle(file, mode)
    private val handle = apple.handle
    private val path = apple.path

    override fun close() {
        apple.close()
    }

    /**
     * Current position of the file, in bytes. Can be changed, if attempt to set outside the limits
     * of the current file, an exception is thrown.
     */
    open var position: ULong = 0u

    /**
     * Current size of the file in bytes
     */
    open val size: ULong get() = apple.file.size

    open var blockSize: UInt = 4096u

    private fun seek(position: Long) {
        AppleFile.throwError {
            memScoped {
                val result = alloc<ULongVar>()
                handle.getOffset(result.ptr, it)
                this@AppleRawFile.position = result.value
            }
            if (position >= 0 && this.position != position.toULong()) {
                val result = handle.seekToOffset(position.toULong(), it)
                if (!result)
                    throw IllegalArgumentException("Could not position file to $position")
                this@AppleRawFile.position = position.toULong()
            }
        }
    }

    /**
     * Read bytes from a file, staring at the specified position.
     * @param buf read buf.remaining bytes into byte buffer.
     * @param position zero-relative position of file to start reading,
     * or if default of -1, the current file position
     * @return number of bytes actually read
     */
    open fun read(buf: ByteBuffer, position: Long): UInt {
        var len = 0u
        AppleFile.throwError {
            seek(position)
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
    open fun read(buf: UByteBuffer, position: Long): UInt {
        var len = 0u
        AppleFile.throwError {
            seek(position)
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
     * Write bytes to a file, staring at the specified position.
     * @param buf write buf.remaining bytes into byte buffer starting at the buffer's current position.
     * @param position zero-relative position of file to start writing,
     * or if default of -1, the current file position
     * @return number of bytes actually read
     */
    open fun write(buf: ByteBuffer, position: Long) {
        AppleFile.throwError { error ->
            seek(position)
            memScoped {
                buf.buf.usePinned {
                    val nsData = NSData.create(bytesNoCopy = it.addressOf(buf.position), buf.remaining.convert())
                    handle.writeData(nsData, error)
                }
            }
        }
        this@AppleRawFile.position += buf.remaining.toUInt()
        buf.position += buf.remaining
    }

    /**
     * Write bytes to a file, staring at the specified position.
     * @param buf write buf.remaining bytes into byte buffer starting at the buffer's current position.
     * @param position zero-relative position of file to start writing,
     * or if default of -1, the current file position
     * @return number of bytes actually read
     */
    open fun write(buf: UByteBuffer, position: Long) {
        AppleFile.throwError { error ->
            seek(position)
            memScoped {
                buf.buf.usePinned {
                    val nsData = NSData.create(bytesNoCopy = it.addressOf(buf.position), buf.remaining.convert())
                    handle.writeData(nsData, error)
                }
            }
        }
        this@AppleRawFile.position += buf.remaining.toUInt()
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
    val file: File,
    val charset: Charset,
    val mode: FileMode,
    source: FileSource
) : Closeable {
    private val apple = AppleFileHandle(file, mode)
    private val blockSize = 2048
    private var buf = ByteArray(blockSize)
    private var index = -1
    private var lineEndIndex = -1
    private var endOfFile = false

    override fun close() {
        apple.close()
    }

    private fun nextBlock(): String {
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

    open fun readLine(): String {
        var str = ""
        if (endOfFile) return ""
        return buildString {
            while(!endOfFile) {
                if (lineEndIndex < 0) {
                    str = nextBlock()
                    lineEndIndex = str.indexOf(eol)
                    if (lineEndIndex < 0)
                        lineEndIndex = str.length
                    else if (lineEndIndex < str.length - 1)
                        lineEndIndex++
                    index = 0
                }
                while (index < lineEndIndex) {
                    append(str.substring(index, lineEndIndex))
                    index = lineEndIndex
                    lineEndIndex = str.indexOf(eol, index)
                    if (lineEndIndex < 0) {
                        append(str.substring(index))
                        index = str.length
                    } else if (lineEndIndex < str.length - 1)
                        lineEndIndex++
                }
                if (!endOfFile) nextBlock()
            }
        }
    }

    open fun forEachLine(action: (line: String) -> Unit) {
        var lin = readLine()
        while (lin.isNotEmpty()) {
            action(lin)
            lin = readLine()
        }
        close()
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
