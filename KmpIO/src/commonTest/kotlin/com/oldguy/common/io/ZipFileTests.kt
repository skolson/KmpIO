package com.oldguy.common.io

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

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

            zip.close()
        }
    }
}