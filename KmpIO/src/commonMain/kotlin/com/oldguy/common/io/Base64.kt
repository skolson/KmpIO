package com.oldguy.common.io

object Base64 {
    private val charset = Charset(Charset.UTF_8)
    fun encode(base64: String): ByteArray = encodeBase64(base64.encodeToByteArray())
    fun decode(base64: ByteArray): String = charset.decode(decodeBase64(base64))

    /**
     * changes UTF-8 to Base64 encoding
     */
    private fun encodeBase64(bytes: ByteArray): ByteArray {
        val table = (CharRange('A', 'Z') + CharRange('a', 'z') + CharRange(
            '0',
            '9'
        ) + '+' + '/').toCharArray()
        val output = ByteBuffer(bytes.size * 2)
        var padding = 0
        var position = 0
        while (position < bytes.size) {
            var b = bytes[position].toInt() and 0xFF shl 16 and 0xFFFFFF
            if (position + 1 < bytes.size) b =
                b or (bytes[position + 1].toInt() and 0xFF shl 8) else padding++
            if (position + 2 < bytes.size) b =
                b or (bytes[position + 2].toInt() and 0xFF) else padding++
            for (i in 0 until 4 - padding) {
                val c = b and 0xFC0000 shr 18
                output.int = table[c].code
                b = b shl 6
            }
            position += 3
        }
        for (i in 0 until padding) {
            output.int = '='.code
        }
        return output.contentBytes
    }

    private fun decodeBase64(base64: ByteArray): ByteArray {
        val table = intArrayOf(
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            62,
            -1,
            -1,
            -1,
            63,
            52,
            53,
            54,
            55,
            56,
            57,
            58,
            59,
            60,
            61,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            0,
            1,
            2,
            3,
            4,
            5,
            6,
            7,
            8,
            9,
            10,
            11,
            12,
            13,
            14,
            15,
            16,
            17,
            18,
            19,
            20,
            21,
            22,
            23,
            24,
            25,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            26,
            27,
            28,
            29,
            30,
            31,
            32,
            33,
            34,
            35,
            36,
            37,
            38,
            39,
            40,
            41,
            42,
            43,
            44,
            45,
            46,
            47,
            48,
            49,
            50,
            51,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1,
            -1
        )

        val output = ByteBuffer(base64.size)
        var position = 0
        while (position < base64.size) {
            var b: Int
            if (table[base64[position].toInt()] != -1) {
                b = table[base64[position].toInt()] and 0xFF shl 18
            } else {
                position++
                continue
            }
            var count = 0
            if (position + 1 < base64.size && table[base64[position + 1].toInt()] != -1) {
                b = b or (table[base64[position + 1].toInt()] and 0xFF shl 12)
                count++
            }
            if (position + 2 < base64.size && table[base64[position + 2].toInt()] != -1) {
                b = b or (table[base64[position + 2].toInt()] and 0xFF shl 6)
                count++
            }
            if (position + 3 < base64.size && table[base64[position + 3].toInt()] != -1) {
                b = b or (table[base64[position + 3].toInt()] and 0xFF)
                count++
            }
            while (count > 0) {
                val c = b and 0xFF0000 shr 16
                output.int = c.toChar().code
                b = b shl 8
                count--
            }
            position += 4
        }
        return ByteArray(output.limit).apply {
            output.flip()
            output.getBytes(this)
        }
    }
}