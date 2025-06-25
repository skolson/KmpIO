package com.oldguy.common.io

import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime

/**
 * The extra field ByteArray on directory records has a basic list structure. Its total length
 * contains individual 'record' structures, each structure starting with a signature and a length
 * followed by bytes to be decoded.  There are a number of reserved signatures, But any generic
 * structure can be added using a signature that is not reserved.  See [ZipExtraGeneral] class for
 * a generic example, used only for non-reserved signatures. See [ZipExtraParser] for decoding
 * any generic Extra buffer into a List of ZipExtra implementations.
 * See section 4.5.2 of the zip spec (https://pkware.cachefly.net/webdocs/casestudies/APPNOTE.TXT)
 * for detail.
 */
abstract class ZipExtra(
    val signature: Short,
    var length: Short,
) {
    abstract fun encode(buffer: ByteBuffer)

    abstract fun decode(buffer: ByteBuffer)
}

/**
 * Use this for a basic Extra field entry, with DIY encoding and decoding of content
 * @param signature must be a non-reserved value (see zip spec)
 * @param length content length in bytes.
 */
class ZipExtraGeneral(
    signature: Short,
    length: Short
) :ZipExtra(
    signature,
    length
) {
    var content = ByteArray(length.toInt())

    override fun decode(buffer: ByteBuffer) {
        content = buffer.getBytes(length.toInt())
    }

    override fun encode(buffer: ByteBuffer) {
        buffer.putBytes(content)
    }
}

/**
 * Required for Zip64 files. Holds long flavors of values. Has different layout on Local
 * directory record vs the regular one.
 * Every data field in this entry is optional, and only present if the corresponding value in the
 * directory entry is -1. Unspecified fields are set to the same value as the directory entry.
 * Supports parsing either the [ZipDirectoryRecord] flavor or the [ZipLocalRecord] flavor which has
 * less content.
 * @param directory either a [ZipDirectoryRecord] or a [ZipLocalRecord]
 */
class ZipExtraZip64(
    private val isLocal: Boolean,
    private val directory: ZipDirectoryCommon
): ZipExtra(ZipExtraParser.zip64Signature,4) {
    var uncompressedSize = -1L
    var compressedSize = -1L
    var localHeaderOffset = -1L
    private var diskNumber = -1

    /**
     * Use this to create a new instance using a ZipDirectoryRecord. Not for use by decode
     */
    constructor(
        originalSize: Long,
        compressedSize: Long,
        localHeaderOffset: Long,
        diskNumber: Int = 0,
        directory: ZipDirectoryRecord
    ): this(false, directory) {
        this.uncompressedSize = originalSize
        this.compressedSize = compressedSize
        this.localHeaderOffset = localHeaderOffset
        this.diskNumber = diskNumber
        var l = 0
        if (directory.intUncompressedSize < 0) l += 8
        if (directory.intCompressedSize < 0) l += 8
        if (directory.intLocalHeaderOffset < 0) l += 8
        if (directory.diskNumber < 0) l += 4
        length = l.toShort()
    }

    /**
     * Use this to create a new instance using a ZipLocalRecord. Not for use by decode
     */
    constructor(
        originalSize: Long,
        compressedSize: Long,
        directory: ZipLocalRecord
    ): this(true, directory) {
        this.uncompressedSize = originalSize
        this.compressedSize = compressedSize
        var l = 0
        if (directory.intUncompressedSize < 0) l += 8
        if (directory.intCompressedSize < 0) l += 8
        length = l.toShort()
    }

    override fun encode(buffer: ByteBuffer) {
        buffer.apply {
            if (!isLocal) {
                if (uncompressedSize >= 0L) long = uncompressedSize
                if (compressedSize >= 0L) long = compressedSize
                if (localHeaderOffset >= 0L) long = localHeaderOffset
                if (diskNumber >= 0) int = diskNumber
            } else {
                if (uncompressedSize >= 0L) long = uncompressedSize
                if (compressedSize >= 0L) long = compressedSize
            }
        }
    }

    override fun decode(buffer: ByteBuffer) {
        buffer.apply {
            when (directory) {
                is ZipDirectoryRecord -> {
                    uncompressedSize = if (directory.intUncompressedSize < 0)
                        long
                    else
                        directory.intUncompressedSize.toLong()
                    compressedSize = if (directory.intCompressedSize < 0)
                        long
                    else
                        directory.intCompressedSize.toLong()
                    localHeaderOffset = if (directory.intLocalHeaderOffset < 0)
                        long
                    else
                        directory.intLocalHeaderOffset.toLong()
                    diskNumber = if (directory.diskNumber < 0)
                        int
                    else
                        directory.diskNumber.toInt()
                }
                is ZipLocalRecord -> {
                    uncompressedSize = if (directory.intUncompressedSize < 0)
                        long
                    else
                        directory.intUncompressedSize.toLong()
                    compressedSize = if (directory.intCompressedSize < 0)
                        long
                    else
                        directory.intCompressedSize.toLong()
                }
            }
        }
    }
}

@OptIn(ExperimentalTime::class)
class ZipExtraNtfs(
    length: Short
): ZipExtra(
    ZipExtraParser.ntfsSignature,
    length
) {
    private val tag: Short = 1.toShort()
    var lastModifiedEpoch = 0L
    var lastAccessEpoch = 0L
    var createdEpoch = 0L
    var reserved = 0

    val lastModified: LocalDateTime get() = Instant
        .fromEpochMilliseconds(lastModifiedEpoch)
        .toLocalDateTime(TimeZones.default)
    val lastAccess: LocalDateTime get() = Instant
        .fromEpochMilliseconds(lastAccessEpoch)
        .toLocalDateTime(TimeZones.default)
    val created: LocalDateTime get() = Instant
        .fromEpochMilliseconds(createdEpoch)
        .toLocalDateTime(TimeZones.default)

    constructor(
        lastModifiedEpoch: Long,
        lastAccessEpoch: Long,
        createdEpoch: Long
    ): this(totalLength) {
        this.lastModifiedEpoch = lastModifiedEpoch
        this.lastAccessEpoch = lastAccessEpoch
        this.createdEpoch = createdEpoch
    }

    override fun encode(buffer: ByteBuffer) {
        if (length != totalLength)
            throw ZipException("Zip extra NTFS field length is $length, expected $totalLength")
        buffer.apply {
            int = reserved
            short = tag
            short = tagSize
            long = lastModifiedEpoch
            long = lastAccessEpoch
            long = createdEpoch
        }
    }

    override fun decode(buffer: ByteBuffer) {
        buffer.apply {
            reserved = int
            val inTag = short
            if (inTag != tag)
                throw ZipException("Zip extra NTFS tag1 is $inTag, expected $tag")
            val sz = short
            if (sz != tagSize)
                throw ZipException("Zip extra NTFS tag1 is $sz, expected $tagSize")
            lastModifiedEpoch = long
            lastAccessEpoch = long
            createdEpoch = long
        }
    }

    companion object {
        const val tagSize: Short = 16
        const val totalLength = (tagSize + 4).toShort()
    }
}
