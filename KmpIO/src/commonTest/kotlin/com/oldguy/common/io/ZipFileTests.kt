package com.oldguy.common.io

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.ExperimentalTime

@ExperimentalTime
@ExperimentalCoroutinesApi
class ZipFileTests {
    private val tz = TimeZone.currentSystemDefault()
    private val readme = "readme.txt"
    private val readmeExcerpt: String = "MaterialDesignIcons.com"
    private val testImageFileName = "ic_help_grey600_48dp.png"
    private val binaryFile = "drawable-xxxhdpi/$testImageFileName"
    private val testFileTime = LocalDateTime(2017, 6, 23, 19, 46, 2)
    private val testFileTime2 = testFileTime.toInstant(tz).plus((-1).hours).toLocalDateTime(tz)

    private suspend fun workDirectory() = FileTests.testDirectory().resolve("Work")
    private suspend fun testFile() = File(FileTests.testDirectory(), "SmallTextAndBinary.zip")

    @Test
    fun zipFileRead() {
        runTest {
            val imgFile = File(FileTests.testDirectory(), "ic_help_grey600_48dp.png")
            assertTrue(imgFile.exists)
            val testFile = testFile()
            ZipFile(testFile).use { zip ->
                assertEquals(81519UL, testFile.size)
                zip.entries.apply {
                    assertEquals(62, size)
                    assertTrue(zip.map.containsKey(readme))
                    assertEquals(61, count { it.name.startsWith("drawable") })
                    assertEquals(1, count { it.name.startsWith("drawable/") })
                    assertEquals(1, count { it.name == "drawable/help.xml" })
                    assertEquals(12, count { it.name.startsWith("drawable-hdpi") })
                    assertEquals(12, count { it.name.startsWith("drawable-mdpi") })
                    assertEquals(12, count { it.name.startsWith("drawable-xhdpi") })
                    assertEquals(12, count { it.name.startsWith("drawable-xxhdpi") })
                    assertEquals(12, count { it.name.startsWith("drawable-xxxhdpi") })
                }
                zip.readEntry(readme) { entry, content, count, last ->
                    assertEquals(readme, entry.name)
                    assertEquals("", entry.comment)
                    val s = ZipRecord.zipCharset.decode(content)
                    assertTrue(s.contains(readmeExcerpt))
                    assertEquals(148u, count)
                    assertEquals(148, s.length)
                    assertEquals(testFileTime2, entry.timeModified)
                    assertEquals(true, last)
                }
                val imgBuf = ByteBuffer(4096)
                val fileSize = RawFile(imgFile).read(imgBuf).toInt()
                imgBuf.positionLimit(0, fileSize)
                assertEquals(3650, fileSize)
                zip.readEntry(binaryFile) { entry, content, count, last ->
                    assertEquals(binaryFile, entry.name)
                    assertEquals(3650, count.toInt())
                    assertEquals(3650, content.size)
                    assertTrue(imgBuf.getBytes(imgBuf.remaining).contentEquals(content))
                    assertEquals(testFileTime2, entry.timeModified)
                    assertEquals(true, last)
                }
            }
        }
    }

    /**
     * Reads a test entry that is 5+MB compressed and 5+GB uncompressed.
     */
    @Test
    fun zip64LargeFileRead() {
        runTest {
            val file = File(FileTests.testDirectory(), "ZerosZip64.zip")
            ZipFile(file).use {
                (it.map["0000"]
                    ?: throw IllegalStateException("0000 file not found")).apply {
                    val uSize = 5242880UL * 1024UL
                    directories.apply {
                        assertEquals(5611526UL, compressedSize)
                        assertEquals(uSize, uncompressedSize)
                        assertEquals(0UL, localHeaderOffset)
                    }
                    var uncompressedCount = 0UL
                    var gb = 1UL
                    it.readEntry(name) { entry, content, count, last ->
                        assertEquals(count, content.size.toUInt())
                        assertEquals("0000", entry.name)
                        uncompressedCount += count
                        if (uncompressedCount > gb * 1000000000UL) {
                            println("Decompressed ${gb++}GB")
                        }
                        if (uncompressedCount == entry.directories.uncompressedSize)
                            assertEquals(true, last)
                        else
                            assertEquals(false, last)
                    }
                    assertEquals(uSize, uncompressedCount)
                }
            }
        }
    }

