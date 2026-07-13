package com.oldguy.common.io

import com.oldguy.common.io.FileTests.Companion.macosIgnore
import com.oldguy.common.io.charsets.Charsets
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

@ExperimentalCoroutinesApi
class AndroidFileUnitTests {
    private val base: AndroidTestBase = AndroidTestBase()
    private val dir: File = File.tempDirectoryFile()

    @Test
    fun textUtf8Basics() {
        runTest {
            FileTests().apply {
                filesBasics(dir)
                textFileWriteRead(dir, com.oldguy.common.io.charsets.Charsets.Utf8.charset)
            }
        }
    }

    @Test
    fun textMediumSizeUtf8Basics() {
        runTest {
            FileTests().apply {
                biggerTextFileWriteRead(
                    dir,
                    com.oldguy.common.io.charsets.Charsets.Utf8.charset,
                    100
                )
            }
        }
    }

    @Test
    fun textUtf16leBasics() {
        runTest {
            FileTests().apply {
                textFileWriteRead(
                    dir,
                    Charsets.Utf16LE.charset
                )
            }
        }
    }

    @Test
    fun rawSmallTest() {
        runTest {
            FileTests().apply {
                testRawWriteRead(
                    dir,
                    "Small", 1
                )
            }
        }
    }

    @Test
    fun directoryList() {
        runTest {
            base.copyAssetDirectory("", dir)
            FileTests().apply {
                directoryList(dir) { name ->
                    name != macosIgnore &&
                    !excludeAssets.any { it == name}
                }
            }
        }
    }

    companion object {

        val excludeAssets = listOf(
            "geoid_map",
            "images",
            "webkit",
        )
    }
}