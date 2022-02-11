package com.oldguy.common.io

import com.oldguy.common.getIntAt
import com.oldguy.common.io.ZipRecord.Companion.decodeComment
import com.oldguy.common.io.ZipRecord.Companion.decodeSignature
import com.oldguy.common.io.ZipRecord.Companion.zipCharset
import kotlin.experimental.and
import kotlin.math.min

interface ZipRecord {

    fun encodeSignature(signature: Int, buffer: ByteBuffer) {
        if (buffer.order != Buffer.ByteOrder.LittleEndian)
            throw littleEndianRequired
        buffer.int = signature
    }

    companion object {
        val zipCharset = Charset(Charsets.Utf8)
        val littleEndianRequired = IllegalArgumentException("Zip file buffer must be little endian")

        fun algorithm(method: Short) = when (method.toInt()) {
            0 -> CompressionAlgorithms.None
            8 -> CompressionAlgorithms.Deflate
            9 -> CompressionAlgorithms.Deflate
            14 -> CompressionAlgorithms.LZMA
            else -> throw IllegalArgumentException("Unsupported compression method: $method")
        }

        fun decodeSignature(signature: Int, minLength: Int, buffer: ByteBuffer) {
            buffer.apply {
                if (remaining < minLength)
                    throw IllegalArgumentException("Buffer remaining: $remaining too small, must be at least $minLength")
                val t = position
                val sig = int
                if (sig != signature)
                    throw IllegalArgumentException("Buffer at position: $t does not match signature: $signature, found: ${sig.toString(16)}")
            }
        }

        fun decodeComment(length: Int, buffer: ByteBuffer): String {
            return if (length > 0) zipCharset.decode(buffer.getBytes(length))
            else ""
        }

        fun decodeComment(length: Int, file: RawFile): String {
            var comment = ""
            if (length > 0) {
                ByteBuffer(length).apply {
                    file.read(this)
                    comment = zipCharset.decode(this.contentBytes)
                }
            }
            return comment
        }

        fun decodeName(nameLength: Short, file: RawFile): String {
            var name = ""
            if (nameLength > 0) {
                ByteBuffer(nameLength.toInt()).apply {
                    file.read(this)
                    name = zipCharset.decode(this.contentBytes)
                }
            }
            return name
        }

        fun decodeExtra(extraLength: Short, file: RawFile): ByteArray {
            var bytes = ByteArray(0)
            if (extraLength > 0) {
                ByteBuffer(extraLength.toInt()).apply {
                    file.read(this)
                    bytes = this.contentBytes
                }
            }
            return bytes
        }

        /**
         * Scan a buffer from the end to the beginning, looking for the first match of [signature]. Scan ignores
         * position and limit. If match found, returns that position, sets buffer position to that value, sets limit
         * to capacity, so buffer is ready for decode. If no match found, returns -1
         * @param signature
         * @param buffer must be little endian.
         */
        fun findSignature(signature: Int, buffer: ByteBuffer): Int {
            buffer.apply {
                if (order != Buffer.ByteOrder.LittleEndian)
                    throw littleEndianRequired
                for (index in buffer.buf.size - 4 downTo 0) {
                    val sig = buffer.buf.getIntAt(index)
                    if (sig == signature) {
                        buffer.positionLimit(index, capacity)
                        return index
                    }
                }
                return -1
            }
        }
    }
}

/**
 * End of Central Directory Record
 */
data class ZipEOCD (
    val diskNumber: Short,
    val directoryDisk: Short,
    val diskEntryCount: Short,
    val entryCount: Short,
    val directoryLength: Int,
    val directoryOffset: Int,
    val comment: String
): ZipRecord {

    val isZip64 get() = directoryLength == -1

    /**
     * Constructs a Zip64 flavor of this record
     */
    constructor(comment: String): this(
        diskNumber = -1,
        directoryDisk = -1,
        diskEntryCount = -1,
        entryCount = -1,
        directoryLength = -1,
        directoryOffset = -1,
        comment = comment
    )

    /**
     * Encode this entry into the specified ByteBuffer.
     * @param buffer encoding will be written starting at current position. If there is insufficient remaining, an
     * exception is thrown. If ByteBuffer is not little endian, exception is thrown
     */
    fun encode(buffer: ByteBuffer) {
        buffer.apply {
            if (comment.length > Short.MAX_VALUE)
                throw IllegalArgumentException("Zip file comment must be < ${Short.MAX_VALUE}>")
            encodeSignature(signature, this)
            short = diskNumber
            short = directoryDisk
            short = diskEntryCount
            short = entryCount
            int = directoryLength
            int = directoryOffset
            short = comment.length.toShort()
            if (comment.isNotEmpty())
                put(zipCharset.encode(comment))
        }
    }

    companion object {
        const val signature = 0x06054b50
        const val minimumLength = 22

        /**
         * Starting at buffer position, verifies signature, decodes buffer into End of Central Directory Record. If buffer
         * can't be decoded, an exception is thrown
         * @param buffer position is pointing at location where record starts
         */
        fun decode(buffer: ByteBuffer): ZipEOCD {
            buffer.apply {
                decodeSignature(signature, minimumLength, this)
                return ZipEOCD(
                    diskNumber = short,
                    directoryDisk = short,
                    diskEntryCount = short,
                    entryCount = short,
                    directoryLength = int,
                    directoryOffset = int,
                    comment = decodeComment(short.toInt(), this)
                )
            }
        }

        /**
         * Invokes [ZipRecord.findSignature] using [signature] for this EOCD record.
         */
        fun findSignature(buffer: ByteBuffer): Int {
            return ZipRecord.findSignature(signature, buffer)
        }
    }
}

