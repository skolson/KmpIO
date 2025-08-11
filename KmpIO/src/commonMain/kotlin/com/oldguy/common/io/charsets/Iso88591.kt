package com.oldguy.common.io.charsets

class Iso88591()
    :Charset(
    "ISO-8859-1",
    1..1
)
{
    override fun decode(bytes: ByteArray, count: Int, offset: Int): String {
        return buildString {
            var read = 0
            for (i in offset until offset + count) {
                val c = bytes[i].toInt()
                if (c > MAX_CODE) {
                    throw IllegalStateException("Invalid $name encoding. byte found ${bytes[i].toString(16)}")
                }
                append(Char(c))
                read++
                if (read > count) break
            }
        }
    }

    override fun decode(bytes: UByteArray, count: Int, offset: Int): String {
        return buildString {
            for (i in offset until offset + count) {
                var read = 0
                val c = bytes[i].toInt()
                if (c > MAX_CODE) {
                    throw IllegalStateException("Invalid $name encoding. byte found ${bytes[i].toString(16)}")
                }
                append(Char(c))
                read++
                if (read > count) break
            }
        }
    }

    override fun encode(inString: String): ByteArray {
        val bytes = ByteArray(inString.length)
        for ((i, char) in inString.withIndex()) {
            if (char.code > MAX_CODE) {
                throw IllegalStateException("Invalid $name encoding. Char code found ${char.code.toString(16)}")
            }
            bytes[i] = char.code.toByte()
        }
        return bytes
    }

    override fun UEencode(inString: String): UByteArray {
        val bytes = UByteArray(inString.length)
        for ((i, char) in inString.withIndex()) {
            if (char.code > MAX_CODE) {
                throw IllegalStateException("Invalid $name encoding. Char code found ${char.code.toString(16)}")
            }
            bytes[i] = char.code.toUByte()
        }
        return bytes
    }

    override fun checkMultiByte(
        bytes: ByteArray,
        count: Int,
        offset: Int,
        throws: Boolean
    ): Int = 0

    override fun byteCount(byte: Byte): Int = 1

    override fun byteCount(byte: UByte): Int = 1

    companion object {
        const val MAX_CODE = 0xff
    }
}