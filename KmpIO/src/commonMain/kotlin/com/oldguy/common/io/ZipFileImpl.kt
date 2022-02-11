package com.oldguy.common.io

import kotlin.math.min

interface ZipFileBase: Closeable {
    val entries: List<ZipEntry>
    val file: RawFile
    val bufferSize: Int

    suspend fun addEntry(entry: ZipEntry)
    suspend fun addEntry(
        entry: ZipEntry,
        block: suspend (content: ByteArray) -> Int
    )
    suspend fun addTextEntry(
        entry: ZipEntry,
        charset: Charset = Charset(Charsets.Utf8),
        appendEol: Boolean = true,
        block: suspend () -> String
    )
    fun merge(vararg zipFiles: ZipFile)
    suspend fun open()
    suspend fun readEntry(
        entryName: String,
        block: suspend (entry:ZipEntry, content: ByteArray, bytes: UInt) -> Unit
    ): ZipEntry
    suspend fun readTextEntry(
        entryName: String,
        charset: Charset = Charset(Charsets.Utf8),
        block: suspend (text: String) -> Boolean
    ): ZipEntry
    suspend fun use(block: suspend () -> Unit)
    suspend fun useEntries(block: suspend (entry: ZipEntry) -> Boolean)
    suspend fun zipDirectory(directory: File, filter: suspend (pathName: String) -> Boolean)
    suspend fun extractToDirectory(directory: File, filter: suspend (entry: ZipEntryImpl) -> Boolean)
}

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

    override fun merge(vararg zipFiles: ZipFile) {
        TODO("Not yet implemented")
    }

    override suspend fun open() {
        if (mode == FileMode.Read || (file.size > 0u)) {
            val eocd = findEocdRecord()
            parseDirectory(
                if (eocd.isZip64)
                    findZip64Eocd(eocd)
                else
                    ZipEOCD64.upgrade(eocd)
            )
        }
    }

    override suspend fun readEntry(
        entryName: String,
        block: suspend (entry:ZipEntry, content: ByteArray, bytes: UInt) -> Unit
    ): ZipEntry {
        val entry = map[entryName]
            ?: throw IllegalArgumentException("Entry name: $entryName not a valid entry")
        val record = ZipLocalRecord.decode(file, entry.record.localHeaderOffset.toULong())
        decompress(file, record, entry.entry, block)
        return entry.entry
    }

    override suspend fun readTextEntry(
        entryName: String,
        charset: Charset,
        block: suspend (text: String) -> Boolean
    ): ZipEntry {
        TODO("Not yet implemented")
    }

    override suspend fun use(block: suspend () -> Unit) {
        TODO("Not yet implemented")
    }

    override suspend fun useEntries(block: suspend (entry: ZipEntry) -> Boolean) {
        TODO("Not yet implemented")
    }

    override suspend fun zipDirectory(directory: File, filter: suspend (pathName: String) -> Boolean) {
        TODO()
    }

    override suspend fun extractToDirectory(directory: File, filter: suspend (entry: ZipEntryImpl) -> Boolean) {
        TODO()
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


    private fun parseDirectory(eocd: ZipEOCD64) {
        file.position = eocd.directoryOffset.toULong()
        map.clear()
        for (index in 0 until eocd.entryCount) {
            val record = ZipDirectoryRecord.decode(file)
            map[record.name] = ZipEntryImpl(record)
        }
    }

    private suspend fun decompress(
        file: RawFile,
        record: ZipLocalRecord,
        entry: ZipEntry,
        block: suspend (entry: ZipEntry, content: ByteArray, bytes: UInt) -> Unit) {
        var remaining = record.compressedSize
        var uncompressedCount = 0UL
        val buf = ByteBuffer(bufferSize)
        while (remaining > 0u) {
            buf.positionLimit(0, min(buf.capacity, remaining.toInt()))
            var count = file.read(buf).toUInt()
            buf.positionLimit(0, count.toInt())
            when (record.algorithm) {
                CompressionAlgorithms.None -> block(entry, buf.getBytes(count.toInt()), count)
                else ->
                    Compression(record.algorithm).apply {
                        decompress(buf) {
                            val outCount = it.remaining.toUInt()
                            uncompressedCount += outCount
                            block(entry, it.getBytes(outCount.toInt()), outCount)
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
    }
}