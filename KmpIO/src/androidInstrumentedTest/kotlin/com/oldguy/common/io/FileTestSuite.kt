package test

import androidx.test.core.app.ApplicationProvider
import com.oldguy.common.io.*
import com.oldguy.common.io.FileTests
import com.oldguy.common.io.charsets.Utf16LE
import com.oldguy.common.io.charsets.Utf8
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals


@ExperimentalCoroutinesApi
class FileTestSuite {

    val tests: FileTests

    init {
        File.appContext = ApplicationProvider.getApplicationContext()
        tests = FileTests(File.tempDirectoryPath())
    }

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
            val work = File.workingDirectory()
            val testDir = DirectoryTests(
                File(work.fullPath.removeSuffix("/KmpIO"))
                    .resolve( "TestFiles")
                    .fullPath
            )
            testDir.testTree()
        }
    }
}