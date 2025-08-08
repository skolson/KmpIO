package com.oldguy.common.io

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
                           javaBuf: java.nio.ByteBuffer,
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
                           javaBuf: java.nio.ByteBuffer,
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

    private fun makeJavaBuffer(buf: com.oldguy.common.io.ByteBuffer): java.nio.ByteBuffer {
        return java.nio.ByteBuffer.wrap(buf.slice().contentBytes)
    }

    private fun makeJavaBuffer(buf: UByteBuffer): java.nio.ByteBuffer {
        return java.nio.ByteBuffer.wrap(buf.slice().toByteBuffer().contentBytes)
    }
}
