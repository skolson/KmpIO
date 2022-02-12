package com.oldguy.common.io

import org.junit.Assert.assertEquals
import org.junit.Test

class CrcTestPlain {

    @Test
    fun crcTest() {
        val t = "s".encodeToByteArray()
        val java = java.util.zip.CRC32()
        java.update(t)
        val kotlin = Crc32()
        kotlin.update(t)
        assertEquals(java.value.toInt(), kotlin.result)

        val t1 = "123456789".encodeToByteArray()
        java.reset()
        java.update(t1)
        kotlin.reset()
        kotlin.update(t1.toUByteArray())
        assertEquals(java.value.toInt(), kotlin.result)
        kotlin.reset()
        for (b in t1) kotlin.update(byteArrayOf(b))
        assertEquals(java.value.toInt(), kotlin.result)
    }
}