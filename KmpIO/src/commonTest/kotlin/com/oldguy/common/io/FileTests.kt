package com.oldguy.common.io

import com.oldguy.common.io.charsets.Charset
import com.oldguy.common.io.charsets.Charsets
import com.oldguy.common.io.charsets.Utf16LE
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.*
import kotlin.test.*
import kotlin.text.contains
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@ExperimentalCoroutinesApi
open class FileTests {
    val subDirName = "kmpIOtestDir"

    suspend fun filesBasics(testDirectory: File) {
        assertTrue(testDirectory.isDirectory)
        val testText = "Test text"
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

    suspend fun checkTextLines(textFile: TextFile): Int {
        var lines = 0
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
        return lines
    }

    @OptIn(ExperimentalTime::class)
    suspend fun textFileWriteRead(
        testDirectory: File,
        charset: Charset
    ) {
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

    suspend fun mediumTextFile(
        testDirectory: File,
        charset: Charset
    ): File {
        val subDir = testDirectory.resolve(subDirName)
        assertTrue { subDir.exists }
        val fileName = "TextMedium${charset.name}.txt"
        return File(subDir, fileName)
    }

    suspend fun createMediumTextFile(
        testDirectory: File,
        charset: Charset,
        copyCount: Int = 100
    ): File {
        return mediumTextFile(testDirectory, charset).apply {
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

    suspend fun biggerTextFileWriteRead(
        testDirectory: File,
        charset: Charset,
        copyCount: Int = 100
    ) {
        createMediumTextFile(testDirectory, charset, copyCount).apply {
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

    suspend fun testRawWriteRead(
        testDirectory: File,
        namePrefix: String,
        copyCount: Int = 10
    ) {
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

    suspend fun directoryList(
        dir: File,
        filterTest: (String) -> Boolean
    ) {
        dir.apply {
            println(fullPath)  // Use to find full path iosX64 and android tests are using.
            directoryList()
                .filter { filterTest(it) }
                .apply {
                    println(this)
                    assertEquals(contentsShallow.size, size)
                }.forEach {
                    assertTrue(contentsShallow.contains(it), "$it not in contents")
                }
            directoryFiles()
                .filter { filterTest(it.name) }
                .apply {
                    assertEquals(contentsShallow.size, size)
                    forEach {
                        assertTrue { it.exists }
                    }
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

        val contentsShallow = listOf(
            "dir1",
            "dir2",
            "ic_help_grey600_48dp.7zip.zip",
            "ic_help_grey600_48dp.png",
            "SmallTextAndBinary.zip",
            "あ.png"
        )
        val contents = contentsShallow + listOf(
            "dir3",
            "image1.png",
            "image2.png",
            "image3.png",
        )
        val fileNameCount = contents.filter { !it.startsWith("dir") }.size
        val shallowFileNameCount = contentsShallow.filter { !it.startsWith("dir") }.size

        /**
         * Note this is not usable on android device tests
         */
        val macosIgnore = ".DS_Store"

        suspend fun testDirectory(): File {
            File.workingDirectory().up().apply {
                assertTrue(exists)
                return resolve("TestFiles")
            }
        }
    }
}