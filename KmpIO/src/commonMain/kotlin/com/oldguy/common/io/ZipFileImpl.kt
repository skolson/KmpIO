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
     * @param block function called repeatedly until content argument is empty array. Implementation
     * should return a full ByteArray containing bytes to be compressed. All of array will be compressed
     * and written to the zip file.Pass an empty ByteArray to signal end of data for the entry.
     */
    suspend fun addEntry(
        entry: ZipEntry,
        block: suspend () -> ByteArray
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
    ): ZipEntryImpl

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
        block: suspend (text: String) -> Unit
    ): ZipEntryImpl

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
     * Adds entries to existing Zip file found in specified directory, or creates a new Zip file if
     * none exists. Entries added in the zip have path names relative from the specified directory.
     * @param directory must be a File instance where isDirectory is true. Directory contents is enumerated.
     * @param shallow If true only the first-level contents of the directory are processed. If false, a left-wise
     * recursive enumeration of all subdirectories and their files is performed.
     * @param filter optional lambda if filtering of file names found is desired, should return true to add file.
     */
    suspend fun zipDirectory(directory: File,
                             shallow: Boolean = false,
                             filter: ((pathName: String) -> Boolean)? = null)

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
    private var buffer = ByteBuffer(bufferSize)

    override suspend fun addEntry(entry: ZipEntry) {
        TODO("Not yet implemented")
    }

    override suspend fun addEntry(entry: ZipEntry,
                                  block: suspend () -> ByteArray) {
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
        block: suspend (entry:ZipEntry, content: ByteArray, count: UInt) -> Unit
    ): ZipEntryImpl {
        val entry = map[entryName]
            ?: throw IllegalArgumentException("Entry name: $entryName not a valid entry")
        val record = ZipLocalRecord.decode(file, entry.record.localHeaderOffset)
        decompress(record, entry, block)
        return entry
    }

    override suspend fun readTextEntry(
        entryName: String,
        charset: Charset,
        block: suspend (text: String) -> Unit
    ): ZipEntryImpl {
        val entry = map[entryName]
            ?: throw IllegalArgumentException("Entry name: $entryName not a valid entry")
        ZipLocalRecord.decode(file, entry.record.localHeaderOffset).apply {
            decompress(this, entry) { _, content, count ->
                if (count > 0u && content.size > 0) {
                    block(charset.decode(content))
                }
            }
        }
        return entry
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
        filter: ((pathName: String) -> Boolean)?
    ) {
        if (!directory.isDirectory)
            throw IllegalArgumentException("Path ${file.file.path} os not a directory")
        val parentPath = if (directory.path.endsWith(File.pathSeparator))
            directory.path
        else
            directory.path + File.pathSeparator
        zipOneDirectory(directory, shallow, parentPath, filter)
    }

    private suspend fun zipOneDirectory(directory: File,
                        shallow: Boolean,
                        parentPath: String,
                        filter: ((pathName: String) -> Boolean)?)
    {
        directory.listFiles.forEach { path ->
            val name = (if (path.isDirectory && !path.path.endsWith(File.pathSeparator))
                path.path + File.pathSeparator
            else
                path.path).replace(parentPath, "")
            if (filter?.invoke(name) != false) {
                RawFile(path).use { rdr ->
                    addEntry(ZipEntry(rdr.file.name)) {
                        buffer.apply {
                            positionLimit(0, bufferSize)
                            val count = rdr.read(this).toInt()
                            positionLimit(0, count)
                        }.getBytes()
                    }
                }
                if (!shallow && path.isDirectory)
                    zipOneDirectory(path, shallow, parentPath, filter)
            }
        }
    }

    override suspend fun extractToDirectory(
        directory: File,
        filter: (suspend (entry: ZipEntryImpl) -> Boolean)?,
        mapPath: ((entry: ZipEntryImpl) -> String)?
    ): List<File> {
        TODO("Not yet implemented")
    }

    /**
     * Rest of methods in the class are private and manage the processing of the various ZipRecord
     * implementations required during reading and writing zip files.
     */

    /**
     * Finds the trailing End of Central Directory (EOCD) record that all zip files must have.  Note
     * that this record has a variable length comment that can be up to 64K long. So when a comment
     * is present, the EOCD record signature must be searched. This implementation is:
     * - read from EOF - [ZipEOCD.minimumLength] to check for signature of EOCD with no comment
     * - if not found, read back another [bufferSize] and search the content from the end of
     * buffer to zero looking for the 4 byte EOCD signature
     * - if still not found, back up another [bufferSize] and search again.
     * - if signature not found after reading more that [64K + 22] bytes from the EOF, throw a ZipException
     * exception.
     */
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
        val eocd: ZipEOCD64
        findEocdRecord().apply {
            eocd = if (isZip64)
                findZip64Eocd(this)
            else
                ZipEOCD64.upgrade(this)
        }

        file.position = eocd.directoryOffset.toULong()
        map.clear()
        for (index in 0 until eocd.entryCount) {
            val record = ZipDirectoryRecord.decode(file)
            map[record.name] = ZipEntryImpl(record)
        }
    }

    /**
     * support method used by entry readers. Handles locating and reading content, block by block.
     * Each read of compressed data is [bufferSize] bytes or less. Determines which (if any)
     * decompression is required. Total bytes read from file MUST be equal to the record's
     * compressedSize. Uncompressed total bytes passed to all calls to the [block] function must
     * equal to the record's [uncompressedSize]. CRC checking is done on the uncompressed data, and
     * that MUST match the records [crc32] value.  If any of these checks fail, a ZipException is
     * thrown.
     */
    private suspend fun decompress(
        record: ZipLocalRecord,
        entry: ZipEntryImpl,
        block: suspend (entry: ZipEntry, content: ByteArray, bytes: UInt) -> Unit) {
        var uncompressedCount = 0UL
        val crc = Crc32()
        when (record.algorithm) {
            CompressionAlgorithms.None -> {
                var remaining = record.compressedSize
                val buf = ByteBuffer(bufferSize)
                while (remaining > 0u) {
                    buf.positionLimit(0, min(bufferSize.toULong(), remaining).toInt())
                    val length = buf.remaining.toUInt()
                    val count = file.read(buf)
                    buf.positionLimit(0, count.toInt())
                    if (count != length)
                        throw ZipException("Read error on entry: ${entry.entry.name}, expected $length bytes, read $count}")
                    val uncompressedContent = buf.getBytes(buf.remaining)
                    crc.update(uncompressedContent)
                    block(entry.entry, uncompressedContent, count)
                    remaining -= count
                }
            }
            else ->
                Compression(record.algorithm).apply {
                    val buf = ByteBuffer(bufferSize)
                    uncompressedCount = decompress(
                        record.compressedSize,
                        4096u,
                        input = { bytesToRead ->
                            buf.positionLimit(0, bytesToRead)
                            val c = file.read(buf)
                            buf.positionLimit(0, c.toInt())
                            buf
                        }) {
                            val outCount = it.remaining.toUInt()
                            uncompressedCount += outCount
                            val uncompressedContent = it.getBytes(outCount.toInt())
                            crc.update(uncompressedContent)
                            block(entry.entry, uncompressedContent, outCount)
                        }
                }
        }
        var workCrc = record.crc32
        if (record.generalPurpose.isDataDescriptor) {
            ZipDataDescriptor.decode(file, record.isZip64).also {
                workCrc = it.crc32
                if (it.compressedSize.toULong() != record.compressedSize)
                    throw ZipException("Uncompressing file ${record.name}, data descriptor compressed: ${it.compressedSize}, expected: $${record.compressedSize}")
                if (it.uncompressedSize.toULong() != record.uncompressedSize)
                    throw ZipException("Uncompressing file ${record.name}, data descriptor uncompressed: ${it.uncompressedSize}, expected: ${record.uncompressedSize}")
            }
        } else if (uncompressedCount != record.uncompressedSize) {
            throw ZipException("Uncompressing file ${record.name}, expected uncompressed: ${record.uncompressedSize}, found: $uncompressedCount")
        }
        if (crc.result != workCrc) {
            throw ZipException("CRC32 values don't match. Entry CRC: ${record.crc32.toString(16)}, content CRC: ${crc.result.toString(16)}")
        }
    }
}