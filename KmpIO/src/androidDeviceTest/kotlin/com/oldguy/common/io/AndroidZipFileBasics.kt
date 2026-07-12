package com.oldguy.common.io

import com.oldguy.common.io.AndroidFileUnitTests.Companion.excludeAssets
import com.oldguy.common.io.FileTests.Companion.contentsShallow
import com.oldguy.common.io.FileTests.Companion.macosIgnore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.ExperimentalTime
import org.junit.jupiter.api.Assertions.assertEquals
import kotlin.time.Duration.Companion.minutes

@OptIn(
    ExperimentalTime::class,
    ExperimentalCoroutinesApi::class
)
class AndroidZipFileBasics {
    val base = AndroidTestBase()

    val tests = ZipFileTests()

    val smallZip = "SmallTextAndBinary.zip"
    val smallImage = "ic_help_grey600_48dp.png"
    val unicodeName = "あ.png"

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
            base.copyAsset(smallZip, File(base.workingDir, smallZip))
            base.copyAsset(smallImage, File(base.workingDir, smallImage))
            tests.zipFileRead(base.workingDir)
        }
    }

    @Test
    fun saveTwoFilesTest() {
        runTest {
            base.copyAsset(unicodeName, File(base.workingDir, unicodeName))
            tests.saveTwoFiles(base.workingDir, base.tempDir)
        }
    }

    @Test
    fun unzipToDirectoryTest() {
        runTest {
            base.copyAsset(smallZip, File(base.workingDir, smallZip))
            tests.unzipToDirectoryTest(
                File(base.workingDir, smallZip),
                base.tempDir
            )
        }
    }

    @Test
    fun timesTest() {
        tests.testTime()
    }

    @Test
    fun zipDirectoryTest() {
        runTest(timeout = 5.minutes) {
            base.copyAssetDirectory("", base.workingDir)
            val l = base.workingDir.directoryList()
            assertEquals(contentsShallow.size, l.size)
            tests.zipDirectoryTest(base.workingDir, base.tempDir, false) { name ->
                name != macosIgnore &&
                !excludeAssets.any { it == name}
            }
            tests.zipDirectoryTest(base.workingDir, base.tempDir, true) { name ->
                name != macosIgnore &&
                        !excludeAssets.any { it == name}
            }
        }
    }
}