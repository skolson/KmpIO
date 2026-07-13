package com.oldguy.common.io

import com.oldguy.common.io.FileTests.Companion.contents
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
class DirectoryTests(testDirPath: String) {
    private val dir = Directory(testDirPath)

    suspend fun testTree() {
        dir.directoryTree().also { list ->
            assertEquals(contents.size, list.size)
            list.forEach {
                assertTrue(contents.contains(it.name))
            }
        }
    }
}