package com.oldguy.common.io

import com.oldguy.common.getBufferLength

/**
 * Common properties and logic shared by ZipLocalDirectory and ZipDirectory. Intent of this is for
 * subclasses to be used as if they are immutable, even though [name] and [extra] properties are
 * defined as var to make decode logic easier.
 */
open class ZipDirectoryCommon(
    val versionMinimum: ZipVersion,
    val purposeBits: Short,
    val compression: Short,
    val zipTime: ZipTime,
    val crc32: Int,
    val intCompressedSize: Int,
    val intUncompressedSize: Int,
    val nameLength: Short,
    var extraLength: Short,
    var name: String,
    var extra: ByteArray
): ZipRecord {

    val isZip64 = intCompressedSize == -1 || intUncompressedSize == -1
    val algorithm = ZipRecord.algorithm(compression)
    val generalPurpose = ZipGeneralPurpose(purposeBits)
    /*
    val extraRecords get() = extraFactory.decode(extra)
    var extraZip64: ZipExtraZip64? = null
    *
     */
    fun encodeSignature(buffer: ByteBuffer, signature: Int) {
        super.encodeSignature(signature, buffer)
        if (name.getBufferLength() > Short.MAX_VALUE)
            throw IllegalArgumentException("Zip file name must be < ${Short.MAX_VALUE}>")
        if (extra.size > Short.MAX_VALUE)
            throw IllegalArgumentException("Zip file extra data length must be < ${Short.MAX_VALUE}>")
    }

    open fun allocateBuffer(): ByteBuffer {
        throw ZipException("Bug: Subclasses must implement this")
    }

    open fun encode(buffer: ByteBuffer) {
        buffer.apply {
            short = versionMinimum.version
            short = generalPurpose.shortValue
            short = compression
            ushort = zipTime.modTime
            ushort = zipTime.modDate
            int = crc32
            int = intCompressedSize
            int = intUncompressedSize
            short = name.getBufferLength().toShort()
            short = extra.size.toShort()
        }
    }

    fun encodeNameExtra(buffer: ByteBuffer) {
        buffer.apply {
            if (name.isNotEmpty())
                put(ZipRecord.zipCharset.encode(name))
            if (extra.isNotEmpty())
                put(extra)
        }
    }

    companion object {

        /**
         * Decodes the portion of a directory record that is common to both regular and local
         * directory headers.
         */
        fun decode(buffer: ByteBuffer): ZipDirectoryCommon {
            buffer.apply {
                return ZipDirectoryCommon(
                    ZipVersion(short),
                    short,
                    short,
                    ZipTime(
                        ushort,
                        ushort
                    ),
                    int,
                    int,
                    int,
                    short,
                    short,
                    "",
                    ByteArray(0)
                )
            }
        }
    }
}

/**
 * Directory record contents. This object should be used as if it is immutable.
 */
