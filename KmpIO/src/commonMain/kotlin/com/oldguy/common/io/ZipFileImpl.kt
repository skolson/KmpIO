package com.oldguy.common.io

import kotlin.math.min

/**
 * This is the base for an internal KMP implementation of the Zip file specification. Written mostly because there is no
 * non-third-party Apple Objective C implementation. Under the covers, an expect/actual setup uses platform-specific
 * implementations of the compression librarires offerd in the JVM and in Apple.
 *
 */
interface ZipFileBase: Closeable {
    /**
     * Populated at open time, is a List of ell entries found in the directory structure of the opened Zip file
     */
    val entries: List<ZipEntry>
    val file: RawFile
    val bufferSize: Int

    /**
     * Add one entry to a FileMode.Write file.  The entry is added after any existing entries in the zip file, and the
     * directory structures are rewritten after each add. No content is added for this entry, use this for directory
     * entries.
     * @param entry to be added. If name matches existing entry, exception is thrown.
     */
    suspend fun addEntry(entry: ZipEntry)
    /**
     * Add one entry to a FileMode.Write file.  The entry is added after any existing entries in the zip file, and the
     * directory structures are rewritten after each add. Use the lambda for adding content for this entry.
     * @param entry to be added. If name matches existing entry, exception is thrown.
     * @param block function called repeatedly until content argument is empty array. All of array will be written to
     * entry and number of bytes written (after any compression) is returned.
     */
    suspend fun addEntry(
        entry: ZipEntry,
        block: suspend (content: ByteArray) -> Int
    )
    /**
     * Add one entry to a FileMode.Write file.  The entry is added after any existing entries in the zip file, and the
     * directory structures are rewritten after each add. Content is encoded using the provided Charset before any
     * compression is performed,
     * @param entry to be added. If name matches existing entry, exception is thrown.
     * @param charset will be used to encode incoming text before compression
     * @param appendEol true if an eol should be appended before encoding. Doesn't care if there are existing eols.
     * @param block will be called repeatedly until returned string is empty. String is encoded, any is compression
     * applied, then content is written.
     */
    suspend fun addTextEntry(
        entry: ZipEntry,
        charset: Charset = Charset(Charsets.Utf8),
        appendEol: Boolean = true,
        block: suspend () -> String
    )

    /**
     * Merges the specified Zip files into this one. Input zipfile entries with matching paths are ignored. Each new
     * entry is added to the end of this one. Directory is re-written after all entries have been copied.
     * @param zipFiles one or more ZipFileImpl instances
     */
    fun merge(vararg zipFiles: ZipFileImpl): List<ZipEntry>

    /**
     * Opens a Zip file. For FileMode Read, and for FileMode.Write where there is an existing file, all directory entries
     * found are parsed.  After open complete, all ZipEntry instances are available for use.  Any integrity issues
     * found will cause ZipException
     */
    suspend fun open()

    /**
     * Reads the content from one entry.
     * @param entryName if no matching name found in directory, exception is thrown
     * @param block is invoked repeatedly with uncompressed content. Arguments are;
     * entry - ZipEntry found with the specified name,
     * content - containing uncompressed data
     * count - number of uncompressed bytes, starting at index 0, in content
     * @return ZipEntry found, after all content retrieved.
     */
    suspend fun readEntry(
        entryName: String,
        block: suspend (entry:ZipEntry, content: ByteArray, count: UInt) -> Unit
    ): ZipEntry

    /**
     * Same setup as [readEntry], with the additional feature that content is read, uncompressed, decoded using the
     * specified Charset.
     * @param entryName if no matching name found in directory, exception is thrown
     * @param charset to be used when decoding uncompressed data to a String.
     * @param block is invoked repeatedly with uncompressed content in blocks of [bufferSize] bytes. Arguments are;
     * entry - ZipEntry found with the specified name,
     * content - containing uncompressed string decoded from the byte content using the Charset
     * count - number of uncompressed bytes, starting at index 0, in content
     * @return ZipEntry found, after all content retrieved.
     */
    suspend fun readTextEntry(
        entryName: String,
        charset: Charset = Charset(Charsets.Utf8),
        block: suspend (text: String) -> Boolean
    ): ZipEntry

    /**
     * Removes an entry from the file. File must be FileMode.Write. Content is NOT removed. Only directory structure is
     * re-written without this entry in it.
     * @param entry if no matching name found, likely caused by using an incorrect entry instance, IllegalArgumentException
     * is thrown
     */
    suspend fun removeEntry(entry: ZipEntry): Boolean

    /**
     * Convenience wrapper. Does open, followed by invoking [block]. At return from [block] or from uncaught exception,
     * ZipFile is closed.
     */
    suspend fun use(block: suspend () -> Unit)

