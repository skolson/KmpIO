package com.oldguy.common.io

import com.oldguy.common.io.FileTests.Companion.line1
import com.oldguy.common.io.FileTests.Companion.line2
import com.oldguy.common.io.FileTests.Companion.line3
import com.oldguy.common.io.charsets.Utf8
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class TextBufferTests {
    private val path = File.tempDirectoryPath()
    private val tests = FileTests(path)

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

    @Test
    fun utf8MediumTextFile() {
        runTest(timeout = 5.minutes) {
            val charset = Utf8()
            tests.createMediumTextFile(charset)
            val raw = RawFile(tests.mediumTextFile(charset))
            TextBuffer(charset) { buffer, size ->
                raw.read(ByteBuffer(buffer))
            }.apply {
                var lines = 0
                forEachLine { count, line ->
                    when ((count - 1) % 6) {
                        0 -> assertEquals(line1, line)
                        1 -> assertEquals(line2, line)
                        2 -> assertEquals(line3, line)
                        3, 4 -> assertTrue(line.isEmpty())
                        5 -> assertEquals("Line6", line)
                        else -> fail("Unexpected line $count, content:\"$line\", charset: $charset")
                    }
                    lines = count
                    true
                }
                assertEquals(600, lines)
            }
        }
    }

    companion object {
        val utf8 = Utf8()
        const val testString1 = "Hello, 世界"
    }
}