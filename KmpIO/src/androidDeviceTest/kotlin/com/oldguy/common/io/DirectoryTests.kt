package com.oldguy.common.io

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
class AndroidDirectoryTests(testDirPath: String) {
    private val dir = Directory(testDirPath)

    suspend fun testTree() {
        dir.directoryTree().also { list ->
            assertEquals(contents.size, list.size)
            list.forEach {
                assertTrue(contents.contains(it.name))
            }
        }
    }

    companion object {
        private val contents = listOf(
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
            "Zip64_90,000_files.zip"
        )
    }
}