    /**
     * Convenience wrapper for existing files only. Does open, followed by invoking [block] for each entry found.
     * After all entries processed, or from uncaught exception, ZipFile is closed.
     */
    suspend fun useEntries(block: suspend (entry: ZipEntryImpl) -> Boolean)

    /**
     * Adds entries to existing Zip file found in specified directory, or creates a new Zip file if none exists. Entries
     * added in the zip have path names relative from the specified directory.
     * @param directory must be a File instance where isDirectory is true. Directory contents is enumerated.
     * @param shallow If true only the first-level contents of the directory are processed. If false, a left-wise
     * recursive enumeration of all subdirectories and their files is performed.
     * @param filter optional lambda if filtering of file names found is desired, should return true to add file.
     */
    suspend fun zipDirectory(directory: File,
                             shallow: Boolean = false,
                             filter: (suspend (pathName: String) -> Boolean)? = null)

    /**
     * Extracts the content of the Zip file into the specified directory.
     * @param directory must be a File instance where isDirectory is true.
     * @param filter optional function returns true to extract file, false to skip
     * @param mapPath optional transform of path in ZipEntry to path used relative to the target directory. Useful
     * for zip files that have non-relative paths, or other cases where file name/path changes desired.
     * @return List of File objects extracted.
     */
    suspend fun extractToDirectory(directory: File,
                                   filter: (suspend (entry: ZipEntryImpl) -> Boolean)? = null,
                                   mapPath: ((entry: ZipEntryImpl) -> String)? = null): List<File>
}

/**
 * The exception strategy for this implementation is for any functions that are value-checking arguments to throw
 * [IllegalArgumentException] for bad ones.  All other exceptions detected or caught throw [ZipException]
 */
class ZipException(message: String): Exception(message)

class ZipEntryImpl(
    val record: ZipDirectoryRecord
) {
    val entry = ZipEntry(record.name, record.comment, record.extra)
}

/**
 * This is a pure Kotlin MP implementation of a subset of the Zip specification. Zip Files can be read, created, updated,
 * merged. There are methods to assist with adding raw content (binary) and text content with charset encdoing.
 * Support includes:
 * Zip and Zip64 formats
 * Support does not include:
 * multi-"disk" or segmented disk files
 * encryption
 */
