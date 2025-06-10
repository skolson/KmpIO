package com.oldguy.common.io.charsets

import com.oldguy.common.io.Buffer.ByteOrder
import com.oldguy.common.io.ByteBuffer
import com.oldguy.common.io.UByteBuffer

open class Utf16(
    name: String,
    val order: ByteOrder = ByteOrder.LittleEndian
    ):
    Charset(
        name,
        2..4
    )
{
    override fun decode(bytes: ByteArray): String {
        return buildString {
            val buf = ByteBuffer(bytes, order)
            while (buf.remaining > 0) {
                val code = buf.ushort.toInt()
                when (code) {
                    in codeRange1 -> {
                        append(code.toChar())
                    }
                    in codeRange2 -> {
                        append(code.toChar())
                    }
                    in highSurrogateRange -> {
                        val lowVal = buf.ushort.toInt()
                        if (lowVal in lowSurrogateRange) {
                            append(
                                (code - HIGH_SURROGATE).shl(10) +
                                (buf.ushort.toInt() - LOW_SURROGATE)
                            )
                        } else {
                            throw IllegalStateException("Invalid UTF-16 surrogate encoding. bytes found ${code.toString(16)}, ${lowVal.toString(16)}")
                        }
                    }
                    else -> {
                        throw IllegalStateException("Invalid UTF-16 encoding. bytes found ${code.toString(16)}")
                    }
                }
            }
        }
    }

    override fun decode(bytes: UByteArray): String {
        return buildString {
            val buf = UByteBuffer(bytes, order)
            while (buf.remaining > 0) {
                val code = buf.ushort.toInt()
                when (code) {
                    in codeRange1 -> {
                        append(code.toChar())
                    }
                    in codeRange2 -> {
                        append(code.toChar())
                    }
                    in highSurrogateRange -> {
                        val lowVal = buf.ushort.toInt()
                        if (lowVal in lowSurrogateRange) {
                            append(
                                (code - HIGH_SURROGATE).shl(10) +
                                        (buf.ushort.toInt() - LOW_SURROGATE)
                            )
                        } else {
                            throw IllegalStateException("Invalid UTF-16 surrogate encoding. bytes found ${code.toString(16)}, ${lowVal.toString(16)}")
                        }
                    }
                    else -> {
                        throw IllegalStateException("Invalid UTF-16 encoding. bytes found ${code.toString(16)}")
                    }
                }
            }
        }
    }

    override fun encode(inString: String): ByteArray {
        val bytes = ByteBuffer(inString.length * 2, order)
        for (char in inString) {
            when (char.code) {
                in codeRange1 -> {
                    bytes.ushort = char.code.toUShort()
                }
                in codeRange2 -> {
                    bytes.ushort = char.code.toUShort()
                }
                else -> {
                    val code = char.code - BASE
                    bytes.ushort = (HIGH_SURROGATE + (code shr 10)).toUShort()
                    bytes.ushort = (LOW_SURROGATE + (code and 0x3ff)).toUShort()
                }
            }
        }
        return bytes.flip().getBytes()
    }

    override fun UEencode(inString: String): UByteArray {
        val bytes = UByteBuffer(inString.length * 2, order)
        for (char in inString) {
            when (char.code) {
                in codeRange1 -> {
                    bytes.ushort = char.code.toUShort()
                }
                in codeRange2 -> {
                    bytes.ushort = char.code.toUShort()
                }
                else -> {
                    val code = char.code - BASE
                    bytes.ushort = (HIGH_SURROGATE + (code shr 10)).toUShort()
                    bytes.ushort = (LOW_SURROGATE + (code and 0x3ff)).toUShort()
                }
            }
        }
        return bytes.flip().getBytes()
    }

    companion object {
        private val codeRange1 = 0x0000..0xd7ff
        private val codeRange2 = 0xe000..0xffff
        private const val BASE = 0x10000
        private const val HIGH_SURROGATE = 0xd800
        private const val LOW_SURROGATE = 0xdC00

        private val highSurrogateRange = HIGH_SURROGATE..(LOW_SURROGATE - 1)
        private val lowSurrogateRange = LOW_SURROGATE..0xdfff
    }
}

class Utf16LE: Utf16(
    "UTF-16LE",
    ByteOrder.LittleEndian
)

class Utf16BE: Utf16(
    "UTF-16BE",
    ByteOrder.BigEndian
)