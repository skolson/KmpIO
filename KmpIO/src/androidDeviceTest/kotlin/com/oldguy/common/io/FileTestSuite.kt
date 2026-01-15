package com.oldguy.common.io

import com.oldguy.common.io.charsets.Utf16LE
import com.oldguy.common.io.charsets.Utf8
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@ExperimentalCoroutinesApi
class AndroidFileTestSuite {

    val tests = AndroidFileTests()

    @Test
    fun textUtf8Basics() {
        tests.filesBasics()
    }

    @Test
    fun textMediumSizeUtf8Basics() {
        tests.biggerTextFileWriteRead(Utf8(), 100)
    }

    @Test
    fun textUtf16leBasics() {
        tests.textFileWriteRead(Utf16LE())
    }

    @Test
    fun rawSmallTest() {
        try {
            tests.testRawWriteRead("Small", 1)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }


    /**
     * appContext paths
     * getExternalDir() path:
     *      /storage/emulated/0/Android/data/com.oldguy.iocommon.test/files/TestFiles
     * filesDir path:
     *      /data/data/com.oldguy.iocommon.test/files
     *
     * NOTE: The Android emulator unit test wipes all the subdirectories for the app every test.
     * The only current way to have this match real world is to breakpoint after it has retrieved the
     * test directory, and then upload the stuff using device explorer from the repo TestFiles directory
     * to the device directory to be read by directoryList
    @Test
    fun directoryList() {
        runTest {
            AndroidFileTests.testDirectory().apply {
                directoryList().apply {
                    println(this)
                    assertEquals(7, size)
                    assertTrue { contains("ZerosZip64.zip") }
                    assertTrue { contains("Zip64_90,000_files.zip") }
                    assertTrue { contains("SmallTextAndBinary.zip") }
                    assertTrue { contains("ic_help_grey600_48dp.png") }
                    assertTrue { contains("ic_help_grey600_48dp.7zip.zip") }
                    assertTrue { contains("dir1") }
                    assertTrue { contains("dir2") }
                }
                directoryFiles().apply {
                    assertEquals(7, size)
                    forEach { assertTrue { it.exists } }
                }
            }
        }
    }
     */
}