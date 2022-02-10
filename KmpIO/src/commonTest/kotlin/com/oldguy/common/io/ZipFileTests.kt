package com.oldguy.common.io

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
class ZipFileTests {

    @Test
    fun zipFileRead() {
        val file = File("..\\TestFiles\\SmallTextAndBinary.zip", null)
        val zip = ZipFileImpl(file, FileMode.Read)
        runTest {
            zip.open()
            assertEquals(81519UL, file.size)
            val entries = zip.entries
            assertEquals(62, entries.size)
            assertTrue(zip.map.containsKey("readme.txt"))
            assertEquals(61, entries.count { it.name.startsWith("drawable") })
            assertEquals(1, entries.count { it.name.startsWith("drawable/") })
            assertEquals(1, entries.count { it.name == "drawable/help.xml" })
            assertEquals(12, entries.count { it.name.startsWith("drawable-hdpi") })
            assertEquals(12, entries.count { it.name.startsWith("drawable-mdpi") })
            assertEquals(12, entries.count { it.name.startsWith("drawable-xhdpi") })
            assertEquals(12, entries.count { it.name.startsWith("drawable-xxhdpi") })
            assertEquals(12, entries.count { it.name.startsWith("drawable-xxxhdpi") })
            zip.close()
        }
    }
}