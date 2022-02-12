package com.oldguy.common.io

import org.junit.Assert.assertEquals
import org.junit.Test

class CrcTest {

    @Test
    fun crcTest() {
        val java = java.util.zip.CRC32()
        val t = "asdfghjkl1234567890-0".encodeToByteArray()
        val kotlin = Crc32()
        java.update(t)
        kotlin.update(t)
        assertEquals(java.value.toInt(), kotlin.result)
    }

}