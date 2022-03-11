package com.oldguy.common.io

import java.nio.ByteBuffer
import java.nio.CharBuffer

@Suppress("UNUSED_PARAMETER")
actual class Charset actual constructor(val set: Charsets) {
    actual val charset:Charsets = set
    val javaCharset: java.nio.charset.Charset = java.nio.charset.Charset.forName(set.charsetName)

    private val encoder = javaCharset.newEncoder()
    private val decoder = javaCharset.newDecoder()

    actual fun decode(bytes: ByteArray): String {
        return decoder.decode(ByteBuffer.wrap(bytes)).toString()
    }

    actual fun encode(inString: String): ByteArray {
        return if (set == Charsets.Utf8)
            inString.encodeToByteArray()
        else {
            val buf = encoder.encode(CharBuffer.wrap(inString))
            val bytes = ByteArray(buf.limit())
            buf.get(bytes)
            bytes
        }
    }
}