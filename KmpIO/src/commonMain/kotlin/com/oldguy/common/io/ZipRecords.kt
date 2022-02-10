package com.oldguy.common.io

import com.oldguy.common.getIntAt
import com.oldguy.common.io.ZipRecord.Companion.decodeComment
import com.oldguy.common.io.ZipRecord.Companion.decodeSignature
import com.oldguy.common.io.ZipRecord.Companion.zipCharset
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
            return if (length > 0)
                zipCharset.decode(buffer.getBytes(length))
            else ""
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
                put(ZipRecord.zipCharset.encode(comment))
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
                put(ZipRecord.zipCharset.encode(comment))
        }
    }

    companion object {
        const val signature = 0x06064b50
        const val minimumLength = 56
        const val commentLengthLimit = (2 * 1024 * 1024).toLong()

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
                put(ZipRecord.zipCharset.encode(name))
            if (extra.isNotEmpty())
                put(extra)
            if (comment.isNotEmpty())
                put(ZipRecord.zipCharset.encode(comment))
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
                if (record.nameLength > 0) {
                    ByteBuffer(record.nameLength.toInt()).apply {
                        file.read(this)
                        record.name = zipCharset.decode(this.contentBytes)
                    }
                }
                if (record.extraLength > 0) {
                    ByteBuffer(record.extraLength.toInt()).apply {
                        file.read(this)
                        record.extra = this.contentBytes
                    }
                }
                if (record.commentLength > 0) {
                    ByteBuffer(record.commentLength.toInt()).apply {
                        file.read(this)
                        record.comment = zipCharset.decode(this.contentBytes)
                    }
                }
                return record
            }
        }
    }
}

/**
 * Local File Header record precedes file data for an entry in the zip.
 */
internal data class ZipLocalRecord(
    var versionMinimum: Short,
    var purposeBits: Short,
    var compression: Short,
    var lastModTime: Short,
    var lastModDate: Short,
    var crc32: Int,
    var compressedSize: Int,
    var uncompressedSize: Int,
    var name: String,
    var extra: ByteArray,

    ): ZipRecord {

    fun setZip64() {
        compressedSize = -1
        uncompressedSize = -1
    }

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
            int = compressedSize
            int = uncompressedSize
            short = name.length.toShort()
            short = extra.size.toShort()
            if (name.isNotEmpty())
                put(ZipRecord.zipCharset.encode(name))
            if (extra.isNotEmpty())
                put(extra)
        }
    }

    /**
     * Starting at buffer position, verifies signature, decodes buffer into Central Directory Record. If buffer
     * can't be decoded, an exception is thrown
     * @param buffer position is pointing at location where record starts
     */
    fun decode(buffer: ByteBuffer) {
        buffer.apply {
            decodeSignature(signature, minimumLength, this)
            versionMinimum = short
            purposeBits = short
            compression = short
            lastModTime = short
            lastModDate = short
            crc32 = int
            compressedSize = int
            uncompressedSize = int
            val nameLength = short
            val extraLength = short
            name = if (nameLength > 0)
                ZipRecord.zipCharset.decode(getBytes(nameLength.toInt()))
            else
                ""
            extra = if (extraLength > 0)
                getBytes(extraLength.toInt())
            else
                ByteArray(0)
        }
    }

    companion object {
        const val signature = 0x04034b50
        const val minimumLength = 30
    }
}