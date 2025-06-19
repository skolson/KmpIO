package com.oldguy.common.io

import com.oldguy.common.io.charsets.Charsets
import com.oldguy.common.io.charsets.Utf8
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
class CompressionTests {
    val test = "123456123456sdfghjklzxcvbxcvxcvbzxcvb"
    val bytes = Utf8().encode(test)
    // bytearray of test string compressed with Android Deflater to do a quick interop test
    val zipDeflaterNowrapResult = byteArrayOf(
        0x1,
        0x25,
        0x0,
        0xDA.toByte(),
        0xFF.toByte(),
        0x31,
        0x32,
        0x33,
        0x34,
        0x35,
        0x36,
        0x31,
        0x32,
        0x33,
        0x34,
        0x35,
        0x36,
        0x73,
        0x64,
        0x66,
        0x67,
        0x68,
        0x6A,
        0x6B,
        0x6C,
        0x7A,
        0x78,
        0x63,
        0x76,
        0x62,
        0x78,
        0x63,
        0x76,
        0x78,
        0x63,
        0x76,
        0x62,
        0x7A,
        0x78,
        0x63,
        0x76,
        0x62
    )

    @Test
    fun basicCompressionTests() {
        val buf = ByteBuffer(bytes)
        val out = ByteBuffer(test.length)
        runTest {
            CompressionDeflate(true).apply {
                val compressed = this.compress(input = { buf }) { out.expand(it) }
                assertEquals(28UL, compressed)
                out.flip()

                val out2 = ByteBuffer(test.length)
                val uncompressed = decompress(input = { out }) { out2.expand(it) }
                out2.flip()
                assertEquals(test.length.toULong(), uncompressed)
                assertEquals(test, Utf8().decode(out2.getBytes()))
            }
            // decompress a byte array of the test string built with Java Deflater class
            CompressionDeflate(true).apply {
                val payload = ByteBuffer(zipDeflaterNowrapResult)
                val out2 = ByteBuffer(test.length)
                val uncompressed = decompress(input = { payload }) { out2.expand(it) }
                out2.flip()
                assertEquals(test.length.toULong(), uncompressed)
                assertEquals(test, Utf8().decode(out2.getBytes()))
            }
        }
    }

    @Test
    fun mediumCompressionTest() {
        val big = 40000
        val copies = 100u
        val buf = ByteBuffer(big)
        for (i in 0u until copies) buf.putBytes(bytes)
        buf.flip()
        val out = ByteBuffer(big)
        runTest {
            CompressionDeflate(true).apply {
                val compressed = this.compress(input = { buf }) { out.expand(it) }
                assertEquals(61UL, compressed)
                out.flip()
                val out2 = ByteBuffer(big)
                val uncompressed = decompress(input = { out }) { out2.expand(it) }
                out2.flip()
                assertEquals((test.length.toULong() * copies), uncompressed)
                val bigString = Utf8().decode(out2.getBytes())
                assertTrue(bigString.startsWith(test))
                assertTrue(bigString.endsWith(test))
                assertEquals(copies.toInt(), bigString.split(test).size - 1)
            }
        }
    }

    @Test
    /**
     * Initialize a chunk of data with a repeating sequence. Compress and decompress, verify result
     */
    fun compressTest() {
        runTest {
            val token = ByteArray(10) { i -> (i + 48).toByte() }
            val dataSize = 24000
            val inData = ByteArray(dataSize) { i -> token[i % 10] }
            val out = ByteBuffer(dataSize)
            val inBuffer = ByteBuffer(inData)
            CompressionDeflate(true).apply {
                val compressed = compress( input = { inBuffer }) { out.expand(it) }
                assertEquals(73, compressed.toInt())
                out.flip()
                val out2 = ByteBuffer(24000)
                val uncompressed = decompress(input = { out }) { out2.expand(it) }
                out2.flip()
                assertEquals(dataSize, uncompressed.toInt())
                assertEquals(dataSize, out2.limit)
                assertEquals(0, out2.compareTo(inBuffer))
            }
        }
    }

}