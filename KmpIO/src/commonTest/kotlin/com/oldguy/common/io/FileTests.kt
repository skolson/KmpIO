package com.oldguy.common.io

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.*
import kotlin.test.*

@ExperimentalCoroutinesApi
class FileTests(testDirPath: String) {
    private val testDirectory = File(testDirPath)
    private val subDirName = "kmpIOtestDir"

    fun filesBasics() {
        assertTrue(testDirectory.isDirectory)
        runTest {
            val subDir = testDirectory.resolve(subDirName)
            assertTrue(subDir.exists)
            assertTrue(subDir.isDirectory)
        }

        val testFileName = "Test.txt"
        val textFile = File(testDirectory, testFileName)
        assertEquals(false, textFile.exists)
        assertEquals(".txt", textFile.extension)
        assertEquals(testFileName, textFile.name)
        assertEquals("Test", textFile.nameWithoutExtension)
        assertEquals(false, textFile.isDirectory)
        val path ="${testDirectory.path}/$testFileName".replace('\\', '/')
        assertEquals(path, textFile.path.replace('\\', '/'))

        val tmpList = testDirectory.listFiles
        assertTrue(tmpList.isNotEmpty())
        assertEquals(1, tmpList.count {it.name == subDirName})
    }

    private fun checkTextLines(textFile: TextFile): Int {
        var lines = 0
        runTest {
            textFile.forEachLine { count, it ->
                when ((count - 1) % 6) {
                    0 -> assertEquals(line1, it)
                    1 -> assertEquals(line2, it)
                    2 -> assertEquals(line3, it)
                    3, 4 -> assertTrue(it.isEmpty())
                    5 -> assertEquals("Line6", it)
                    else -> fail("Unexpected line $count, content:\"$it\", file ${textFile.file.name}, charset: ${textFile.charset}.")
                }
                lines = count
                true
            }
        }
        return lines
    }

    fun textFileWriteRead(charset: Charset) {
        runTest {
            val subDir = testDirectory.resolve(subDirName)
            val fil = File(subDir, "Text${charset.charset.charsetName}.txt")
            fil.delete()
            assertEquals(false, fil.exists)
            TextFile(
                fil,
                charset,
                FileMode.Write,
                FileSource.File
            ).use {
                it.write(textContent)
            }

            assertEquals(true, fil.exists)
            assertEquals(textContent.length * charset.charset.bytesPerChar, fil.size.toInt())
            val lastModDate = fil.lastModified
            val createdDate = fil.createdTime
            val lastAccessDate = fil.lastAccessTime
            val nowTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            assertEquals(nowTime.year, lastModDate.year)
            assertEquals(nowTime.monthNumber, lastModDate.monthNumber)
            assertEquals(nowTime.dayOfMonth, lastModDate.dayOfMonth)
            assertEquals(nowTime.year, lastAccessDate.year)
            assertEquals(nowTime.monthNumber, lastAccessDate.monthNumber)
            assertEquals(nowTime.dayOfMonth, lastAccessDate.dayOfMonth)
            assertEquals(nowTime.year, createdDate.year)
            assertEquals(nowTime.monthNumber, createdDate.monthNumber)
            assertEquals(nowTime.dayOfMonth, createdDate.dayOfMonth)

            val textFileIn = TextFile(
                fil,
                charset,
                FileMode.Read,
                FileSource.File
            )
            val lines = checkTextLines(textFileIn)
            assertEquals(6, lines)
            fil.delete()
        }
    }

    fun biggerTextFileWriteRead(charset: Charset, copyCount: Int = 100) {
        runTest {
            val subDir = testDirectory.resolve(subDirName)
            val fil = File(subDir, "TextMedium${charset.charset.charsetName}.txt")
            fil.delete()
            assertEquals(false, fil.exists)
            val textFile = TextFile(
                fil,
                charset,
                FileMode.Write,
                FileSource.File
            )
            for (i in 0 until copyCount)
                textFile.write(textContent)
            textFile.close()
            assertEquals(true, fil.exists)
            assertEquals(textContent.length * charset.charset.bytesPerChar * copyCount, fil.size.toInt())

            val textFileIn = TextFile(
                fil,
                charset,
                FileMode.Read,
                FileSource.File
            )
            val lines = checkTextLines(textFileIn)
            assertEquals(6 * copyCount, lines)
            fil.delete()
        }
    }

    fun testRawWriteRead(namePrefix: String, copyCount: Int = 10) {
        runTest {
            val subDir = testDirectory.resolve(subDirName)
            val fil = File(subDir, "${namePrefix}Hex.utf16")
            fil.delete()
            RawFile(fil, FileMode.Write).use {
                it.write(ByteBuffer(hexContent))
            }
            assertTrue(fil.exists)
            assertEquals((hexContent.size * copyCount).toULong(), fil.size)
            RawFile(fil).use {
                val buf = ByteBuffer(4096)
                var count = it.read(buf)
                assertEquals(hexContent.size, buf.position)
                assertEquals(hexContent.size.toUInt(), count)
                buf.rewind()
                assertContentEquals(hexContent, buf.getBytes(count.toInt()))
                count = it.read(buf)
                assertEquals(0u, count)
                val x = it.size - 12u
                it.position = x
                buf.clear()
                count = it.read(buf)
                assertEquals(12u, count)
                buf.rewind()
                val lastLine = Charset(Charsets.Utf16le).decode(buf.getBytes(count.toInt()))
                assertEquals("Line6\n", lastLine)
            }
        }
    }

    companion object {
        private const val eol = "\n"
        const val line1 = "Line1 ancvb568099jkhrwsiuoidsygoedyt03ohgnejbj  eo;iuwoiopww79lhzH;EndLine1"
        const val line2 = "Line2 ancvb568099jkhrwsiuoidsygoedyt03ohgnejbj  eo;iuwoiopww79lhzH;EndLine2"
        const val line3 = "Line3 ancvb568099jkhrwsiuoidsygoedytEndLine3"
        val textContent = """
            $line1
            $line2
            $line3
            
            
            Line6
            """.trimIndent() + eol
        val hexContent = Charset(Charsets.Utf16le).encode(textContent)

        suspend fun testDirectory(): File {
            val up = File("..")
            assertTrue(up.exists)
            return up.resolve("TestFiles")
        }
    }
}

@ExperimentalCoroutinesApi
class FileUnitTests {
    private val path = "d:\\temp"
    private val tests = FileTests(path)

    @Test
    fun textUtf8Basics() {
        tests.filesBasics()
        tests.textFileWriteRead(Charset(Charsets.Utf8))
    }

    @Test
    fun textMediumSizeUtf8Basics() {
        tests.biggerTextFileWriteRead(Charset(Charsets.Utf8), 100)
    }

    @Test
    fun textUtf16leBasics() {
        tests.textFileWriteRead(Charset(Charsets.Utf16le))
    }

    @Test
    fun rawSmallTest() {
        try {
            tests.testRawWriteRead("Small", 1)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }
}