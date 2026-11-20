package com.aliasadi.tamdid.core

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** A month in either calendar system. */
data class CalendarMonth(val system: CalendarSystem, val year: Int, val month: Int) : Comparable<CalendarMonth> {
    val numberOfDays: Int
        get() = when (system) {
            CalendarSystem.Gregorian -> LocalDate.of(year, month, 1).lengthOfMonth()
            CalendarSystem.Jalali -> Jalali.daysInMonth(year, month)
        }

    fun date(day: Int): LocalDate = when (system) {
        CalendarSystem.Gregorian -> LocalDate.of(year, month, day)
        CalendarSystem.Jalali -> Jalali.toGregorian(JalaliDate(year, month, day))
    }

    val firstDay: LocalDate get() = date(1)
    val lastDay: LocalDate get() = date(numberOfDays)

    fun plusMonths(months: Int): CalendarMonth {
        val total = year * 12 + (month - 1) + months
        val y = Math.floorDiv(total, 12)
        return CalendarMonth(system, y, total - y * 12 + 1)
    }

    /**
     * Week rows for a month grid. null cells pad the first and last week.
     * @param firstWeekday ISO weekday the week starts on (6 = Saturday for Jalali, 1 = Monday).
     */
    fun weeks(firstWeekday: Int): List<List<LocalDate?>> {
        val leading = (firstDay.dayOfWeek.value - firstWeekday + 7) % 7
        val cells = MutableList<LocalDate?>(leading) { null }
        for (day in 1..numberOfDays) cells += date(day)
        while (cells.size % 7 != 0) cells += null
        return cells.chunked(7)
    }

    override fun compareTo(other: CalendarMonth): Int = compareValuesBy(this, other, { it.year }, { it.month })

    companion object {
        fun containing(date: LocalDate, system: CalendarSystem): CalendarMonth = when (system) {
            CalendarSystem.Gregorian -> CalendarMonth(system, date.year, date.monthValue)
            CalendarSystem.Jalali -> Jalali.fromGregorian(date).let { CalendarMonth(system, it.year, it.month) }
        }
    }
}

/** Date text in the user's calendar and language. Deterministic, like [MoneyFormatter]. */
object DateText {
    enum class Style {
        /** "Nov 20, 2026" / "۲۹ آبان ۱۴۰۵" */
        Medium,
        /** "Nov 20" / "۲۹ آبان" */
        DayMonth,
        /** "November 2026" / "آبان ۱۴۰۵" */
        MonthYear,
        /** "Nov" / "آبان" */
        ShortMonth,
    }

    fun format(date: LocalDate, calendar: CalendarSystem, language: AppLanguage, style: Style): String {
        val (y, m, d) = when (calendar) {
            CalendarSystem.Gregorian -> Triple(date.year, date.monthValue, date.dayOfMonth)
            CalendarSystem.Jalali -> Jalali.fromGregorian(date).let { Triple(it.year, it.month, it.day) }
        }
        return format(y, m, d, calendar, language, style)
    }

    fun format(month: CalendarMonth, language: AppLanguage, style: Style = Style.MonthYear): String =
        format(month.year, month.month, 1, month.system, language, style)

    private fun format(year: Int, month: Int, day: Int, calendar: CalendarSystem, language: AppLanguage, style: Style): String {
        val full = monthName(month, calendar, language)
        val short = shortMonthName(month, calendar, language)
        val gregorian = calendar == CalendarSystem.Gregorian
        val text = when {
            language == AppLanguage.English && style == Style.Medium -> if (gregorian) "$short $day, $year" else "$day $short $year"
            language == AppLanguage.English && style == Style.DayMonth -> if (gregorian) "$short $day" else "$day $short"
            language == AppLanguage.German && style == Style.Medium -> "$day. $full $year"
            language == AppLanguage.German && style == Style.DayMonth -> "$day. $full"
            style == Style.Medium -> "$day $full $year"
            style == Style.DayMonth -> "$day $full"
            style == Style.MonthYear -> "$full $year"
            else -> short
        }
        return Digits.localize(text, language)
    }

