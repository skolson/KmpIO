package com.oldguy.common.io

import com.oldguy.common.getIntAt
import com.oldguy.common.getShortAt
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
    val intCompressedSize: Int,
    val intUncompressedSize: Int,
    val nameLength: Short,
    val extraLength: Short,
    val commentLength: Short,
    val diskNumber: Short,
    val internalAttributes: Short,
    val externalAttributes: Int,
    val intLocalHeaderOffset: Int,
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

    val isZip64 = intCompressedSize == -1 || intUncompressedSize == -1
    val hostAttributesCode = version / 0x100
    val versionMajor = (version and 0xFF) / 10
    val versionMinor = (version and 0xFF) % 10
    val versionString = "$versionMajor.$versionMinor"
    val minVersionMajor = (versionMinimum and 0xFF) / 10
    val minVersionMinor = (versionMinimum and 0xFF) % 10
    val minVersionString = "$minVersionMajor.$minVersionMinor"
    val algorithm = ZipRecord.algorithm(compression)
    val generalPurpose = ZipGeneralPurpose(purposeBits)
    val extraRecords get() = ZipExtra.decode(extra)
    var extraZip64: ZipExtraZip64? = null
    val compressedSize: ULong get() = if (isZip64)
        extraZip64?.compressedSize ?: throw ZipException("Zip64 spec requires Zip64 Extended Information Extra Field")
    else
        intCompressedSize.toULong()
    val localHeaderOffset: ULong get() = if (isZip64)
        extraZip64?.localHeaderOffset ?: throw ZipException("Zip64 spec requires Zip64 Extended Information Extra Field")
    else
        intLocalHeaderOffset.toULong()

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
            short = generalPurpose.shortValue
            short = compression
            short = lastModTime
            short = lastModDate
            int = crc32
            int = intCompressedSize
            int = intUncompressedSize
            short = name.length.toShort()
            short = extra.size.toShort()
            short = comment.length.toShort()
            short = diskNumber
            short = internalAttributes
            int = externalAttributes
            int = intLocalHeaderOffset
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
                    intCompressedSize = int,
                    intUncompressedSize = int,
                    nameLength = short,
                    extraLength = short,
                    commentLength = short,
                    diskNumber = short,
                    internalAttributes = short,
                    externalAttributes = int,
                    intLocalHeaderOffset = int,
                    "",
                    ByteArray(0),
                    ""
                )
            }
            return record.apply {
                name = ZipRecord.decodeName(nameLength, file)
                extra = ZipRecord.decodeExtra(extraLength, file)
                comment = decodeComment(commentLength.toInt(), file)
                if (isZip64) {
                    extraRecords.firstOrNull { it.isZip64}?.let {
                        extraZip64 = ZipExtraZip64.decode(it)
                    } ?: throw ZipException("Zip64 entry $name must have an zip64 extra field signature. Extra: $extraRecords")
                }
            }
        }
    }
}

data class ZipExtraZip64(
    val originalSize: ULong,
    val compressedSize: ULong,
    val localHeaderOffset: ULong,
    val diskNumber: Int
) {
    companion object {
        const val signature: Short = 1
        private val length: Short = 28

        fun decode(extra: ZipExtra): ZipExtraZip64 {
            if (extra.signature != signature)
                throw ZipException("Extra zip64 decode expected signature: $signature, found: ${extra.signature}")
            if (extra.length != length)
                throw ZipException("Extra zip64 decode expected length: $length, found: ${extra.length}")
            extra.buf.apply {
                return ZipExtraZip64(
                    long.toULong(),
                    long.toULong(),
                    long.toULong(),
                    int
                )
            }
        }
    }
}

/**
 * The extra fields have some reserved values. Some of these have properties defined here.
 * See 4.5.2 of the zip spec for detail. Implementation currently only uses isZip64.
 */
data class ZipExtra(
    val signature: Short,
    val length: Short,
    val buf: ByteBuffer
) {
    val isZip64 = signature == ZipExtraZip64.signature
    val isNTFS = signature.toInt() == 0xa
    val isPatchDescriptor = signature.toInt() == 0xf
    val isStrongEncryptionHeader = signature.toInt() == 0x17

    companion object {
        fun decode(extraBytes: ByteArray): List<ZipExtra> {
            ByteBuffer(extraBytes).apply {
                val list = mutableListOf<ZipExtra>()
                while (remaining > 0) {
                    val sig = short
                    val length = short
                    list.add(ZipExtra(
                        sig,
                        length,
                        ByteBuffer(getBytes(length.toInt()))
                    ))
                }
                return list
            }
        }

        fun encode(list: List<ZipExtra>): ByteArray {
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
    val generalPurpose = ZipGeneralPurpose(purposeBits)

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
            short = generalPurpose.shortValue
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
                    // if encryption is present, get encryption header here (12 bytes)
                }
            }
        }
    }
}

/**
 * For historical reasons the signature for this is optional, with no good way of knowing which is
 * in use. This impl always encodes a signature, but during decode if no signature present then the
 * end of the bytes read are checked for either the signature of the next local file header, or of
 * a central directory header (last local file). If none of these
 * verify the end of the data descriptor, a ZipException is thrown
 * Note if encryption is supported at some point, will have to also allow checking for start of
 * an archive decryption header
 * Just to further complicate things, this record is different if file is Zip64. If it is, then the
 * two size fields are Longs
 */
data class ZipDataDescriptor(
    val crc32: Int,
    val compressedSize: Long,
    val uncompressedSize: Long
): ZipRecord {
    fun encode(buffer: ByteBuffer, isZip64: Boolean) {
        buffer.apply {
            encodeSignature(signature, this)
            int = crc32
            if (isZip64) {
                long = compressedSize
                long = uncompressedSize
            } else {
                int = compressedSize.toInt()
                int = uncompressedSize.toInt()
            }
        }
    }

    companion object {
        private const val signature = 0x08074b50
        private const val length = 16
        private const val zip64Length = 24

        /**
         * Since this record, when in use, always immediately follows the uncompressed data, the
         * current file position MUST BE at the end of the uncompressed data for decode to work.
         * Note: see the class comment, signature of this record on decode is optional which is a pain.
         */
        fun decode(file: RawFile, isZip64: Boolean): ZipDataDescriptor {
            val l = if (isZip64) zip64Length else length
            ByteBuffer(l).apply {
                if (file.read(this) != l.toUInt())
                    throw ZipException("Data descriptor expected length $l not found (zip64: $isZip64")
                positionLimit(0, l)
                val sigFound = getElementAsInt(position) == signature
                if (sigFound) int       // position past signature only if it IS a signature
                val descriptor = if (isZip64) {
                    ZipDataDescriptor(
                        crc32 =  int,
                        compressedSize = long,
                        uncompressedSize = long
                    )
                } else {
                    ZipDataDescriptor(
                        crc32 = int,
                        compressedSize = int.toLong(),
                        uncompressedSize = int.toLong()
                    )
                }
                if (!sigFound) {
                    val nextSig = int
                    if (nextSig != ZipLocalRecord.signature
                        && nextSig != ZipDirectoryRecord.signature)
                        throw ZipException("Failure trying to verify data descriptor with no signature. Last four bytes: ${nextSig.toString(16)}")
                }
                return descriptor
            }
        }
    }
}