package com.oldguy.common.io

import com.oldguy.common.getShortAt
import kotlin.experimental.and

/**
 * 4.4.4 general purpose bit flag
 * https://pkware.cachefly.net/webdocs/casestudies/APPNOTE.TXT
 *
 * Class to assign named properties to used portions of the General Purpose bitmask from the zip spec.
 * See spec for details of each property.
 */
data class ZipGeneralPurpose(val bits: Short) {
    private val compressBitsError = IllegalArgumentException("Property only settable to true")
    private val bitSet = BitSet(byteArrayOf(
        (bits.toInt() shr 8).toByte(),
        (bits and 0xff).toByte()
    ))

    val shortValue get() = bitSet.toByteArray().getShortAt(0)

    var isEncrypted get() = bitSet[0]
        set(value) {
            bitSet[0] = value
        }
    var isDeflateNormal get() = !bitSet[2] && !bitSet[1]
        set(value) {
            if (value) {
                bitSet[2] = false
                bitSet[1] = false
            } else {
                throw compressBitsError
            }
        }
    var isDeflateMax get() = !bitSet[2] && bitSet[1]
        set(value) {
            if (value) {
                bitSet[2] = false
                bitSet[1] = true
            } else {
                throw compressBitsError
            }
        }
    var isDeflateFast get() = bitSet[2] && !bitSet[1]
        set(value) {
            if (value) {
                bitSet[2] = true
                bitSet[1] = false
            } else {
                throw compressBitsError
            }
        }

    var isDeflateSuper get() = bitSet[2] && bitSet[1]
        set(value) {
            if (value) {
                bitSet[2] = true
                bitSet[1] = true
            } else {
                throw compressBitsError
            }
        }

    var isLzmaEosUsed get() = bitSet[1]
        set(value) { bitSet[1] = value }

    var isDataDescriptor get() = bitSet[3]
        set(value) { bitSet[3] = value }
    var isStrongEncryption get() = bitSet[6]
        set(value) { bitSet[6] = value }
    var isUtf8 get() = bitSet[11]
        set(value) { bitSet[11] = value }
    var isDirectoryMasked get() = bitSet[13]
        set(value) { bitSet[13] = value }

    companion object {
        val defaultValue = ZipGeneralPurpose(0).apply {
            isUtf8 = true
            isDeflateNormal = true
            isDataDescriptor = true
        }
    }
}

/**
 * Holds a host attributes value byte (currently unused), and a major and minor version code.
 */
data class ZipVersion(val version:Short) {
    constructor(major:Int, minor: Int): this(((major*10) + minor).toShort())

    val hostAttributesCode = version / 0x100
    val lsb = version and 0xff
    val major = lsb / 10
    val minor = lsb % 10
    val versionString = "$major.$minor"
    val supportsZip64 = major > 4 || (major == 4 && minor >= 5 )
}


/**
 * Common properties and logic shared by ZipLocalDirectory and ZipDirectory. Intent of this is for
 * subclasses to be used as if they are immutable, even though [name] and [extra] properties are
 * defined as var to make decode logic easier.
 */
