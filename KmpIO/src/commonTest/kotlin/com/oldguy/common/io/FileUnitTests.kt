package com.oldguy.common.io

import com.oldguy.common.io.FileTests.Companion.macosIgnore
import com.oldguy.common.io.charsets.Charsets
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

@ExperimentalCoroutinesApi
class FileUnitTests {

    @Test
    fun textUtf8Basics() {
        runTest {
            FileTests().apply {
                val dir = File.tempDirectoryFile()
                filesBasics(dir)
                textFileWriteRead(dir, Charsets.Utf8.charset)
            }
        }
    }

    @Test
    fun textMediumSizeUtf8Basics() {
        runTest {
            FileTests().apply {
                biggerTextFileWriteRead(
                    File.tempDirectoryFile(),
                    Charsets.Utf8.charset,
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
                    File.tempDirectoryFile(),
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
                    File.tempDirectoryFile(),
                    "Small", 1
                )
            }
        }
    }

    @Test
    fun directoryList() {
        runTest {
            FileTests().apply {
                directoryList(FileTests.testDirectory()) {
                    it != macosIgnore
                }
            }
        }
    }
}