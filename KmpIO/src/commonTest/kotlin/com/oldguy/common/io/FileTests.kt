package com.oldguy.common.io

import com.oldguy.common.io.charsets.Charset
import com.oldguy.common.io.charsets.Charsets
import com.oldguy.common.io.charsets.Utf16LE
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.*
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@ExperimentalCoroutinesApi
class FileTests(testDirPath: String) {
    private val testDirectory = File(testDirPath)
    private val subDirName = "kmpIOtestDir"

    fun filesBasics() {
        assertTrue(testDirectory.isDirectory)
        val testText = "Test text"
        runTest {
            val subDir = testDirectory.resolve(subDirName)
            println("subDir: ${subDir.fullPath}")
            assertTrue(subDir.exists)
            assertTrue(subDir.isDirectory)

            val testFileName = "Test.txt"
            val testFilePath = "${testDirectory.fullPath}/$subDirName/$testFileName"
            File(subDir, testFileName).delete()
            val tmpList = subDir.directoryList()
            assertFalse(tmpList.contains(testFileName))

            File(subDir, testFileName).apply {
                assertEquals(false, exists)
                assertEquals("txt", extension)
                assertEquals(testFileName, name)
                assertEquals("Test", nameWithoutExtension)
                assertEquals(false, isDirectory)
                assertEquals(testFilePath, fullPath)
                TextFile(this, mode = FileMode.Write).use {
                    it.write(testText)
                }
                assertTrue(subDir.directoryList().contains(testFileName))
                TextFile(this).forEachLine {count, line ->
                    assertEquals(1, count)
                    assertEquals(testText,line)
                    true
                }
            }
        }
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

    @OptIn(ExperimentalTime::class)
    fun textFileWriteRead(charset: Charset) {
        runTest {
            val subDir = testDirectory.resolve(subDirName)
            val name = "Text${charset.name}.txt"
            File(subDir, name).delete()
            val fil = File(subDir, name)
            assertEquals(false, fil.exists)
            TextFile(
                fil,
                charset,
                FileMode.Write,
                FileSource.File
            ).use {
                it.write(textContent)
            }

            fil.newFile().apply {
                assertEquals(true, exists)
                val lastModDate = lastModified!!
                val createdDate = createdTime!!
                val lastAccessDate = lastAccessTime!!
                val x = Clock.System.now().toLocalDateTime(TimeZone.UTC)
                Clock.System.now().toLocalDateTime(TimeZones.default).apply {
                    assertEquals(year, lastModDate.year)
                    assertEquals(month.number, lastModDate.month.number)
                    assertEquals(day, lastModDate.day)
                    assertEquals(year, lastAccessDate.year)
                    assertEquals(month.number, lastAccessDate.month.number)
                    assertEquals(day, lastAccessDate.day)
                    assertEquals(year, createdDate.year)
                    assertEquals(month.number, createdDate.month.number)
                    assertEquals(day, createdDate.day)
                }
            }

            val textFileIn = TextFile(
                fil,
                charset,
                FileMode.Read,
                FileSource.File
            )
            val lines = checkTextLines(textFileIn)
            assertEquals(6, lines)
            fil.newFile().delete()
        }
    }

    suspend fun mediumTextFile(charset: Charset): File {
        val subDir = testDirectory.resolve(subDirName)
        assertTrue { subDir.exists }
        val fileName = "TextMedium${charset.name}.txt"
        return File(subDir, fileName)
    }

    suspend fun createMediumTextFile(charset: Charset, copyCount: Int = 100): File {
        return mediumTextFile(charset).apply {
            delete()
            assertFalse(newFile().exists)
            TextFile(
                this,
                charset,
                FileMode.Write,
                FileSource.File
            ).apply {
                repeat(copyCount) {
                    write(textContent)
                }
                close()
            }
            assertTrue(newFile().exists)
        }
    }

    fun biggerTextFileWriteRead(charset: Charset, copyCount: Int = 100) {
        runTest {
            createMediumTextFile(charset, copyCount).apply {
                val textFileIn = TextFile(
                    this,
                    charset,
                    FileMode.Read,
                    FileSource.File
                )
                val lines = checkTextLines(textFileIn)
                assertEquals(6 * copyCount, lines)
                newFile().delete()
            }
        }
    }

    fun testRawWriteRead(namePrefix: String, copyCount: Int = 10) {
        runTest {
            val subDir = testDirectory.resolve(subDirName)
            println("subDir: ${subDir.fullPath}, prefix: $namePrefix")
            var fil = File(subDir, "${namePrefix}Hex.utf16")
            fil.delete()
            RawFile(fil, FileMode.Write).use { file ->
                repeat(copyCount) { file.write(ByteBuffer(hexContent)) }
            }
            fil = fil.newFile()
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
                val lastLine = Utf16LE().decode(buf.getBytes(count.toInt()))
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
        val hexContent = Utf16LE().encode(textContent)

        suspend fun testDirectory(): File {
            File.workingDirectory().up().apply {
                assertTrue(exists)
                return resolve("TestFiles")
            }
        }
    }
}

@ExperimentalCoroutinesApi
class FileUnitTests {
    private val path = File.tempDirectoryPath()
    private val tests = FileTests(path)

    @Test
    fun textUtf8Basics() {
        tests.filesBasics()
        tests.textFileWriteRead(Charsets.Utf8.charset)
    }

    @Test
    fun textMediumSizeUtf8Basics() {
        tests.biggerTextFileWriteRead(Charsets.Utf8.charset, 100)
    }

    @Test
    fun textUtf16leBasics() {
        tests.textFileWriteRead(Charsets.Utf16LE.charset)
    }

    @Test
    fun rawSmallTest() {
        try {
            tests.testRawWriteRead("Small", 1)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    @Test
    fun directoryList() {
        runTest {
            FileTests.testDirectory().apply {
                println(fullPath)  // Use to find full path iosX64 and android tests are using.
                directoryList().apply {
                    println(this)
                    assertEquals(8, size)
                    assertTrue { contains("ZerosZip64.zip") }
                    assertTrue { contains("Zip64_90,000_files.zip") }
                    assertTrue { contains("SmallTextAndBinary.zip") }
                    assertTrue { contains("ic_help_grey600_48dp.png") }
                    assertTrue { contains("ic_help_grey600_48dp.7zip.zip") }
                    assertTrue { contains("dir1") }
                    assertTrue { contains("dir2") }
                    assertTrue { contains("あ.png") }
                }
                directoryFiles().apply {
                    assertEquals(7, size)
                    forEach {
                        assertTrue { it.exists }
                    }
                }
            }
        }
    }
}