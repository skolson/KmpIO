package com.oldguy.common.io

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class ByteBufferTestHelpJava(buf: ByteBuffer): ByteBufferTestHelp(buf) {
    val javaBuf: java.nio.ByteBuffer = java.nio.ByteBuffer.allocate(buf.capacity)

    constructor() : this(ByteBuffer(BufferTester.cap)) {
        when (buf.order) {
            Buffer.ByteOrder.LittleEndian -> javaBuf.order(java.nio.ByteOrder.LITTLE_ENDIAN)
            Buffer.ByteOrder.BigEndian -> javaBuf.order(java.nio.ByteOrder.BIG_ENDIAN)
        }
    }

    fun populate(): ByteBuffer {
        buf.byte = BufferTester.byteValue
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

        BufferTester.bytes.apply {
            buf.put(this)
            javaBuf.put(this)
            checkPosition(53 + size)
        }

        buf.char = BufferTester.charValue
        javaBuf.putChar(BufferTester.charValue)
        return buf
    }
}

object BitsetTestHelp {
    fun dumpSetBitIndexes(bitset: BitSet): MutableList<Int> {
        val indexes = MutableList(0) { 0 }
        bitset.iterateSetBits { index ->
            indexes.add(index)
            true
        }
        return indexes
    }
}

class ByteBufferTestsJava {

    /**
     * Populate a buffer with all of the basic types, then reset position and verify that all the same values are retrieved.
     * Also compare whats stored to what the java ByteBuffer stores.  Test both default LittleEndian, then test for
     * BigEndian to be sure both work properly
     */
    @Test
    fun basicTest() {
        ByteBufferTestHelpJava().apply {
            val buf = populate()

            assertContentEquals(javaBuf.array(), buf.contentBytes)
            verifyContents()

            buf.order = Buffer.ByteOrder.BigEndian
            buf.clear()
            checkPosition(0)

            javaBuf.clear()
            javaBuf.order(java.nio.ByteOrder.BIG_ENDIAN)
            populate()
            verifyContents()
        }
    }

    @Test
    fun bitSetTest() {
        val bytes = byteArrayOf(0x40, 0x80.toByte(), 0x01, 0, 0, 0, 0x60, 1)

        val javaBitset = java.util.BitSet.valueOf(bytes)
        val javaIndexes = MutableList(0) { 0 }
        var i: Int = javaBitset.nextSetBit(0)
        while (i >= 0) {
            javaIndexes.add(i)
            i = javaBitset.nextSetBit(i + 1)
        }

        val bitset = BitSet(bytes)
        val indexes = BitsetTestHelp.dumpSetBitIndexes(bitset)

        assertContentEquals(javaIndexes, indexes)
        assertEquals(javaIndexes.size, indexes.size)

        javaIndexes.clear()
        indexes.clear()

        i = javaBitset.nextClearBit(0)
        while (i < javaBitset.size()) {
            javaIndexes.add(i)
            i = javaBitset.nextClearBit(i + 1)
        }

        bitset.iterateClearBits { index ->
            indexes.add(index)
            true
        }

        assertContentEquals(javaIndexes, indexes)

        val smallBytes = byteArrayOf(0xff.toByte(), 0x0, 0)
        val javaBitset2 = java.util.BitSet.valueOf(smallBytes)
        val bitset2 = BitSet(17)
        for (x in 0..17) {
            bitset2[x] = javaBitset2.get(x)
        }
        javaIndexes.clear()
        i = javaBitset2.nextSetBit(0)
        while (i >= 0) {
            javaIndexes.add(i)
            i = javaBitset2.nextSetBit(i + 1)
        }
        val indexes2 = BitsetTestHelp.dumpSetBitIndexes(bitset2)
        assertContentEquals(javaIndexes, indexes2)
        assertEquals(javaIndexes.size, indexes2.size)

        val testBuf = ByteBuffer(4)
        testBuf.let {
            it.int = 0xFF
            it.flip()
            val bitset3 = BitSet(it, 17)
            val indexes3 = BitsetTestHelp.dumpSetBitIndexes(bitset3)
            assertContentEquals(javaIndexes, indexes3)
        }
    }

    @Test
    fun byteArrayExtensionTests() {
        BufferTester.byteArrayExtensionTests()
    }

    @Test
    fun ubyteArrayExtensionTests() {
        BufferTester.ubyteArrayExtensionTests()
    }
}
