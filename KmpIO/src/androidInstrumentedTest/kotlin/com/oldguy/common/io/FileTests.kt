package com.oldguy.common.io

import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.oldguy.common.io.charsets.Charset
import com.oldguy.common.io.charsets.Charsets
import com.oldguy.common.io.charsets.Utf16LE
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.*
import kotlinx.datetime.number
import org.junit.Rule
import kotlin.test.*
import kotlin.test.Test
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
            assertTrue(subDir.exists)
            assertTrue(subDir.isDirectory)

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
                val tmpList2 = subDir.directoryList()
                assertTrue(tmpList2.isNotEmpty(), "tmpList2 Not empty")
                assertEquals(1, tmpList2.size)
                assertEquals(testFilePath, tmpList2.first())
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
        runTest {
            val subDir = testDirectory.resolve(subDirName)
            val fil = File(subDir, "Text${charset.name}.txt")
            fil.delete()
            assertEquals(false, fil.newFile().exists)
            TextFile(
                fil.newFile(),
                charset,
                FileMode.Write,
                FileSource.File
            ).use {
                it.write(textContent)
            }

            assertEquals(true, fil.newFile().exists)
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
            File.workingDirectory().apply {
                assertTrue(exists)
                return resolve("TestFiles")
            }
        }
    }
}

@ExperimentalCoroutinesApi
class FileUnitTests {

    private val path: String
    private val tests: FileTests

    init {
        //File.appContext = ApplicationProvider.getApplicationContext()
        File.appContext = InstrumentationRegistry.getInstrumentation().targetContext
        @Rule
        GrantPermissionRule.grant(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        path = File.tempDirectoryPath()
        tests = FileTests(path)
    }
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

    /**
     * appContext paths
     * getExternalDir() path:
     *      /storage/emulated/0/Android/data/com.oldguy.iocommon.test/files/TestFiles
     * filesDir path:
     *      /data/data/com.oldguy.iocommon.test/files
     *
     * NOTE: The unit test wipes the all the subdirectories for the app every test. The only
     * current wy to have this match real world is to breakpoint after is has retrieved the
     * test directory, and then upload the stuff to be read by listFiles
     */
    @Test
    fun directoryList() {
        runTest {
            FileTests.testDirectory().apply {
                directoryList().apply {
                    println(this)
                    assertEquals(7, size)
                    assertTrue { contains("ZerosZip64.zip") }
                    assertTrue { contains("Zip64_90,000_files.zip") }
                    assertTrue { contains("SmallTextAndBinary.zip") }
                    assertTrue { contains("ic_help_grey600_48dp.png") }
                    assertTrue { contains("ic_help_grey600_48dp.7zip.zip") }
                    assertTrue { contains("dir1") }
                    assertTrue { contains("dir2") }
                }
                directoryFiles().apply {
                    assertEquals(7, size)
                    forEach { assertTrue { it.exists } }
                }
            }
        }
    }
}