package com.oldguy.common.io

import kotlin.math.max

/**
 * Most of the functionality of a java.util.BitSet
 */
class BitSet(val numberOfBits: Int) {
    private var words =
        LongArray(wordIndex(numberOfBits - 1) + 1) { 0L }
    private val wordsInUse: Int
        get() {
            var i = words.size - 1
            while (i >= 0) {
                if (words[i] != 0L) break
                i--
            }
            return i + 1
        }

    val length: Int
        get() {
            if (wordsInUse == 0)
                return 0

            return BITS_PER_WORD * (wordsInUse - 1) +
                    (BITS_PER_WORD - numberOfLeadingZeros(words[wordsInUse - 1]))
        }

    /**
     * Initializes a new bit set containing all the bits in the given byte
     * buffer between its position and limit.
     *
     * <p>The byte buffer is not modified by this method, and no
     * reference to the buffer is retained by the bit set.
     *
     * @param bytes a byte array containing a little-endian representation
     *        of a sequence of bits, to be
     *        used as the initial bits of the new bit set
     */
    constructor(bytes: ByteArray, bitsCount: Int = bytes.size * 8) : this(bitsCount) {
        transformBuffer(ByteBuffer(bytes))
    }

    /**
     * Initializes a new bit set containing all the bits in the given byte
     * buffer between its position and limit.
     *
     * <p>The byte buffer is not modified by this method, and no
     * reference to the buffer is retained by the bit set.
     *
     * @param buffer a byte buffer containing a little-endian representation
     *        of a sequence of bits between its position and limit, to be
     *        used as the initial bits of the new bit set
     */
    constructor(buffer: ByteBuffer, bitsCount: Int = buffer.remaining * 8) : this(bitsCount) {
        transformBuffer(buffer)
    }

    /**
     * Initializes a new bit set containing all the bits in the given byte
     * buffer between its position and limit.
     *
     * <p>The byte buffer content is not modified by this method, but the position ill be set to limit.
     * Bitmask is treated as LittleEndian for the bytes containing the mask.
     *
     * @param buffer a byte buffer containing a little-endian representation
     *        of a sequence of bits between its position and limit, to be
     *        used as the initial bits of the new bit set
     */
    private fun transformBuffer(buffer: ByteBuffer) {
        words = LongArray((buffer.remaining + 7) / 8) { 0 }
        var i = 0
        while (buffer.remaining >= 8) words[i++] = buffer.long

        var j = 0
        while (buffer.remaining > 0) {
            words[i] = words[i] or ((buffer.byte.toLong() and 0xffL) shl (8 * j))
            j++
        }
    }

    val empty: Boolean
        get() {
            return wordsInUse == 0
        }

    fun toByteArray(): ByteArray {
        val n = wordsInUse
        if (n == 0) return ByteArray(0)
        var len = 8 * (n - 1)

        var x = words[n - 1]
        while (x != 0L) {
            len++
            x = x ushr 8
        }

        val sz = if (numberOfBits % 8 > 0) (numberOfBits / 8) + 1 else numberOfBits / 8
        val bytes = ByteArray(sz)
        val bb = ByteBuffer(bytes)
        bb.order = Buffer.ByteOrder.LittleEndian
        for (i in 0 until n - 1) bb.long = words[i]
        x = words[n - 1]
        while (x != 0L) {
            bb.byte = (x and 0xff).toByte()
            x = x ushr 8
        }
        return bytes
    }

    /**
     * Sets the bit at the specified index to the complement of its
     * current value.
     *
     * @param bitIndex the index of the bit to flip
     * @throws IndexOutOfBoundsException if the specified index is negative
     */
    fun flip(bitIndex: Int) {
        if (bitIndex < 0) throw IndexOutOfBoundsException("bitIndex < 0: $bitIndex")
        val wordIndex = wordIndex(bitIndex)
        expandTo(wordIndex)
        words[wordIndex] = words[wordIndex] xor (1L shl bitIndex)
        checkInvariants()
    }

