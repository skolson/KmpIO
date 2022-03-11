package com.oldguy.common.io

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@ExperimentalCoroutinesApi
class BufferReaderTests {

    @Test
    fun readerTest() {
        runTest {
            val source = RawFile(File(FileTests.testDirectory(), "SmallTextAndBinary.zip"))
            val chunkSize = 16u
            val buf = ByteBuffer(24)
            var totalBytes = 0u
            var totalReaderBytes = 0u
            var chunks = 0u
            BufferReader {
                buf.clear()
                totalBytes += source.read(buf)
                buf.flip()
            }.apply {
                var chunk = readArray(chunkSize.toInt())
                while (chunk.isNotEmpty()) {
                    chunks++
                    totalReaderBytes += chunk.size.toUInt()
                    chunk = readArray(chunkSize.toInt())
                }
            }
            val fileSize = 81519u
            assertEquals(fileSize, totalBytes)
            assertEquals((fileSize / chunkSize) + 1u, chunks)
            assertEquals(fileSize, totalReaderBytes)
        }
    }
}