class ZipDirectoryRecord(
    val version: ZipVersion,
    versionMinimum: ZipVersion,
    purposeBits: Short,
    compression: Short,
    zipTime: ZipTime,
    crc32: Int,
    intCompressedSize: Int,
    intUncompressedSize: Int,
    nameLength: Short,
    extraLength: Short,
    val commentLength: Short,
    val diskNumber: Short,
    val internalAttributes: Short,
    val externalAttributes: Int,
    val intLocalHeaderOffset: Int,
    name: String,
    extra: ByteArray,
    var comment: String
): ZipDirectoryCommon(
    versionMinimum, purposeBits, compression, zipTime,
    crc32, intCompressedSize, intUncompressedSize, nameLength, extraLength,
    name, extra
) {

    constructor(
        version: ZipVersion,
        common: ZipDirectoryCommon,
        commentLength: Short,
        diskNumber: Short,
        internalAttributes: Short,
        externalAttributes: Int,
        intLocalHeaderOffset: Int,
        comment: String,
    ): this(
        version,
        common.versionMinimum,
        common.purposeBits,
        common.compression,
        common.zipTime,
        common.crc32,
        common.intCompressedSize,
        common.intUncompressedSize,
        common.nameLength,
        common.extraLength,
        commentLength,
        diskNumber,
        internalAttributes,
        externalAttributes,
        intLocalHeaderOffset,
        common.name,
        common.extra,
        comment
    )
    /**
     * Makes a Zip64 flavor of a directory record.
     */
    constructor(
        version: ZipVersion,
        versionMinimum: ZipVersion,
        purposeBits: Short,
        compression: Short,
        zipTime: ZipTime,
        crc32: Int,
        nameLength: Short,
        extraLength: Short,
        commentLength: Short,
        internalAttributes: Short,
        externalAttributes: Int,
        intLocalHeaderOffset: Int,
        name: String,
        extra: ByteArray,
        comment: String,
    ): this(
        version,
        versionMinimum,
        purposeBits,
        compression,
        zipTime,
        crc32,
        intCompressedSize = -1,
        intUncompressedSize = -1,
        nameLength,
        extraLength,
        commentLength,
        diskNumber = -1,
        internalAttributes,
        externalAttributes,
        intLocalHeaderOffset,
        name,
        extra,
        comment
    )

    override fun allocateBuffer(): ByteBuffer {
        return ByteBuffer(minimumLength + name.getBufferLength() + extra.size + comment.getBufferLength())
    }

    /**
     * Encode this entry into the specified ByteBuffer.
     * @param buffer encoding will be written starting at current position. If there is insufficient remaining, an
     * exception is thrown. If ByteBuffer is not little endian, exception is thrown
     */
    override fun encode(buffer: ByteBuffer) {
        buffer.apply {
            val start = position
            super.encodeSignature(this, signature)
            short = version.version
            super.encode(buffer)
            if (comment.getBufferLength() > Short.MAX_VALUE)
                throw IllegalArgumentException("Zip file comment must be < ${Short.MAX_VALUE}>")

            short = comment.getBufferLength().toShort()
            short = diskNumber
            short = internalAttributes
            int = externalAttributes
            int = intLocalHeaderOffset
            encodeNameExtra(this)
            if (comment.isNotEmpty())
                put(ZipRecord.zipCharset.encode(comment))
            positionLimit(start, position - start)
        }
    }

    companion object {
        const val signature = 0x02014b50
        private const val minimumLength = 46
        /**
         * Starting at the current file position, verifies signature, decodes buffer into Central
         * Directory Record. If buffer can't be decoded, an exception is thrown. Since the record
         * can be long, this is done with two reads. One to get the relevant length data,
         * and one to read the entire record.
         * @param file Zip file's RawFile directory record is read from.
         */
        suspend fun decode(file: RawFile): ZipDirectoryRecord {
            ByteBuffer(minimumLength).apply {
                file.read(this)
                rewind()
                ZipRecord.decodeSignature(signature, minimumLength, this)
                return ZipDirectoryRecord(
                    ZipVersion(short),
                    decode(this),
                    short,
                    short,
                    short,
                    int,
                    int,
                    ""
                ).apply {
                    name = ZipRecord.decodeName(nameLength, file)
                    extra = ZipRecord.decodeExtra(extraLength, file)
                    comment = ZipRecord.decodeComment(commentLength.toInt(), file)
                }
            }
        }
    }
}

/**
 * Local File Header record precedes file data for an entry in the zip. It contains an optional encryption header.
 */