class ZipFileImpl(
    fileArg: File,
    val mode: FileMode
) :ZipFileBase {

    val map = mutableMapOf<String, ZipEntryImpl>()
    override val bufferSize = 4096
    override val entries get() = map.values.map { it.entry }.toList()
    override val file: RawFile = RawFile(fileArg, mode, FileSource.File)

    private var eocdPosition: ULong = 0u

    override suspend fun addEntry(entry: ZipEntry) {
        TODO("Not yet implemented")
    }

    override suspend fun addEntry(entry: ZipEntry, block: suspend (content: ByteArray) -> Int) {
        TODO("Not yet implemented")
    }

    override suspend fun addTextEntry(
        entry: ZipEntry,
        charset: Charset,
        appendEol: Boolean,
        block: suspend () -> String
    ) {
        TODO("Not yet implemented")
    }

    override fun close() {
        file.close()
    }

    override fun merge(vararg zipFiles: ZipFileImpl): List<ZipEntry> {
        TODO("Not yet implemented")
    }

    override suspend fun open() {
        try {
            when (mode) {
                FileMode.Read -> if (file.size > 0u) {
                    parseDirectory()
                }
                FileMode.Write -> {

                }
            }
        } catch (e: Throwable) {
            close()
            throw e
        }
    }

    override suspend fun readEntry(
        entryName: String,
        block: suspend (entry:ZipEntry, content: ByteArray, bytes: UInt) -> Unit
    ): ZipEntry {
        val entry = map[entryName]
            ?: throw IllegalArgumentException("Entry name: $entryName not a valid entry")
        val record = ZipLocalRecord.decode(file, entry.record.localHeaderOffset.toULong())
        decompress(record, entry.entry, block)
        return entry.entry
    }

    override suspend fun readTextEntry(
        entryName: String,
        charset: Charset,
        block: suspend (text: String) -> Boolean
    ): ZipEntry {
        TODO("Not yet implemented")
    }

    override suspend fun removeEntry(entry: ZipEntry): Boolean {
        if (!map.containsKey(entry.name))
            throw IllegalArgumentException("Entry name ${entry.name} not found in directory")
        TODO("Not yet implemented")
    }

    override suspend fun use(block: suspend () -> Unit) {
        try {
            open()
            block()
        } finally {
            close()
        }
    }

    override suspend fun useEntries(block: suspend (entry: ZipEntryImpl) -> Boolean) {
        try {
            open()
            map.values.forEach {
                block(it)
            }
        } finally {
            close()
        }
    }

    override suspend fun zipDirectory(
        directory: File,
        shallow: Boolean,
        filter: (suspend (pathName: String) -> Boolean)?
    ) {
        TODO("Not yet implemented")
    }

    override suspend fun extractToDirectory(
        directory: File,
        filter: (suspend (entry: ZipEntryImpl) -> Boolean)?,
        mapPath: ((entry: ZipEntryImpl) -> String)?
    ): List<File> {
        TODO("Not yet implemented")
    }

    private fun findEocdRecord(): ZipEOCD {
        file.apply {
            val sz = size
            var bufSize = ZipEOCD.minimumLength
            if (sz < ZipEOCD.minimumLength.toUInt())
                throw ZipException("Zip file minimum size required is $bufSize, found $sz")
            var buf = ByteBuffer(bufSize)
            var pos = sz - ZipEOCD.minimumLength.toULong()
            read(buf, pos)
            var found = false
            do {
                buf.rewind()
                val x = ZipEOCD.findSignature(buf)
                if (x >= 0) {
                    eocdPosition = pos + x.toULong()
                    found = true
                    break
                }
                // search backwards in bufferSize chunks looking for signature. Max 64K + 22 since comment max size is 64k
                bufSize += bufferSize
                if (pos == 0UL || (bufSize > (ZipEOCD.minimumLength + Short.MAX_VALUE))) break
                buf = ByteBuffer(bufSize)
                pos = min(0u, pos - bufferSize.toULong())
            } while (read(buf, pos) > 0u)
            if (!found)
                throw ZipException("Invalid Zip file ${file.path}, no EOCD record found")
            return ZipEOCD.decode(buf)
        }
    }

    private fun findZip64Eocd(eocd: ZipEOCD): ZipEOCD64 {
        //find locator, use it to find EOCD64
        val sz = ZipEOCD64Locator.minimumLength.toUInt()
        val buf = ByteBuffer(sz.toInt())
        if (eocdPosition <= sz)
            throw ZipException("Zip is Zip64, but no EOCD64 locator record found")
        val count = file.read(buf, eocdPosition - sz)
        if (count != sz)
            throw ZipException("Zip is Zip64, but no EOCD64 locator record found")
        buf.rewind()
        val locator = ZipEOCD64Locator.decode(buf)
        return ZipEOCD64.decode(file, locator.eocdOffset.toULong())
    }


    private fun parseDirectory() {
        val eocd = findEocdRecord()
        if (eocd.isZip64)
            findZip64Eocd(eocd)
        else
            ZipEOCD64.upgrade(eocd)

        file.position = eocd.directoryOffset.toULong()
        map.clear()
        for (index in 0 until eocd.entryCount) {
            val record = ZipDirectoryRecord.decode(file)
            map[record.name] = ZipEntryImpl(record)
        }
    }

    private suspend fun decompress(
        record: ZipLocalRecord,
        entry: ZipEntry,
        block: suspend (entry: ZipEntry, content: ByteArray, bytes: UInt) -> Unit) {
        var remaining = record.compressedSize
        var uncompressedCount = 0UL
        val buf = ByteBuffer(bufferSize)
        val crc = Crc32()
        while (remaining > 0u) {
            buf.positionLimit(0, min(buf.capacity, remaining.toInt()))
            var count = file.read(buf)
            buf.positionLimit(0, count.toInt())
            when (record.algorithm) {
                CompressionAlgorithms.None -> block(entry, buf.getBytes(count.toInt()), count)
                else ->
                    Compression(record.algorithm).apply {
                        decompress(buf) {
                            val outCount = it.remaining.toUInt()
                            uncompressedCount += outCount
                            val uncompressedContent = it.getBytes(outCount.toInt())
                            crc.update(uncompressedContent)
                            block(entry, uncompressedContent, outCount)
                            if (remaining - count > 0u) {
                                buf.clear()
                                count = file.read(buf)
                                buf.positionLimit(0, count.toInt())
                            }
                            else buf.positionLimit(0, 0)
                            buf
                        }
                    }
            }
            remaining -= count.toULong()
        }
        if (uncompressedCount != record.uncompressedSize) {
            throw ZipException("Uncompressing file ${record.name}, expected uncompressed: ${record.uncompressedSize}, found: $uncompressedCount")
        }
        if (crc.result != record.crc32) {
            throw ZipException("CRC32 values don't match. Entry CRC: ${record.crc32.toString(16)}, content CRC: ${crc.result.toString(16)}")
        }
    }
}