data class ZipEOCD64Locator(
    val diskNumber: Int,
    val eocdOffset: Long,
    val disksCount: Int
): ZipRecord {
    /**
     * Encode this entry into the specified ByteBuffer.
     * @param buffer encoding will be written starting at current position. If there is insufficient remaining, an
     * exception is thrown. If ByteBuffer is not little endian, exception is thrown
     */
    fun encode(buffer: ByteBuffer) {
        buffer.apply {
            encodeSignature(signature, this)
            int = diskNumber
            long = eocdOffset
            int = disksCount
        }
    }

    companion object {
        const val signature = 0x07064b50
        const val minimumLength = 20

        /**
         * Starting at buffer position, verifies signature, decodes buffer into End of Central Directory Record. If buffer
         * can't be decoded, an exception is thrown
         * @param buffer position is pointing at location where record starts
         */
        fun decode(buffer: ByteBuffer): ZipEOCD64Locator {
            buffer.apply {
                decodeSignature(signature, minimumLength, this)
                return ZipEOCD64Locator(
                    diskNumber = int,
                    eocdOffset = long,
                    disksCount = int
                )
            }
        }
    }
}

/**
 * End of Central Directory Record for Zip64 files. Note that the comment on these can be HUGE, so class has artificial
 * self-defense default limit of 2MB for the comment.
 */
data class ZipEOCD64 (
    val version: Short,
    val versionMinimum: Short,
    val diskNumber: Int,
    val directoryDisk: Int,
    val diskEntryCount: Long,
    val entryCount: Long,
    val directoryLength: Long,
    val directoryOffset: Long,
    val comment: String
): ZipRecord {
    /**
     * Encode this entry into the specified ByteBuffer.
     * @param buffer encoding will be written starting at current position. If there is insufficient remaining, an
     * exception is thrown. If ByteBuffer is not little endian, exception is thrown
     */
    fun encode(buffer: ByteBuffer) {
        buffer.apply {
            encodeSignature(signature, this)
            long = (36 + comment.length).toLong()
            short = version
            short = versionMinimum
            int = diskNumber
            int = directoryDisk
            long = diskEntryCount
            long = entryCount
            long = directoryLength
            long = directoryOffset
            if (comment.isNotEmpty())
                put(zipCharset.encode(comment))
        }
    }

    companion object {
        const val signature = 0x06064b50
        private const val minimumLength = 56
        private const val commentLengthLimit = (2 * 1024 * 1024).toLong()

        /**
         * This entry can be very long, so this decode is two steps. Small buffer is used to fetch the beginning of
         * the record, determine its length, then do a second read.
         * NOTE: As a self-defense mechanism, if a comment longer than 2MB is found, it is truncated at 2MB.
         * @param rawFile to be read
         * @param position where record is located
         */
        fun decode(file: RawFile, position: ULong): ZipEOCD64 {
            val buf = ByteBuffer(minimumLength)
            file.read(buf, position)
            buf.rewind()
            decodeSignature(signature, minimumLength, buf)
            val totalLength = buf.long + 12
            val commentLength = min(totalLength - minimumLength, commentLengthLimit).toInt()
            ByteBuffer(totalLength.toInt()).apply {
                file.read(this)
                buf.rewind()
                return ZipEOCD64(
                    version = short,
                    versionMinimum = short,
                    diskNumber = int,
                    directoryDisk = int,
                    diskEntryCount = long,
                    entryCount = long,
                    directoryLength = long,
                    directoryOffset = long,
                    comment = decodeComment(commentLength, buf)
                )
            }
        }

        fun upgrade(eocd: ZipEOCD): ZipEOCD64 {
            return ZipEOCD64(
                -1,
                -1,
                eocd.diskNumber.toInt(),
                eocd.directoryDisk.toInt(),
                eocd.diskEntryCount.toLong(),
                eocd.entryCount.toLong(),
                eocd.directoryLength.toLong(),
                eocd.directoryOffset.toLong(),
                eocd.comment
            )
        }
    }
}

