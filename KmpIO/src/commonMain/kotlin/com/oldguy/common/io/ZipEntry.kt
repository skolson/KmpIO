package com.oldguy.common.io

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Combines directory and local directory header [ZipExtraParser] instances. Functions:
 *  - uncompressed and compressed values independent of Zip64 usage
 *  - comparisons of [ZipLocalRecord] and [ZipDirectoryRecord] content that should match
 *  - extra parsing/decoding using the provided ZipExtraFactory
 *  Used by [ZipEntry] for parsing
 */
class ZipDirectory(
    var directoryRecord: ZipDirectoryRecord,
    val parserFactory: ExtraParserFactory
) {
    val parser get() = parserFactory(directoryRecord)
    var localParser = parserFactory(ZipLocalRecord(directoryRecord))

    private val directory get() = parser.directory as ZipDirectoryRecord
    val localDirectory get() = localParser.directory

    val extras get() = parser.decode()
    val localExtras get() = localParser.decode()
    val extraZip64: ZipExtraZip64? get() =
        extras.firstOrNull { it.signature == ZipExtraParser.zip64Signature } as ZipExtraZip64?

    val compressedSize: ULong
        get() = extraZip64?.let {
            if (it.compressedSize >= 0)
                it.compressedSize.toULong()
            else null
        } ?: if (directory.intCompressedSize > 0)
            directory.intCompressedSize.toULong()
        else
            throw IllegalStateException("Bug: using compressedSize property when negative")

    val uncompressedSize: ULong
        get() = extraZip64?.let {
            if (it.uncompressedSize >= 0)
                it.uncompressedSize.toULong()
            else null
        } ?: if (directory.intUncompressedSize > 0)
            directory.intUncompressedSize.toULong()
        else
            throw IllegalStateException("Bug: using uncompressedSize property when negative")

    val localHeaderOffset: ULong
        get() = extraZip64?.let {
            if (it.localHeaderOffset >= 0)
                it.localHeaderOffset.toULong()
            else null
        } ?: directory.intLocalHeaderOffset.toULong()


    /**
     * If any of the values that are supposed to be copies of the [ZipDirectoryRecord] values
     * don't match, throw a ZipException
     */
    fun compare() {
        var fields = ""
        if (directory.intCompressedSize != localDirectory.intCompressedSize) fields = "compressed"
        if (directory.intUncompressedSize != localDirectory.intUncompressedSize) fields += " uncompressed"
        if (directory.crc32 != localDirectory.crc32) fields = " CRC32"
        if (directory.generalPurpose != localDirectory.generalPurpose) fields = " generalPurpose"
        if (directory.compression != localDirectory.compression) fields = " compressionMethod"
        if (directory.name != localDirectory.name) fields = " name"
        if (fields.isNotEmpty())
            throw ZipException("Entry ${directory.name} unequal fields: $fields ")
    }

    /**
     * Use this to add entries to a list, guarantees all signatures are unique.
     * If matching signature is already present, it is replaced.
     * Both [ZipDirectoryRecord] and [ZipLocalRecord] extra content will updated
     */
    fun addOrReplace(extra: ZipExtra) {
        val newList = extras.toMutableList()
        newList.removeAll { it.signature == extra.signature }
        newList.add(extra)
        parser.encode(newList)
        localParser.encode(newList)
    }

    fun update(localRecord: ZipLocalRecord) {
        localParser = parserFactory(localRecord)
        if (directory.isZip64) {
            localParser.verifyZip64(this)
        }
    }

    fun update(directoryRecord: ZipDirectoryRecord) {
        this.directoryRecord = directoryRecord
        localParser = parserFactory(ZipLocalRecord(directoryRecord))
    }
}

/**
 * Represents a Zip file entry. Contains .
 *
 * Contains default properties for all of the required fields in the Zip specification when creating
 * entries. Many of the properties can be changed for use during save.
 *
 * @param directoryRecordArg typically decoded from an input file
 * @param parserFactory from the ZipFile instance, provides the factory class for parsing extra data to a List<ZipExtra>
 *     instances, or encoding a List<ZipExtra> instances to extra data.
 */
