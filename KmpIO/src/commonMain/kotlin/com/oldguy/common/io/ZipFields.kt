package com.oldguy.common.io

import com.oldguy.common.getShortAt
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.number
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
            isDataDescriptor = false
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
 * Handles conversions to/from the zip spec which uses old-time MS DOS standard time.
 * * Note, don't change the order of these two properties as it affects ZipDirectoryCommon decode
 * @param modTime zip spec msDos time
 * @param modDate zip spec msDos date
 */
data class ZipTime(val modTime: UShort, val modDate: UShort) {

    constructor(time: LocalDateTime): this(
        convertTime(time),
        convertDate(time)
    )

    val zipTime: LocalDateTime
        get() {
        val i = modDate.toInt()
        val t = modTime.toInt()
        return LocalDateTime(
            1980 + ((i shr 9) and 0x7f),
            ((i shr 5) and 0xf),
            i and 0x1f,
            ((t shr 11) and 0x1f),
            (t shr 5) and 0x3f,
            ((t shl 1) and 0x3e)
        )
    }

    companion object {
        fun convertDate(time: LocalDateTime): UShort {
            time.apply {
                return if (year < 1980)
                    ((1 shl 21) or (1 shl 16)).toUShort()
                else {
                    (((year - 1980) shl 9) or
                            (month.number shl 5) or
                            day).toUShort()
                }
            }
        }

        fun convertTime(time: LocalDateTime): UShort {
            time.apply {
                return ((hour shl 11) or
                        (minute shl 5) or
                        (second shr 1)).toUShort()
            }
        }
    }
}