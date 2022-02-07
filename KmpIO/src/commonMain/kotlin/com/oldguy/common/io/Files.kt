package com.oldguy.common.io

enum class Charsets(val charsetName: String) {
    Utf8("UTF-8"),
    Utf16le("UTF-16LE"),
    Utf16be("UTF-16BE"),
    Iso8859_1("ISO8859-1"),
    UsAscii("US-ASCII");

    companion object {
        fun fromName(name: String): Charsets {
            return values().first { it.charsetName == name }
        }
    }
}


expect class Charset(set: Charsets) {

    fun decode(bytes: ByteArray): String
    fun encode(inString: String): ByteArray

}

expect class TimeZones {
    companion object {
        fun getDefaultId(): String
    }
}


/**
 * Some platform-specific file descriptors are not easily encoded to a string.  Platform specific
 * implementations of File can type-cast these based on the matching code, which must be unique.
 *
 * code = 0 is a normal file path - not platform-specific object
 *
 * Example - Android-specific URIs for Google Drive - Uri.toString does not retain all the information
 * from the android content provider.
 *
 * actual class implementations are responsible for casting the descriptor back to the appropriate
 * type for the code specified
 */
data class FileDescriptor(val code: Int = 0, val descriptor: Any)

/**
 * Represents a file ID.  The filePath can be file syntax or URI or something else - actual platform
 * implementation will parse accordingly. Does assume file name at end of string may have an extension
 * of form "<path><nameWithoutExtension>.<extension>" = fullPath.
 * @param filePath string value identifying a file
 */
expect class File(filePath: String, platformFd: FileDescriptor? = null) {
    constructor(parentDirectory: String, name: String)
    constructor(parentDirectory: File, name: String)
    constructor(fd: FileDescriptor)

    val name: String
    val nameWithoutExtension: String
    val extension: String
    val path: String
    val fullPath: String
    val isDirectory: Boolean
    val listFiles: List<File>
    val exists: Boolean
    val isUri: Boolean
    val isUriString: Boolean

    fun delete(): Boolean
    fun copy(destinationPath: String): File
    fun resolve(directoryName: String): File
}

enum class FileSource {
    Asset, Classpath, File
}

expect interface Closeable {
    fun close()
}

expect inline fun <T : Closeable?, R> T.use(body: (T) -> R): R

enum class FileMode {
    Read, Write
}

/**
 * Use this to access a file at the bytes level. Random access by byte position is required, with
 * first byte of file as position 0. No translation of data is performed
 */
expect class RawFile(
    fileArg: File,
    mode: FileMode = FileMode.Read,
    source: FileSource = FileSource.File
) : Closeable {
    override fun close()

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
     * Read bytes from a file, staring at the specified position.
     * @param buf read buf.remaining bytes into byte buffer.
     * @param position zero-relative position of file to start reading,
     * or if default of -1, the current file position
     * @return number of bytes actually read
     */
    fun read(buf: ByteBuffer, position: Long = -1): UInt

    /**
     * Read bytes from a file, staring at the specified position.
     * @param buf read buf.remaining bytes into byte buffer.
     * @param position zero-relative position of file to start reading,
     * or if default of -1, the current file position
     * @return number of bytes actually read
     */
    fun read(buf: UByteBuffer, position: Long = -1): UInt

    /**
     * Write bytes to a file, staring at the specified position.
     * @param buf write buf.remaining bytes into byte buffer starting at the buffer's current position.
     * @param position zero-relative position of file to start writing,
     * or if default of -1, the current file position
     * @return number of bytes actually read
     */
    fun write(buf: ByteBuffer, position: Long = -1)

    /**
     * Write bytes to a file, staring at the specified position.
     * @param buf write buf.remaining bytes into byte buffer starting at the buffer's current position.
     * @param position zero-relative position of file to start writing,
     * or if default of -1, the current file position
     * @return number of bytes actually read
     */
    fun write(buf: UByteBuffer, position: Long = -1)

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
    fun copyTo(
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
    fun copyToU(
        destination: RawFile,
        blockSize: Int = 0,
        transform: ((buffer: UByteBuffer, lastBlock: Boolean) -> UByteBuffer)? = null
    ): ULong

    /**
     * Copy a portion of the specified source file to the current file at the current position
     */
    fun transferFrom(source: RawFile, startIndex: ULong, length: ULong): ULong

    /**
     * Truncate the current file to the specified size.  Not usable on Mode.Read files.
     */
    fun truncate(size: ULong)

}

/**
 * Read a text file, and provide both character set translation as well as line-based processing
 */
expect class TextFile(
    file: File,
    charset: Charset = Charset(Charsets.Utf8),
    mode: FileMode = FileMode.Read,
    source: FileSource = FileSource.File
) : Closeable {

    constructor(
        filePath: String,
        charset: Charset = Charset(Charsets.Utf8),
        mode: FileMode = FileMode.Read,
        source: FileSource = FileSource.File
    )

    override fun close()
    fun readLine(): String
    fun forEachLine(action: (line: String) -> Unit)
    fun write(text: String)
    fun writeLine(text: String)
}
