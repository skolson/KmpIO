package com.oldguy.common.io

import com.oldguy.common.io.charsets.Utf8
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TextBufferTests {
    @Test
    fun utf8NextTests() {
        val bytes = testString1.encodeToByteArray()
        var count = 0
        val textBuffer = TextBuffer(utf8) { buffer, size ->
            if (count == 0) {
                bytes.copyInto(buffer)
                count++
                bytes.size.toUInt()
            } else {
                0u
            }
        }
        runTest {
            for (c in testString1) {
                assertEquals(c, textBuffer.next())
                assertFalse(textBuffer.isEndOfFile)
            }
            assertFalse(textBuffer.isEndOfFile)
            assertEquals(Char(0), textBuffer.next())
            assertTrue(textBuffer.isEndOfFile)
        }
    }

    companion object {
        val utf8 = Utf8()
        const val testString1 = "Hello, 世界"
    }
}