class ZipLocalRecord(
    versionMinimum: ZipVersion,
    purposeBits: Short,
    compression: Short,
    zipTime: ZipTime,
    crc32: Int,
    intCompressedSize: Int,
    intUncompressedSize: Int,
    nameLength: Short,
    extraLength: Short,
    name: String,
    extra: ByteArray,
    var encryptionHeader: ByteArray,
): ZipDirectoryCommon(
    versionMinimum, purposeBits, compression, zipTime, crc32, intCompressedSize,
    intUncompressedSize, nameLength, extraLength, name, extra
) {

    /**
     * Construct a default local record from a new (not existing) directory record
     */
    constructor(
        directoryRecord: ZipDirectoryRecord
    ): this (
        directoryRecord,
        directoryRecord.name,
        ByteArray(0)
    )

    /**
     * Construct from a decode of an existing local record
     */
    constructor(
        common: ZipDirectoryCommon,
        name: String,
        encryptionHeader: ByteArray
    ):this(
        common.versionMinimum,
        common.purposeBits,
        common.compression,
        common.zipTime,
        common.crc32,
        common.intCompressedSize,
        common.intUncompressedSize,
        common.nameLength,
        common.extraLength,
        name,
        common.extra,
        encryptionHeader
    )

    /**
     * Zip64 new entry constructor
     */
    constructor(
        versionMinimum: ZipVersion,
        purposeBits: Short,
        compression: Short,
        zipTime: ZipTime,
        crc32: Int,
        nameLength: Short,
        extraLength: Short,
        name: String,
        extra: ByteArray,
        encryptionHeader: ByteArray
    ): this(
        versionMinimum,
        purposeBits,
        compression,
        zipTime,
        crc32,
        intCompressedSize = -1,
        intUncompressedSize = -1,
        nameLength,
        extraLength,
        name,
        extra,
        encryptionHeader
    )

    /**
     * Compression field has these values from the spec:
     * 4.4.5 compression method: (2 bytes)
    0 - The file is stored (no compression)
    1 - The file is Shrunk
    2 - The file is Reduced with compression factor 1
    3 - The file is Reduced with compression factor 2
    4 - The file is Reduced with compression factor 3
    5 - The file is Reduced with compression factor 4
    6 - The file is Imploded
    7 - Reserved for Tokenizing compression algorithm
    8 - The file is Deflated
    9 - Enhanced Deflating using Deflate64(tm)
    10 - PKWARE Data Compression Library Imploding (old IBM TERSE)
    11 - Reserved by PKWARE
    12 - File is compressed using BZIP2 algorithm
    13 - Reserved by PKWARE
    14 - LZMA
    15 - Reserved by PKWARE
    16 - IBM z/OS CMPSC Compression
    17 - Reserved by PKWARE
    18 - File is compressed using IBM TERSE (new)
    19 - IBM LZ77 z Architecture
    20 - deprecated (use method 93 for zstd)
    93 - Zstandard (zstd) Compression
    94 - MP3 Compression
    95 - XZ Compression
    96 - JPEG variant
    97 - WavPack compressed data
    98 - PPMd version I, Rev 1
    99 - AE-x encryption marker (see APPENDIX E)
     */

    val hasDataDescriptor = crc32 == 0 && intCompressedSize == 0 && intUncompressedSize == 0

    override fun allocateBuffer(): ByteBuffer {
        return ByteBuffer(minimumLength + name.getBufferLength() + extra.size)
    }

    /**
     * Encode this entry into the specified ByteBuffer.
     * @param buffer encoding will be written starting at current position. If there is insufficient remaining, an
     * exception is thrown. If ByteBuffer is not little endian, exception is thrown
     */
    override fun encode(buffer: ByteBuffer) {
        buffer.apply {
            val start = position
            super.encodeSignature(this, signature)
            super.encode(buffer)
            if (name.isNotEmpty())
                put(ZipRecord.zipCharset.encode(name))
            if (extra.isNotEmpty())
                put(extra)
            positionLimit(start, position - start)
        }
    }

    companion object {
        const val signature = 0x04034b50
        private const val minimumLength = 30

        /**
         * Starting at buffer position, verifies signature, decodes buffer into Central Directory Record. If buffer
         * can't be decoded, an exception is thrown
         * @param file ZipFile
         * @param position must be pointing at location where record starts
         */
        suspend fun decode(file: RawFile, position: ULong): ZipLocalRecord {
            ByteBuffer(minimumLength).apply {
                val n = file.read(this, position)
                rewind()
                ZipRecord.decodeSignature(signature, minimumLength, this)
                return ZipLocalRecord(
                    decode(this),
                    "",
                    ByteArray(0)
                ).apply {
                    name = ZipRecord.decodeName(nameLength, file)
                    extra = ZipRecord.decodeExtra(extraLength, file)
                }
            }
        }
    }
}
