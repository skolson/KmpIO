package com.oldguy.common.io

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
class ZipFileTests {
    private val readme = "readme.txt"
    private val readmeExcerpt: String = "MaterialDesignIcons.com"
    private val testImageFileName = "ic_help_grey600_48dp.png"
    private val binaryFile = "drawable-xxxhdpi/$testImageFileName"
    private val testDirectory = File("..\\TestFiles")
    private val workDirectory = testDirectory.resolve("Work")
    private val testFile = File(testDirectory, "SmallTextAndBinary.zip")
    private val testFileTime = LocalDateTime(2017, 6, 23, 18, 46, 2)
    private val parser = ZipFile.defaultExtraParser

    @Test
    fun zipFileRead() {
        val imgFile = File(testDirectory, "ic_help_grey600_48dp.png")
        val zip = ZipFile(testFile)
        runTest {
            try {
                zip.open()
                assertEquals(81519UL, testFile.size)
                val entries = zip.entries
                assertEquals(62, entries.size)
                assertTrue(zip.map.containsKey(readme))
                assertEquals(61, entries.count { it.name.startsWith("drawable") })
                assertEquals(1, entries.count { it.name.startsWith("drawable/") })
                assertEquals(1, entries.count { it.name == "drawable/help.xml" })
                assertEquals(12, entries.count { it.name.startsWith("drawable-hdpi") })
                assertEquals(12, entries.count { it.name.startsWith("drawable-mdpi") })
                assertEquals(12, entries.count { it.name.startsWith("drawable-xhdpi") })
                assertEquals(12, entries.count { it.name.startsWith("drawable-xxhdpi") })
                assertEquals(12, entries.count { it.name.startsWith("drawable-xxxhdpi") })

                zip.readEntry(readme) { entry, content, count ->
                    assertEquals(readme, entry.name)
                    assertEquals("", entry.comment)
                    val s = ZipRecord.zipCharset.decode(content)
                    assertTrue(s.contains(readmeExcerpt))
                    assertEquals(148u, count)
                    assertEquals(148, s.length)
                    assertEquals(testFileTime, entry.timeModified)
                }
                val imgBuf = ByteBuffer(4096)
                val fileSize = RawFile(imgFile).read(imgBuf).toInt()
                imgBuf.positionLimit(0, fileSize)
                assertEquals(3650, fileSize)
                zip.readEntry(binaryFile) { entry, content, count ->
                    assertEquals(binaryFile, entry.name)
                    assertEquals(3650, count.toInt())
                    assertEquals(3650, content.size)
                    assertTrue(imgBuf.getBytes(imgBuf.remaining).contentEquals(content))
                    assertEquals(testFileTime, entry.timeModified)
                }
            } finally {
                zip.close()
            }
        }
    }

    /**
     * Reads a test entry that is 5+MB compressed and 5+GB uncompressed.
     */
    @Test
    fun zip64LargeFileRead() {
        val file = File(testDirectory, "ZerosZip64.zip")
        ZipFile(file).apply {
            runTest {
                try {
                    open()
                    val testEntry = map["0000"]
                        ?: throw IllegalStateException("0000 file not found")
                    val uSize = 5242880UL * 1024UL
                    testEntry.entryDirectory.apply {
                        assertEquals(5611526UL, compressedSize)
                        assertEquals(uSize, uncompressedSize)
                        assertEquals(0UL, localHeaderOffset)
                    }
                    var uncompressedCount = 0UL
                    var gb = 1UL
                    readEntry(testEntry.name) { entry, content, count ->
                        assertEquals(count, content.size.toUInt())
                        assertEquals("0000", entry.name)
                        uncompressedCount += count
                        if (uncompressedCount > gb * 1000000000UL) {
                            println("Decompressed ${gb++}GB")
                        }
                    }
                    assertEquals(uSize, uncompressedCount)
                    // CRC is already verified
                } finally {
                    close()
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
        val dir = workDirectory.resolve("SmallTextAndBinaryTest")
        runTest {
            ZipFile(testFile).apply {
                extractToDirectory(dir)
            }
        }
        val list = dir.listFiles
        assertEquals(7, list.size)
        assertEquals(6, list.count {it.isDirectory})
        assertEquals(1, list.count {!it.isDirectory})
        assertEquals(readme, list.first {!it.isDirectory}.name)
        val tree = dir.listFilesTree
        assertEquals(69, tree.size)
        dir.delete()
    }

    @Test
    fun saveEmpty() {
        val dir = workDirectory.resolve("EmptyTest")
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

    @Test
    fun compressionTest() {
        val test = "123456123456sdfghjklzxcvbxcvxcvbzxcvb"
        val bytes = Charset(Charsets.Utf8).encode(test)
        val buf = ByteBuffer(bytes)
        val out = ByteBuffer(test.length)
        val out2 = ByteBuffer(test.length)
        runTest {
            CompressionDeflate(true).apply {
                val compressed = this.compress(
                    input = { buf }
                ) {
                    out.expand(it)
                }
                out.flip()
                val uncompressed = decompress(
                    compressed,
                    2048u,
                    input = {
                        ByteBuffer(out.getBytes(it))
                    }
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

        val t2 = LocalDateTime(2017, 6, 23, 18, 46, 2)
        val z1 = ZipTime(t2)
        assertEquals(t2, z1.zipTime)

        val tFile = File(testDirectory, testImageFileName)
        ZipEntry(parser, "Any", lastModTime = tFile.lastModified, ).apply {
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
    fun saveOne() {
        val dir = workDirectory.resolve("SaveOne")
        val oneFileZip = File(dir, "saveOne.zip")
        oneFileZip.delete()
        val entryFile = File(testDirectory, testImageFileName)
        runTest {
            ZipFile(oneFileZip, FileMode.Write).use {
                RawFile(entryFile).apply {
                    val entry = ZipEntry(parser, testImageFileName, lastModTime = entryFile.lastModified)
                    val buf = ByteBuffer(4096)
                    it.addEntry(entry) {
                        val count = read(buf)
                        buf.positionLimit(0, count.toInt())
                        buf.getBytes()
                    }
                }
            }
            ZipFile(oneFileZip).use { e ->
                assertEquals(1, e.map.size)
                assertTrue(e.map.containsKey(testImageFileName))
                val t = e.map[testImageFileName] ?: throw ZipException("Lookup fail $testImageFileName")
                assertEquals(testImageFileName, t.name)
                assertEquals(3650UL, t.entryDirectory.uncompressedSize)
                val copy = File(workDirectory, "Copy$testImageFileName")
                copy.delete()
                RawFile(copy, FileMode.Write).use {
                    (e.readEntry(testImageFileName) { _, bytes, count ->
                        assertEquals(3650, bytes.size)
                        assertEquals(3650u, count)
                        it.write(ByteBuffer(bytes))
                    }).apply {
                        assertEquals(testImageFileName, name)
                        assertEquals(3655UL, entryDirectory.compressedSize)
                        assertEquals(3650UL, entryDirectory.uncompressedSize)
                    }
                }
            }
        }
    }
}