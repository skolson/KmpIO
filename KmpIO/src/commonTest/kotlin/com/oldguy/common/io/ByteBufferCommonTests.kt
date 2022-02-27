package com.oldguy.common.io

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
class ByteBufferCommonTests {

    @Test
    fun readerTest() {
        val charset = Charset(Charsets.Utf8)
        val anyContent = "1234567890abcdefghijklmnopqrstuvwxyz"
        val anyBytes = charset.encode(anyContent)

        runTest {
            // empty source content
            BufferReader {
                ByteBuffer(ByteArray(0))
            }.apply {
                val buf = readArray(10)
                assertEquals(0, buf.size)
                assertTrue { isDrained }
                assertEquals(0UL, position)
            }

            // small source content
            var inbuf = ByteBuffer(anyBytes)
            BufferReader {
                inbuf
            }.apply {
                val buf = readArray(100)
                assertEquals(36, buf.size)
                assertTrue { isDrained }
                assertEquals(36UL, position)
                val bufstr = charset.decode(buf)
                assertEquals(anyContent, bufstr)
            }

            // small source content
            inbuf = ByteBuffer(anyBytes)
            BufferReader {
                inbuf
            }.apply {
                var buf = readArray(10)
                assertEquals(10, buf.size)
                assertTrue { !isDrained }
                assertEquals(10UL, position)
                var bufstr = charset.decode(buf)
                assertEquals(anyContent.substring(0, 10), bufstr)

                buf = readArray(10)
                assertEquals(10, buf.size)
                assertTrue { !isDrained }
                assertEquals(20UL, position)
                bufstr = charset.decode(buf)
                assertEquals(anyContent.substring(10, 20), bufstr)

                buf = readArray(20)
                assertEquals(16, buf.size)
                assertTrue { isDrained }
                assertEquals(36UL, position)
                bufstr = charset.decode(buf)
                assertEquals(anyContent.substring(20), bufstr)
            }

            var count = 0
            BufferReader {
                if (count++ < 100) ByteBuffer(anyBytes) else ByteBuffer(0)
            }.apply {
                var buf = readArray(2000)
                assertEquals(2000, buf.size)
                assertTrue { !isDrained }
                assertEquals(2000UL, position)
                var bufstr = charset.decode(buf)
                assertEquals(anyContent, bufstr.substring(0, 36))
                assertEquals(anyContent.substring(0, 20), bufstr.substring(1980))
                buf = readArray(2000)
                assertEquals(1600, buf.size)
                assertTrue { isDrained }
                assertEquals(3600UL, position)
                bufstr = charset.decode(buf)
                assertEquals(anyContent.substring(20), bufstr.substring(0, 16))
                assertEquals(anyContent, bufstr.substring(1600 - anyContent.length))
            }
        }
    }
}