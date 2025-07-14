package com.oldguy.common.io

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.ExperimentalTime

@OptIn(
    ExperimentalStdlibApi::class,
    ExperimentalTime::class,
    ExperimentalCoroutinesApi::class
)
class ZipFileBasics {
    val tests = ZipFileTests()

    @Test
    fun zipFileEmpty() {
        tests.zipFileEmpty()
    }

    @Test
    fun compressionTest() {
        tests.smallCompressionTest()
    }

    @Test
    fun readSmallTextAndBinaryTest() {
        tests.zipFileRead()
    }

    @Test
    fun unzipToDirectoryTest() {
        tests.unzipToDirectoryTest()
    }

    @Test
    fun timesTest() {
        tests.testTime()
    }

    @Test
    fun zip64LargeFileReadTest() {
        runTest {
            tests.zip64LargeFileRead(FileTests.testDirectory())
        }
    }

    @Test
    fun zipDirectoryTest() {
        tests.zipDirectoryTest(false)
        tests.zipDirectoryTest(true)
    }

    @Test
    fun ntfsTagTest() {
        tests.ntfsTagTest()
    }
}