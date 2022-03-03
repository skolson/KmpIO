package com.oldguy.common.io

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.launch
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
    var isZip64: Boolean
    var comment: String
    var textEndOfLine: String

    suspend fun exists(): Boolean {
        return file.file.exists
    }

    /**
     * Add one entry to a FileMode.Write file.  The entry is added after any existing entries in the zip file, and the
     * directory structures are rewritten after each add. No content is added for this entry, use this for directory
     * entries.
     * @param entry to be added. If name matches existing entry, exception is thrown.
     */
    suspend fun addEntry(entry: ZipEntry)
    /**
     * Add one entry to a FileMode.Write file.  The entry is added after any existing entries in the zip file, and the
     * directory structures are not written until [finish] or [close]. Use the lambda for adding content for this entry.
     * @param entry to be added. If name matches existing entry, exception is thrown.
     * @param inputBlock function called repeatedly until content argument is empty array. Implementation
     * should return a full ByteArray containing bytes to be compressed. All of array will be compressed
     * and written to the zip file.Pass an empty ByteArray to signal end of data for the entry.
     */
    suspend fun addEntry(
        entry: ZipEntry,
        inputBlock: suspend () -> ByteArray
    )

    /**
     * Add one entry to a FileMode.Write file.  The entry is added after any existing entries in the zip file, and the
     * directory structures are not written until [finish] or [close]. Use the lambda for adding content for this entry.
     * @param entry to be added. If name matches existing entry, exception is thrown.
     * @param inputBlock function called repeatedly until content argument is ByteBuffer with hasRemaining == false. Data
     * will be read from buffer's position for remaining bytes. Zero remaining indicates end of input. Data will be
     * compressed using the compression algorithm specified by the [ZipEntry].
     */
    suspend fun addEntryBuffered(
        entry: ZipEntry,
        inputBlock: suspend () -> ByteBuffer
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
     * Copy one entry from the source Zip to this one.
     * @param sourceZip source file, treated as read-only
     * @param sourceEntry must be one of the valid entries in sourceZip
     * @param newEntry will be saved into this zip, with a copy of the data from [sourceEntry]
     */
    suspend fun copyEntry(
        sourceZip:ZipFile,
        sourceEntry: ZipEntry,
        newEntry: ZipEntry
    )

    /**
     * Call this on FileMode.Write files after one or more add/merge operations to save the
     * updated directory structure.  If not called explicitly, will be called on close. After the
     * first addEntry, readEntry calls will cause exceptions. finish() must be called before readEntry
     * calls are usable again.
     */
    suspend fun finish()

    /**
     * Merges the specified Zip files into this one. Input zipfile entries with matching paths are ignored. Each new
     * entry is added to the end of this one. Directory is re-written after all entries have been copied.
     * @param zipFile ZipFile instance whose entries are merged into the current Zip. File is opened read-only.
     * @param filter invoked once for each entry found in the zip file being merged, Arguments are:
     * original: entry from the file being merged.
     * new: the ZipEntry copied from the original
     * IF function provided, must return true to have entry merged, false to skip. Function can make changes to the new
     * instance since this is invoked before actual writing of the entry and its data.
     * @return list of entries added/merged
     */
    suspend fun merge(
        zipFile: ZipFile,
        filter: ((original: ZipEntry, new: ZipEntry) -> Boolean)? = null
    ): List<ZipEntry>

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
     * last - true on last call, all bytes read. false otherwise
     * @return ZipEntry found, after all content retrieved.
     */
    suspend fun readEntry(
        entryName: String,
        block: suspend (entry:ZipEntry, content: ByteArray, count: UInt, last: Boolean) -> Unit
    ): ZipEntry

    /**
     * Reads the content from one entry.
     * @param entry if no matching name found in directory, exception is thrown
     * @param block is invoked repeatedly with uncompressed content. Arguments are;
     * entry - ZipEntry found with the specified name,
     * content - containing uncompressed data
     * count - number of uncompressed bytes, starting at index 0, in content
     * last - true on last call, all bytes read. false otherwise
     * @return ZipEntry found, after all content retrieved.
     */
    suspend fun readEntry(
        entry: ZipEntry,
        block: suspend (entry:ZipEntry, content: ByteArray, count: UInt, last: Boolean) -> Unit
    )

    /**
     * Same setup as [readEntry], with the additional feature that content is read, uncompressed, decoded using the
     * specified Charset.
     * @param entryName if no matching name found in directory, exception is thrown
     * @param charset to be used when decoding uncompressed data to a String.
     * @param block is invoked repeatedly with uncompressed content in blocks of [bufferSize] bytes. Arguments are;
     * text - containing uncompressed string decoded from the byte content using the Charset
     * last - true on last call, all bytes read. false otherwise
     * @return ZipEntry found, after all content retrieved.
     */
    suspend fun readTextEntry(
        entryName: String,
        charset: Charset = Charset(Charsets.Utf8),
        block: suspend (text: String, last: Boolean) -> Unit
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
    suspend fun use(block: suspend (file: ZipFile) -> Unit)

    /**
     * Convenience wrapper for existing files only. Does open, followed by invoking [block] for each entry found.
     * After all entries processed, or from uncaught exception, ZipFile is closed.
     */
    suspend fun useEntries(block: suspend (entry: ZipEntry) -> Boolean)

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
     * Add one entry built from a ZipEntry. Convenience method with all ZipEntry setup is defaulted.
     * @param inputFile file to add
     * @param entryName defaults to name of file with no path.
     */
    suspend fun zipFile(
        inputFile: File,
        entryName: String = inputFile.name
    )

    /**
     * Extracts the content of the Zip file into the specified directory.
     * @param directory must be a File instance where isDirectory is true.
     * @param filter optional function returns true to extract file, false to skip
     * @param mapPath optional transform of path in ZipEntry to path used relative to the target directory. Useful
     * for zip files that have non-relative paths, or other cases where file name/path changes desired.
     * @return List of File objects extracted.
     */
    suspend fun extractToDirectory(directory: File,
                                   filter: (suspend (entry: ZipEntry) -> Boolean)? = null,
                                   mapPath: ((entry: ZipEntry) -> String)? = null): List<File>
}

/**
 * The exception strategy for this implementation is for any functions that are value-checking arguments to throw
 * [IllegalArgumentException] for bad ones.  All other exceptions detected or caught throw [ZipException]
 */
class ZipException(message: String): Exception(message)

typealias ExtraParserFactory = ((directory: ZipDirectoryCommon) -> ZipExtraParser)

/**
 * This is a pure Kotlin MP implementation of a subset of the Zip specification. Zip Files can be read, created, updated,
 * merged. There are methods to assist with adding raw content (binary) and text content with charset encdoing.
 * Support includes:
 * Zip and Zip64 formats
 * Deflate compression
 * Support does not include:
 * multi-"disk" or segmented disk files
 * encryption
 */
class ZipFile(
    fileArg: File,
    val mode: FileMode = FileMode.Read
) :ZipFileBase {

    val map = mutableMapOf<String, ZipEntry>()
    override var bufferSize = 4096
    override val entries get() = map.values.toList()
    override val file: RawFile = RawFile(fileArg, mode, FileSource.File)
    override var isZip64: Boolean = false
    override var textEndOfLine: String = "\n"

    /**
     * Set this to provide a sub-class of ZipExtraFactory that supports additional subclasses
     * of the ZipExtra class. Default is [ZipExtraParser].
     */
    var parser: ExtraParserFactory = { it ->
        ZipExtraParser(it)
    }

    private var eocdPosition: ULong = 0u
    private var buffer = ByteBuffer(bufferSize)
    private var isOpen = false

    private var directoryPosition = 0UL
    private var pendingChanges = mode == FileMode.Write && file.size == 0UL
    override var comment = ""
        set(value) {
            field = value
            if (value.length > maxCommentLength)
                throw ZipException("Zip file comment length: ${value.length} greater than supported limit of $maxCommentLength")
        }

    override suspend fun addEntry(entry: ZipEntry) {
        insertEntry(entry)
        writeEntry(entry) {
            WriteResult(0UL, 0UL, 0)
        }
    }

    private data class WriteResult(
        val compressed: ULong,
        val uncompressed: ULong,
        val crc: Int
    )

    override suspend fun addEntryBuffered(entry: ZipEntry,
                                  inputBlock: suspend () -> ByteBuffer) {
        addEntry(entry) {
            inputBlock().getBytes()
        }
    }

    override suspend fun addEntry(entry: ZipEntry,
                                  inputBlock: suspend () -> ByteArray) {
        insertEntry(entry)
        writeEntry(entry) {
            var uncompressed = 0UL
            val crc = Crc32()
            val compressed = entry.compression.compressArray(
                input = {
                    inputBlock().also {
                        uncompressed += it.size.toUInt()
                        crc.update(it)
                    }
                }
            ) {
                file.write(ByteBuffer(it))
            }
            WriteResult(compressed, uncompressed, crc.result)
        }
    }

    private fun insertEntry(entry: ZipEntry) {
        checkWriteMode()
        if (map.containsKey(entry.name))
            throw IllegalArgumentException("Entry with name ${entry.name} already added")
        map[entry.name] = entry
        pendingChanges = true
    }

    private suspend fun writeEntry(entry: ZipEntry, content: suspend (entry: ZipEntry) -> WriteResult) {
        file.position = directoryPosition
        val savePosition = directoryPosition
        saveLocal(entry)
        content(entry).apply {
            if (!isZip64 && uncompressed > Int.MAX_VALUE.toULong()) {
                file.position = savePosition
                throw ZipException("Zip64 is not enabled, but entry size: $uncompressed requires Zip64. Entry not saved")
            }
            pendingChanges = true
            entry.updateDirectory(
                isZip64,
                compressedSize = compressed,
                uncompressedSize = uncompressed,
                crc,
                savePosition
            )
            if (entry.directory.generalPurpose.isDataDescriptor) {
                ZipDataDescriptor(crc, compressed, uncompressed).apply {
                    allocateBuffer(isZip64).apply {
                        encode(this, isZip64)
                        file.write(this)
                    }
                }
            }
            directoryPosition = file.position
        }
    }

    override suspend fun addTextEntry(
        entry: ZipEntry,
        charset: Charset,
        appendEol: Boolean,
        block: suspend () -> String
    ) {
        addEntry(entry) {
            val s = block()
            if (s.isEmpty())
                ByteArray(0)
            else
                charset.encode(if (appendEol) s + textEndOfLine else s)
        }
    }

    override suspend fun close() {
        if (pendingChanges) finish()
        file.close()
        isOpen = false
    }

    override suspend fun finish() {
        if (!pendingChanges) return
        saveDirectory()
        pendingChanges = false
    }

    override suspend fun merge(
        zipFile: ZipFile,
        filter: ((original: ZipEntry, new: ZipEntry) -> Boolean)?
    ): List<ZipEntry> {
        checkWriteMode()
        val list = mutableListOf<ZipEntry>()
        if (zipFile.exists()) {
            zipFile.use { merge ->
                merge.entries.forEach {
                    val newEntry = ZipEntry(
                        it.name,
                        isZip64,
                        it.comment,
                        extraArg = it.directories.extras,
                        lastModTime = it.zipTime.zipTime
                    )
                    if (filter?.invoke(it, newEntry) != false) {
                        copyEntry(zipFile, it, newEntry)
                        list.add(newEntry)
                    }
                }
            }
        }
        return list
    }

    override suspend fun copyEntry(sourceZip: ZipFile, sourceEntry: ZipEntry, newEntry: ZipEntry) {
        val ch = Channel<ByteArray>(5)
        CoroutineScope(Dispatchers.Default).launch {
            sourceZip.readEntry(sourceEntry) { _, content, count, _ ->
                ch.send(content.sliceArray(0 until count.toInt()))
            }
            ch.close()
        }
        addEntry(newEntry) {
            try {
                ch.receive()
            } catch (e: ClosedReceiveChannelException) {
                ByteArray(0)
            }
        }
    }

    override suspend fun open() {
        if (isOpen) return
        try {
            if (file.size > 0u)
                parseDirectory()
            else when (mode) {
                FileMode.Read -> throw ZipException("File: ${file.file.path} is empty, cannot be read")
                FileMode.Write -> directoryPosition = 0UL
            }
            isOpen = true
        } catch (e: Throwable) {
            close()
            isOpen = false
            throw e
        }
    }

    override suspend fun readEntry(
        entryName: String,
        block: suspend (entry:ZipEntry, content: ByteArray, count: UInt, last: Boolean) -> Unit
    ): ZipEntry {
        if (pendingChanges)
            throw ZipException("Pending changes not saved to directory. Call close() or finish()")
        (map[entryName]
            ?: throw IllegalArgumentException("Entry name: $entryName not a valid entry")).apply {
            readEntry(this, block)
            return this
        }
    }

    override suspend fun readEntry(
        entry: ZipEntry,
        block: suspend (entry:ZipEntry, content: ByteArray, count: UInt, last: Boolean) -> Unit
    ) {
        ZipLocalRecord.decode(file, entry.directories.localHeaderOffset).apply {
            entry.directories.update(this)
            entry.directories.apply {
                decompress(entry, block)
                if (generalPurpose.isDataDescriptor) {
                    if (!hasDataDescriptor)
                        throw ZipException("Entry ${entry.name} general purpose bits specify Data Descriptor present after data, but local record content does not")
                    ZipDataDescriptor.decode(file, isZip64).also {
                        if (it.compressedSize != compressedSize)
                            throw ZipException("Uncompressing file $name, data descriptor compressed: ${it.compressedSize}, expected: $compressedSize")
                        if (it.uncompressedSize != uncompressedSize)
                            throw ZipException("Uncompressing file $name, data descriptor uncompressed: ${it.uncompressedSize}, expected: $uncompressedSize")
                        if (it.crc32 != entry.directory.crc32)
                            throw ZipException(
                                "Reading file $name, data descriptor crc: ${
                                    it.crc32.toString(
                                        16
                                    )
                                }, expected: ${entry.directory.crc32.toString(16)}"
                            )
                    }
                }
            }
        }
    }

    override suspend fun readTextEntry(
        entryName: String,
        charset: Charset,
        block: suspend (text: String, last: Boolean) -> Unit
    ): ZipEntry {
        map[entryName]?.let {
            readEntry(it) {_, content, count, last ->
                if (count > 0u && content.isNotEmpty()) {
                    block(charset.decode(content), last)
                }
            }
            return it
        } ?: throw IllegalArgumentException("Entry name: $entryName not a valid entry")
    }

    /**
     * Removes entry from map, when [finish] or [close] happens, the new zip directory will be
     * written without this entry. THE DATA for this entry is NOT DELETED.
     */
    override suspend fun removeEntry(entry: ZipEntry): Boolean {
        val rc = map.containsKey(entry.name)
        if (rc)
            map.remove(entry.name)
        return rc
    }

    override suspend fun use(block: suspend (file: ZipFile) -> Unit) {
        try {
            open()
            block(this)
        } finally {
            close()
        }
    }

    override suspend fun useEntries(block: suspend (entry: ZipEntry) -> Boolean) {
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

    override suspend fun zipFile(
        inputFile: File,
        entryName: String
    ) {
        RawFile(inputFile).use { rdr ->
            addEntry(ZipEntry(
                entryName,
                isZip64 = isZip64,
                parserFactory = parser)
            ) {
                buffer.apply {
                    positionLimit(0, bufferSize)
                    rdr.read(this)
                    flip()
                }.getBytes()
            }
        }
    }

    override suspend fun extractToDirectory(
        directory: File,
        filter: (suspend (entry: ZipEntry) -> Boolean)?,
        mapPath: ((entry: ZipEntry) -> String)?
    ): List<File> {
        directory.makeDirectory()
        val list = mutableListOf<File>()
        useEntries {
            if (filter?.invoke(it) != false) {
                val name = mapPath?.invoke(it) ?: it.name
                val f = File(name)
                val d = if (f.directoryPath.isEmpty())
                    directory
                else
                    directory.resolve(f.directoryPath)
                val copy = RawFile(File(d, f.name), FileMode.Write)
                readEntry(it) { _, bytes, _, _ ->
                    copy.write(ByteBuffer(bytes))
                }
                copy.close()
                list.add(copy.file)
            }
            true
        }
        return list
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
    private suspend fun findEocdRecord(): ZipEOCD {
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

    /**
     * Find locator, use it to find EOCD64. save both for later possible saves
     */
    private suspend fun findZip64Eocd(): ZipEOCD64 {
        val sz = ZipEOCD64Locator.length.toUInt()
        val buf = ByteBuffer(sz.toInt())
        if (eocdPosition <= sz)
            throw ZipException("Zip is Zip64, but no EOCD64 locator record found")
        val count = file.read(buf, eocdPosition - sz)
        if (count != sz)
            throw ZipException("Zip is Zip64, but no EOCD64 locator record found")
        buf.rewind()
        ZipEOCD64Locator.decode(buf).also {
            return ZipEOCD64.decode(file, it.eocdOffset)
        }
    }


    private suspend fun parseDirectory() {
        val eocd: ZipEOCD64
        findEocdRecord().apply {
            eocd = if (isZip64)
                findZip64Eocd()
            else
                ZipEOCD64.upgrade(this)
        }

        eocd.directoryOffset.apply {
            file.position = this
            directoryPosition = this
        }
        map.clear()
        for (index in 0UL until eocd.entryCount) {
            ZipDirectoryRecord.decode(file).apply {
                map[name] = ZipEntry(this, parser)
            }
        }
    }

    /**
     * support method used by entry readers. Handles locating and reading content, block by block.
     * Each read of compressed data is [bufferSize] bytes or less. Determines which (if any)
     * decompression is required. Total bytes read from file MUST be equal to the record's
     * compressedSize. Uncompressed total bytes passed to all calls to the [block] function must
     * equal to the record's uncompressedSize. CRC checking is done on the uncompressed data, and
     * that MUST match the records crc32 value.  If any of these checks fail, a ZipException is
     * thrown.
     */
    private suspend fun decompress(
        entry: ZipEntry,
        block: suspend (entry: ZipEntry, content: ByteArray, bytes: UInt, last:Boolean) -> Unit)
    {
        var uncompressedCount = 0UL
        val crc = Crc32()
        entry.compression.apply {
            val buf = ByteBuffer(bufferSize)
            var compressedCount = entry.directories.compressedSize
            uncompressedCount = decompress(
                input = {
                    buf.apply {
                        clear()
                        limit = min(compressedCount, buf.capacity.toULong()).toInt()
                        compressedCount -= file.read(buf)
                        flip()
                    }
                }
            ) {
                uncompressedCount += it.remaining.toUInt()
                val uncompressedContent = it.getBytes()
                crc.update(uncompressedContent)
                val last = uncompressedCount == entry.directories.uncompressedSize
                block(entry, uncompressedContent, uncompressedContent.size.toUInt(), last)
            }
        }
        entry.directories.apply {
            if (uncompressedCount != uncompressedSize) {
                throw ZipException("Uncompressing file ${entry.name}, expected uncompressed: $uncompressedSize, found: $uncompressedCount")
            }
            if (crc.result != entry.directory.crc32) {
                throw ZipException("CRC32 values don't match. Entry CRC: ${entry.directory.crc32.toString(16)}, content CRC: ${crc.result.toString(16)}")
            }
        }
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
                zipFile(path)
                if (!shallow && path.isDirectory)
                    zipOneDirectory(path, shallow, parentPath, filter)
            }
        }
    }

    private fun checkWriteMode() {
        if (mode == FileMode.Read)
            throw IllegalStateException("Cannot alter zip file when using FileMode.Read")
    }

    /**
     * File must be positioned at local header start position.
     */
    private suspend fun saveLocal(entry: ZipEntry) {
        entry.directories.localDirectory.apply {
            allocateBuffer().apply {
                encode(this)
                file.write(this)
            }
        }
    }

    /**
     * Steps:
     * 1 - Save all directory entries at the current file position.
     * 2 - For each entry, save an updated local directory with crc, sizes etc to match directory.
     * 3 - if Zip64, save the EOCD Zip64 record, and the locator record
     * 4 - Save the EOCD record
     */
    private suspend fun saveDirectory() {
        val directoryOffset = file.position
        entries.forEach {
            it.directory.allocateBuffer().apply {
                it.directory.encode(this)
                file.write(this)
            }
        }
        val eocd64Position = file.position
        entries.forEach {
            it.directories.apply {
                file.position = localHeaderOffset
                localDirectory.allocateBuffer().apply {
                    localDirectory.encode(this)
                    file.write(this)
                }
            }
        }
        file.position = eocd64Position
        val directoryLength = eocd64Position - directoryOffset
        if (isZip64) {
            ZipEOCD64(
                ZipEntry.defaultVersion,
                ZipEntry.defaultVersion,
                0,
                0,
                map.size.toULong(),
                map.size.toULong(),
                directoryLength,
                directoryOffset,
                comment
            ).apply {
                allocateBuffer().apply {
                    encode(this)
                    file.write(this)
                }
            }
            ZipEOCD64Locator(
                0,
                eocd64Position,
                0
            ).apply {
                ByteBuffer(ZipEOCD64Locator.length).apply {
                    encode(this)
                    file.write(this)
                }
            }
        }

        (if (isZip64)
            ZipEOCD()
        else
            ZipEOCD(
                0,
                0,
                map.size.toShort(),
                map.size.toShort(),
                directoryLength.toInt(),
                directoryOffset.toInt(),
                comment
            )
        ).apply {
            ByteBuffer(length).apply {
                encode(this)
                file.write(this)
            }
        }
        if (file.size > file.position) file.setLength(file.position)
    }

    companion object {
        // This is a self defense mechanism against huge comment values in Zip64 formats.
        const val maxCommentLength = 2 * 1024 * 1024
        val defaultExtraParser: ExtraParserFactory = { it ->
            ZipExtraParser(it)
        }
    }
}