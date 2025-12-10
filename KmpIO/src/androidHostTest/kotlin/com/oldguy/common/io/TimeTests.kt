package com.oldguy.common.io

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.number
import org.junit.Test
import java.util.*
import java.util.zip.ZipEntry
import kotlin.test.assertEquals

class TimeTests {
    /**
     * Brute forces the conversion from the spec, compares the result to java and to ZipTime.
     * Brute force logic is granular to help with debugging arcane MSDOS time format.
     */
    @Test
    fun timeTest() {
        val modTime: UShort = 0x95C1u
        val modDate:UShort = 0x4AD7u
        val l:Long = modDate.toLong() * 0x10000
        val y = ((modDate.toInt() shr 9) and 0x7f) + 1980
        val jm = ((l shr 21) and 0x0f) - 1
        val d = (l shr 16) and 0x1f
        val h = (modTime.toInt() shr 11) and 0x1f
        val m = (modTime.toInt() shr 5) and 0x3f
        val s = (modTime.toInt() shl 1) and 0x3e
        val cal = GregorianCalendar(y, jm.toInt(), d.toInt(), h, m, s)
        val dt = cal.time
        val e = ZipEntry("x")
        e.time = dt.time
        val cal2 = GregorianCalendar.getInstance().apply { time = Date(e.time) }
        ZipTime(LocalDateTime(2017, 6, 23, 18, 46, s)).apply {
            assertEquals(cal.get(Calendar.YEAR), zipTime.year)
            assertEquals(cal.get(Calendar.MONTH) + 1, zipTime.month.number)
            assertEquals(cal.get(Calendar.DAY_OF_MONTH), zipTime.day)
            assertEquals(cal.get(Calendar.HOUR_OF_DAY), zipTime.hour)
            assertEquals(cal.get(Calendar.MINUTE), zipTime.minute)
            assertEquals(cal.get(Calendar.SECOND), zipTime.second)
            assertEquals(cal2.get(Calendar.YEAR), zipTime.year)
            assertEquals(cal2.get(Calendar.MONTH) + 1, zipTime.month.number)
            assertEquals(cal2.get(Calendar.DAY_OF_MONTH), zipTime.day)
            assertEquals(cal2.get(Calendar.HOUR_OF_DAY), zipTime.hour)
            assertEquals(cal2.get(Calendar.MINUTE), zipTime.minute)
            assertEquals(cal2.get(Calendar.SECOND), zipTime.second)
        }
    }
}