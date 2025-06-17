package com.oldguy.common.test

import com.oldguy.common.io.ZipFileTests
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.test.Test
import kotlin.time.ExperimentalTime

@OptIn(
    ExperimentalStdlibApi::class,
    ExperimentalTime::class,
    ExperimentalCoroutinesApi::class
    )
class ZipFileBasics {
    val tests = ZipFileTests()

    @Test
    fun zipFileEmpty() {
        tests.zipFileEmpty()
    }

    @Test
    fun compressionTest() {
        println("Test start")
        tests.smallCompressionTest()
    }

}