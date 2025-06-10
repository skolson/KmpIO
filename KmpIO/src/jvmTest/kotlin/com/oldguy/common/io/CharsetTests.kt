package com.oldguy.common.io

import com.oldguy.common.io.charsets.Charset
import com.oldguy.common.io.charsets.Utf16BE
import com.oldguy.common.io.charsets.Utf16LE
import com.oldguy.common.io.charsets.Utf8
import com.oldguy.common.io.charsets.Charsets
import com.oldguy.common.io.charsets.Iso88591
import com.oldguy.common.io.charsets.Utf32BE
import com.oldguy.common.io.charsets.Utf32LE
import com.oldguy.common.io.charsets.Windows1252
import java.nio.CharBuffer
import kotlin.test.Test
import kotlin.test.assertEquals

class CharsetTests {

    @Test
    fun charsetNames() {
        assertEquals("UTF-16LE", Utf16LE().name)
        assertEquals("UTF-16BE", Utf16BE().name)
        assertEquals("UTF-8", Utf8().name)
        assertEquals("UTF-16LE", Charsets.fromName("UTF-16LE").name)
        assertEquals("UTF-16BE", Charsets.fromName("UTF-16BE").name)
        assertEquals("UTF-16BE", Charsets.fromName("ISO-10646-UCS-2").name)
        assertEquals("UTF-8", Charsets.fromName("UTF-8").name)
        assertEquals("UTF-32LE", Charsets.fromName("UTF-32LE").name)
        assertEquals("UTF-32BE", Charsets.fromName("UTF-32BE").name)
        assertEquals("ISO-8859-1", Charsets.fromName("ISO-8859-1").name)
        assertEquals("Windows-1252", Charsets.fromName("cp1252").name)
    }

    @Test
    fun testUtf16LE() {
        compareCharset(Utf16LE())
    }

    @Test
    fun testUtf16BE() {
        compareCharset(Utf16BE())
    }

    @Test
    fun testUtf8() {
        compareCharset(Utf8())
    }

    @Test
    fun testUtf32LE() {
        compareCharset(Utf32LE())
    }

    @Test
    fun testUtf32BE() {
        compareCharset(Utf32BE())
    }

    @Test
    fun test8859_1() {
        compareCharset(Iso88591())
    }

    @Test
    fun testWindows1252() {
        compareCharset(Windows1252(), winString)
    }

    private fun compareCharset(
        set: Charset,
        str: String = bigString) {
        val javaCharset: java.nio.charset.Charset = java.nio.charset.Charset.forName(set.name)
        val javaBigString = CharBuffer.wrap(str)
        val javaBuffer = javaCharset.newEncoder().encode(javaBigString)

        val buf = set.encode(str)
        compareBuffers(javaBuffer, buf)

        val str = set.decode(buf)
        assertEquals(str, str)
    }

    private fun compareBuffers(
            javaBuf: java.nio.ByteBuffer,
            buf: ByteArray) {
        assertEquals(javaBuf.remaining(), buf.size)
        var i = 0
        while (javaBuf.hasRemaining()) {
            assertEquals(javaBuf.get(), buf[i++])
        }
    }

    companion object {
        private val bigString = "1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZ`~!@#$%^&*()-_=+[{]}\\|;:',<.>/?"
        private val winString = "1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZ`~!@#$%^&*()-_=+[{]}\\|;:',<.>/?\u201a\u0152"
    }
}