package com.oldguy.common.io

import com.oldguy.common.io.charsets.Charsets
import kotlinx.coroutines.ExperimentalCoroutinesApi
import platform.Foundation.NSTemporaryDirectory
import kotlin.test.Test

@ExperimentalCoroutinesApi
class FileTestSuite {
    val path = NSTemporaryDirectory()
    val tests = FileTests(path)

    @Test
    fun textUtf8Basics() {
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