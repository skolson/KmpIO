package com.oldguy.common.io

import com.oldguy.common.io.charsets.Utf8
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TextBufferSimpleTests {
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
                next()
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
                next()
                val value2 = quotedString()
                assertEquals("name2", name2)
                assertEquals("val\"ue2", value2)
            }
        }
    }

    @Test
    fun parseTokenTest() {
        runTest {
            var count = 0
            val bytes = tokenTest.encodeToByteArray()
            TextBuffer(Utf8()) { buffer, size ->
                bytes.copyInto(buffer)
                count++
                if (count > 1) 0u else bytes.size.toUInt()
            }.apply {
                tokenSeparators = simpleXmlTokenSeparators
                var token = token(true)
                assertEquals("<?", token.separator)
                token = token(true)
                assertEquals("xml", token.value)
                token = token()
                assertEquals("=", token.separator)
                assertEquals("version", token.value)
                token = token()
                assertEquals("1.0", token.value)
                token = token()
                assertEquals("?>", token.separator)
                assertTrue(isEndOfFile)
            }
        }
    }

    @Test
    fun parseSimpleXmlTest() {
        runTest {
            var count = 0
            val bytes = tokenXmlTest.encodeToByteArray()
            TextBuffer(Utf8()) { buffer, size ->
                bytes.copyInto(buffer)
                count++
                if (count > 1) 0u else bytes.size.toUInt()
            }.apply {
                tokenSeparators = simpleXmlTokenSeparators
                var token = token(true)
                assertEquals("<?", token.separator)
                assertTrue(token.value.isEmpty())
                token = token(true)
                assertEquals("xml", token.value)
                token = token()
                assertEquals("=", token.separator)
                assertEquals("version", token.value)
                token = token()
                assertEquals("1.0", token.value)
                assertTrue(token.quotesFound)
                token = token()
                assertEquals("?>", token.separator)
                token = token()
                assertEquals("<", token.separator)
                token = token(true)
                assertEquals("Test", token.value)
                assertEquals(">", token.separator)
                token = token()
                assertEquals("<", token.separator)
                token = token(true)
                assertEquals("el1", token.value)
                assertFalse(token.quotesFound)
                assertEquals("/>", token.separator)
                token = token()
                assertEquals("<", token.separator)
                token = token(true)
                assertEquals("el2", token.value)
                token = token()
                assertEquals("att1", token.value)
                assertEquals("=", token.separator)
                token = token()
                assertEquals("val1", token.value)
                assertTrue(token.quotesFound)
                token = token()
                assertEquals("att2", token.value.trim())
                assertEquals("=", token.separator)
                token = token()
                assertEquals("val2", token.value)
                assertTrue(token.quotesFound)
                token = token()
                assertEquals("/>", token.separator)
                token = token()
                assertEquals("</", token.separator)
                token = token(true)
                assertEquals("Test", token.value)
                assertEquals(">", token.separator)
                assertTrue(isEndOfFile)
            }
        }
    }

    @Test
    fun parseMultiCharSeparators() {
        runTest {
            var count = 0
            val bytes = pdfSubset.encodeToByteArray()
            TextBuffer(Utf8()) { buffer, size ->
                bytes.copyInto(buffer)
                count++
                if (count > 1) 0u else bytes.size.toUInt()
            }.apply {
                tokenSeparators = pdfTokenSeparators
                var token = token(true)
                assertEquals("1", token.value)
                token = token(true)
                assertEquals("0", token.value)
                token = token(true)
                assertEquals("obj", token.separator)
                assertTrue(token.value.isBlank())
                token = token(true)
                assertTrue(token.value.isBlank())
                assertTrue(token.separator.isBlank())
                token = token(true)
                assertEquals("<<", token.separator)
                assertTrue(token.value.isBlank())
                token = token(true)
                assertTrue(token.value.isBlank())
                assertTrue(token.separator.isBlank())
                token = token(true)
                assertEquals("/", token.separator)
                assertTrue(token.value.isBlank())
                token = token(true)
                assertEquals("Type", token.value)
                assertTrue(token.separator.isBlank())
                token = token(true)
                assertEquals("/", token.separator)
                assertTrue(token.value.isBlank())
                token = token(true)
                assertEquals("Catalog", token.value)
                assertTrue(token.separator.isBlank())
                token = token(true)
                assertEquals(">>", token.separator)
                assertTrue(token.value.isBlank())
                token = token(true)
                assertTrue(token.value.isBlank())
                assertTrue(token.separator.isBlank())
                token = token(true)
                assertEquals("endobj", token.separator)
                assertTrue(token.value.isBlank())
            }
        }
    }
    companion object {
        val utf8 = Utf8()
        const val testAttributes = "name1=\"value1\" name2=\"val\\\"ue2\""

        const val tokenTest = "<?xml version=\"1.0\"?>"
        const val tokenXmlTest = "<?xml version=\"1.0\"?><Test><el1/><el2 att1=\"val1\" att2=\"val2\"/></Test>"
        val simpleXmlTokenSeparators = listOf("<", ">", "/>", "</", "<?", "?>", "<!--", "-->", "=")

        const val pdfSubset = "1 0 obj << /Type /Catalog >> endobj"
        val pdfTokenSeparators = listOf("/", "obj", "endobj", ">>", "<<")
    }

}