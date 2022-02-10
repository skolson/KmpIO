package com.oldguy.common.io

data class ZipEntry(
    val name: String,
    val comment: String,
    val extra: ByteArray?
) {
    constructor(name: String): this(name, "", null)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as ZipEntry

        if (name != other.name) return false
        if (comment != other.comment) return false
        if (!extra.contentEquals(other.extra)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + comment.hashCode()
        result = 31 * result + extra.contentHashCode()
        return result
    }
}

expect class ZipFile(
    file: File,
    mode: FileMode
) {
    constructor(file: File)

    /**
     * Used only for text entries.  Defaults to the standard for the platform in use. Typically either
     * "\n" or "\r\n".
     */
    var lineSeparator: String

    /**
     * Implementations should return the current list of entries for the zip file. On Mode Read
     * files, if file is not a Zip os some other error occurs, an IOException is thrown.
     */
    val entries: List<ZipEntry>
    val isOpen: Boolean

    /**
     * Opens the ZipFile specified.
     */
    fun open()

    /**
     * Closes the ZipFile specified, and frees any associated buffers or resources
     */
    fun close()

    /**
     * Convenience method, opens current zip file, invokes lambda, closes file.
     * @param block for a Mode Write file, perform all AddEntry or AddTextEntry calls desired.
     * For a Mode Read file, perform all ReadEntry or ReadTextEntry calls needed.
     */
    fun use(block: () -> Unit)

    /**
     * Add an empty entry, typically used for directories.  Note that empty directories should have
     * and entry name ending in '/'
     */
    fun addEntry(entry: ZipEntry)

    /**
     * Use this to add a new Entry, and use the lambda to provide content to the entry.
     * @param entry describes the entry to be added
     * @param bufferSize size of the content ByteArray that will be passed to the block
     * lambda, controls how much data will be written in one write
     * @param block lambda will be invoked repeatedly until it returns 0.  Each time,
     * content ByteArray that is supplied will be of size [bufferSize] and will be initialized
     * with all binary zeros.  It should be populated with data to be written. Lambda
     * should return a count of actual number of bytes in the content to be written, between
     * zero and [bufferSize]. Return zero to indicate entry is complete and should be closed,
     * resulting in [addEntry] being complete.
     */
    suspend fun addEntry(
        entry: ZipEntry,
        bufferSize: Int = 4096,
        block: suspend (content: ByteArray) -> Int
    )

    /**
     * Use this to add a new Entry, and use the lambda to provide content to the entry.
     * @param entry describes the entry to be added
     * @param charset controls how String will be encoded when written to entry
     * @param block lambda will be invoked repeatedly until it returns empty String.  Each time,
     * String returned will have a lineSeparator appended if it does not end with one, then will be
     * encoded with the specified Charset and written to the entry output. If
     * String returned is empty, write process stops, entry is closed and [addTextEntry] completes.
     */
    suspend fun addTextEntry(
        entry: ZipEntry,
        charset: Charset,
        appendEol: Boolean = true,
        block: suspend () -> String
    )

    /**
     * For a mode read file, reads the specified entry if it exists, throws an exception if it does
     * not. Reads continue as long as lambda returns true, until all data is consumed.
     * @param entryName specifies the entry to be read, typically a name from the [entries] list
     * @param bufferSize controls the maximum number of bytes to be read for one lambda call.
     * @param block invoked once for each block read, until either the entire entry is read, or
     * until the lambda returns false, indicating complete entry read is not desired.  Arguments
     * to lambda contain the butes read, and a count of the number of bytes read.
     * @return the ZipEntry read, including its metadata
     */
    suspend fun readEntry(
        entryName: String,
        bufferSize: Int = 4096,
        block: suspend (content: ByteArray, bytes: Int) -> Boolean
    ): ZipEntry


    /**
     * Read a text entry.
     * @param entryName must match an existing entry in the zip file
     * @param bufferSize if specified, determines the maximum size of the text to be read in one
     * iteration of [block].  If not specified, readLine is used. Note that readLine has a "feature"
     * that it returns lines of unlimited size, which is bad in a large text file with no line breaks.
     * Only use readLine in cases where this is a manageable risk.
     */
    suspend fun readTextEntry(
        entryName: String,
        charset: Charset,
        bufferSize: Int = 0,
        block: suspend (text: String) -> Boolean
    ): ZipEntry

    /**
     * merges a set of input zip files into this zip file.  If duplicate entries are detected,
     * if an input file is missing or is not a zip file, or if any other errors occur, an exception
     * is thrown. If this zipfile is not write mode, en exception is thrown.
     *
     * @param zipFiles one ore more zip files to be merged
     */
    fun merge(vararg zipFiles: ZipFile)
}