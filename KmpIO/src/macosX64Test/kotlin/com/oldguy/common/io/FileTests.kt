package com.oldguy.common.io

import platform.Foundation.NSTemporaryDirectory
import kotlin.test.Test

class FileTestSuite {

    @Test
    fun textUtf8Basics() {
        println("Start textUtf8Basics")
        val path = NSTemporaryDirectory()
        println("fileBasics1: $path")
        /*
        val tests = FileTests(path)
        tests.filesBasics()
        println("textUtf8Basics1: $path")
        tests.textFileBasics(Charset(Charsets.Utf8))

         */
    }

    @Test
    fun textUtf16leBasics() {
        val path = NSTemporaryDirectory()
        val tests = FileTests(path)
        tests.filesBasics()
        tests.textFileBasics(Charset(Charsets.Utf16le))
    }

}