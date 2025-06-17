package com.oldguy.common.io

import com.oldguy.common.io.charsets.Charset
import com.oldguy.common.io.charsets.Utf8
import kotlinx.datetime.LocalDateTime

expect class TimeZones {
    companion object {
        fun getDefaultId(): String
    }
}

class IOException(message: String, cause: Throwable? = null): Exception(message, cause)

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
 * Note that a File object is immutable. The properties are set from the file system only at constructor time.
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
    val directoryPath: String
    val isDirectory: Boolean
    val exists: Boolean
    val isUri: Boolean
    val isUriString: Boolean
    val size: ULong
    val lastModifiedEpoch: Long
    val lastModified: LocalDateTime?
    val createdTime: LocalDateTime?
    val lastAccessTime: LocalDateTime?

    suspend fun delete(): Boolean
    suspend fun copy(destinationPath: String): File
    suspend fun makeDirectory(): File
    suspend fun resolve(directoryName: String, make: Boolean = true): File
    suspend fun directoryList(): List<String>
    fun up(): File

    /**
     * Make a new File object with updated attributes from the same fullPath as this File instance
     */
    fun newFile(): File

    companion object {
        val pathSeparator: String
        fun tempDirectoryPath(): String
        fun tempDirectoryFile(): File
        fun workingDirectory(): File
    }
}

enum class FileSource {
    Asset, Classpath, File
}

expect interface Closeable {
    suspend fun close()
}

expect suspend fun <T : Closeable?, R> T.use(body: suspend (T) -> R): R

enum class FileMode {
    Read, Write
}

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

/**
 * Read a text file, and provide both character set translation as well as line-based processing.
 */
expect class TextFile(
    file: File,
    charset: Charset = Utf8(),
    mode: FileMode = FileMode.Read,
    source: FileSource = FileSource.File
) : Closeable {
    val file: File
    val charset: Charset

    constructor(
        filePath: String,
        charset: Charset = Utf8(),
        mode: FileMode = FileMode.Read,
        source: FileSource = FileSource.File
    )

    override suspend fun close()

    /**
     * Convenience method for reading text file line by line.  No protection for large text files with no line breaks.
     * Uses the specified charset to decode the file content and invoke the lambda for each line found.  Function
     * completes at end of file, or when lambda returns false.
     * @param action invoked for each line. Argument "count" contains line number, one-relative. Argument "line"
     * contains decoded string, with the terminating eol character if
     * there is one - there may not be if the last line in the file has no eol character. Lambda should return true
     * to continue, false to cause function to close the file and return.  Note that file will be closed on return,
     * even if some exception is thrown during processing
     */
    suspend fun forEachLine(action: (count: Int, line: String) -> Boolean)

    /**
     * Convenience method for reading a file by text block. Lambda is invoked once for each block read until end of file,
     * when the file is closed and the function returns.
     * @param maxSizeBytes number of bytes, before character set decoding, to be read from the file in one operation
     * @param action will be invoked once for each block read. The bytes read are decoded using [Charset] and the
     * resulting String is the value of the argument. Function should return true to continue reading, or false to
     * close file and complete. Note that file will be closed on return, even if some exception is thrown during
     * processing.
     */
    suspend fun forEachBlock(maxSizeBytes: Int, action: (text: String) -> Boolean)

    /**
     * Read one block of text decoded using [Charset]. Caller is responsible for closing file when done.
     * @param maxSizeBytes number of bytes, before character set decoding, to be read from the file.
     * @return decoded String. Will be empty if end of file has been reached.
     */
    suspend fun read(maxSizeBytes: Int): String

    /**
     * Reads one line of text. Implementations read a buffer of bytes, decodes them using [Charset], then searches
     * for EOL characters (typically '\n').
     * @return next line found. Will always include terminating eol, unless end of file is reached. If last line of text
     * has no eol, no eol will be returned on last String.  If all lines have been read, subsequent calls return
     * empty String.
     */
    suspend fun readLine(): String

    /**
     * Encodes a String using [Charset], then writes it at the current file position.
     * @param text is encoded to bytes, then written to file
     */
    suspend fun write(text: String)

    /**
     * This is a convenience method for writing text that should end with an EOL.
     * Encodes a String using [Charset], then writes it at the current file position.
     * @param text is checked for an existing EOL on the end. If one is not found it is added. Result is encoded to
     * bytes using [Charset], then written to file. Note that if text has embedded EOLs these are encoded and written
     * unchanged.
     */
    suspend fun writeLine(text: String)
}