    /**
     * Sets each bit from the specified `fromIndex` (inclusive) to the
     * specified `toIndex` (exclusive) to the complement of its current
     * value.
     *
     * @param fromIndex index of the first bit to flip
     * @param toIndex index after the last bit to flip
     * @throws IndexOutOfBoundsException if `fromIndex` is negative,
     * or `toIndex` is negative, or `fromIndex` is
     * larger than `toIndex`
     * @since 1.4
     */
    fun flip(fromIndex: Int, toIndex: Int) {
        checkRange(fromIndex, toIndex)
        if (fromIndex == toIndex) return
        val startWordIndex: Int = wordIndex(fromIndex)
        val endWordIndex: Int = wordIndex(toIndex - 1)
        expandTo(endWordIndex)
        val firstWordMask: Long = WORD_MASK shl fromIndex
        val lastWordMask: Long = WORD_MASK ushr -toIndex
        if (startWordIndex == endWordIndex) {
            // Case 1: One word
            words[startWordIndex] =
                words[startWordIndex] xor (firstWordMask and lastWordMask)
        } else {
            // Case 2: Multiple words
            // Handle first word
            words[startWordIndex] = words[startWordIndex] xor firstWordMask

            // Handle intermediate words, if any
            for (i in startWordIndex + 1 until endWordIndex) words[i] =
                words[i] xor WORD_MASK

            // Handle last word
            words[endWordIndex] = words[endWordIndex] xor lastWordMask
        }
        checkInvariants()
    }

    operator fun get(bitIndex: Int): Boolean {
        if (bitIndex < 0) throw IndexOutOfBoundsException("bitIndex < 0: $bitIndex")

        checkInvariants()

        val wordIndex = wordIndex(bitIndex)
        return (wordIndex < wordsInUse &&
                words[wordIndex] and (1L shl bitIndex) != 0L)
    }

    operator fun set(bitIndex: Int, on: Boolean) {
        if (!on) {
            clear(bitIndex)
            return
        }
        if (bitIndex < 0) throw IndexOutOfBoundsException("bitIndex < 0: $bitIndex")
        val wordIndex: Int = wordIndex(bitIndex)
        expandTo(wordIndex)
        words[wordIndex] = words[wordIndex] or (1L shl bitIndex) // Restores invariants
        checkInvariants()
    }

    /**
     * find all set bits at or after startIndex. For each bit, invoke the lambda passing the index
     * of the set bit. The lamda can return true to continue, or false to stop iterating.
     *
     * @return number of bits set
     */
    fun iterateSetBits(startIndex: Int = 0, onSetBit: (Int) -> Boolean): Int {
        var index = nextSetBit(startIndex)
        var count = 0
        while (index >= 0) {
            count++
            if (!onSetBit(index)) break
            index = nextSetBit(index + 1)
        }
        return count
    }

    fun nextSetBit(fromIndex: Int): Int {
        if (fromIndex < 0) throw IndexOutOfBoundsException("fromIndex < 0: $fromIndex")
        checkInvariants()
        var u = wordIndex(fromIndex)
        if (u >= wordsInUse) return -1
        var word = words[u] and (WORD_MASK shl fromIndex)
        while (true) {
            if (word != 0L) return u * BITS_PER_WORD + numberOfTrailingZeros(word)
            if (++u == wordsInUse) return -1
            word = words[u]
        }
    }

    /**
     * find all set bits at or after startIndex. For each bit, invoke the lambda passing the index
     * of the set bit. The lamda can return true to continue, or false to stop iterating.
     *
     * @return number of bits set
     */
    fun iterateClearBits(startIndex: Int = 0, onClearedBit: (Int) -> Boolean): Int {
        var index = nextClearBit(startIndex)
        var count = 0
        while (index < numberOfBits) {
            count++
            if (!onClearedBit(index)) break
            index = nextClearBit(index + 1)
        }
        return count
    }

    fun nextClearBit(fromIndex: Int): Int {
        if (fromIndex < 0) throw IndexOutOfBoundsException("fromIndex < 0: $fromIndex")
        checkInvariants()
        var u = wordIndex(fromIndex)
        if (u >= wordsInUse) return fromIndex
        var word = words[u].inv() and (WORD_MASK shl fromIndex)
        while (true) {
            if (word != 0L) return u * BITS_PER_WORD + numberOfTrailingZeros(word)
            if (++u == wordsInUse) return wordsInUse * BITS_PER_WORD
            word = words[u].inv()
        }
    }

    /**
     * Sets the bit specified by the index to `false`.
     *
     * @param bitIndex the index of the bit to be cleared
     * @throws IndexOutOfBoundsException if the specified index is negative
     */
    fun clear(bitIndex: Int) {
        if (bitIndex < 0) throw IndexOutOfBoundsException("bitIndex < 0: $bitIndex")
        val wordIndex = wordIndex(bitIndex)
        if (wordIndex >= wordsInUse) return
        words[wordIndex] = words[wordIndex] and (1L shl bitIndex).inv()
        checkInvariants()
    }

