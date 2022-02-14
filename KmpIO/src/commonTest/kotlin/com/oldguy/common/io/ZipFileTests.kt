package com.oldguy.common.io

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
class ZipFileTests {
    private val readme = "readme.txt"
    private val readmeExcerpt: String = "MaterialDesignIcons.com"
    private val binaryFile = "drawable-xxxhdpi/ic_help_grey600_48dp.png"

    @Test
    fun zipFileRead() {
        val file = File("..\\TestFiles\\SmallTextAndBinary.zip", null)
        val imgFile = File("..\\TestFiles\\ic_help_grey600_48dp.png", null)
        val zip = ZipFileImpl(file, FileMode.Read)
        runTest {
            try {
                zip.open()
                assertEquals(81519UL, file.size)
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
                }
            } finally {
                zip.close()
            }
        }
    }

    @Test
    fun zip64FileRead() {
        val file = File("..\\TestFiles\\ZerosZip64.zip", null)
        ZipFileImpl(file, FileMode.Read).apply {
            runTest {
                try {
                    open()
                    val testEntry = map["0000"]
                        ?: throw IllegalStateException("0000 file not found")
                    val uSize = 5242880UL * 1024UL
                    assertEquals(5611526UL, testEntry.record.compressedSize)
                    assertEquals(uSize, testEntry.record.uncompressedSize)
                    assertEquals(0UL, testEntry.record.localHeaderOffset)
                    // read 5 gig entry
                    var uncompressedCount = 0UL
                    readEntry(testEntry.entry.name) { entry, content, count ->
                        assertEquals(count, content.size.toUInt())
                        assertEquals("0000", entry.name)
                        uncompressedCount += count
                    }
                    assertEquals(uSize, uncompressedCount)
                } finally {
                    close()
                }
            }
        }
    }
}