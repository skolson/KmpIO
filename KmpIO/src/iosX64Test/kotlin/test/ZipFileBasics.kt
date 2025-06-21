package com.oldguy.common.test

import com.oldguy.common.io.Directory
import com.oldguy.common.io.File
import com.oldguy.common.io.ZipFileTests
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
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
        runTest(timeout = 4.minutes) {
            tests.zip64LargeFileRead(
                File.workingDirectory()
                    .resolve("KmpIOLargeZip"))
        }
    }

    @Test
    fun zipDirectoryTest() {
        tests.zipDirectoryTest(false)
        tests.zipDirectoryTest(true)
    }
}