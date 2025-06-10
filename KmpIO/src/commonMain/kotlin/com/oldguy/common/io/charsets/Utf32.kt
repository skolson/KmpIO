package com.oldguy.common.io.charsets

import com.oldguy.common.io.Buffer
import com.oldguy.common.io.ByteBuffer
import com.oldguy.common.io.UByteBuffer

open class Utf32(
    name: String = "UTF-32",
    val order: Buffer.ByteOrder = Buffer.ByteOrder.LittleEndian
) : Charset(
    name,
    4..4
)
{
    override fun decode(bytes: ByteArray): String {
        return buildString {
            val buf = ByteBuffer(bytes, order)
            while (buf.remaining > 0) {
                append(buf.int.toChar())
            }
        }
    }

    override fun decode(bytes: UByteArray): String {
        return buildString {
            val buf = UByteBuffer(bytes, order)
            while (buf.remaining > 0) {
                append(buf.int.toChar())
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
}

class Utf32LE():
    Utf32("UTF-32LE", Buffer.ByteOrder.LittleEndian)

class Utf32BE():
    Utf32("UTF-32BE", Buffer.ByteOrder.BigEndian)