    fun monthName(month: Int, calendar: CalendarSystem, language: AppLanguage): String {
        val names = when (calendar) {
            CalendarSystem.Gregorian -> when (language) {
                AppLanguage.English -> listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
                AppLanguage.Persian -> listOf("ژانویه", "فوریه", "مارس", "آوریل", "مه", "ژوئن", "ژوئیه", "اوت", "سپتامبر", "اکتبر", "نوامبر", "دسامبر")
                AppLanguage.Arabic -> listOf("يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر")
                AppLanguage.Turkish -> listOf("Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran", "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık")
                AppLanguage.German -> listOf("Januar", "Februar", "März", "April", "Mai", "Juni", "Juli", "August", "September", "Oktober", "November", "Dezember")
            }
            CalendarSystem.Jalali -> when (language) {
                AppLanguage.Persian -> listOf("فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور", "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند")
                AppLanguage.Arabic -> listOf("فروردين", "أرديبهشت", "خرداد", "تير", "مرداد", "شهريور", "مهر", "آبان", "آذر", "دي", "بهمن", "إسفند")
                else -> listOf("Farvardin", "Ordibehesht", "Khordad", "Tir", "Mordad", "Shahrivar", "Mehr", "Aban", "Azar", "Dey", "Bahman", "Esfand")
            }
        }
        return names[month - 1]
    }

    /** Three-letter month for Latin-script Gregorian months ("Nov", "Kas"); full names otherwise. */
    fun shortMonthName(month: Int, calendar: CalendarSystem, language: AppLanguage): String {
        val full = monthName(month, calendar, language)
        return if (calendar == CalendarSystem.Gregorian && !language.isRightToLeft) full.take(3) else full
    }

    /** Narrow weekday labels in grid order, starting from [firstWeekday] (ISO). */
    fun weekdaySymbols(firstWeekday: Int, language: AppLanguage): List<String> {
        val symbols = when (language) {
            AppLanguage.English -> listOf("M", "T", "W", "T", "F", "S", "S")
            AppLanguage.Persian -> listOf("د", "س", "چ", "پ", "ج", "ش", "ی")
            AppLanguage.Arabic -> listOf("ن", "ث", "ر", "خ", "ج", "س", "ح")
            AppLanguage.Turkish -> listOf("P", "S", "Ç", "P", "C", "C", "P")
            AppLanguage.German -> listOf("M", "D", "M", "D", "F", "S", "S")
        }
        return (0 until 7).map { symbols[(firstWeekday - 1 + it) % 7] }
    }

    /** The conventional first day of the week: Saturday for the Jalali calendar and in Arabic, Monday otherwise. */
    fun defaultFirstWeekday(calendar: CalendarSystem, language: AppLanguage): Int =
        if (calendar == CalendarSystem.Jalali || language == AppLanguage.Arabic) 6 else 1

    /** "In 3 days", "Tomorrow", "Today" — relative to [today]. */
    fun relative(date: LocalDate, today: LocalDate, language: AppLanguage): String {
        val days = ChronoUnit.DAYS.between(today, date).toInt()
        val n = kotlin.math.abs(days)
        val text = when (language) {
            AppLanguage.English -> when {
                days == 0 -> "Today"; days == 1 -> "Tomorrow"; days == -1 -> "Yesterday"
                days > 1 -> "In $n days"; else -> "$n days ago"
            }
            AppLanguage.Persian -> when {
                days == 0 -> "امروز"; days == 1 -> "فردا"; days == -1 -> "دیروز"
                days > 1 -> "$n روز دیگر"; else -> "$n روز پیش"
            }
            AppLanguage.Arabic -> when (days) {
                0 -> "اليوم"; 1 -> "غدًا"; -1 -> "أمس"
                else -> (if (days > 0) "بعد " else "قبل ") + when {
                    n == 2 -> "يومين"; n <= 10 -> "$n أيام"; else -> "$n يومًا"
                }
            }
            AppLanguage.Turkish -> when {
                days == 0 -> "Bugün"; days == 1 -> "Yarın"; days == -1 -> "Dün"
                days > 1 -> "$n gün sonra"; else -> "$n gün önce"
            }
            AppLanguage.German -> when {
                days == 0 -> "Heute"; days == 1 -> "Morgen"; days == -1 -> "Gestern"
                days > 1 -> "In $n Tagen"; else -> "Vor $n Tagen"
            }
        }
        return Digits.localize(text, language)
    }
}
