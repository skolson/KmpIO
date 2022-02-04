package com.oldguy.common.io

import com.oldguy.common.getIntAt
import com.oldguy.common.getLongAt
import com.oldguy.common.getShortAt
import com.oldguy.common.getUIntAt
import com.oldguy.common.getULongAt
import com.oldguy.common.getUShortAt
import com.oldguy.common.toIntShl
import com.oldguy.common.toPosInt
import com.oldguy.common.toPosLong
import com.oldguy.common.toUIntShl
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

object BufferTester {
    const val cap = 1024
    const val byteValue = 0xf4.toByte()
    val ubyteValue = 0xf4.toUByte()
    const val shortValue = (Short.MAX_VALUE - 1).toShort()
    const val shortNegValue = Short.MIN_VALUE
    const val intValue = Int.Companion.MAX_VALUE - 1
    const val intNegValue = intValue * -1
    const val longValue = Long.Companion.MAX_VALUE - 1L
    const val longNegValue = longValue * -1L
    const val floatValue = Float.Companion.MAX_VALUE - 1.0F
    const val floatNegValue = floatValue * -1
    const val doubleValue = Double.Companion.MAX_VALUE - 1.0
    const val doubleNegValue = doubleValue * -1
    val bytes = byteArrayOf(0x01, 0x10, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x90.toByte())
    val ubytes = ubyteArrayOf(0x01u, 0x10u, 0x22u, 0x33u, 0x44u, 0x55u, 0x66u, 0x77u, 0x90u)
    const val charValue = 'Z'

    fun byteArrayExtensionTests() {
        val bytes = byteArrayOf(0x00, 0xff.toByte(), 0x7f, 0x80.toByte(), 0xA0.toByte(), 0, 0, 0)
        assertEquals(0, bytes toPosInt 0)
        assertEquals(255, bytes toPosInt 1)
        assertEquals(127, bytes toPosInt 2)
        assertEquals(128, bytes toPosInt 3)

        // verify left to right precedence with shift ops
        assertEquals(32768, bytes toPosInt 3 shl 8)
        assertEquals(32768, bytes toPosInt 2 + 1 - 1 + 1 shl 8)
        // weird to use shl to calculate index, but test that usage
        assertEquals(160, bytes toPosInt (1 shl 2))

        assertEquals(0L, bytes toPosLong 0)
        assertEquals(255L, bytes toPosLong 1)
        assertEquals(127L, bytes toPosLong 2)
        assertEquals(128L, bytes toPosLong 3)

        assertEquals(32768L, bytes toPosLong 3 shl 8)

        assertEquals(0xFF00.toShort(), bytes.getShortAt(0))
        assertEquals(0x00FF.toShort(), bytes.getShortAt(0, false))

        assertEquals(0xFF00.toUShort(), bytes.getUShortAt(0))
        assertEquals(0xFF.toUShort(), bytes.getUShortAt(0, false))

        assertEquals(0x807FFF00.toInt(), bytes.getIntAt(0))
        assertEquals(0xFF7F80, bytes.getIntAt(0, false))

        assertEquals(0x807FFF00.toUInt(), bytes.getUIntAt(0))
        assertEquals(0xFF7F80.toUInt(), bytes.getUIntAt(0, false))

        assertEquals(0xA0, bytes.getIntAt(4))
        assertEquals(0xA0000000.toInt(), bytes.getIntAt(4, false))

        assertEquals(689350639360L, bytes.getLongAt(0))
        assertEquals(71916309478113280L, bytes.getLongAt(0, false))

        assertEquals(689350639360L.toULong(), bytes.getULongAt(0))
        assertEquals(71916309478113280L.toULong(), bytes.getULongAt(0, false))

        val intBytes = byteArrayOf(1, 4)
        assertEquals(256, intBytes.toIntShl(0, 8))
        assertEquals(1024, intBytes.toIntShl(1, 8))
    }

