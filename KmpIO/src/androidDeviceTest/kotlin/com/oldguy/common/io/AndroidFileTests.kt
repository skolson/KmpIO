package com.oldguy.common.io

import androidx.test.platform.app.InstrumentationRegistry
import com.oldguy.common.io.charsets.Charset
import com.oldguy.common.io.charsets.Utf16LE
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.*
import kotlinx.datetime.number
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime

@ExperimentalCoroutinesApi
class AndroidFileTests {
    private val testDirectory: File
    private val subDirName = "kmpIOtestDir"

    init {
        AndroidTestBase()
        val path = File.tempDirectoryPath()
        testDirectory = File(path)
    }


    fun filesBasics() {
        assertTrue(testDirectory.isDirectory)
        val testText = "Test text"
        runTest {
            val subDir = testDirectory.resolve(subDirName)
            assertTrue(subDir.exists)
            assertTrue(subDir.isDirectory)
            Directory(subDir).apply {
                assertTrue(directory.exists)
                empty()
            }

            val testFileName = "Test.txt"
            val testFilePath = "${testDirectory.fullPath}/$subDirName/$testFileName"

            File(subDir, testFileName).delete()
            val tmpList = subDir.directoryList()
            assertTrue(tmpList.isEmpty())
            assertEquals(0, tmpList.size)

            File(subDir, testFileName).apply {
                assertEquals(false, exists, "Exists")
                assertEquals("txt", extension, "Extension")
                assertEquals(testFileName, name, "Name")
                assertEquals("Test", nameWithoutExtension, "Name without extension")
                assertEquals(false, isDirectory, "Is directory")
                assertEquals(testFilePath, fullPath, "Full path")
                TextFile(this, mode = FileMode.Write).use {
                    it.write(testText)
                }
                val tmpList2 = subDir.directoryFiles()
                assertTrue(tmpList2.isNotEmpty(), "tmpList2 Not empty")
                assertEquals(1, tmpList2.size)
                assertEquals(testFilePath, tmpList2.first().fullPath)
                TextFile(this).forEachLine { count, line ->
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
        runTest(timeout = 5.minutes) {
            val subDir = testDirectory.resolve(subDirName)
            var fil = File(subDir, "Text${charset.name}.txt")
            fil.delete()
            fil = fil.newFile()
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
            val lastModDate = fil.lastModified!!
            val createdDate = fil.createdTime!!
            val lastAccessDate = fil.lastAccessTime!!
            val nowTime = Clock.System.now().toLocalDateTime(TimeZones.default)
            assertEquals(nowTime.year, lastModDate.year)
            assertEquals(nowTime.month.number, lastModDate.month.number)
            assertEquals(nowTime.day, lastModDate.day)
            assertEquals(nowTime.year, lastAccessDate.year)
            assertEquals(nowTime.month.number, lastAccessDate.month.number)
            assertEquals(nowTime.day, lastAccessDate.day)
            assertEquals(nowTime.year, createdDate.year)
            assertEquals(nowTime.month.number, createdDate.month.number)
            assertEquals(nowTime.day, createdDate.day)

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
            assertTrue { subDir.exists }
            val fileName = "TextMedium${charset.name}.txt"
            File(subDir, fileName).delete()
            val fil = File(subDir, fileName)
            assertEquals(false, fil.exists)
            val textFile = TextFile(
                fil,
                charset,
                FileMode.Write,
                FileSource.File
            )
            (0 until copyCount)
                .forEach { _ ->
                    textFile.write(textContent)
                }
            textFile.close()
            assertEquals(true, File(subDir, fileName).exists)

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
            assertTrue(fil.newFile().exists)
            assertEquals((hexContent.size * copyCount).toULong(), fil.size)
            RawFile(fil).use {
                val buf = ByteBuffer(4096)
                var count = it.read(buf)
                assertEquals(hexContent.size, buf.position)
                assertEquals(hexContent.size.toUInt(), count)
                buf.rewind()
                assertArrayEquals(hexContent, buf.getBytes(count.toInt()))
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

        /**
         * Note this is unusable with android tests where files must come from assets.
         */
        val contents = listOf(
            "dir1",
            "dir2",
            "dir3",
            "image1.png",
            "image2.png",
            "image3.png",
            "ic_help_grey600_48dp.7zip.zip",
            "ic_help_grey600_48dp.png",
            "SmallTextAndBinary.zip",
            "ZerosZip64.zip",
            "Zip64_90,000_files.zip",
            "あ.png"
        )
        fun initializeAndroid() {
            File.appContext = InstrumentationRegistry.getInstrumentation().targetContext
        }

        suspend fun testDirectory(): File {
            File.workingDirectory().apply {
                assertTrue(exists)
                return resolve("TestFiles")
            }
        }
    }
}