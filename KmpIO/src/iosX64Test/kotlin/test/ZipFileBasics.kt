package com.oldguy.common.test

import com.oldguy.common.io.File
import com.oldguy.common.io.FileMode
import com.oldguy.common.io.ZipFile
import kotlinx.coroutines.test.runTest
import kotlin.experimental.ExperimentalNativeApi
import kotlin.test.Test
import platform.Foundation.*
import kotlin.test.assertEquals
import kotlin.test.fail

class ZipFileBasics {

    @OptIn(ExperimentalNativeApi::class)
    @Test
    fun zipFileEmpty() {
        runTest {
            NSFileManager.defaultManager.temporaryDirectory.path?.let {
                val zipPath = "$it/test.zip"
                val zipFile = ZipFile(File(zipPath), FileMode.Write)
                assertEquals(0, zipFile.entries.size)
                zipFile.close()
                println("zip: $zipPath")
            } ?: run {
                fail("no temp directory found")
            }
        }
    }
}