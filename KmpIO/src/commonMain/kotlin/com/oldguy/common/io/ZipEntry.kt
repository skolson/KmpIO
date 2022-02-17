package com.oldguy.common.io

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Represents a Zip file entry. If creating one, the internal records will be instantiated when
 * writing the entry.  When non-null they contain the Zip internals, intended for read-only use.
 *
 * Contains default properties for all of the required fields in the Zip specification when creating
 * entries. Many of the properties can be changed for use during save.
 *
 * @param directoryRecord typically decoded from an input file
 */
class ZipEntry(directoryRecord: ZipDirectoryRecord) {
    var directory: ZipDirectoryRecord = directoryRecord
        private set
    val localDirectory:ZipLocalRecord get() = createLocal()
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
        CompressionAlgorithms.Deflate64 -> 9
        CompressionAlgorithms.BZip2 -> 12
        CompressionAlgorithms.LZMA -> 14
    }
    var timeModified = defaultDateTime
        set(value) {
            field = value
            updateDirectory()
        }

    val dateModified get() = timeModified.date
    val zipModDate: Short get() = modDate(timeModified)
    val zipModeTime: Short get() = modTime(timeModified)
    val extras get() = directory.extraRecords

    constructor(
        nameArg: String,
        isZip64: Boolean = false,
        commentArg: String = "",
        extraArg: List<ZipExtra> = emptyList()
    ): this(
        ZipDirectoryRecord(
            defaultVersion,
            defaultVersion,
            ZipGeneralPurpose.defaultValue.shortValue,
            defaultCompressionValue,
            modTime(defaultDateTime),
            modDate(defaultDateTime),
            0,
            if (isZip64) -1 else 0,
            if (isZip64) -1 else 0,
            nameArg.length.toShort(),
            ZipExtra.encode(extraArg).size.toShort(),
            commentArg.length.toShort(),
            0,
            0,
            0,
            if (isZip64) -1 else 0,
            nameArg,
            ZipExtra.encode(extraArg),
            commentArg
        )
    )

    init {
        compression = when (directory.algorithm) {
            CompressionAlgorithms.None -> CompressionNone()
            CompressionAlgorithms.Deflate -> CompressionDeflate(true)
            CompressionAlgorithms.Deflate64,
            CompressionAlgorithms.BZip2,
            CompressionAlgorithms.LZMA ->
                throw ZipException("Unsupported compression algorithm ${directory.algorithm}")
        }
    }

    fun updateDirectory(
        isZip64: Boolean,
        compressedSize: ULong,
        uncompressedSize: ULong,
        crc: Int,
        localFileOffset: ULong
    ) {
        val newExtra = if (isZip64) {
            val list = extras.toMutableList()
            list.removeAll { it.signature == ZipExtraZip64.signature }
            ZipExtraZip64(
                uncompressedSize,
                compressedSize,
                localFileOffset
            ).apply {
                list.add(this.encode(directory))
            }
            ZipExtra.encode(list)
        } else
            directory.extra

        directory = ZipDirectoryRecord(
            directory.version,
            directory.versionMinimum,
            ZipGeneralPurpose.defaultValue.shortValue,
            compressionValue,
            zipModeTime,
            zipModDate,
            crc,
            if (isZip64) -1 else compressedSize.toInt(),
            if (isZip64) -1 else uncompressedSize.toInt(),
            directory.name.length.toShort(),
            newExtra.size.toShort(),
            directory.commentLength,
            directory.diskNumber,
            directory.internalAttributes,
            directory.externalAttributes,
            if (isZip64) -1 else localFileOffset.toInt(),
            directory.name,
            newExtra,
            directory.comment
        )
    }

    /**
     * Since the directory record objects are immutable, changes to state require a new instance.
     * This is a copy constructor of the current directory using updated values from the entry.
     */
    private fun updateDirectory() {
        directory = ZipDirectoryRecord(
            directory.version,
            directory.versionMinimum,
            ZipGeneralPurpose.defaultValue.shortValue,
            compressionValue,
            zipModeTime,
            zipModDate,
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
        )
    }
    /**
     * Set time using the Zip specification encoding for date and time
     */
    fun setTime(lastModTime: Short, lastModDate: Short) {
        val i = lastModDate.toInt()
        val t = lastModTime.toInt()
        timeModified = LocalDateTime(
            1980 + ((i shr 9) and 0x7f),
            ((i shr 5) and 0xf) - 1,
            i and 0x1f,
            (t shr 11) and 0x1f,
            (t shr 5) and 0x3f,
            (t and 0x1f) shl 1
        )
    }

    private fun createLocal(): ZipLocalRecord {
        return ZipLocalRecord(
            directory,
            directory.name,
            directory.extra,
            ByteArray(0)
        )
    }

    companion object {
        val defaultVersion = ZipVersion(4, 5)
        const val defaultCompressionValue: Short = 8
        val defaultDateTime get() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

        fun modDate(timeModified: LocalDateTime): Short {
            return if (timeModified.year < 1980)
                0x21.toShort()
            else {
                ((timeModified.year - 1980 shl 9) or
                        (timeModified.month.ordinal + 1 shl 5) or
                        timeModified.dayOfMonth).toShort()
            }
        }

        fun modTime(timeModified: LocalDateTime): Short {
            return ((timeModified.hour shl 11) or
                    (timeModified.minute shl 5) or
                    (timeModified.second shr 1)).toShort()
        }
    }
}