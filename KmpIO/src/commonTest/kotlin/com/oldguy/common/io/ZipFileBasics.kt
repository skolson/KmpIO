package com.oldguy.common.io

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.ExperimentalTime

@OptIn(
    ExperimentalTime::class,
    ExperimentalCoroutinesApi::class
)
class ZipFileBasics {
    val tests = ZipFileTests()

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
    fun saveTwoFilesTest() {
        runTest {
            tests.saveTwoFiles(FileTests.testDirectory(), tempDir())
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
        runTest {
            tests.zip64LargeFileRead(FileTests.testDirectory())
        }
    }

    @Test
    fun zipDirectoryTest() {
        runTest {
            tests.zipDirectoryTest(FileTests.testDirectory(), tempDir(),false) {
                !(it.contains("ZerosZip64") || it.contains("Zip64_90,000_files")) &&
                !it.contains(FileTests.macosIgnore)
            }
            tests.zipDirectoryTest(FileTests.testDirectory(), tempDir(),true) {
                !(it.contains("ZerosZip64") || it.contains("Zip64_90,000_files")) &&
                !it.contains(FileTests.macosIgnore)
            }
        }
    }
}