    /**
     * Sets all of the bits in this BitSet to `false`.
     */
    fun clear() {
        for (i in 0..words.size) words[i] = 0
    }

    private constructor(longArray: LongArray) : this(0) {
        words = longArray
    }

    private fun ensureCapacity(wordsRequired: Int) {
        if (words.size < wordsRequired) {
            // Allocate larger of doubled size or required size
            val request: Int = max(2 * words.size, wordsRequired)
            words = words.copyOf(request)
        }
    }

    private fun expandTo(wordIndex: Int) {
        val wordsRequired = wordIndex + 1
        if (wordsInUse < wordsRequired) {
            ensureCapacity(wordsRequired)
        }
    }

    private fun checkInvariants() {
        if (wordsInUse == 0 || words[wordsInUse - 1] != 0L)
            if (wordsInUse >= 0 && wordsInUse <= words.size)
                if (wordsInUse == words.size || words[wordsInUse] == 0L)
                    return
        throw IllegalStateException("BitSet check failed. wordsInUse:$wordsInUse, words:${words.size}")
    }

    override fun toString(): String {
        var text = ""
        var count = 0
        iterateSetBits {
            text = "$text, $it"
            count++ < 50
        }
        return text
    }

    companion object {
        private const val ADDRESS_BITS_PER_WORD = 6
        private const val BITS_PER_WORD = 1 shl ADDRESS_BITS_PER_WORD
        private const val BIT_INDEX_MASK = BITS_PER_WORD - 1
        private const val WORD_MASK = -0x1L

        private fun wordIndex(bitIndex: Int): Int {
            return bitIndex shr ADDRESS_BITS_PER_WORD
        }

        fun numberOfLeadingZeros(i: Long): Int {
            // HD, Figure 5-6
            if (i == 0L) return 64
            var n = 1
            var x = (i ushr 32).toInt()
            if (x == 0) {
                n += 32
                x = i.toInt()
            }
            if (x ushr 16 == 0) {
                n += 16
                x = x shl 16
            }
            if (x ushr 24 == 0) {
                n += 8
                x = x shl 8
            }
            if (x ushr 28 == 0) {
                n += 4
                x = x shl 4
            }
            if (x ushr 30 == 0) {
                n += 2
                x = x shl 2
            }
            n -= x ushr 31
            return n
        }

        fun numberOfTrailingZeros(i: Long): Int {
            // HD, Figure 5-14
            var x: Int
            var y: Int
            if (i == 0L) return 64
            var n = 63
            y = i.toInt()
            if (y != 0) {
                n -= 32
                x = y
            } else x = (i ushr 32).toInt()
            y = x shl 16
            if (y != 0) {
                n -= 16
                x = y
            }
            y = x shl 8
            if (y != 0) {
                n -= 8
                x = y
            }
            y = x shl 4
            if (y != 0) {
                n -= 4
                x = y
            }
            y = x shl 2
            if (y != 0) {
                n -= 2
                x = y
            }
            return n - (x shl 1 ushr 31)
        }

        fun create(longs: LongArray): BitSet {
            var n: Int
            n = longs.size
            while (n > 0 && longs[n - 1] == 0L) {
                n--
            }
            return BitSet(longs.copyOf(n))
        }

        private fun checkRange(fromIndex: Int, toIndex: Int) {
            if (fromIndex < 0) throw IndexOutOfBoundsException("fromIndex < 0: $fromIndex")
            if (toIndex < 0) throw IndexOutOfBoundsException("toIndex < 0: $toIndex")
            if (fromIndex > toIndex) throw IndexOutOfBoundsException(
                "fromIndex: " + fromIndex +
                        " > toIndex: " + toIndex
            )
        }

        fun create(bbIn: ByteBuffer): BitSet {
            val bb = bbIn.slice()
            bb.order = Buffer.ByteOrder.LittleEndian
            var n = bb.remaining
            while (n > 0 && bb.getElementAsInt(n - 1) == 0) {
                n--
            }
            val words = LongArray((n + 7) / 8)
            bb.limit = n
            var i = 0
            while (bb.remaining >= 8) words[i++] = bb.long

            val remaining: Int = bb.remaining
            var j = 0
            while (j < remaining) {
                words[i] = words[i] or ((bb.byte.toLong() and 0xffL) shl (8 * j))
                j++
            }
            return BitSet(words)
        }
    }
}
