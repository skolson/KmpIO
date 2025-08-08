package com.oldguy.common.io

/**
 * Use this to access a file at the bytes level. Random access by byte position is supported, with
 * first byte of a file as position 0. No translation of data is performed
 */
expect class RawFile(
    fileArg: File,
    mode: FileMode = FileMode.Read,
    source: FileSource = FileSource.File
) : Closeable {
    override suspend fun close()

    val file: File

    /**
     * Current position of the file, in bytes. Can be changed, if attempt to set outside the limits
     * of the current file, an exception is thrown.
     */
    var position: ULong

    /**
     * Current size of the file in bytes
     */
    val size: ULong


    var blockSize: UInt

    /**
     * Read bytes from a file, from the current file position.
     * @param buf read buf.remaining bytes into byte buffer.
     * @param reuseBuffer if false (default), position is advanced by number of bytes read and function
     * returns. If true, buffer is cleared before read so capacity bytes can be read. Position
     * advances by number of bytes read, then buffer flip() is called so position is zero, limit and
     * remaining are both number of bytes read, and capacity remains unchanged.
     * @return number of bytes actually read
     */
    suspend fun read(buf: ByteBuffer, reuseBuffer: Boolean = false): UInt

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
    suspend fun read(buf: ByteBuffer, newPos: ULong, reuseBuffer: Boolean = false): UInt

    /**
     * Read bytes from a file, staring at the specified position.
     * @param buf read buf.remaining bytes into byte buffer.
     * @param reuseBuffer if false (default), position is advanced by number of bytes read and function
     * returns. If true, buffer is cleared before read so capacity bytes can be read. Position
     * advances by number of bytes read, then buffer flip() is called so position is zero, limit and
     * remaining are both number of bytes read, and capacity remains unchanged.
     * @return number of bytes actually read
     */
    suspend fun read(buf: UByteBuffer, reuseBuffer: Boolean = false): UInt

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
    suspend fun read(buf: UByteBuffer, newPos: ULong, reuseBuffer: Boolean = false): UInt

    /**
     * Allocates a new buffer of length specified. Reads bytes at current position.
     * @param length maximum number of bytes to read
     * @return buffer: capacity == length, position = 0, limit = number of bytes read, remaining = limit.
     */
    suspend fun readBuffer(length: UInt): ByteBuffer

    /**
     * Allocates a new buffer of length specified. Reads bytes at specified position.
     * @param length maximum number of bytes to read
     * @return buffer: capacity == length, position = 0, limit = number of bytes read, remaining = limit.
     */
    suspend fun readBuffer(length: UInt, newPos: ULong): ByteBuffer

    /**
     * Allocates a new buffer of length specified. Reads bytes at current position.
     * @param length maximum number of bytes to read
     * @return buffer: capacity == length, position = 0, limit = number of bytes read, remaining = limit.
     */
    suspend fun readUBuffer(length: UInt): UByteBuffer

    /**
     * Allocates a new buffer of length specified. Reads bytes at specified position.
     * @param length maximum number of bytes to read
     * @return buffer: capacity == length, position = 0, limit = number of bytes read, remaining = limit.
     */
    suspend fun readUBuffer(length: UInt, newPos: ULong): UByteBuffer

    /**
     * Sets the length of the file in bytes. Ony usable during FileMode.Write.
     * @param length If current file size is less than [length], file will be expanded.  If current
     * file size is greater than [length] file will be shrunk.
     */
    suspend fun setLength(length: ULong)

    /**
     * Write bytes to a file, staring at the current file position.
     * @param buf write buf.remaining bytes into byte buffer starting at the buffer's current position.
     * or if default of -1, the current file position
     */
    suspend fun write(buf: ByteBuffer)

    /**
     * Write bytes to a file, staring at the specified position.
     * @param buf write buf.remaining bytes into byte buffer starting at the buffer's current position.
     * @param newPos zero-relative position of file to start writing,
     * or if default of -1, the current file position
     */
    suspend fun write(buf: ByteBuffer, newPos: ULong)

    /**
     * Write bytes to a file, staring at the current file position.
     * @param buf write buf.remaining bytes into byte buffer starting at the buffer's current position.
     * or if default of -1, the current file position
     */
    suspend fun write(buf: UByteBuffer)

    /**
     * Write bytes to a file, staring at the specified position.
     * @param buf write buf.remaining bytes into byte buffer starting at the buffer's current position.
     * @param newPos zero-relative position of file to start writing,
     * or if default of -1, the current file position
     * @return number of bytes actually read
     */
    suspend fun write(buf: UByteBuffer, newPos: ULong)

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
    suspend fun copyTo(
        destination: RawFile, blockSize: Int = 0,
        transform: ((buffer: ByteBuffer, lastBlock: Boolean) -> ByteBuffer)? = null
    ): ULong

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
    suspend fun copyToU(
        destination: RawFile,
        blockSize: Int = 0,
        transform: ((buffer: UByteBuffer, lastBlock: Boolean) -> UByteBuffer)? = null
    ): ULong

    /**
     * Copy a portion of the specified source file to the current file at the current position
     */
    suspend fun transferFrom(source: RawFile, startIndex: ULong, length: ULong): ULong

    /**
     * Truncate the current file to the specified size.  Not usable on Mode.Read files.
     */
    suspend fun truncate(size: ULong)

}