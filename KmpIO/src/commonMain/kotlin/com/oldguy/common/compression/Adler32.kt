package com.oldguy.common.compression

import kotlin.experimental.and

class Adler32 {
    private var s1 = 1L
    private var s2 = 0L
    fun reset(init: Long) {
        s1 = init and 0xffff
        s2 = init shr 16 and 0xffff
    }

    fun reset() {
        s1 = 1L
        s2 = 0L
    }

    val value: Long
        get() = s2 shl 16 or s1

    fun update(buf: ByteArray, indexArg: Int, length: Int) {
        var index = indexArg
        val ff = 0xff.toByte()
        var len = length
        if (len == 1) {
            s1 += (buf[index++] and ff).toLong()
            s2 += s1
            s1 %= BASE.toLong()
            s2 %= BASE.toLong()
            return
        }
        var len1 = len / NMAX
        val len2 = len % NMAX
        while (len1-- > 0) {
            var k = NMAX
            len -= k
            while (k-- > 0) {
                s1 += (buf[index++] and ff).toLong()
                s2 += s1
            }
            s1 %= BASE.toLong()
            s2 %= BASE.toLong()
        }
        var k = len2
        len -= k
        while (k-- > 0) {
            s1 += (buf[index++] and ff).toLong()
            s2 += s1
        }
        s1 %= BASE.toLong()
        s2 %= BASE.toLong()
    }

    fun copy(): Adler32 {
        val foo = Adler32()
        foo.s1 = s1
        foo.s2 = s2
        return foo
    }

    companion object {
        // largest prime smaller than 65536
        private const val BASE = 65521

        // NMAX is the largest n such that 255n(n+1)/2 + (n+1)(BASE-1) <= 2^32-1
        private const val NMAX = 5552

        // The following logic has come from zlib.1.2.
        fun combine(adler1: Long, adler2: Long, len2: Long): Long {
            val BASEL = BASE.toLong()
            var sum1: Long
            var sum2: Long
            val rem: Long // unsigned int
            rem = len2 % BASEL
            sum1 = adler1 and 0xffffL
            sum2 = rem * sum1
            sum2 %= BASEL // MOD(sum2);
            sum1 += (adler2 and 0xffffL) + BASEL - 1
            sum2 += (adler1 shr 16 and 0xffffL) + (adler2 shr 16 and 0xffffL) + BASEL - rem
            if (sum1 >= BASEL) sum1 -= BASEL
            if (sum1 >= BASEL) sum1 -= BASEL
            if (sum2 >= BASEL shl 1) sum2 -= BASEL shl 1
            if (sum2 >= BASEL) sum2 -= BASEL
            return sum1 or (sum2 shl 16)
        }
    }
}
