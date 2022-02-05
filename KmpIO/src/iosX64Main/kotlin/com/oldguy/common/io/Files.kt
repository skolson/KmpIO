package com.oldguy.common.io

actual class Charset actual constructor(charsetName: String) {
    actual fun decode(bytes: ByteArray): String {
        TODO("Not yet implemented")
    }

    actual fun encode(inString: String): ByteArray {
        TODO("Not yet implemented")
    }

    actual companion object {
        /**
         * CharsetNames required to be supported
         */
        actual val UTF_8: String
            get() = TODO("Not yet implemented")
        actual val US_ASCII: String
            get() = TODO("Not yet implemented")
        actual val UTF_16LE: String
            get() = TODO("Not yet implemented")
        actual val ISO8859_1: String
            get() = TODO("Not yet implemented")
        actual val UTF_16: String
            get() = TODO("Not yet implemented")
    }
}

actual class TimeZones {
    actual companion object {
        actual fun getDefaultId(): String {
            TODO("Not yet implemented")
        }
    }
}

actual class File actual constructor(val filePath: String, val platformFd: FileDescriptor?) {
    actual val name: String
        get() = TODO("Not yet implemented")
    actual val nameWithoutExtension: String
        get() = TODO("Not yet implemented")
    actual val extension: String
        get() = TODO("Not yet implemented")
    actual val path: String
        get() = TODO("Not yet implemented")
    actual val fullPath: String
        get() = TODO("Not yet implemented")
    actual val isDirectory: Boolean
        get() = TODO("Not yet implemented")
    actual val listFiles: List<File>
        get() = TODO("Not yet implemented")
    actual val exists: Boolean
        get() = TODO("Not yet implemented")
    actual val isUri: Boolean
        get() = TODO("Not yet implemented")
    actual val isUriString: Boolean
        get() = TODO("Not yet implemented")
    actual var parentFile: File?
        get() = TODO("Not yet implemented")
        set(value) {}

    actual fun delete(): Boolean {
        TODO("Not yet implemented")
    }

    actual fun copy(destinationPath: String): File {
        TODO("Not yet implemented")
    }

    actual fun resolve(directoryName: String): File {
        TODO("Not yet implemented")
    }

    actual constructor(parentDirectory: String, name: String) : this(parentDirectory + name, null) {
        TODO("Not yet implemented")
    }

    actual constructor(parentDirectory: File, name: String) : this(parentDirectory.resolve(name).fullPath, null) {
        TODO("Not yet implemented")
    }

    actual constructor(fd: FileDescriptor) : this("", fd) {
        TODO("Not yet implemented")
    }

}

actual inline fun <T : Closeable?, R> T.use(body: (T) -> R): R {
    return body(this)
}

actual class RawFile actual constructor(
    fileArg: File,
    mode: FileMode,
    source: FileSource
) : Closeable {
    actual override fun close() {
        TODO("Not yet implemented")
    }

    actual val file: File
        get() = TODO("Not yet implemented")

    /**
     * Current position of the file, in bytes. Can be changed, if attempt to set outside the limits
     * of the current file, an exception is thrown.
     */
    actual var position: Long
        get() = TODO("Not yet implemented")
        set(value) {}

    /**
     * Current size of the file in bytes
     */
    actual val size: Long
        get() = TODO("Not yet implemented")
    actual var copyBlockSize: Int
        get() = TODO("Not yet implemented")
        set(value) {}

    /**
     * Read bytes from a file, staring at the specified position.
     * @param buf read buf.remaining bytes into byte buffer.
     * @param position zero-relative position of file to start reading,
     * or if default of -1, the current file position
     * @return number of bytes actually read
     */
    actual fun read(buf: ByteBuffer, position: Long): Int {
        TODO("Not yet implemented")
    }

    /**
     * Read bytes from a file, staring at the specified position.
     * @param buf read buf.remaining bytes into byte buffer.
     * @param position zero-relative position of file to start reading,
     * or if default of -1, the current file position
     * @return number of bytes actually read
     */
    actual fun read(buf: UByteBuffer, position: Long): Int {
        TODO("Not yet implemented")
    }

    /**
     * Write bytes to a file, staring at the specified position.
     * @param buf write buf.remaining bytes into byte buffer starting at the buffer's current position.
     * @param position zero-relative position of file to start writing,
     * or if default of -1, the current file position
     * @return number of bytes actually read
     */
    actual fun write(buf: ByteBuffer, position: Long) {
    }

    /**
     * Write bytes to a file, staring at the specified position.
     * @param buf write buf.remaining bytes into byte buffer starting at the buffer's current position.
     * @param position zero-relative position of file to start writing,
     * or if default of -1, the current file position
     * @return number of bytes actually read
     */
    actual fun write(buf: UByteBuffer, position: Long) {
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
    actual fun copyTo(
        destination: RawFile,
        blockSize: Int,
        transform: ((buffer: ByteBuffer, lastBlock: Boolean) -> ByteBuffer)?
    ): Long {
        TODO("Not yet implemented")
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
    actual fun copyToU(
        destination: RawFile,
        blockSize: Int,
        transform: ((buffer: UByteBuffer, lastBlock: Boolean) -> UByteBuffer)?
    ): Long {
        TODO("Not yet implemented")
    }

    /**
     * Copy a portion of the specified source file to the current file at the current position
     */
    actual fun transferFrom(
        source: RawFile,
        startIndex: Long,
        length: Long
    ): Long {
        TODO("Not yet implemented")
    }

    /**
     * Truncate the current file to the specified size.  Not usable on Mode.Read files.
     */
    actual fun truncate(size: Long) {
    }
}

actual class TextFile actual constructor(
    val file: File,
    charset: Charset,
    val mode: FileMode,
    source: FileSource
) : Closeable {
    actual override fun close() {
    }

    actual fun readLine(): String {
        TODO("Not yet implemented")
    }

    actual fun forEachLine(action: (line: String) -> Unit) {
    }

    actual fun write(text: String) {
    }

    actual fun writeLine(text: String) {
    }

    actual constructor(
        filePath: String,
        charset: Charset,
        mode: FileMode,
        source: FileSource
    ) : this(File(filePath, null), charset, mode, source) {
        TODO("Not yet implemented")
    }

}

actual interface Closeable {
    actual fun close()
}