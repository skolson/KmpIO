package com.oldguy.common.io.charsets

class Windows1252()
    :Charset(
    "Windows-1252",
    1..1
) {
    override fun decode(bytes: ByteArray, count: Int): String {
        return buildString {
            for(i in 0 until count) {
                val key = bytes[i].toInt()
                checkCode(key)
                val c = byteToCode[key] ?: key
                append(Char(c))
            }
        }
    }

    override fun decode(bytes: UByteArray, count: Int): String {
        return buildString {
            for(i in 0 until count) {
                val key = bytes[i].toInt()
                checkCode(key)
                val c = byteToCode[key] ?: key
                append(Char(c))
            }
        }
    }

    override fun encode(inString: String): ByteArray {
        val bytes = ByteArray(inString.length)
        for ((i, char) in inString.withIndex()) {
            checkCode(char.code)
            bytes[i] = codeToByte[char.code] ?: char.code.toByte()
        }
        return bytes
    }

    override fun UEencode(inString: String): UByteArray {
        val bytes = UByteArray(inString.length)
        for ((i, char) in inString.withIndex()) {
            checkCode(char.code)
            bytes[i] = codeToByte[char.code]?.toUByte() ?: char.code.toUByte()
        }
        return bytes
    }

    private fun checkCode(code: Int) {
        if (code > MAX_CODE && !codeToByte.containsKey(code)) {
                throw IllegalStateException("Invalid $name encoding. Char code found ${code.toString(16)}")
        }
    }

    companion object {
        val codeToByte = mapOf<Int, Byte>(
            0x20ac to 0x80.toByte(),
            0x201a to 0x82.toByte(),
            0x0192 to 0x83.toByte(),
            0x201e to 0x84.toByte(),
            0x2026 to 0x85.toByte(),
            0x2020 to 0x86.toByte(),
            0x2021 to 0x87.toByte(),
            0x02c6 to 0x88.toByte(),
            0x2030 to 0x89.toByte(),
            0x0160 to 0x8a.toByte(),
            0x2039 to 0x8b.toByte(),
            0x0152 to 0x8c.toByte(),
            0x017d to 0x8e.toByte(),
            0x2018 to 0x91.toByte(),
            0x2019 to 0x92.toByte(),
            0x201c to 0x93.toByte(),
            0x201d to 0x94.toByte(),
            0x2022 to 0x95.toByte(),
            0x2013 to 0x96.toByte(),
            0x2014 to 0x97.toByte(),
            0x02dc to 0x98.toByte(),
            0x2122 to 0x99.toByte(),
            0x0161 to 0x9a.toByte(),
            0x203a to 0x9b.toByte(),
            0x0153 to 0x9c.toByte(),
            0x0178 to 0x9f.toByte(),
        )
        val byteToCode = codeToByte.entries.associate { (k, v) -> v.toInt() to k }

        const val MAX_CODE = 0xff

    }
}