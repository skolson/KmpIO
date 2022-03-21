package com.oldguy.common.io

/**
 * Base 64 encode/decode from/to UTF-8 bytes, or from/to String (decoded from UTF-8 bytes). No
 * line separator options available. No options around padding, line handling. Decode ignores
 * non-base64 characters
 */
object Base64 {
    private val charset = Charset(Charsets.Utf8)

    /**
     * Encode incoming string to UTF-8, then do Base64 encode to produce UTF-8 bytes.
     * @param anyString
     * @return ByteArray containing Base64 encoded result in UTF-8
     */
    fun encode(anyString: String): ByteArray = encodeBase64(anyString.encodeToByteArray())
    /**
     * Base64 encode to produce UTF-8 bytes.
     * @param bytes any ByteArray
     * @return ByteArray containing Base64 encoded result in UTF-8
     */
    fun encode(bytes: ByteArray): ByteArray = encodeBase64(bytes)
    /**
     * Base64 encode to produce String.
     * @param bytes any ByteArray
     * @return String containing Base64 result
     */
    fun encodeToString(bytes: ByteArray): String = charset.decode(encodeBase64(bytes))
    /**
     * Base64 decode to produce String. Will fail if decoded bytes are not valid UTF-8
     * @param base64Bytes ByteArray containing Base64 UTF-8 encoded bytes
     * @return String containing decoded result
     */
    fun decode(base64Bytes: ByteArray): String = charset.decode(decodeBase64(base64Bytes))
    /**
     * Base64 decode to produce String. Will fail if decoded bytes are not valid UTF-8
     * @param base64 String containing Base64
     * @return String containing decoded result
     */
    fun decode(base64: String): String = charset.decode(decodeBase64(charset.encode(base64)))
    /**
     * Base64 decode to produce UTF-8 Bytes. Will fail if decoded bytes are not valid UTF-8
     * @param base64 string containing Base64 encoded payload that decodes to valid UTF-8 bytes
     * @return ByteArray containing decoded result in UTF-8 bytes
     */
    fun decodeToBytes(base64: String): ByteArray = decodeBase64(charset.encode(base64))

    /**
     * changes UTF-8 to Base64 encoding
     */
    private fun encodeBase64(bytes: ByteArray): ByteArray {
        val table = (CharRange('A', 'Z') + CharRange('a', 'z') + CharRange(
            '0',
            '9'
        ) + '+' + '/').toCharArray()
        val output = ByteBuffer(bytes.size * 2)
        var paddingCount = 0
        var position = 0
        while (position < bytes.size) {
            var b = bytes[position].toInt() and 0xFF shl 16 and 0xFFFFFF
            if (position + 1 < bytes.size) b =
                b or (bytes[position + 1].toInt() and 0xFF shl 8) else paddingCount++
            if (position + 2 < bytes.size) b =
                b or (bytes[position + 2].toInt() and 0xFF) else paddingCount++
            for (i in 0 until 4 - paddingCount) {
                val c = b and 0xFC0000 shr 18
                output.byte = table[c].code.toByte()
                b = b shl 6
            }
            position += 3
        }
        for (i in 0 until paddingCount) {
            output.byte = '='.code.toByte()
        }
        return output.flip().getBytes()
    }

    private fun decodeBase64(base64In: ByteArray): ByteArray {
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

        val base64 = base64In.filter { table[it.toInt()] != -1 }
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
                output.byte = c.toChar().code.toByte()
                b = b shl 8
                count--
            }
            position += 4
        }
        return output.flip().getBytes()
    }
}