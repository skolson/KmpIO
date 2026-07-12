package com.oldguy.common.io

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.ExperimentalTime

@OptIn(
    ExperimentalTime::class,
    ExperimentalCoroutinesApi::class
)
class AndroidZipFileBasics {
    val base = AndroidTestBase()

    val tests = ZipFileTests()

    @Test
    fun zipFileEmpty() {
        runTest {
            tests.zipFileEmpty(base.workingDir)
        }
    }

    @Test
    fun compressionTest() {
        tests.smallCompressionTest()
    }

    @Test
    fun readSmallTextAndBinaryTest() {
        runTest {
            tests.zipFileRead(base.workingDir)
        }
    }

    @Test
    fun saveTwoFilesTest() {
        runTest {
            tests.saveTwoFiles(base.tempDir)
        }
    }

    @Test
    fun unzipToDirectoryTest() {
        runTest {
            tests.unzipToDirectoryTest(
                File(base.workingDir, "SmallTextAndBinary.zip"),
                base.tempDir
            )
        }
    }

    @Test
    fun timesTest() {
        tests.testTime()
    }

    @Test
    fun zip64LargeFileReadTest() {
        runTest {
            tests.zip64LargeFileRead(base.workingDir)
        }
    }

    @Test
    fun zipDirectoryTest() {
        runTest {
            tests.zipDirectoryTest(base.tempDir, false)
            tests.zipDirectoryTest(base.tempDir, true)
        }
    }
}