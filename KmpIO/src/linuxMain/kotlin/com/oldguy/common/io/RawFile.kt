package com.oldguy.common.io

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.refTo
import platform.posix.SEEK_SET
import platform.posix._IO_FILE
import platform.posix.errno
import platform.posix.fclose
import platform.posix.fflush
import platform.posix.fileno
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import platform.posix.ftruncate
import platform.posix.fwrite

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
        if (buf.isEmpty()) return 0u
        val bytesRead = fread(
            buf.refTo(0),
            byteSz,
            count.toULong(), raw
        )
        return bytesRead.toUInt()
    }

    open suspend fun read(buf: ByteBuffer, reuseBuffer: Boolean): UInt {
        if (reuseBuffer) buf.clear()
        if (buf.remaining == 0) return 0u
        val bytes = ByteArray(buf.remaining)
        val bytesRead = fread(bytes.refTo(0),
            com.oldguy.common.io.byteSz, buf.remaining.toULong(), raw)
        return acceptRead(buf, bytesRead.toInt(), bytes, reuseBuffer)
    }

    open suspend fun read(buf: UByteBuffer, reuseBuffer: Boolean): UInt {
        if (reuseBuffer) buf.clear()
        if (buf.remaining == 0) return 0u
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
        val buffer = ByteBuffer(blkSize, isReadOnly = true)
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
