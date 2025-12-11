package com.oldguy.common.io

import androidx.test.core.app.ApplicationProvider
import com.oldguy.common.io.charsets.Utf16LE
import com.oldguy.common.io.charsets.Utf8
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test


@ExperimentalCoroutinesApi
class AndroidFileTestSuite {

    val tests: AndroidFileTests

    init {
        File.appContext = ApplicationProvider.getApplicationContext()
        tests = AndroidFileTests(File.tempDirectoryPath())
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
            val testDir = AndroidDirectoryTests(
                File(work.fullPath.removeSuffix("/KmpIO"))
                    .resolve( "TestFiles")
                    .fullPath
            )
            testDir.testTree()
        }
    }
}