package com.aliasadi.tamdid.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Languages the app ships in. Formatting rules depend on it (digits, separators, names). */
enum class AppLanguage(val code: String, val nativeName: String) {
    English("en", "English"),
    Persian("fa", "فارسی"),
    Arabic("ar", "العربية"),
    Turkish("tr", "Türkçe"),
    German("de", "Deutsch");

    val isRightToLeft: Boolean get() = this == Persian || this == Arabic

    internal val groupingSeparator: String
        get() = when (this) {
            English -> ","
            Persian, Arabic -> "\u066C"
            Turkish, German -> "."
        }

    internal val decimalSeparator: String
        get() = when (this) {
            English -> "."
            Persian, Arabic -> "\u066B"
            Turkish, German -> ","
        }

    companion object {
        fun fromCode(code: String): AppLanguage? = entries.firstOrNull { it.code == code }

        /** Maps a BCP-47 code such as "fa-IR"; unknown languages fall back to English. */
        fun forLocale(code: String?): AppLanguage = fromCode(code?.take(2)?.lowercase() ?: "en") ?: English
    }
}

@Serializable
enum class CurrencyCode(val code: String) {
    /** Iranian toman (10 rials). Not an ISO code, but what people actually price things in. */
    @SerialName("IRT") Toman("IRT"),
    @SerialName("USD") UsDollar("USD"),
    @SerialName("EUR") Euro("EUR"),
    @SerialName("GBP") Pound("GBP"),
    @SerialName("AED") Dirham("AED"),
    @SerialName("TRY") Lira("TRY"),
    @SerialName("CAD") CanadianDollar("CAD");

    val fractionDigits: Int get() = if (this == Toman) 0 else 2

    val minorUnitsPerMajor: Long get() = if (fractionDigits == 0) 1 else 100

    /** Full name, for pickers. */
    fun displayName(language: AppLanguage): String = when (language) {
        AppLanguage.English -> listOf("Toman", "US Dollar", "Euro", "British Pound", "UAE Dirham", "Turkish Lira", "Canadian Dollar")
        AppLanguage.Persian -> listOf("تومان", "دلار", "یورو", "پوند", "درهم", "لیر", "دلار کانادا")
        AppLanguage.Arabic -> listOf("تومان", "دولار أمريكي", "يورو", "جنيه إسترليني", "درهم إماراتي", "ليرة تركية", "دولار كندي")
        AppLanguage.Turkish -> listOf("Toman", "ABD Doları", "Euro", "İngiliz Sterlini", "BAE Dirhemi", "Türk Lirası", "Kanada Doları")
        AppLanguage.German -> listOf("Toman", "US-Dollar", "Euro", "Britisches Pfund", "VAE-Dirham", "Türkische Lira", "Kanadischer Dollar")
    }[ordinal]

    /** The word written after an amount in Persian and Arabic ("۱۲۰٬۰۰۰ تومان"). */
    internal fun amountWord(language: AppLanguage): String =
        if (language == AppLanguage.Arabic) listOf("تومان", "دولار", "يورو", "جنيه", "درهم", "ليرة", "دولار كندي")[ordinal]
        else displayName(language)

    /** Symbol used by languages that write it next to the number. */
    internal val symbol: String
        get() = when (this) {
            Toman -> "Toman"
            UsDollar -> "$"
            Euro -> "€"
            Pound -> "£"
            Dirham -> "AED"
            Lira -> "₺"
            CanadianDollar -> "CA$"
        }

    companion object {
        fun fromCode(code: String): CurrencyCode? = entries.firstOrNull { it.code == code }
    }
}

/** An exact amount of money in minor units (cents, or whole tomans). */
@Serializable
data class Money(val amountMinor: Long, val currency: CurrencyCode) {
    companion object {
        fun zero(currency: CurrencyCode) = Money(0, currency)
    }
}

/**
 * Deterministic money formatting, identical on iOS and Android.
 *
 * Platform number formatters differ between OS versions (and between ICU builds for
 * Persian), so the app formats amounts itself and verifies the result with shared fixtures.
 */
object MoneyFormatter {
    fun format(money: Money, language: AppLanguage): String {
        val number = formatNumber(money.amountMinor, money.currency.fractionDigits, language)
        val sign = if (money.amountMinor < 0) "-" else ""
        val currency = money.currency
        return when (language) {
            AppLanguage.Persian, AppLanguage.Arabic -> "$sign$number ${currency.amountWord(language)}"
            AppLanguage.German -> "$sign$number\u00A0${currency.symbol}"
            AppLanguage.English, AppLanguage.Turkish -> when (currency) {
                CurrencyCode.Toman -> "$sign$number Toman"
                CurrencyCode.Dirham -> "${sign}AED $number"
                else -> "$sign${currency.symbol}$number"
            }
        }
    }