/**
 * Directory record contents.
 */
data class ZipDirectoryRecord(
    val version: Short,
    val versionMinimum: Short,
    val purposeBits: Short,
    val compression: Short,
    val lastModTime: Short,
    val lastModDate: Short,
    val crc32: Int,
    val compressedSize: Int,
    val uncompressedSize: Int,
    val nameLength: Short,
    val extraLength: Short,
    val commentLength: Short,
    val diskNumber: Short,
    val internalAttributes: Short,
    val externalAttributes: Int,
    val localHeaderOffset: Int,
    var name: String,
    var extra: ByteArray,
    var comment: String,

): ZipRecord {

    /**
     * Makes a Zip64 flavor of a directory record.
     */
    constructor(
        version: Short,
        versionMinimum: Short,
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
        localHeaderOffset: Int,
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
        compressedSize = -1,
        uncompressedSize = -1,
        nameLength,
        extraLength,
        commentLength,
        diskNumber = -1,
        internalAttributes,
        externalAttributes,
        localHeaderOffset,
        name,
        extra,
        comment
    )

    val isZip64 = compressedSize == -1 || uncompressedSize == -1
    val hostAttributesCode = version / 0x100
    val versionMajor = (version and 0xFF) / 10
    val versionMinor = (version and 0xFF) % 10
    val versionString = "$versionMajor.$versionMinor"
    val minVersionMajor = (versionMinimum and 0xFF) / 10
    val minVersionMinor = (versionMinimum and 0xFF) % 10
    val minVersionString = "$minVersionMajor.$minVersionMinor"
    val algorithm = ZipRecord.algorithm(compression)

    /**
     * Encode this entry into the specified ByteBuffer.
     * @param buffer encoding will be written starting at current position. If there is insufficient remaining, an
     * exception is thrown. If ByteBuffer is not little endian, exception is thrown
     */
    fun encode(buffer: ByteBuffer) {
        buffer.apply {
            encodeSignature(signature, this)
            if (comment.length > Short.MAX_VALUE)
                throw IllegalArgumentException("Zip file comment must be < ${Short.MAX_VALUE}>")
            if (name.length > Short.MAX_VALUE)
                throw IllegalArgumentException("Zip file name must be < ${Short.MAX_VALUE}>")
            if (extra.size > Short.MAX_VALUE)
                throw IllegalArgumentException("Zip file extra data length must be < ${Short.MAX_VALUE}>")

            short = version
            short = versionMinimum
            short = purposeBits
            short = compression
            short = lastModTime
            short = lastModDate
            int = crc32
            int = compressedSize
            int = uncompressedSize
            short = name.length.toShort()
            short = extra.size.toShort()
            short = comment.length.toShort()
            short = diskNumber
            short = internalAttributes
            int = externalAttributes
            int = localHeaderOffset
            if (name.isNotEmpty())
                put(zipCharset.encode(name))
            if (extra.isNotEmpty())
                put(extra)
            if (comment.isNotEmpty())
                put(zipCharset.encode(comment))
        }
    }

    companion object {
        const val signature = 0x02014b50
        private const val minimumLength = 46
        /**
         * Starting at specified position, verifies signature, decodes buffer into Central Directory Record. If buffer
         * can't be decoded, an exception is thrown. Since the record can be long, this is done with two reads. One to
         * get the relevant length data, and one to read the entire record.
         * @param buffer position is pointing at location where record starts
         */
        fun decode(file: RawFile): ZipDirectoryRecord {
            var record: ZipDirectoryRecord
            ByteBuffer(minimumLength).apply {
                file.read(this)
                rewind()
                decodeSignature(signature, minimumLength, this)
                record = ZipDirectoryRecord(
                    version = short,
                    versionMinimum = short,
                    purposeBits = short,
                    compression = short,
                    lastModTime = short,
                    lastModDate = short,
                    crc32 = int,
                    compressedSize = int,
                    uncompressedSize = int,
                    nameLength = short,
                    extraLength = short,
                    commentLength = short,
                    diskNumber = short,
                    internalAttributes = short,
                    externalAttributes = int,
                    localHeaderOffset = int,
                    "",
                    ByteArray(0),
                    ""
                )
            }
            return record.apply {
                name = ZipRecord.decodeName(nameLength, file)
                extra = ZipRecord.decodeExtra(extraLength, file)
                comment = decodeComment(commentLength.toInt(), file)
            }
        }
    }
}

