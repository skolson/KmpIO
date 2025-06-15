package test

import com.oldguy.common.io.*
import com.oldguy.common.io.charsets.Charsets
import com.oldguy.common.io.charsets.Utf16LE
import com.oldguy.common.io.charsets.Utf8
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.test.Test

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
}