open class ZipDirectoryCommon(
    val versionMinimum: ZipVersion,
    val purposeBits: Short,
    val compression: Short,
    val lastModTime: Short,
    val lastModDate: Short,
    val crc32: Int,
    val intCompressedSize: Int,
    val intUncompressedSize: Int,
    val nameLength: Short,
    val extraLength: Short,
    var name: String,
    var extra: ByteArray
): ZipRecord {

    val isZip64 = intCompressedSize == -1 || intUncompressedSize == -1
    val algorithm = ZipRecord.algorithm(compression)
    val generalPurpose = ZipGeneralPurpose(purposeBits)
    val extraRecords get() = ZipExtra.decode(extra)
    var extraZip64: ZipExtraZip64? = null
    val compressedSize: ULong
        get() = if (isZip64)
            extraZip64?.compressedSize
                ?: throw ZipException("Zip64 spec requires Zip64 Extended Information Extra Field")
        else
            intCompressedSize.toULong()
    val uncompressedSize: ULong
        get() = if (isZip64)
            extraZip64?.originalSize
                ?: throw ZipException("Zip64 spec requires Zip64 Extended Information Extra Field")
        else
            intUncompressedSize.toULong()

    fun encodeSignature(buffer: ByteBuffer, signature: Int) {
        super.encodeSignature(signature, buffer)
        if (name.length > Short.MAX_VALUE)
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
            short = lastModTime
            short = lastModDate
            int = crc32
            int = intCompressedSize
            int = intUncompressedSize
            short = name.length.toShort()
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
        fun decode(buffer: ByteBuffer): ZipDirectoryCommon {
            buffer.apply {
                return ZipDirectoryCommon(
                    versionMinimum = ZipVersion(short),
                    purposeBits = short,
                    compression = short,
                    lastModTime = short,
                    lastModDate = short,
                    crc32 = int,
                    intCompressedSize = int,
                    intUncompressedSize = int,
                    nameLength = short,
                    extraLength = short,
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
    lastModTime: Short,
    lastModDate: Short,
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
    versionMinimum, purposeBits, compression, lastModTime, lastModDate,
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
        common.lastModTime,
        common.lastModDate,
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
        lastModTime: Short,
        lastModDate: Short,
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
        lastModTime,
        lastModDate,
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

    val localHeaderOffset: ULong get() = if (isZip64)
        extraZip64?.localHeaderOffset ?: throw ZipException("Zip64 spec requires Zip64 Extended Information Extra Field")
    else
        intLocalHeaderOffset.toULong()

    override fun allocateBuffer(): ByteBuffer {
        return ByteBuffer(minimumLength + name.length + extra.size + comment.length)
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
            if (comment.length > Short.MAX_VALUE)
                throw IllegalArgumentException("Zip file comment must be < ${Short.MAX_VALUE}>")

            short = comment.length.toShort()
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
        fun decode(file: RawFile): ZipDirectoryRecord {
            ByteBuffer(minimumLength).apply {
                file.read(this)
                rewind()
                ZipRecord.decodeSignature(signature, minimumLength, this)
                return ZipDirectoryRecord(
                    version = ZipVersion(short),
                    decode(this),
                    commentLength = short,
                    diskNumber = short,
                    internalAttributes = short,
                    externalAttributes = int,
                    intLocalHeaderOffset = int,
                    ""
                ).apply {
                    name = ZipRecord.decodeName(nameLength, file)
                    extra = ZipRecord.decodeExtra(extraLength, file)
                    comment = ZipRecord.decodeComment(commentLength.toInt(), file)
                    extraRecords.firstOrNull { it.isZip64}?.let {
                        extraZip64 = ZipExtraZip64.decode(
                            it,
                            intUncompressedSize,
                            intCompressedSize,
                            intLocalHeaderOffset,
                            diskNumber
                        )
                    } ?: if (isZip64) throw ZipException("Zip64 entry $name must have an zip64 extra field signature. Extra: $extraRecords")
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
    lastModTime: Short,
    lastModDate: Short,
    crc32: Int,
    intCompressedSize: Int,
    intUncompressedSize: Int,
    nameLength: Short,
    extraLength: Short,
    name: String,
    extra: ByteArray,
    var encryptionHeader: ByteArray,
    var longCompressedSize: Long = 0L,
    var longUncompressedSize: Long = 0L
): ZipDirectoryCommon(
    versionMinimum, purposeBits, compression, lastModTime, lastModDate, crc32, intCompressedSize,
    intUncompressedSize, nameLength, extraLength, name, extra
) {

    constructor(
        common: ZipDirectoryCommon,
        name: String,
        extra: ByteArray,
        encryptionHeader: ByteArray
    ):this(
        common.versionMinimum,
        common.purposeBits,
        common.compression,
        common.lastModTime,
        common.lastModDate,
        common.crc32,
        common.intCompressedSize,
        common.intUncompressedSize,
        common.nameLength,
        common.extraLength,
        name,
        extra,
        encryptionHeader
    )

    /**
     * Zip64 constructor
     */
    constructor(
        versionMinimum: ZipVersion,
        purposeBits: Short,
        compression: Short,
        lastModTime: Short,
        lastModDate: Short,
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
        lastModTime,
        lastModDate,
        crc32,
        intCompressedSize = -1,
        intUncompressedSize = -1,
        nameLength,
        extraLength,
        name,
        extra,
        encryptionHeader,
        0L,
        0L
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
        return ByteBuffer(minimumLength + name.length + extra.size)
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
        fun decode(file: RawFile, position: ULong): ZipLocalRecord {
            ByteBuffer(minimumLength).apply {
                file.read(this, position)
                rewind()
                ZipRecord.decodeSignature(signature, minimumLength, this)
                return ZipLocalRecord(
                    decode(this),
                    "",
                    ByteArray(0),
                    ByteArray(0)
                ).apply {
                    name = ZipRecord.decodeName(nameLength, file)
                    extra = ZipRecord.decodeExtra(extraLength, file)
                    if (isZip64) {
                        extraRecords.firstOrNull { it.isZip64}?.let {
                            extraZip64 = ZipExtraZip64.decodeLocal(
                                it,
                                intUncompressedSize,
                                intCompressedSize
                            )
                        } ?: throw ZipException("Zip64 entry $name must have an zip64 extra field signature. Extra: $extraRecords")
                    }
                }
            }
        }
    }
}