    /** Formats an absolute amount: grouping, decimal separator, and digits for [language]. */
    fun formatNumber(amountMinor: Long, fractionDigits: Int, language: AppLanguage): String {
        val magnitude = if (amountMinor == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(amountMinor)
        var divisor = 1L
        repeat(fractionDigits) { divisor *= 10 }
        val whole = (magnitude / divisor).toString()
        val fraction = magnitude % divisor
        val groupSeparator = language.groupingSeparator
        val decimalSeparator = language.decimalSeparator
        val grouped = StringBuilder()
        whole.forEachIndexed { index, c ->
            if (index > 0 && (whole.length - index) % 3 == 0) grouped.append(groupSeparator)
            grouped.append(c)
        }
        if (fractionDigits > 0) {
            grouped.append(decimalSeparator).append(fraction.toString().padStart(fractionDigits, '0'))
        }
        return Digits.localize(grouped.toString(), language)
    }

    /**
     * Parses what a user typed into an amount field, in any of the app's languages.
     *
     * Accepts Latin, Persian and Arabic digits. `٫` and `/` are always the decimal point;
     * `.` or `,` is the decimal point when it is the last separator and is followed by no more
     * digits than the currency allows (so "9,99" and "9.99" are both 9.99, "1.234,56" and
     * "1,234.56" both 1234.56). Every other separator must group digits in threes.
     * Returns null for anything else. Same algorithm as iOS, checked by shared vectors.
     */
    fun parse(input: String, currency: CurrencyCode): Long? {
        val text = Digits.latinize(input).trim(' ')
        val grouping = setOf('.', ',', '\u066C', '\u060C', ' ', '\u00A0', '\u202F', '\'')
        val alwaysDecimal = setOf('\u066B', '/')
        if (text.isEmpty() || !text.all { it in '0'..'9' || it in grouping || it in alwaysDecimal }) return null

        val explicit = text.indices.filter { text[it] in alwaysDecimal }
        if (explicit.size > 1) return null
        var decimalIndex: Int? = explicit.firstOrNull()
        if (decimalIndex == null && currency.fractionDigits > 0) {
            val index = text.indexOfLast { it == '.' || it == ',' }
            if (index >= 0) {
                val trailing = text.substring(index + 1)
                if (trailing.all { it in '0'..'9' } && trailing.length <= currency.fractionDigits) decimalIndex = index
            }
        }

        val wholeChars = decimalIndex?.let { text.substring(0, it) } ?: text
        val fractionChars = decimalIndex?.let { text.substring(it + 1) } ?: ""
        if (!fractionChars.all { it in '0'..'9' } || fractionChars.length > currency.fractionDigits) return null
        if (wholeChars.isEmpty() && fractionChars.isEmpty()) return null

        // Digits between grouping separators: 1-3 first, then exactly 3.
        val groups = wholeChars.split(*grouping.toCharArray())
        if (groups.size > 1 && (groups[0].length !in 1..3 || groups.drop(1).any { it.length != 3 })) return null
        val wholeText = groups.joinToString("").ifEmpty { "0" }
        if (!wholeText.all { it in '0'..'9' }) return null
        val whole = wholeText.toLongOrNull() ?: return null
        val fraction = fractionChars.padEnd(currency.fractionDigits, '0').ifEmpty { "0" }.toLong()
        val scaled = try {
            Math.multiplyExact(whole, currency.minorUnitsPerMajor)
        } catch (_: ArithmeticException) {
            return null
        }
        return scaled + fraction
    }

    /** The amount as a plain editable string ("9.99", "120000"), with Latin digits. */
    fun editableText(amountMinor: Long, currency: CurrencyCode): String {
        val divisor = currency.minorUnitsPerMajor
        val whole = amountMinor / divisor
        if (currency.fractionDigits == 0) return whole.toString()
        val fraction = kotlin.math.abs(amountMinor % divisor).toString().padStart(currency.fractionDigits, '0')
        return "$whole.$fraction"
    }
}

object Digits {
    private const val PERSIAN = "۰۱۲۳۴۵۶۷۸۹"
    private const val ARABIC = "٠١٢٣٤٥٦٧٨٩"

    /** Replaces Latin digits with the language's own digits (Persian or Arabic-Indic). */
    fun localize(text: String, language: AppLanguage): String {
        val table = when (language) {
            AppLanguage.Persian -> PERSIAN
            AppLanguage.Arabic -> ARABIC
            else -> return text
        }
        return buildString(text.length) {
            for (c in text) append(if (c in '0'..'9') table[c - '0'] else c)
        }
    }

    /** Replaces Persian and Arabic-Indic digits with Latin ones. */
    fun latinize(text: String): String = buildString(text.length) {
        for (c in text) {
            val p = PERSIAN.indexOf(c)
            val a = ARABIC.indexOf(c)
            append(
                when {
                    p >= 0 -> '0' + p
                    a >= 0 -> '0' + a
                    else -> c
                },
            )
        }
    }
}
