package com.oldguy.common.io

import com.oldguy.common.io.FileTests.Companion.line1
import com.oldguy.common.io.FileTests.Companion.line2
import com.oldguy.common.io.FileTests.Companion.line3
import com.oldguy.common.io.charsets.Charset
import com.oldguy.common.io.charsets.Iso88591
import com.oldguy.common.io.charsets.Utf16LE
import com.oldguy.common.io.charsets.Utf8
import com.oldguy.common.io.charsets.Windows1252
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail
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

    suspend fun mediumTextFile(charset: Charset) {
        println(charset.name)
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
        raw.close()
        raw.file.delete()
    }

    @Test
    fun mediumTextFileAllCharsets() {
        runTest(timeout = 5.minutes) {
            mediumTextFile(Utf8())
            mediumTextFile(Utf16LE())
            mediumTextFile(Iso88591())
            mediumTextFile(Windows1252())
        }
    }

    @Test
    fun parseQuotedStringTest() {
        runTest {
            var count = 0
            val bytes = testAttributes.encodeToByteArray()
            TextBuffer(Utf8()) { buffer, size ->
                bytes.copyInto(buffer)
                count++
                if (count > 1) 0u else bytes.size.toUInt()
            }.apply {
                val name = StringBuilder(32).apply {
                    var c = next()
                    while (c != '=') {
                        if (!c.isWhitespace()) append(c)
                        c = next()
                    }
                }.toString()
                val value = quotedString()
                assertEquals("name1", name)
                assertEquals("value1", value)
                val name2 = StringBuilder(32).apply {
                    var c = next()
                    while (c != '=') {
                        if (!c.isWhitespace()) append(c)
                        c = next()
                    }
                }.toString()
                val value2 = quotedString()
                assertEquals("name2", name2)
                assertEquals("val\"ue2", value2)
            }
        }
    }

    companion object {
        val utf8 = Utf8()
        const val testString1 = "Hello, 世界"

        val testAttributes = "name1=\"value1\" name2=\"val\\\"ue2\""
    }
}