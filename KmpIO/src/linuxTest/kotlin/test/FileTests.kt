package test

import com.oldguy.common.io.*
import com.oldguy.common.io.charsets.Charsets
import com.oldguy.common.io.charsets.Utf16LE
import com.oldguy.common.io.charsets.Utf8
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/*
./gradlew :KmpIO:linuxX64Test --tests "test.FileTestSuite.textUtf8Basics"
 */
@ExperimentalCoroutinesApi
class FileTestSuite {

    val tests = FileTests(File.tempDirectoryPath())

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

    @Test
    fun smallDirectoryTreeWalk() {
        runTest {
            val testDir = DirectoryTests(FileTests.testDirectory().fullPath)
            testDir.testTree()
        }
    }
}