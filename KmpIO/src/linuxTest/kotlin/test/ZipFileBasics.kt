package com.oldguy.common.test

import com.oldguy.common.io.File
import com.oldguy.common.io.FileMode
import com.oldguy.common.io.ZipFile
import com.oldguy.common.io.tempDirectory
import kotlinx.coroutines.test.runTest
import kotlin.native.runtime.GC
import kotlin.test.Test
import kotlin.native.runtime.NativeRuntimeApi
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ZipFileBasics {

    @OptIn(NativeRuntimeApi::class, ExperimentalStdlibApi::class)
    @Test
    fun zipFileEmpty() {
        runTest {
            GC.collect()
            val startMemory = GC.lastGCInfo!!.memoryUsageAfter["heap"]?.totalObjectsSizeBytes
            val temp = tempDirectory().let {
                val fil = File(tempDirectory(), "test.zip")
                assertFalse(fil.exists)
                assertFalse(fil.delete())
                val zipFile = ZipFile(fil, FileMode.Write)
                assertEquals(0, zipFile.entries.size)
                zipFile.close()
                assertTrue(fil.exists)
                assertTrue(fil.delete())
                assertFalse(fil.exists)
            }
            try {
                println("Second CG start")
                GC.collect()
                println("Second CG end")
            } catch (e: Throwable) {
                println(e.message)
            }

            /*
                        val endMemory = GC.lastGCInfo?.let {
                            it.memoryUsageAfter["heap"]?.totalObjectsSizeBytes
                        }
                        println("Start memory = $startMemory, end = ${endMemory ?: "null"}")

             */
        }
    }
}