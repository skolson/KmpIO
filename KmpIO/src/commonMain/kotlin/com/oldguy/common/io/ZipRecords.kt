package com.oldguy.common.io

import com.oldguy.common.getIntAt
import com.oldguy.common.io.ZipRecord.Companion.decodeComment
import com.oldguy.common.io.ZipRecord.Companion.decodeSignature
import com.oldguy.common.io.ZipRecord.Companion.zipCharset
import com.oldguy.common.io.charsets.Utf8
import kotlin.math.min

interface ZipRecord {

    fun encodeSignature(signature: Int, buffer: ByteBuffer) {
        if (buffer.order != Buffer.ByteOrder.LittleEndian)
            throw littleEndianRequired
        buffer.int = signature
    }

    companion object {
        val zipCharset = Utf8()
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
                    throw IllegalArgumentException("Buffer at position: $t does not match signature: ${signature.toString(16)}, found: ${sig.toString(16)}")
            }
        }

        fun decodeComment(length: Int, buffer: ByteBuffer): String {
            return if (length > 0) zipCharset.decode(buffer.getBytes(length))
            else ""
        }

        suspend fun decodeComment(length: Int, file: RawFile): String {
            var comment = ""
            if (length > 0) {
                ByteBuffer(length).apply {
                    file.read(this)
                    comment = zipCharset.decode(this.contentBytes)
                }
            }
            return comment
        }

        suspend fun decodeName(nameLength: Short, file: RawFile): String {
            var name = ""
            if (nameLength > 0) {
                ByteBuffer(nameLength.toInt()).apply {
                    file.read(this)
                    name = zipCharset.decode(this.contentBytes)
                }
            }
            return name
        }

        suspend fun decodeExtra(extraLength: Short, file: RawFile): ByteArray {
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
    val length = minimumLength + comment.length

    /**
     * Constructs a Zip64 flavor of this record
     */
    constructor(): this(
        diskNumber = -1,
        directoryDisk = -1,
        diskEntryCount = -1,
        entryCount = -1,
        directoryLength = -1,
        directoryOffset = -1,
        comment = ""
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
            flip()
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
    val eocdOffset: ULong,
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
            ulong = eocdOffset
            int = disksCount
            flip()
        }
    }

    companion object {
        const val signature = 0x07064b50
        const val length = 20

        /**
         * Starting at buffer position, verifies signature, decodes buffer into End of Central Directory Record. If buffer
         * can't be decoded, an exception is thrown
         * @param buffer position is pointing at location where record starts
         */
        fun decode(buffer: ByteBuffer): ZipEOCD64Locator {
            buffer.apply {
                decodeSignature(signature, length, this)
                return ZipEOCD64Locator(
                    diskNumber = int,
                    eocdOffset = ulong,
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
    val version: ZipVersion,
    val versionMinimum: ZipVersion,
    val diskNumber: Int,
    val directoryDisk: Int,
    val diskEntryCount: ULong,
    val entryCount: ULong,
    val directoryLength: ULong,
    val directoryOffset: ULong,
    val comment: String
): ZipRecord {

    fun allocateBuffer(): ByteBuffer {
        return ByteBuffer(minimumLength + min(comment.length, ZipFile.maxCommentLength))
    }

    /**
     * Encode this entry into the specified ByteBuffer.
     * @param buffer encoding will be written starting at current position. If there is insufficient remaining, an
     * exception is thrown. If ByteBuffer is not little endian, exception is thrown
     */
    fun encode(buffer: ByteBuffer) {
        buffer.apply {
            encodeSignature(signature, this)
            long = (36 + comment.length).toLong()
            short = version.version
            short = versionMinimum.version
            int = diskNumber
            int = directoryDisk
            ulong = diskEntryCount
            ulong = entryCount
            ulong = directoryLength
            ulong = directoryOffset
            if (comment.isNotEmpty())
                put(zipCharset.encode(comment))
            flip()
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
         * @param file to be read
         * @param position where record is located
         */
        suspend fun decode(file: RawFile, position: ULong): ZipEOCD64 {
            var buf = ByteBuffer(minimumLength)
            file.read(buf, position)
            buf.rewind()
            decodeSignature(signature, minimumLength, buf)
            val totalLength = buf.long + 12
            val commentLength = min(totalLength - minimumLength, commentLengthLimit).toInt()
            if (commentLength > 0) {
                buf = ByteBuffer(totalLength.toInt())
                file.read(buf, position)
                buf.rewind()
                decodeSignature(signature, minimumLength, buf)
            }
            buf.apply {
                return ZipEOCD64(
                    version = ZipVersion(short),
                    versionMinimum = ZipVersion(short),
                    diskNumber = int,
                    directoryDisk = int,
                    diskEntryCount = ulong,
                    entryCount = ulong,
                    directoryLength = ulong,
                    directoryOffset = ulong,
                    comment = decodeComment(commentLength, buf)
                ).apply {
                    if (!versionMinimum.supportsZip64)
                        throw ZipException("Zip64 expects minimum version 4.5, actual: ${versionMinimum.versionString}")
                }
            }
        }

        fun upgrade(eocd: ZipEOCD): ZipEOCD64 {
            return ZipEOCD64(
                ZipVersion(0x0045),
                ZipVersion(0x0045),
                eocd.diskNumber.toInt(),
                eocd.directoryDisk.toInt(),
                eocd.diskEntryCount.toULong(),
                eocd.entryCount.toULong(),
                eocd.directoryLength.toULong(),
                eocd.directoryOffset.toULong(),
                eocd.comment
            )
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
    val compressedSize: ULong,
    val uncompressedSize: ULong
): ZipRecord {

    fun allocateBuffer(isZip64: Boolean): ByteBuffer {
        return ByteBuffer(if(isZip64) zip64Length else length)
    }

    fun encode(buffer: ByteBuffer, isZip64: Boolean) {
        buffer.apply {
            encodeSignature(signature, this)
            int = crc32
            if (isZip64) {
                ulong = compressedSize
                ulong = uncompressedSize
            } else {
                int = compressedSize.toInt()
                int = uncompressedSize.toInt()
            }
            flip()
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
        suspend fun decode(file: RawFile, isZip64: Boolean): ZipDataDescriptor {
            val l = if (isZip64) zip64Length else length
            ByteBuffer(l).apply {
                if (file.read(this) != l.toUInt())
                    throw ZipException("Data descriptor expected length $l not found (zip64: $isZip64")
                flip()
                val sigFound = int == signature
                if (!sigFound) rewind()
                val descriptor = if (isZip64) {
                    ZipDataDescriptor(
                        crc32 =  int,
                        compressedSize = ulong,
                        uncompressedSize = ulong
                    )
                } else {
                    ZipDataDescriptor(
                        crc32 = int,
                        compressedSize = int.toULong(),
                        uncompressedSize = int.toULong()
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