    /**
     * Extracts a zip file with a test file and some subdirectories to a test directory and confirm
     * results.
     */
    @Test
    fun unzipToDirectoryTest() {
        runTest {
            val dir = workDirectory().resolve("SmallTextAndBinaryTest")
            ZipFile(testFile()).apply {
                extractToDirectory(dir)
            }
            val list = dir.listFiles
            assertEquals(7, list.size)
            assertEquals(6, list.count { it.isDirectory })
            assertEquals(1, list.count { !it.isDirectory })
            assertEquals(readme, list.first { !it.isDirectory }.name)
            val tree = dir.listFilesTree
            assertEquals(68, tree.size)
            dir.delete()
        }
    }

    @Test
    fun saveEmpty() {
        runTest {
            val dir = workDirectory().resolve("EmptyTest")
            val emptyZip = File(dir, "empty.zip")
            emptyZip.delete()
            runTest {
                ZipFile(emptyZip, FileMode.Write).use {
                }
                ZipFile(emptyZip).use {
                    assertTrue(it.map.isEmpty())
                }
            }
            emptyZip.delete()
            dir.delete()
        }
    }

    @Test
    fun compressionTest() {
        val test = "123456123456sdfghjklzxcvbxcvxcvbzxcvb"
        val bytes = Charset(Charsets.Utf8).encode(test)
        val buf = ByteBuffer(bytes)
        val out = ByteBuffer(test.length)
        val out2 = ByteBuffer(test.length)

        val zipDeflateNowrapResult = byteArrayOf(
            0x1,
            0x25,
            0x0,
            0xDA.toByte(),
            0xFF.toByte(),
            0x31,
            0x32,
            0x33,
            0x34,
            0x35,
            0x36,
            0x31,
            0x32,
            0x33,
            0x34,
            0x35,
            0x36,
            0x73,
            0x64,
            0x66,
            0x67,
            0x68,
            0x6A,
            0x6B,
            0x6C,
            0x7A,
            0x78,
            0x63,
            0x76,
            0x62,
            0x78,
            0x63,
            0x76,
            0x78,
            0x63,
            0x76,
            0x62,
            0x7A,
            0x78,
            0x63,
            0x76,
            0x62
        )

        runTest {
            CompressionDeflate(true).apply {
                val compressed = this.compress( input = { buf }) {
                    out.expand(it)
                }
                out.flip()
                assertContentEquals(zipDeflateNowrapResult, out.getBytes())
                out.flip()
                assertEquals(28u, compressed)
                val uncompressed = decompress(
                    input = { out }
                ) {
                    out2.expand(it)
                }
                out2.flip()
                assertEquals(test.length.toULong(), uncompressed)
                assertEquals(test, Charset(Charsets.Utf8).decode(out2.getBytes()))
            }
        }
    }

    @Test
    fun testTime() {
        assertEquals(testFileTime, ZipTime(testFileTime).zipTime)
        assertEquals(testFileTime, ZipTime(0x9DC1u, 0x4AD7u).zipTime)

        val modTime: UShort = 0x7a9cu
        val modDate: UShort = 0x544bu
        val z = ZipTime(modTime, modDate)
        val d = LocalDateTime(2022, 2, 11, 15, 20, 56)
        ZipTime(d).apply {
            assertEquals(modTime, modTime)
            assertEquals(modDate, modDate)
        }
        assertEquals(d, z.zipTime)

        val z1 = ZipTime(testFileTime)
        assertEquals(testFileTime, z1.zipTime)

        ZipEntry("Any", lastModTime = d).apply {
            assertEquals(d.year, timeModified.year)
            assertEquals(d.month, timeModified.month)
            assertEquals(d.dayOfMonth, timeModified.dayOfMonth)
            assertEquals(d.hour, timeModified.hour)
            assertEquals(d.minute, timeModified.minute)
            assertEquals(d.second, (timeModified.second - (timeModified.second % 2)))
            assertEquals(modTime, zipTime.modTime, "modTime: ${zipTime.modTime.toString(16)}")
            assertEquals(modDate, zipTime.modDate, "modDate: ${zipTime.modDate.toString(16)}")
        }
    }

