package com.oldguy.common.io.charsets

import com.oldguy.common.io.Buffer
import com.oldguy.common.io.ByteBuffer
import com.oldguy.common.io.UByteBuffer
import kotlinx.datetime.ZoneOffset

open class Utf32(
    name: String = "UTF-32",
    val order: Buffer.ByteOrder = Buffer.ByteOrder.LittleEndian
) : Charset(
    name,
    4..4
)
{
    override fun decode(bytes: ByteArray, count: Int, offset: Int): String {
        return buildString {
            ByteBuffer(bytes.sliceArray(0 until count), order)
                .apply {
                    position = offset
                    while (remaining > 0) {
                        append(int.toChar())
                    }
                }
        }
    }

    override fun decode(bytes: UByteArray, count: Int, offset: Int): String {
        return buildString {
            UByteBuffer(bytes.sliceArray(0 until count), order)
                .apply {
                    position = offset
                    while (remaining > 0) {
                        append(int.toChar())
                    }
                }
        }
    }

    override fun encode(inString: String): ByteArray {
        val bytes = ByteBuffer(inString.length * bytesPerChar.last, order)
        for (char in inString) {
            bytes.int = char.code
        }
        return bytes.flip().getBytes()
    }

    override fun UEencode(inString: String): UByteArray {
        val bytes = UByteBuffer(inString.length * bytesPerChar.last, order)
        for (char in inString) {
            bytes.int = char.code
        }
        return bytes.flip().getBytes()
    }

    override fun checkMultiByte(
        bytes: ByteArray,
        count: Int,
        offset: Int,
        throws: Boolean
    ): Int {
        if (count % 4 == 0 && throws)
            throw MultiByteDecodeException(
                "Number of bytes to decode must be divisible by 4",
                count + offset - 1,
                4,
                count % 4,
                bytes[count + offset - 1]
            )
        return count % 4
    }

    override fun byteCount(byte: Byte): Int = 4

    override fun byteCount(byte: UByte): Int = 4
}

class Utf32LE():
    Utf32("UTF-32LE", Buffer.ByteOrder.LittleEndian)

class Utf32BE():
    Utf32("UTF-32BE", Buffer.ByteOrder.BigEndian)
