package com.oldguy.common.io

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toLong
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.Foundation.NSData
import platform.Foundation.NSFileHandle
import platform.Foundation.NSFileManager
import platform.Foundation.closeFile
import platform.Foundation.create
import platform.Foundation.fileHandleForReadingAtPath
import platform.Foundation.fileHandleForUpdatingAtPath
import platform.Foundation.fileHandleForWritingAtPath
import platform.posix.memcpy
import kotlin.math.min


/**
 * Apple-specific native file code that is usable on macOS, iOS, and ios simulator targets.
 * This class owns an Objective C FileManager instance for use by any of the subclasses.
 *
 * Note: many of the FileManager and FileHandle functions return errors as an NSError object. This class
 * also provides help for allocating and translating an NSError required. If an error occurs,
 * a Kotlin NSErrorException is thrown.
 */
class AppleFileHandle(file: File, mode: FileMode)
{
    constructor(path: String, mode: FileMode): this(File(path), mode)

    var updating = false
        private set
    val fullPath = file.fullPath
    val handle:NSFileHandle get() {
        if (closed)
            throw IllegalStateException("File $fullPath is closed")
        return field
    }

    private var closed = true

    init {
        handle = when (mode) {
            FileMode.Read ->
                NSFileHandle.fileHandleForReadingAtPath(fullPath)
            FileMode.Write -> {
                updating = file.exists
                if (updating) {
                    NSFileHandle.fileHandleForUpdatingAtPath(fullPath)
                } else {
                    val fm = NSFileManager.defaultManager
                    if (fm.createFileAtPath(fullPath, null, null))
                        NSFileHandle.fileHandleForWritingAtPath(fullPath)
                    else
                        throw IllegalArgumentException("Create file failed: $fullPath ")
                }
            }
        } ?: throw IllegalArgumentException("Path $fullPath mode $mode could not be opened")
        closed = false
    }
    fun close() {
        if (!closed)
            handle.closeFile()
        closed = true
    }
}

@OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
actual class RawFile actual constructor(
    fileArg: File,
    val mode: FileMode,
    source: FileSource
): Closeable
{
    private val apple = AppleFileHandle(fileArg, mode)
    private val fullPath = fileArg.fullPath
    actual val file = fileArg

    /**
     * Current position of the file, in bytes. Can be changed, if attempt to set outside the limits
     * of the current file, an exception is thrown.
     */
    actual var position: ULong
        get() {
            return File.throwError {
                memScoped {
                    val result = alloc<ULongVar>()
                    apple.handle.getOffset(result.ptr, it)
                    result.value
                }
            }
        }
        set(value) {
            File.throwError { cPointer ->
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
    actual val size: ULong get() = file.newFile().size
    actual var blockSize: UInt = 4096u

    actual override suspend fun close() {
        apple.close()
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
    actual suspend fun read(buf: ByteBuffer, newPos: ULong, reuseBuffer: Boolean): UInt {
        var len = 0u
        File.throwError { cPointer ->
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
     * @param newPos zero-relative position of file to start reading,
     * or if default of -1, the current file position
     * @param reuseBuffer if false (default), position is advanced by number of bytes read and function
     * returns. If true, buffer is cleared before read so capacity bytes can be read. Position
     * advances by number of bytes read, then buffer flip() is called so position is zero, limit and
     * remaining are both number of bytes read, and capacity remains unchanged.
     * @return number of bytes actually read
     */
    actual suspend fun read(buf: ByteBuffer, reuseBuffer: Boolean): UInt {
        var len = 0u
        File.throwError { cPointer ->
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
     * @param reuseBuffer if false (default), position is advanced by number of bytes read and function
     * returns. If true, buffer is cleared before read so capacity bytes can be read. Position
     * advances by number of bytes read, then buffer flip() is called so position is zero, limit and
     * remaining are both number of bytes read, and capacity remains unchanged.
     * @return number of bytes actually read
     */
    actual suspend fun read(buf: UByteBuffer, newPos: ULong, reuseBuffer: Boolean): UInt {
        var len = 0u
        File.throwError { cPointer ->
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
    actual suspend fun read(buf: UByteBuffer, reuseBuffer: Boolean): UInt {
        var len = 0u
        File.throwError { cPointer ->
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
     * Allocates a new buffer of length specified. Reads bytes at current position.
     * @param length maximum number of bytes to read
     * @return buffer: capacity == length, position = 0, limit = number of bytes read, remaining = limit.
     */
    actual suspend fun readBuffer(length: UInt): ByteBuffer {
        return ByteBuffer(length.toInt()).apply {
            read(this, true)
        }    }

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
        }    }

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

    /**
     * Write bytes to a file, staring at the specified position.
     * @param buf write buf.remaining bytes into byte buffer starting at the buffer's current position.
     * @param newPos zero-relative position of file to start writing,
     * or if default of -1, the current file position
     * @return number of bytes actually read
     */
    actual suspend fun write(buf: ByteBuffer, newPos: ULong) {
        if (buf.remaining == 0) return
        File.throwError { error ->
            position = newPos
            memScoped {
                val bytes = buf.getBytes()
                apple.handle.writeData(
                    NSData.create(bytes = allocArrayOf(bytes), length = bytes.size.toULong()),
                    error
                )
            }
        }
        buf.position += buf.remaining
    }

    /**
     * Write bytes to a file, staring at the current file position.
     * @param buf write buf.remaining bytes into byte buffer starting at the buffer's current position.
     * @return number of bytes actually read
     */
    actual suspend fun write(buf: ByteBuffer) {
        if (buf.remaining == 0) return
        File.throwError { error ->
            memScoped {
                val bytes = buf.getBytes()
                apple.handle.writeData(
                    NSData.create(bytes = allocArrayOf(bytes), length = bytes.size.toULong()),
                    error
                )
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
    actual suspend fun write(buf: UByteBuffer, newPos: ULong) {
        if (buf.remaining == 0) return
        File.throwError { error ->
            position = newPos
            memScoped {
                val bytes = buf.getBytes().toByteArray()
                apple.handle.writeData(
                    NSData.create(bytes = allocArrayOf(bytes), length = bytes.size.toULong()),
                    error
                )
            }
        }
        buf.position += buf.remaining    }

    /**
     * Write bytes to a file, staring at the current position.
     * @param buf write buf.remaining bytes into byte buffer starting at the buffer's current position.
     * @return number of bytes actually read
     */
    actual suspend fun write(buf: UByteBuffer) {
        if (buf.remaining == 0) return
        File.throwError { error ->
            memScoped {
                val bytes = buf.getBytes().toByteArray()
                apple.handle.writeData(
                    NSData.create(bytes = allocArrayOf(bytes), length = bytes.size.toULong()),
                    error
                )
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
    actual suspend fun copyTo(
        destination: RawFile,
        blockSize: Int,
        transform: ((buffer: ByteBuffer, lastBlock: Boolean) -> ByteBuffer)?
    ): ULong {
        var bytesRead:ULong = 0u
        if (transform == null) {
            File.throwError {
                val fm = NSFileManager.defaultManager
                fm.copyItemAtPath(fullPath, destination.file.fullPath, it)
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
    actual suspend fun copyToU(
        destination: RawFile,
        blockSize: Int,
        transform: ((buffer: UByteBuffer, lastBlock: Boolean) -> UByteBuffer)?
    ): ULong {
        var bytesRead: ULong = 0u
        if (transform == null) {
            File.throwError {
                val fm = NSFileManager.defaultManager
                fm.copyItemAtPath(fullPath, destination.file.fullPath, it)
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


    actual suspend fun transferFrom(
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
    actual suspend fun truncate(size: ULong) {
        if (mode == FileMode.Read)
            throw IllegalStateException("No truncate on read-only RawFile")
        File.throwError {
            apple.handle.truncateAtOffset(size.convert(), it)
        }    }

    /**
     * Sets the length of the file in bytes. Ony usable during FileMode.Write.
     * @param length If current file size is less than [length], file will be expanded.  If current
     * file size is greater than [length] file will be shrunk.
     */
    actual suspend fun setLength(length: ULong) {
        throw IllegalStateException("setLength not implemented on this platform")
    }

    companion object {
        val byteSz = 1.toULong()
    }
}