class ZipEntry(
    directoryRecordArg:ZipDirectoryRecord,
    parserFactory: ExtraParserFactory
) {
    val entryDirectory = ZipDirectory(directoryRecordArg, parserFactory)
    val extraParser get() = entryDirectory.parser
    val localExtraParser get() = entryDirectory.localParser
    val directory get() = entryDirectory.directoryRecord
    val name get() = directory.name
    val comment get() = directory.comment

    var compression: Compression = CompressionDeflate(true)
        set(value) {
            field = value
            updateDirectory()
        }
    var deflateStrategy = CompressionDeflate.Strategy.Default

    val algorithm get() = when (compression) {
        is CompressionNone -> CompressionAlgorithms.None
        is CompressionDeflate -> CompressionAlgorithms.Deflate
        else -> throw ZipException("Bug: missing algorithm for subclass of compression: ${compression::class.simpleName ?: ""}")
    }
    val compressionValue: Short get() = when (algorithm) {
        CompressionAlgorithms.None -> 0
        CompressionAlgorithms.Deflate -> 8
        CompressionAlgorithms.BZip2 -> 12
        CompressionAlgorithms.LZMA -> 14
    }
    val timeModified get() = directory.zipTime.zipTime

    val dateModified get() = timeModified.date
    val zipTime get() = ZipTime(timeModified)

    constructor(
        nameArg: String,
        isZip64: Boolean = false,
        commentArg: String = "",
        parserFactory: ExtraParserFactory = ZipFile.defaultExtraParser,
        extraArg: List<ZipExtra> = emptyList(),
        lastModTime: LocalDateTime = defaultDateTime
    ): this(
        ZipDirectoryRecord(
            defaultVersion,
            defaultVersion,
            ZipGeneralPurpose.defaultValue.shortValue,
            defaultCompressionValue,
            ZipTime(lastModTime),
            0,
            if (isZip64) -1 else 0,
            if (isZip64) -1 else 0,
            nameArg.length.toShort(),
            ZipExtraParser.contentLength(extraArg).toShort(),
            commentArg.length.toShort(),
            0,
            0,
            0,
            if (isZip64) -1 else 0,
            nameArg,
            ByteArray(0),
            commentArg
        ),
        parserFactory
    ) {
        extraParser.encode(extraArg)
        localExtraParser.encode(extraArg)
    }

    init {
        compression = when (directory.algorithm) {
            CompressionAlgorithms.None -> CompressionNone()
            CompressionAlgorithms.Deflate -> CompressionDeflate(true)
            CompressionAlgorithms.BZip2,
            CompressionAlgorithms.LZMA ->
                throw ZipException("Unsupported compression algorithm ${directory.algorithm}")
        }
    }

    fun addOrReplace(extra: ZipExtra) {
        entryDirectory.addOrReplace(extra)
    }

    fun updateDirectory(
        isZip64: Boolean,
        compressedSize: ULong,
        uncompressedSize: ULong,
        crc: Int,
        localFileOffset: ULong
    ) {
        entryDirectory.update(ZipDirectoryRecord(
            directory.version,
            directory.versionMinimum,
            ZipGeneralPurpose.defaultValue.shortValue,
            compressionValue,
            zipTime,
            crc,
            if (isZip64) -1 else compressedSize.toInt(),
            if (isZip64) -1 else uncompressedSize.toInt(),
            directory.name.length.toShort(),
            directory.extraLength,
            directory.commentLength,
            directory.diskNumber,
            directory.internalAttributes,
            directory.externalAttributes,
            if (isZip64) -1 else localFileOffset.toInt(),
            directory.name,
            directory.extra,
            directory.comment
        ))
    }

    /**
     * Since the directory record objects are immutable, changes to state require a new instance.
     * This is a copy constructor of the current directory using updated values from the entry.
     */
    private fun updateDirectory() {
        entryDirectory.update(ZipDirectoryRecord(
            directory.version,
            directory.versionMinimum,
            ZipGeneralPurpose.defaultValue.shortValue,
            compressionValue,
            zipTime,
            directory.crc32,
            directory.intCompressedSize,
            directory.intUncompressedSize,
            directory.name.length.toShort(),
            directory.extra.size.toShort(),
            directory.commentLength,
            directory.diskNumber,
            directory.internalAttributes,
            directory.externalAttributes,
            directory.intLocalHeaderOffset,
            directory.name,
            directory.extra,
            directory.comment
        ))
    }

    companion object {
        val defaultVersion = ZipVersion(4, 5)
        const val defaultCompressionValue: Short = 8
        val defaultDateTime get() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

    }
}