package com.oldguy.common.io

import com.oldguy.common.io.charsets.Charset
import com.oldguy.common.io.charsets.Utf8

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
     * Skips a specified number of bytes from the current position of the file. Only usable on FileMode.Read
     */
    suspend fun skip(bytesCount: ULong)

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