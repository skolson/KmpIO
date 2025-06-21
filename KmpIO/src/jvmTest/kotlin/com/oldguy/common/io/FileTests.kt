package com.oldguy.common.io

import kotlin.test.Test
import kotlin.test.assertEquals

class JvmFileTests {
    @Test
    fun windowsPathTest() {
        val p = Path.newPath("C:\\Temp", "someFile.txt", '\\')
        assertEquals("C:\\Temp\\someFile.txt", p)
        Path(p, '\\').apply {
            assertEquals(true,isAbsolute)
            assertEquals("someFile.txt", name)
            assertEquals("someFile", nameWithoutExtension)
            assertEquals("txt", extension)
            assertEquals("C:\\Temp", directoryPath)
            assertEquals(false, isHidden)
        }
    }
}