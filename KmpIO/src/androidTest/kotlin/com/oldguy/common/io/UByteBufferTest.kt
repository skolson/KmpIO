package com.oldguy.common.io

import kotlin.test.Test
import java.nio.ByteOrder
import kotlin.test.assertContentEquals

class UByteBufferTestHelpJava(buf: UByteBuffer): UByteBufferTestHelp(buf) {
    val javaBuf: java.nio.ByteBuffer = java.nio.ByteBuffer.allocate(buf.capacity)

    constructor() : this(UByteBuffer(BufferTester.cap)) {
        when (buf.order) {
            Buffer.ByteOrder.LittleEndian -> javaBuf.order(ByteOrder.LITTLE_ENDIAN)
            Buffer.ByteOrder.BigEndian -> javaBuf.order(ByteOrder.BIG_ENDIAN)
        }
    }

    fun populate(): UByteBuffer {
        buf.byte = BufferTester.ubyteValue
        javaBuf.put(BufferTester.byteValue)

        buf.short = BufferTester.shortValue
        javaBuf.putShort(BufferTester.shortValue)
        checkPosition(3)
        buf.int = BufferTester.intValue
        javaBuf.putInt(BufferTester.intValue)
        checkPosition(7)
        buf.long = BufferTester.longValue
        javaBuf.putLong(BufferTester.longValue)
        checkPosition(15)

        buf.short = BufferTester.shortNegValue
        javaBuf.putShort(BufferTester.shortNegValue)
        checkPosition(17)
        buf.int = BufferTester.intNegValue
        javaBuf.putInt(BufferTester.intNegValue)
        checkPosition(21)
        buf.long = BufferTester.longNegValue
        javaBuf.putLong(BufferTester.longNegValue)
        checkPosition(29)

        buf.float = BufferTester.floatValue
        javaBuf.putFloat(BufferTester.floatValue)
        checkPosition(33)
        buf.double = BufferTester.doubleValue
        javaBuf.putDouble(BufferTester.doubleValue)
        checkPosition(41)

        buf.float = BufferTester.floatNegValue
        javaBuf.putFloat(BufferTester.floatNegValue)
        checkPosition(45)
        buf.double = BufferTester.doubleNegValue
        javaBuf.putDouble(BufferTester.doubleNegValue)
        checkPosition(53)

        buf.put(BufferTester.ubytes)
        javaBuf.put(BufferTester.bytes)
        checkPosition(53 + BufferTester.ubytes.size)

        buf.char = BufferTester.charValue
        javaBuf.putChar(BufferTester.charValue)
        checkPosition(55 + BufferTester.ubytes.size)
        return buf
    }
}

class UByteBufferTestsJava {
    /**
     * Populate a buffer with all of the basic types, then reset position and verify that all the same values are retrieved.
     * Also compare whats stored to what the java ByteBuffer stores.  Test both default LittleEndian, then test for
     * BigEndian to be sure both work properly
     */
    @Test
    fun unsignedTest() {
        UByteBufferTestHelpJava().apply {
            val buf = populate()

            assertContentEquals(javaBuf.array(), buf.contentBytes.toByteArray())
            verifyContents()

            buf.order = Buffer.ByteOrder.BigEndian
            buf.clear()
            checkPosition(0)

            javaBuf.clear()
            javaBuf.order(ByteOrder.BIG_ENDIAN)
            populate()
            verifyContents()
        }
    }
}