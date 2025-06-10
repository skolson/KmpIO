package test

import com.oldguy.common.io.*
import com.oldguy.common.io.charsets.Charsets
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.test.Test

/*
./gradlew :KmpIO:linuxX64Test --tests "test.FileTestSuite.textUtf8Basics"
 */
@ExperimentalCoroutinesApi
class FileTestSuite {
    val path = File(".").tempDirectory
    val tests = FileTests(path)

    init {
        println("Path: $path")
    }

    @Test
    fun textUtf8Basics() {
        //println("testUtf8Basics entry")
        tests.filesBasics()
    }

    @Test
    fun textMediumSizeUtf8Basics() {
        tests.biggerTextFileWriteRead(com.oldguy.common.io.charsets.Charset(Charsets.Utf8), 100)
    }

    @Test
    fun textUtf16leBasics() {
        tests.textFileWriteRead(com.oldguy.common.io.charsets.Charset(Charsets.Utf16LE))
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