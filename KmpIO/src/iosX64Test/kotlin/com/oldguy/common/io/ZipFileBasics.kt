package com.oldguy.common.io

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime

@OptIn(
    ExperimentalTime::class,
    ExperimentalCoroutinesApi::class
    )
class ZipFileBasics {
    val tests = ZipFileTests()

    private suspend fun inputDir() =
        File.workingDirectory().resolve("TestFiles")
    private suspend fun tempDir() =
        File.tempDirectoryFile().resolve("kmpIOtestDir")

    @Test
    fun zipFileEmpty() {
        runTest {
            tests.zipFileEmpty(tempDir())
        }
    }

    @Test
    fun compressionTest() {
        tests.smallCompressionTest()
    }

    @Test
    fun readSmallTextAndBinaryTest() {
        runTest {
            tests.zipFileRead(FileTests.testDirectory())
        }
    }

    @Test
    fun unzipToDirectoryTest() {
        runTest {
            tests.unzipToDirectoryTest(
                File(FileTests.testDirectory(), "SmallTextAndBinary.zip"),
                tempDir()
            )
        }
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
        runTest {
            tests.zipDirectoryTest(inputDir(),false)
            tests.zipDirectoryTest(inputDir(),true)
        }
    }
}