package com.oldguy.common.io

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

@ExperimentalCoroutinesApi
class CompressionTest {
    val test = "123456123456sdfghjklzxcvbxcvxcvbzxcvb"
    val bytes = Charset(Charsets.Utf8).encode(test)

    @Test
    fun compressTest1() {
        val javaOut = ByteArrayOutputStream(100)
        val java = DeflaterOutputStream(javaOut, Deflater(Deflater.DEFAULT_STRATEGY, true))
        java.write(bytes)
        java.finish()
        val javaB = javaOut.toByteArray()
        java.close()

        val buf = ByteBuffer(bytes)
        val out = ByteBuffer(test.length)
        runTest {
            CompressionDeflate(true).apply {
                val compressed = this.compress(input = { buf }) {
                    out.expand(it)
                }
                out.flip()

                val b = out.getBytes()
                assertContentEquals(javaB, b)
                assertEquals(javaB.size.toULong(), compressed)
                out.flip()

                val javaI = InflaterInputStream(ByteArrayInputStream(b), Inflater(true))
                val javaIB = javaI.readBytes()
                assertEquals(test.length, javaIB.size)
                assertContentEquals(bytes, javaIB)
                javaI.close()

                ByteBuffer(test.length).apply {
                    val uncompressed = decompress(
                        compressed,
                        2048u,
                        input = {
                            out
                        }
                    ) {
                        expand(it)
                    }
                    flip()
                    val bk = getBytes()
                    assertEquals(test.length, bk.size)
                    assertEquals(test.length.toULong(), uncompressed)
                    assertContentEquals(bytes, bk)
                }
            }
        }
    }
}