data class ZipLocalExtra(
    val signature: Short,
    val length: Short,
    val buf: ByteBuffer
) {
    companion object {
        fun decode(extraBytes: ByteArray): List<ZipLocalExtra> {
            ByteBuffer(extraBytes).apply {
                val list = mutableListOf<ZipLocalExtra>()
                while (remaining > 0) {
                    val sig = short
                    val length = short
                    list.add(ZipLocalExtra(
                        sig,
                        length,
                        ByteBuffer(getBytes(length.toInt()))
                    ))
                }
                return list
            }
        }

        fun encode(list: List<ZipLocalExtra>): ByteArray {
            return ByteBuffer(list.sumOf { 4 + it.buf.capacity }).apply {
                list.forEach {
                    short = it.signature
                    short = it.length
                    putBytes(it.buf.buf)
                }
            }.contentBytes
        }
    }
}

/**
 * Local File Header record precedes file data for an entry in the zip. It contains an optional encryption header.
 */
data class ZipLocalRecord(
    val versionMinimum: Short,
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
    var extra: ByteArray,
    var encryptionHeader: ByteArray,
    var longCompressedSize: Long = 0L,
    var longUncompressedSize: Long = 0L
): ZipRecord {

    /**
     * Zip64 constructor
     */
    constructor(
        versionMinimum: Short,
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

    val algorithm = ZipRecord.algorithm(compression)
    val hasDataDescriptor = crc32 == 0 && intCompressedSize == 0 && intUncompressedSize == 0
    val isZip64 = intCompressedSize < 0
    val compressedSize: ULong get() = if (isZip64) longCompressedSize.toULong() else intCompressedSize.toULong()
    val uncompressedSize: ULong get() = if (isZip64) longUncompressedSize.toULong() else intUncompressedSize.toULong()

    val extraRecords get() = ZipLocalExtra.decode(extra)

    /**
     * Encode this entry into the specified ByteBuffer.
     * @param buffer encoding will be written starting at current position. If there is insufficient remaining, an
     * exception is thrown. If ByteBuffer is not little endian, exception is thrown
     */
    fun encode(buffer: ByteBuffer) {
        buffer.apply {
            encodeSignature(signature, this)
            if (name.length > Short.MAX_VALUE)
                throw IllegalArgumentException("Zip file name must be < ${Short.MAX_VALUE}>")
            if (extra.size > Short.MAX_VALUE)
                throw IllegalArgumentException("Zip file extra data length must be < ${Short.MAX_VALUE}>")

            short = versionMinimum
            short = purposeBits
            short = compression
            short = lastModTime
            short = lastModDate
            int = crc32
            int = intCompressedSize
            int = intUncompressedSize
            short = name.length.toShort()
            short = extra.size.toShort()
            if (name.isNotEmpty())
                put(zipCharset.encode(name))
            if (extra.isNotEmpty())
                put(extra)
        }
    }

    companion object {
        const val signature = 0x04034b50
        const val minimumLength = 30

        /**
         * Starting at buffer position, verifies signature, decodes buffer into Central Directory Record. If buffer
         * can't be decoded, an exception is thrown
         * @param buffer position is pointing at location where record starts
         */
        fun decode(file: RawFile, position: ULong): ZipLocalRecord {
            ByteBuffer(minimumLength).apply {
                file.read(this, position)
                rewind()
                decodeSignature(signature, minimumLength, this)
                return ZipLocalRecord(
                    versionMinimum = short,
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
                    ByteArray(0),
                    ByteArray(0)
                ).apply {
                    name = ZipRecord.decodeName(nameLength, file)
                    extra = ZipRecord.decodeExtra(extraLength, file)
                    if (isZip64) {
                        extraRecords.firstOrNull { it.signature.toInt() == 1 && it.length.toInt() == 16}?.let {
                            longCompressedSize = it.buf.long
                            longUncompressedSize = it.buf.long
                        } ?: throw ZipException("Zip64 entry $name must have an extra field signature == 1. Extra: $extraRecords")
                    }
                    // if encryption is present, get encryption header here (12 bytes)
                }
            }
        }
    }
}