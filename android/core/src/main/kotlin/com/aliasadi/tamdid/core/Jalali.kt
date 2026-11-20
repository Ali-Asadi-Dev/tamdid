package com.aliasadi.tamdid.core

import java.time.LocalDate

/** A date in the Solar Hijri (Jalali) calendar, the official calendar in Iran. */
data class JalaliDate(val year: Int, val month: Int, val day: Int) {
    override fun toString(): String =
        "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
}

/**
 * Gregorian ↔ Jalali conversion.
 *
 * Port of the widely used `jalaali-js` algorithm (Borkowski's method with the published
 * leap-year break points), identical to the Swift implementation. Kotlin's `/` and `%`
 * truncate toward zero, exactly like the reference implementation.
 */
object Jalali {
    private val breaks = intArrayOf(
        -61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181, 1210,
        1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178,
    )
    private const val JULIAN_DAY_OF_EPOCH = 2_440_588 // JDN of 1970-01-01

    fun fromGregorian(date: LocalDate): JalaliDate {
        val jdn = date.toEpochDay().toInt() + JULIAN_DAY_OF_EPOCH
        val gy = gregorianYearFromJdn(jdn)
        var jy = gy - 621
        val info = calendarInfo(jy)
        val jdn1f = gregorianToJdn(gy, 3, info.march)
        var k = jdn - jdn1f
        if (k >= 0) {
            if (k <= 185) return JalaliDate(jy, 1 + k / 31, k % 31 + 1)
            k -= 186
        } else {
            jy -= 1
            k += 179
            if (info.leap == 1) k += 1
        }
        return JalaliDate(jy, 7 + k / 30, k % 30 + 1)
    }

    fun toGregorian(date: JalaliDate): LocalDate {
        val info = calendarInfo(date.year)
        val jdn = gregorianToJdn(info.gregorianYear, 3, info.march) +
            (date.month - 1) * 31 - (date.month / 7) * (date.month - 7) + date.day - 1
        return LocalDate.ofEpochDay((jdn - JULIAN_DAY_OF_EPOCH).toLong())
    }

    fun isLeap(year: Int): Boolean = calendarInfo(year).leap == 0

    fun daysInMonth(year: Int, month: Int): Int = when {
        month <= 6 -> 31
        month <= 11 -> 30
        isLeap(year) -> 30
        else -> 29
    }

    private class Info(val leap: Int, val gregorianYear: Int, val march: Int)

    private fun calendarInfo(jy: Int): Info {
        val gy = jy + 621
        var leapJ = -14
        var jp = breaks[0]
        var jump = 0
        for (i in 1 until breaks.size) {
            val jm = breaks[i]
            jump = jm - jp
            if (jy < jm) break
            leapJ += (jump / 33) * 8 + (jump % 33) / 4
            jp = jm
        }
        var n = jy - jp
        leapJ += (n / 33) * 8 + ((n % 33) + 3) / 4
        if (jump % 33 == 4 && jump - n == 4) leapJ += 1
        val leapG = gy / 4 - ((gy / 100 + 1) * 3) / 4 - 150
        val march = 20 + leapJ - leapG
        if (jump - n < 6) n = n - jump + ((jump + 4) / 33) * 33
        var leap = (((n + 1) % 33) - 1) % 4
        if (leap == -1) leap = 4
        return Info(leap, gy, march)
    }

    private fun gregorianToJdn(gy: Int, gm: Int, gd: Int): Int {
        var d = ((gy + (gm - 8) / 6 + 100_100) * 1461) / 4 +
            (153 * ((gm + 9) % 12) + 2) / 5 + gd - 34_840_408
        d = d - ((gy + 100_100 + (gm - 8) / 6) / 100 * 3) / 4 + 752
        return d
    }

    private fun gregorianYearFromJdn(jdn: Int): Int {
        var j = 4 * jdn + 139_361_631
        j = j + ((4 * jdn + 183_187_720) / 146_097 * 3) / 4 * 4 - 3908
        val i = ((j % 1461) / 4) * 5 + 308
        val gm = ((i / 153) % 12) + 1
        return j / 1461 - 100_100 + (8 - gm) / 6
    }
}
