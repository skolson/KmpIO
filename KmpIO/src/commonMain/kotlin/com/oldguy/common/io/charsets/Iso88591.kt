package com.oldguy.common.io.charsets

class Iso88591()
    :Charset(
    "ISO-8859-1",
    1..1
)
{
    override fun decode(bytes: ByteArray): String {
        return buildString {
            for (byte in bytes) {
                val c = byte.toInt()
                if (c > MAX_CODE) {
                    throw IllegalStateException("Invalid $name encoding. byte found ${byte.toString(16)}")
                }
                append(Char(c))
            }
        }
    }

    override fun decode(bytes: UByteArray): String {
        return buildString {
            for (byte in bytes) {
                val c = byte.toInt()
                if (c > MAX_CODE) {
                    throw IllegalStateException("Invalid $name encoding. byte found ${byte.toString(16)}")
                }
                append(Char(c))
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

    companion object {
        const val MAX_CODE = 0xff
    }
}