    fun ubyteArrayExtensionTests() {
        val bytes = ubyteArrayOf(0x00u, 0xffu, 0x7fu, 0x80u, 0xA0u, 0u, 0u, 0u)
        assertEquals(0, bytes toPosInt 0)
        assertEquals(255, bytes toPosInt 1)
        assertEquals(127, bytes toPosInt 2)
        assertEquals(128, bytes toPosInt 3)

        // verify left to right precedence with shift ops
        assertEquals(32768, bytes toPosInt 3 shl 8)
        // weird to use shl to calculate index, but test that usage
        assertEquals(160, bytes toPosInt (1 shl 2))

        assertEquals(0L, bytes toPosLong 0)
        assertEquals(255L, bytes toPosLong 1)
        assertEquals(127L, bytes toPosLong 2)
        assertEquals(128L, bytes toPosLong 3)

        assertEquals(32768L,bytes toPosLong 3 shl 8)

        assertEquals(0xFF00.toShort(), bytes.getShortAt(0))
        assertEquals(0x00FF.toShort(), bytes.getShortAt(0, false))

        assertEquals(0xFF00.toUShort(), bytes.getUShortAt(0))
        assertEquals(0xFF.toUShort(), bytes.getUShortAt(0, false))

        assertEquals(0x807FFF00.toInt(), bytes.getIntAt(0))
        assertEquals(0xFF7F80, bytes.getIntAt(0, false))

        assertEquals(0x807FFF00.toUInt(), bytes.getUIntAt(0))
        assertEquals(0xFF7F80.toUInt(), bytes.getUIntAt(0, false))

        assertEquals(0xA0, bytes.getIntAt(4))
        assertEquals(0xA0000000.toInt(), bytes.getIntAt(4, false))

        assertEquals(689350639360L, bytes.getLongAt(0))
        assertEquals(71916309478113280L, bytes.getLongAt(0, false))

        assertEquals(689350639360L.toULong(), bytes.getULongAt(0))
        assertEquals(71916309478113280L.toULong(), bytes.getULongAt(0, false))

        val intBytes = ubyteArrayOf(1u, 4u)
        assertEquals(256u, intBytes.toUIntShl(0, 8))
        assertEquals(1024u, intBytes.toUIntShl(1, 8))
    }
}

open class ByteBufferTestHelp(val buf: ByteBuffer) {

    fun checkPosition(checkValue: Int) {
        assertEquals(checkValue, buf.position)
        assertEquals(BufferTester.cap - checkValue, buf.remaining)
    }

    fun verifyContents() {
        buf.position = 0
        assertEquals(BufferTester.byteValue, buf.byte)
        assertEquals(BufferTester.shortValue, buf.short)
        assertEquals(BufferTester.intValue, buf.int)
        assertEquals(BufferTester.longValue, buf.long)
        assertEquals(BufferTester.shortNegValue, buf.short)
        assertEquals(BufferTester.intNegValue, buf.int)
        assertEquals(BufferTester.longNegValue, buf.long)

        assertEquals(BufferTester.floatValue, buf.float)
        assertEquals(BufferTester.doubleValue, buf.double)
        assertEquals(BufferTester.floatNegValue, buf.float)
        assertEquals(BufferTester.doubleNegValue, buf.double)

        val tmp = buf.position
        BufferTester.bytes.apply {
            val bytesIn = ByteArray(size)
            buf.get(bytesIn)
            assertContentEquals(this, bytesIn)
            checkPosition(tmp + size)
            assertEquals(BufferTester.charValue, buf.char)
        }
    }
}

open class UByteBufferTestHelp(val buf: UByteBuffer) {

    fun checkPosition(checkValue: Int) {
        assertEquals(checkValue, buf.position)
        assertEquals(BufferTester.cap - checkValue, buf.remaining)
    }

    fun verifyContents() {
        buf.position = 0
        assertEquals(BufferTester.ubyteValue, buf.byte)
        assertEquals(BufferTester.shortValue, buf.short)
        assertEquals(BufferTester.intValue, buf.int)
        assertEquals(BufferTester.longValue, buf.long)
        assertEquals(BufferTester.shortNegValue, buf.short)
        assertEquals(BufferTester.intNegValue, buf.int)
        assertEquals(BufferTester.longNegValue, buf.long)

        assertEquals(BufferTester.floatValue, buf.float)
        assertEquals(BufferTester.doubleValue, buf.double)
        assertEquals(BufferTester.floatNegValue, buf.float)
        assertEquals(BufferTester.doubleNegValue, buf.double)

        val tmp = buf.position
        BufferTester.ubytes.apply {
            val bytesIn = UByteArray(size)
            buf.get(bytesIn)
            assertContentEquals(this, bytesIn)
            checkPosition(tmp + size)
            assertEquals(BufferTester.charValue, buf.char)
        }
    }
}