    @Test
    fun saveTwoFiles() {
        runTest {
            val dir = workDirectory().resolve("SaveOne")
            val oneFileZip = File(dir, "saveOne.zip")
            oneFileZip.delete()
            val entryFile = File(FileTests.testDirectory(), testImageFileName)
            ZipFile(oneFileZip, FileMode.Write).use {
                it.zipFile(entryFile)
                it.zipFile(entryFile, "Copy${entryFile.name}")
            }
            ZipFile(oneFileZip).use { e ->
                assertEquals(2, e.map.size)
                assertTrue(e.map.containsKey(testImageFileName))
                val t = e.map[testImageFileName] ?: throw ZipException("Lookup fail $testImageFileName")
                assertEquals(testImageFileName, t.name)
                assertEquals(3650UL, t.directories.uncompressedSize)
                val copy = File(workDirectory(), "Copy$testImageFileName")
                copy.delete()
                RawFile(copy, FileMode.Write).use {
                    (e.readEntry(testImageFileName) { _, bytes, count, _ ->
                        assertEquals(3650, bytes.size)
                        assertEquals(3650u, count)
                        it.write(ByteBuffer(bytes))
                    }).apply {
                        assertEquals(testImageFileName, name)
                        assertEquals(3655UL, directories.compressedSize)
                        assertEquals(3650UL, directories.uncompressedSize)
                    }
                }
            }
        }
    }

    @Test
    fun twoPlusMergeTest() {
        runTest {
            val dir = workDirectory()
            val mergedZip = File(dir, "merged.zip")
            mergedZip.delete()
            val entryFile = File(FileTests.testDirectory(), testImageFileName)
            ZipFile(mergedZip, FileMode.Write).use {
                it.zipFile(entryFile)
                it.zipFile(entryFile, "Copy${entryFile.name}")
                it.merge(ZipFile(testFile()))
            }
            ZipFile(mergedZip).use { zip ->
                zip.entries.apply {
                    assertEquals(64, size)
                    assertTrue(zip.map.containsKey(readme))
                    assertTrue { zip.map.containsKey(testImageFileName) }
                    assertTrue { zip.map.containsKey("Copy$testImageFileName") }
                    assertEquals(61, count { it.name.startsWith("drawable") })
                    assertEquals(1, count { it.name.startsWith("drawable/") })
                    assertEquals(1, count { it.name == "drawable/help.xml" })
                    assertEquals(12, count { it.name.startsWith("drawable-hdpi") })
                    assertEquals(12, count { it.name.startsWith("drawable-mdpi") })
                    assertEquals(12, count { it.name.startsWith("drawable-xhdpi") })
                    assertEquals(12, count { it.name.startsWith("drawable-xxhdpi") })
                    assertEquals(12, count { it.name.startsWith("drawable-xxxhdpi") })
                }
            }
        }
    }

    @Test
    fun saveDirAndFile() {
        runTest {
            val dir = workDirectory()
            val oneFileZip = File(dir, "saveDirAndOne.zip")
            oneFileZip.delete()
            val entryFile = File(FileTests.testDirectory(), testImageFileName)
            ZipFile(oneFileZip, FileMode.Write).use {
                it.addEntry(ZipEntry("anydirName"))
                it.zipFile(entryFile)
            }
            ZipFile(oneFileZip).use { e ->
                assertEquals(2, e.map.size)
                assertTrue(e.map.containsKey(testImageFileName))
                assertTrue(e.map.containsKey("anydirName"))
                val t = e.map[testImageFileName] ?: throw ZipException("Lookup fail $testImageFileName")
                assertEquals(testImageFileName, t.name)
                assertEquals(3650UL, t.directories.uncompressedSize)
                e.map["anydirName"]?.let {
                    assertEquals(0UL, it.directories.uncompressedSize)
                    assertEquals(0UL, it.directories.compressedSize)
                }
            }
            println("Path: ${oneFileZip.fullPath}")
        }
    }
}