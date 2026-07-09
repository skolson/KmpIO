package com.oldguy.common.io

import com.oldguy.common.io.AndroidFileTests.Companion.contents
import com.oldguy.common.io.AndroidFileTests.Companion.initializeAndroid
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@ExperimentalCoroutinesApi
class AndroidDirectoryTests(testDirPath: String) {
    private val dir = Directory(testDirPath)

    init {
        initializeAndroid()
    }

    @Test
    fun testTree() {
        runTest {
            dir.directoryTree().also { list ->
                assertEquals(contents.size, list.size)
                list.forEach {
                    assertTrue(contents.contains(it.name))
                }
            }
        }
    }
}