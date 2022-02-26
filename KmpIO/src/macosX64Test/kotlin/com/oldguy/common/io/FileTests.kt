package com.oldguy.common.io

import kotlinx.coroutines.ExperimentalCoroutinesApi
import platform.Foundation.NSTemporaryDirectory
import kotlin.test.Test

@ExperimentalCoroutinesApi
class FileTestSuite {
    val path = NSTemporaryDirectory()
    val tests = FileTests(path)

    init {
        println(path)
    }

    @Test
    fun textUtf8Basics() {
        tests.filesBasics()
    }

    @Test
    fun textMediumSizeUtf8Basics() {
        tests.biggerTextFileWriteRead(Charset(Charsets.Utf8), 100)
    }

    @Test
    fun textUtf16leBasics() {
        tests.textFileWriteRead(Charset(Charsets.Utf16le))
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